package in.supporthub.auth.service;

import in.supporthub.auth.domain.Customer;
import in.supporthub.auth.dto.OtpSendRequest;
import in.supporthub.auth.dto.OtpVerifyRequest;
import in.supporthub.auth.dto.TokenResponse;
import in.supporthub.auth.exception.AuthException;
import in.supporthub.auth.repository.CustomerRepository;
import in.supporthub.shared.security.JwtClaims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Handles the customer OTP-based authentication flow.
 *
 * <p>Flow:
 * <ol>
 *   <li>Customer calls {@link #sendOtp} → OTP generated, stored in Redis, sent via SMS.</li>
 *   <li>Customer calls {@link #verifyOtp} → OTP verified, customer upserted, JWT pair issued.</li>
 *   <li>Customer calls {@link #refresh} with refresh-token cookie → new access token issued.</li>
 *   <li>Customer calls {@link #logout} → refresh token deleted from Redis.</li>
 * </ol>
 *
 * <p>PII rules: phone numbers are NEVER logged. tenantId is always included for traceability.
 */
@Service
@Slf4j
@RequiredArgsConstructor
@Transactional
public class CustomerAuthService {

    private static final String REFRESH_KEY_PREFIX = "refresh:";
    private static final int REFRESH_TTL_DAYS = 30;

    private final OtpService otpService;
    private final SmsService smsService;
    private final JwtService jwtService;
    private final CustomerRepository customerRepository;
    private final StringRedisTemplate stringRedisTemplate;

    /**
     * Generates an OTP for the given phone number and dispatches it via SMS.
     *
     * @param request  contains phone number (validated E.164 format)
     * @param tenantId validated tenant UUID from X-Tenant-ID header
     */
    public void sendOtp(OtpSendRequest request, String tenantId) {
        String otp = otpService.generateAndStoreOtp(request.phone(), tenantId);
        smsService.sendOtp(request.phone(), otp, tenantId);
        log.info("OTP send request processed: tenantId={}", tenantId);
    }

    /**
     * Verifies an OTP and issues a JWT pair on success.
     *
     * <p>If no customer record exists for the phone hash, a new customer is created (registration
     * and login are unified in the OTP flow). On every successful login, {@code lastLoginAt}
     * is updated.
     *
     * @param request  contains phone and OTP
     * @param tenantId validated tenant UUID
     * @return access token response (refresh token delivered separately via cookie)
     */
    public TokenResponse verifyOtp(OtpVerifyRequest request, String tenantId) {
        otpService.verifyOtp(request.phone(), tenantId, request.otp());

        UUID tenantUuid = UUID.fromString(tenantId);
        String phoneHash = hashPhone(request.phone());

        Customer customer = customerRepository
                .findByTenantIdAndPhoneHash(tenantUuid, phoneHash)
                .orElseGet(() -> createNewCustomer(tenantUuid, phoneHash));

        customer.setLastLoginAt(Instant.now());
        customer = customerRepository.save(customer);

        String accessToken = jwtService.generateAccessToken(
                customer.getId(), tenantUuid, JwtClaims.ROLE_CUSTOMER, JwtClaims.TYPE_CUSTOMER);
        String refreshToken = jwtService.generateRefreshToken();

        storeRefreshToken(refreshToken, customer.getId().toString(), tenantId, JwtClaims.TYPE_CUSTOMER);

        log.info("Customer OTP verified, tokens issued: customerId={}, tenantId={}",
                customer.getId(), tenantId);

        return TokenResponse.of(accessToken, jwtService.getAccessTokenExpiry(), JwtClaims.ROLE_CUSTOMER);
    }

    /**
     * Validates a refresh token and issues a new access token.
     *
     * @param refreshToken opaque refresh token UUID from httpOnly cookie
     * @param tenantId     tenant UUID from request context
     * @return new access token response
     */
    public TokenResponse refresh(String refreshToken, String tenantId) {
        String redisKey = REFRESH_KEY_PREFIX + refreshToken;
        String subjectId = (String) stringRedisTemplate.opsForHash().get(redisKey, "subjectId");

        if (subjectId == null) {
            log.info("Refresh token not found or expired: tenantId={}", tenantId);
            throw new AuthException("Refresh token is invalid or expired. Please log in again.");
        }

        String storedTenantId = (String) stringRedisTemplate.opsForHash().get(redisKey, "tenantId");
        if (!tenantId.equals(storedTenantId)) {
            log.warn("Refresh token tenant mismatch: requestTenantId={}", tenantId);
            throw new AuthException("Refresh token is invalid. Please log in again.");
        }

        String accessToken = jwtService.generateAccessToken(
                UUID.fromString(subjectId),
                UUID.fromString(tenantId),
                JwtClaims.ROLE_CUSTOMER,
                JwtClaims.TYPE_CUSTOMER);

        log.info("Access token refreshed: subjectId={}, tenantId={}", subjectId, tenantId);
        return TokenResponse.of(accessToken, jwtService.getAccessTokenExpiry(), JwtClaims.ROLE_CUSTOMER);
    }

    /**
     * Invalidates a refresh token by deleting it from Redis.
     *
     * @param refreshToken opaque refresh token UUID from httpOnly cookie
     */
    public void logout(String refreshToken) {
        String redisKey = REFRESH_KEY_PREFIX + refreshToken;
        Boolean deleted = stringRedisTemplate.delete(redisKey);
        log.info("Customer logout: refreshTokenDeleted={}", deleted);
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    private Customer createNewCustomer(UUID tenantId, String phoneHash) {
        log.info("Creating new customer record: tenantId={}", tenantId);
        return customerRepository.save(
                Customer.builder()
                        .tenantId(tenantId)
                        .phoneHash(phoneHash)
                        .isActive(true)
                        .build()
        );
    }

    private void storeRefreshToken(String token, String subjectId, String tenantId, String type) {
        String redisKey = REFRESH_KEY_PREFIX + token;
        stringRedisTemplate.opsForHash().put(redisKey, "subjectId", subjectId);
        stringRedisTemplate.opsForHash().put(redisKey, "tenantId", tenantId);
        stringRedisTemplate.opsForHash().put(redisKey, "type", type);
        stringRedisTemplate.expire(redisKey, REFRESH_TTL_DAYS, TimeUnit.DAYS);
    }

    /**
     * Computes a SHA-256 hash of the phone number for indexed look-ups.
     * This is a deterministic hash — the same phone always produces the same hash.
     * The raw phone number is never stored.
     */
    private static String hashPhone(String phone) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(phone.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }
}
