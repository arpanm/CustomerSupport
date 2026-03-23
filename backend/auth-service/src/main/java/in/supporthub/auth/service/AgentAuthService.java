package in.supporthub.auth.service;

import in.supporthub.auth.domain.AgentRole;
import in.supporthub.auth.domain.AgentUser;
import in.supporthub.auth.dto.AgentLoginRequest;
import in.supporthub.auth.dto.AgentLoginResponse;
import in.supporthub.auth.dto.TokenResponse;
import in.supporthub.auth.dto.TwoFaVerifyRequest;
import in.supporthub.auth.exception.AuthException;
import in.supporthub.auth.repository.AgentUserRepository;
import in.supporthub.shared.security.JwtClaims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Handles agent email/password authentication with optional 2-FA for privileged roles.
 *
 * <p>Flow:
 * <ol>
 *   <li>Agent calls {@link #login} → password verified.
 *       If role is ADMIN/SUPER_ADMIN, a 2-FA email OTP is sent and
 *       {@link AgentLoginResponse#requiresTwoFa(String)} is returned.</li>
 *   <li>ADMIN/SUPER_ADMIN calls {@link #verify2fa} with the email OTP → tokens issued.</li>
 *   <li>AGENT/TEAM_LEAD: tokens issued directly from {@link #login}.</li>
 * </ol>
 *
 * <p>PII rules: email addresses are NEVER included in log output.
 */
@Service
@Slf4j
@RequiredArgsConstructor
@Transactional
public class AgentAuthService {

    private static final String TWO_FA_KEY_PREFIX = "2fa:";
    private static final int TWO_FA_OTP_TTL_SECONDS = 300;
    private static final int REFRESH_TTL_DAYS = 30;
    private static final String REFRESH_KEY_PREFIX = "refresh:";

    private final AgentUserRepository agentUserRepository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final StringRedisTemplate stringRedisTemplate;
    private final JavaMailSender mailSender;

    /**
     * Authenticates an agent with email and password.
     *
     * <p>ADMIN and SUPER_ADMIN roles require a subsequent 2-FA OTP verification.
     * Other roles receive tokens immediately.
     *
     * @param request  login credentials (email + password — NEVER log these)
     * @param tenantId tenant UUID from request context
     * @return login response indicating whether 2-FA is required
     */
    public AgentLoginResponse login(AgentLoginRequest request, String tenantId) {
        UUID tenantUuid = UUID.fromString(tenantId);

        AgentUser agent = agentUserRepository
                .findByTenantIdAndEmailAndIsActiveTrue(tenantUuid, request.email())
                .orElseThrow(() -> {
                    log.info("Agent login failed: agent not found or inactive: tenantId={}", tenantId);
                    return new AuthException();
                });

        if (!passwordEncoder.matches(request.password(), agent.getPasswordHash())) {
            log.info("Agent login failed: invalid password: agentId={}, tenantId={}", agent.getId(), tenantId);
            throw new AuthException();
        }

        // Privileged roles require 2-FA
        if (agent.getRole() == AgentRole.ADMIN || agent.getRole() == AgentRole.SUPER_ADMIN) {
            sendTwoFaOtp(agent, tenantId);
            log.info("Agent 2FA OTP sent: agentId={}, tenantId={}, role={}",
                    agent.getId(), tenantId, agent.getRole());
            return AgentLoginResponse.requiresTwoFa(agent.getId().toString());
        }

        agent.setLastLoginAt(Instant.now());
        agentUserRepository.save(agent);

        TokenResponse tokens = issueTokens(agent, tenantId);
        log.info("Agent login successful: agentId={}, tenantId={}, role={}",
                agent.getId(), tenantId, agent.getRole());
        return AgentLoginResponse.authenticated(tokens);
    }

    /**
     * Completes 2-FA for ADMIN/SUPER_ADMIN agents by verifying the email OTP.
     *
     * @param request  contains agentId and 6-digit 2-FA code
     * @param tenantId tenant UUID from request context
     * @return issued token response
     */
    public TokenResponse verify2fa(TwoFaVerifyRequest request, String tenantId) {
        String twoFaKey = twoFaKey(tenantId, request.agentId());
        String storedOtp = stringRedisTemplate.opsForValue().get(twoFaKey);

        if (storedOtp == null) {
            log.info("2FA verify failed: OTP expired or not found: agentId={}, tenantId={}",
                    request.agentId(), tenantId);
            throw new AuthException("2FA code has expired. Please log in again.");
        }

        if (!storedOtp.equals(request.code())) {
            log.info("2FA verify failed: invalid code: agentId={}, tenantId={}",
                    request.agentId(), tenantId);
            throw new AuthException("Invalid 2FA code. Please try again.");
        }

        stringRedisTemplate.delete(twoFaKey);

        UUID agentUuid = UUID.fromString(request.agentId());
        AgentUser agent = agentUserRepository.findById(agentUuid)
                .orElseThrow(() -> new AuthException("Agent not found."));

        agent.setLastLoginAt(Instant.now());
        agentUserRepository.save(agent);

        TokenResponse tokens = issueTokens(agent, tenantId);
        log.info("Agent 2FA verified, tokens issued: agentId={}, tenantId={}", agent.getId(), tenantId);
        return tokens;
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    private void sendTwoFaOtp(AgentUser agent, String tenantId) {
        String otp = generateSixDigitOtp();
        String twoFaKey = twoFaKey(tenantId, agent.getId().toString());

        stringRedisTemplate.opsForValue().set(twoFaKey, otp, TWO_FA_OTP_TTL_SECONDS, TimeUnit.SECONDS);

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(agent.getEmail());
            message.setSubject("SupportHub Admin Login - Verification Code");
            message.setText("Your admin login verification code is: " + otp
                    + "\n\nThis code expires in 5 minutes. Do not share it with anyone.");
            mailSender.send(message);
            log.info("2FA email dispatched: agentId={}, tenantId={}", agent.getId(), tenantId);
        } catch (Exception ex) {
            log.error("Failed to send 2FA email: agentId={}, tenantId={}, error={}",
                    agent.getId(), tenantId, ex.getMessage(), ex);
            throw new RuntimeException("Failed to send 2FA email. Please try again.", ex);
        }
    }

    private TokenResponse issueTokens(AgentUser agent, String tenantId) {
        String role = agent.getRole().name();
        String accessToken = jwtService.generateAccessToken(
                agent.getId(), UUID.fromString(tenantId), role, JwtClaims.TYPE_AGENT);
        String refreshToken = jwtService.generateRefreshToken();

        storeRefreshToken(refreshToken, agent.getId().toString(), tenantId, JwtClaims.TYPE_AGENT);

        return TokenResponse.of(accessToken, jwtService.getAccessTokenExpiry(), role);
    }

    private void storeRefreshToken(String token, String subjectId, String tenantId, String type) {
        String redisKey = REFRESH_KEY_PREFIX + token;
        stringRedisTemplate.opsForHash().put(redisKey, "subjectId", subjectId);
        stringRedisTemplate.opsForHash().put(redisKey, "tenantId", tenantId);
        stringRedisTemplate.opsForHash().put(redisKey, "type", type);
        stringRedisTemplate.expire(redisKey, REFRESH_TTL_DAYS, TimeUnit.DAYS);
    }

    private static String twoFaKey(String tenantId, String agentId) {
        return TWO_FA_KEY_PREFIX + tenantId + ":" + agentId;
    }

    private static String generateSixDigitOtp() {
        SecureRandom random = new SecureRandom();
        return String.format("%06d", random.nextInt(1_000_000));
    }
}
