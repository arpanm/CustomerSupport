package in.supporthub.auth.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.supporthub.auth.exception.OtpExpiredException;
import in.supporthub.auth.exception.OtpInvalidException;
import in.supporthub.auth.exception.OtpRateLimitException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Manages OTP lifecycle: generation, storage in Redis, rate limiting, and verification.
 *
 * <p>Redis key scheme:
 * <ul>
 *   <li>{@code otp:{tenantId}:{phone}} — stores JSON with {@code otp} and {@code attempts}.
 *       TTL: 300 seconds.</li>
 *   <li>{@code otp:sends:{tenantId}:{phone}:{hourEpoch}} — send counter for rate limiting.
 *       TTL: 3600 seconds (1 hour bucket).</li>
 * </ul>
 *
 * <p>IMPORTANT: Phone numbers and OTP values are NEVER logged by this service.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class OtpService {

    private static final int OTP_LENGTH = 6;
    private static final int OTP_TTL_SECONDS = 300;
    private static final int MAX_VERIFY_ATTEMPTS = 3;
    private static final int MAX_SENDS_PER_HOUR = 3;
    private static final int RATE_LIMIT_TTL_SECONDS = 3600;

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    /**
     * Generates a 6-digit OTP, stores it in Redis, and returns it for SMS dispatch.
     *
     * <p>Rate limited: maximum {@value MAX_SENDS_PER_HOUR} sends per phone per hour.
     * The OTP expires after {@value OTP_TTL_SECONDS} seconds.
     *
     * @param phone    E.164 phone number (NEVER logged)
     * @param tenantId tenant UUID string
     * @return the generated OTP (caller must pass to {@link SmsService} or email)
     * @throws OtpRateLimitException if the rate limit has been exceeded
     */
    public String generateAndStoreOtp(String phone, String tenantId) {
        enforceRateLimit(phone, tenantId);

        String otp = generateSecureOtp();
        String otpKey = otpKey(tenantId, phone);

        Map<String, Object> otpData = new HashMap<>();
        otpData.put("otp", otp);
        otpData.put("attempts", 0);

        try {
            String json = objectMapper.writeValueAsString(otpData);
            stringRedisTemplate.opsForValue().set(otpKey, json, OTP_TTL_SECONDS, TimeUnit.SECONDS);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize OTP data", ex);
        }

        incrementSendCounter(phone, tenantId);

        log.info("OTP generated and stored: tenantId={}, otpKey={}",
                tenantId, maskKey(otpKey));
        return otp;
    }

    /**
     * Verifies the supplied OTP against the stored value.
     *
     * <p>On success: deletes the Redis key. On failure: increments attempts.
     * After {@value MAX_VERIFY_ATTEMPTS} failed attempts the key is deleted.
     *
     * @param phone    E.164 phone number (NEVER logged)
     * @param tenantId tenant UUID string
     * @param inputOtp user-supplied OTP to verify (NEVER logged)
     * @return {@code true} if the OTP is correct
     * @throws OtpExpiredException if no OTP is stored for this phone/tenant
     * @throws OtpInvalidException if the OTP is wrong (and not yet max attempts)
     */
    public boolean verifyOtp(String phone, String tenantId, String inputOtp) {
        String otpKey = otpKey(tenantId, phone);
        String json = stringRedisTemplate.opsForValue().get(otpKey);

        if (json == null) {
            log.info("OTP verify: key not found (expired or never sent): tenantId={}", tenantId);
            throw new OtpExpiredException();
        }

        Map<String, Object> otpData = deserializeOtpData(json);
        String storedOtp = (String) otpData.get("otp");
        int attempts = ((Number) otpData.get("attempts")).intValue();

        if (!storedOtp.equals(inputOtp)) {
            int newAttempts = attempts + 1;
            if (newAttempts >= MAX_VERIFY_ATTEMPTS) {
                stringRedisTemplate.delete(otpKey);
                log.info("OTP verify: max attempts exceeded, key deleted: tenantId={}", tenantId);
            } else {
                otpData.put("attempts", newAttempts);
                persistOtpData(otpKey, otpData);
                log.info("OTP verify: invalid attempt {}/{}: tenantId={}", newAttempts, MAX_VERIFY_ATTEMPTS, tenantId);
            }
            throw new OtpInvalidException();
        }

        stringRedisTemplate.delete(otpKey);
        log.info("OTP verify: success, key deleted: tenantId={}", tenantId);
        return true;
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    private void enforceRateLimit(String phone, String tenantId) {
        String hourBucket = String.valueOf(Instant.now().truncatedTo(ChronoUnit.HOURS).getEpochSecond());
        String rateKey = rateLimitKey(tenantId, phone, hourBucket);

        String countStr = stringRedisTemplate.opsForValue().get(rateKey);
        int count = countStr == null ? 0 : Integer.parseInt(countStr);

        if (count >= MAX_SENDS_PER_HOUR) {
            log.info("OTP rate limit exceeded: tenantId={}", tenantId);
            throw new OtpRateLimitException();
        }
    }

    private void incrementSendCounter(String phone, String tenantId) {
        String hourBucket = String.valueOf(Instant.now().truncatedTo(ChronoUnit.HOURS).getEpochSecond());
        String rateKey = rateLimitKey(tenantId, phone, hourBucket);
        Long newCount = stringRedisTemplate.opsForValue().increment(rateKey);
        if (newCount != null && newCount == 1L) {
            // First increment — set expiry for the rate-limit bucket
            stringRedisTemplate.expire(rateKey, RATE_LIMIT_TTL_SECONDS, TimeUnit.SECONDS);
        }
    }

    private String generateSecureOtp() {
        SecureRandom random = new SecureRandom();
        int bound = (int) Math.pow(10, OTP_LENGTH);
        return String.format("%0" + OTP_LENGTH + "d", random.nextInt(bound));
    }

    private static String otpKey(String tenantId, String phone) {
        return "otp:" + tenantId + ":" + phone;
    }

    private static String rateLimitKey(String tenantId, String phone, String hourBucket) {
        return "otp:sends:" + tenantId + ":" + phone + ":" + hourBucket;
    }

    /**
     * Masks the tail of a Redis key in log output to avoid leaking PII.
     * The phone number is the suffix of the key — we replace it with a placeholder.
     */
    private static String maskKey(String key) {
        int lastColon = key.lastIndexOf(':');
        if (lastColon > 0) {
            return key.substring(0, lastColon) + ":***";
        }
        return "otp:***";
    }

    private Map<String, Object> deserializeOtpData(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to deserialize OTP data from Redis", ex);
        }
    }

    private void persistOtpData(String key, Map<String, Object> data) {
        try {
            Long remainingTtl = stringRedisTemplate.getExpire(key, TimeUnit.SECONDS);
            long ttl = (remainingTtl != null && remainingTtl > 0) ? remainingTtl : OTP_TTL_SECONDS;
            stringRedisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(data), ttl, TimeUnit.SECONDS);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize updated OTP data", ex);
        }
    }
}
