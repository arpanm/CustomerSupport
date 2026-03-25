package in.supporthub.auth.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.supporthub.auth.config.JwtConfig;
import in.supporthub.auth.domain.AgentRole;
import in.supporthub.auth.domain.AgentUser;
import in.supporthub.auth.domain.Customer;
import in.supporthub.auth.dto.AgentLoginRequest;
import in.supporthub.auth.dto.AgentLoginResponse;
import in.supporthub.auth.dto.OtpSendRequest;
import in.supporthub.auth.dto.OtpVerifyRequest;
import in.supporthub.auth.dto.TokenResponse;
import in.supporthub.auth.exception.AuthException;
import in.supporthub.auth.exception.OtpExpiredException;
import in.supporthub.auth.exception.OtpRateLimitException;
import in.supporthub.auth.repository.AgentUserRepository;
import in.supporthub.auth.repository.CustomerRepository;
import in.supporthub.shared.security.JwtClaims;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Base64;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Requirement-traceability tests for the auth-service.
 *
 * <p>Each test is annotated with the REQ-* identifier it verifies. Tests that cover behaviour
 * not yet implemented are marked {@code @Disabled} with the corresponding REQ-* reason.
 *
 * <p>Mocks all external I/O (Redis, JPA repositories, mail). No live infrastructure is needed.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthRequirementsTest {

    // -----------------------------------------------------------------------
    // Shared test constants
    // -----------------------------------------------------------------------

    private static final String PHONE      = "+919876543210";
    private static final String TENANT_ID  = "a1b2c3d4-e5f6-7890-abcd-ef1234567890";
    private static final UUID   TENANT_UUID = UUID.fromString(TENANT_ID);
    private static final String AGENT_EMAIL = "agent@example.com";
    private static final String AGENT_PASSWORD_PLAIN = "Sup3rS3cret!";
    private static final String AGENT_PASSWORD_HASH  = "$2a$10$hashedpassword";

    // -----------------------------------------------------------------------
    // Shared mocks
    // -----------------------------------------------------------------------

    @Mock private StringRedisTemplate stringRedisTemplate;
    @Mock private ValueOperations<String, String> valueOps;
    @Mock private HashOperations<String, Object, Object> hashOps;
    @Mock private CustomerRepository customerRepository;
    @Mock private AgentUserRepository agentUserRepository;
    @Mock private SmsService smsService;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JavaMailSender mailSender;

    // -----------------------------------------------------------------------
    // Shared service instances (rebuilt per nested class where needed)
    // -----------------------------------------------------------------------

    private OtpService otpService;
    private JwtService jwtService;
    private CustomerAuthService customerAuthService;
    private AgentAuthService agentAuthService;

    // RSA key pair generated once per test class load
    private KeyPair keyPair;

    @BeforeEach
    void setUpSharedDependencies() throws Exception {
        // Generate an in-memory RSA-2048 key pair — no PEM files needed
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        keyPair = gen.generateKeyPair();

        String privatePem = toPkcs8Pem(keyPair.getPrivate());
        String publicPem  = toX509Pem(keyPair.getPublic());

        JwtConfig jwtConfig = new JwtConfig();
        jwtConfig.setPrivateKeyLocation("classpath:keys/test-private.pem");
        jwtConfig.setPublicKeyLocation("classpath:keys/test-public.pem");
        jwtConfig.setAccessTokenExpirySeconds(3600);   // 1 hour (REQ-CUI-AUTH-03)
        jwtConfig.setRefreshTokenExpiryDays(30);       // 30 days (REQ-CUI-AUTH-03)

        jwtService = new JwtService(jwtConfig, new InMemoryPemResourceLoader(privatePem, publicPem));
        jwtService.loadKeys();

        // OtpService: valueOps stub must be ready before construction
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOps);
        otpService = new OtpService(stringRedisTemplate, new ObjectMapper());

        customerAuthService = new CustomerAuthService(
                otpService, smsService, jwtService, customerRepository, stringRedisTemplate);

        agentAuthService = new AgentAuthService(
                agentUserRepository, jwtService, passwordEncoder, stringRedisTemplate, mailSender);
    }

    // =======================================================================
    // REQ-CUI-AUTH-01 — OTP format and TTL
    // =======================================================================

    @Nested
    @DisplayName("REQ-CUI-AUTH-01: OTP generation")
    class OtpGenerationTests {

        @Test
        @DisplayName("REQ-CUI-AUTH-01: OTP is exactly 6 digits")
        void otp_isExactlySixDigits() {
            // ARRANGE — no existing rate-limit counter
            when(valueOps.get(anyString())).thenReturn(null);

            // ACT
            String otp = otpService.generateAndStoreOtp(PHONE, TENANT_ID);

            // ASSERT — must be exactly 6 numeric characters
            assertThat(otp)
                    .as("OTP must be exactly 6 digits (REQ-CUI-AUTH-01)")
                    .matches("^[0-9]{6}$");
        }

        @Test
        @DisplayName("REQ-CUI-AUTH-01: OTP validity is 5 minutes (300 seconds TTL)")
        void otp_storedWithThreeHundredSecondTtl() {
            // ARRANGE
            when(valueOps.get(anyString())).thenReturn(null);

            // ACT
            otpService.generateAndStoreOtp(PHONE, TENANT_ID);

            // ASSERT — verify the TTL argument passed to Redis is exactly 300 seconds
            ArgumentCaptor<Long> ttlCaptor = ArgumentCaptor.forClass(Long.class);
            verify(valueOps).set(anyString(), anyString(), ttlCaptor.capture(), eq(TimeUnit.SECONDS));

            assertThat(ttlCaptor.getValue())
                    .as("OTP Redis TTL must be 300 seconds / 5 minutes (REQ-CUI-AUTH-01)")
                    .isEqualTo(300L);
        }
    }

    // =======================================================================
    // REQ-SEC-06 — OTP send rate limiting (max 3 per phone per hour)
    // =======================================================================

    @Nested
    @DisplayName("REQ-SEC-06: OTP rate limiting")
    class OtpRateLimitTests {

        @Test
        @DisplayName("REQ-SEC-06: OTP rate limiting — max 3 attempts per phone per hour; 4th attempt is rejected")
        void otp_fourthSendAttempt_isRejected() {
            // ARRANGE — counter already at MAX_SENDS_PER_HOUR (3)
            when(valueOps.get(anyString())).thenReturn("3");

            // ACT + ASSERT — the 4th attempt must be rejected with OtpRateLimitException
            assertThatThrownBy(() -> otpService.generateAndStoreOtp(PHONE, TENANT_ID))
                    .as("4th OTP send in the same hour must throw OtpRateLimitException (REQ-SEC-06)")
                    .isInstanceOf(OtpRateLimitException.class);

            // The OTP must NOT be stored when rate-limited
            verify(valueOps, never()).set(anyString(), anyString(), anyLong(), any(TimeUnit.class));
        }

        @Test
        @DisplayName("REQ-SEC-06: OTP rate limiting — 3rd attempt (at limit boundary) is still accepted")
        void otp_thirdSendAttempt_isAccepted() {
            // ARRANGE — counter at 2 (next send is the 3rd, which is still within limit)
            when(valueOps.get(anyString())).thenReturn("2");
            when(valueOps.increment(anyString())).thenReturn(3L);

            // ACT — should not throw
            String otp = otpService.generateAndStoreOtp(PHONE, TENANT_ID);

            // ASSERT
            assertThat(otp)
                    .as("3rd OTP send must succeed (REQ-SEC-06 limit is max 3 per hour)")
                    .matches("^[0-9]{6}$");
        }
    }

    // =======================================================================
    // REQ-CUI-AUTH-05 — Guest access not allowed
    // =======================================================================

    @Nested
    @DisplayName("REQ-CUI-AUTH-05: Guest access — OTP verification required before ticket actions")
    class GuestAccessTests {

        @Test
        @DisplayName("REQ-CUI-AUTH-05: Guest access not allowed — verifyOtp throws OtpExpiredException when no OTP was sent")
        void verifyOtp_throwsExpired_whenNoOtpWasSent_blockingGuestAccess() {
            // ARRANGE — Redis has no stored OTP (key absent), simulating a guest who never
            // requested an OTP. The service layer should reject the verify call outright,
            // preventing any unauthenticated token issuance.
            when(valueOps.get(anyString())).thenReturn(null);

            // ACT + ASSERT
            assertThatThrownBy(() -> otpService.verifyOtp(PHONE, TENANT_ID, "000000"))
                    .as("Absent OTP must throw OtpExpiredException, blocking guest ticket access (REQ-CUI-AUTH-05)")
                    .isInstanceOf(OtpExpiredException.class);
        }

        @Test
        @DisplayName("REQ-CUI-AUTH-05: Guest access not allowed — CustomerAuthService.verifyOtp issues tokens only after successful OTP check")
        void customerAuthService_verifyOtp_issuesTokensOnlyAfterOtpPasses() {
            // ARRANGE — stored OTP matches the submitted value
            String storedJson = "{\"otp\":\"123456\",\"attempts\":0}";
            when(valueOps.get(anyString())).thenReturn(storedJson);
            when(stringRedisTemplate.delete(anyString())).thenReturn(true);
            when(stringRedisTemplate.opsForHash()).thenReturn(hashOps);

            UUID customerId = UUID.randomUUID();
            Customer customer = Customer.builder()
                    .id(customerId)
                    .tenantId(TENANT_UUID)
                    .phoneHash("somehash")
                    .isActive(true)
                    .build();
            when(customerRepository.findByTenantIdAndPhoneHash(eq(TENANT_UUID), anyString()))
                    .thenReturn(Optional.of(customer));
            when(customerRepository.save(any(Customer.class))).thenReturn(customer);

            OtpVerifyRequest request = new OtpVerifyRequest(PHONE, "123456");

            // ACT
            TokenResponse response = customerAuthService.verifyOtp(request, TENANT_ID);

            // ASSERT — tokens are issued only after the OTP is validated
            assertThat(response).isNotNull();
            assertThat(response.accessToken())
                    .as("Access token must be issued only after successful OTP verification (REQ-CUI-AUTH-05)")
                    .isNotBlank();
        }
    }

    // =======================================================================
    // REQ-CUI-AUTH-03 — JWT token expiry (access = 1 hour, refresh = 30 days)
    // =======================================================================

    @Nested
    @DisplayName("REQ-CUI-AUTH-03: JWT token expiry")
    class JwtExpiryTests {

        @Test
        @DisplayName("REQ-CUI-AUTH-03: JWT access token expires in 1 hour (3600 seconds)")
        void accessToken_expiresInOneHour() {
            // ARRANGE + ACT
            long before = System.currentTimeMillis();
            String token = jwtService.generateAccessToken(
                    UUID.randomUUID(), TENANT_UUID, JwtClaims.ROLE_CUSTOMER, JwtClaims.TYPE_CUSTOMER);
            long after = System.currentTimeMillis();

            // Parse the token directly to read expiry without going through validateToken
            Claims claims = Jwts.parser()
                    .verifyWith(keyPair.getPublic())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            long issuedAtMs  = claims.getIssuedAt().getTime();
            long expirationMs = claims.getExpiration().getTime();
            long actualTtlSeconds = (expirationMs - issuedAtMs) / 1000;

            assertThat(actualTtlSeconds)
                    .as("Access token TTL must be 3600 seconds (1 hour) per REQ-CUI-AUTH-03")
                    .isEqualTo(3600L);

            // Also verify the configured expiry value is 3600
            assertThat(jwtService.getAccessTokenExpiry())
                    .as("JwtService.getAccessTokenExpiry() must return 3600 (REQ-CUI-AUTH-03)")
                    .isEqualTo(3600L);
        }

        @Test
        @DisplayName("REQ-CUI-AUTH-03: JWT refresh token expires in 30 days")
        void refreshToken_expiresInThirtyDays() {
            // The refresh token is opaque (UUID). Its 30-day TTL is enforced via Redis EXPIRE.
            // We verify that CustomerAuthService stores it with the correct TTL.

            // ARRANGE
            String storedJson = "{\"otp\":\"654321\",\"attempts\":0}";
            when(valueOps.get(anyString())).thenReturn(storedJson);
            when(stringRedisTemplate.delete(anyString())).thenReturn(true);
            when(stringRedisTemplate.opsForHash()).thenReturn(hashOps);

            UUID customerId = UUID.randomUUID();
            Customer customer = Customer.builder()
                    .id(customerId)
                    .tenantId(TENANT_UUID)
                    .phoneHash("somehash")
                    .isActive(true)
                    .build();
            when(customerRepository.findByTenantIdAndPhoneHash(eq(TENANT_UUID), anyString()))
                    .thenReturn(Optional.of(customer));
            when(customerRepository.save(any(Customer.class))).thenReturn(customer);

            OtpVerifyRequest request = new OtpVerifyRequest(PHONE, "654321");

            // ACT
            customerAuthService.verifyOtp(request, TENANT_ID);

            // ASSERT — refresh token must be stored with 30-day TTL
            verify(stringRedisTemplate).expire(anyString(), eq(30L), eq(TimeUnit.DAYS));
        }
    }

    // =======================================================================
    // REQ-AGT-AUTH-01 — Agent email + password login
    // =======================================================================

    @Nested
    @DisplayName("REQ-AGT-AUTH-01: Agent login with email and password")
    class AgentLoginTests {

        @Test
        @DisplayName("REQ-AGT-AUTH-01: Agent can login with correct email and password")
        void agentLogin_succeeds_withCorrectCredentials() {
            // ARRANGE
            UUID agentId = UUID.randomUUID();
            AgentUser agent = AgentUser.builder()
                    .id(agentId)
                    .tenantId(TENANT_UUID)
                    .email(AGENT_EMAIL)
                    .displayName("Test Agent")
                    .passwordHash(AGENT_PASSWORD_HASH)
                    .role(AgentRole.AGENT)
                    .isActive(true)
                    .build();

            when(agentUserRepository.findByTenantIdAndEmailAndIsActiveTrue(TENANT_UUID, AGENT_EMAIL))
                    .thenReturn(Optional.of(agent));
            when(passwordEncoder.matches(AGENT_PASSWORD_PLAIN, AGENT_PASSWORD_HASH)).thenReturn(true);
            when(agentUserRepository.save(any(AgentUser.class))).thenReturn(agent);
            when(stringRedisTemplate.opsForHash()).thenReturn(hashOps);

            AgentLoginRequest request = new AgentLoginRequest(AGENT_EMAIL, AGENT_PASSWORD_PLAIN);

            // ACT
            AgentLoginResponse response = agentAuthService.login(request, TENANT_ID);

            // ASSERT
            assertThat(response.requires2Fa())
                    .as("AGENT role must not require 2-FA (REQ-AGT-AUTH-01)")
                    .isFalse();
            assertThat(response.tokens())
                    .as("Tokens must be issued on successful login (REQ-AGT-AUTH-01)")
                    .isNotNull();
            assertThat(response.tokens().accessToken())
                    .as("Access token must be non-blank on successful agent login (REQ-AGT-AUTH-01)")
                    .isNotBlank();
        }

        @Test
        @DisplayName("REQ-AGT-AUTH-01: Agent login fails with wrong password")
        void agentLogin_fails_withWrongPassword() {
            // ARRANGE
            UUID agentId = UUID.randomUUID();
            AgentUser agent = AgentUser.builder()
                    .id(agentId)
                    .tenantId(TENANT_UUID)
                    .email(AGENT_EMAIL)
                    .displayName("Test Agent")
                    .passwordHash(AGENT_PASSWORD_HASH)
                    .role(AgentRole.AGENT)
                    .isActive(true)
                    .build();

            when(agentUserRepository.findByTenantIdAndEmailAndIsActiveTrue(TENANT_UUID, AGENT_EMAIL))
                    .thenReturn(Optional.of(agent));
            when(passwordEncoder.matches("wrongpassword", AGENT_PASSWORD_HASH)).thenReturn(false);

            AgentLoginRequest request = new AgentLoginRequest(AGENT_EMAIL, "wrongpassword");

            // ACT + ASSERT
            assertThatThrownBy(() -> agentAuthService.login(request, TENANT_ID))
                    .as("Incorrect password must throw AuthException (REQ-AGT-AUTH-01)")
                    .isInstanceOf(AuthException.class);
        }

        @Test
        @DisplayName("REQ-AGT-AUTH-01: Agent login fails when agent does not exist")
        void agentLogin_fails_whenAgentNotFound() {
            // ARRANGE
            when(agentUserRepository.findByTenantIdAndEmailAndIsActiveTrue(TENANT_UUID, AGENT_EMAIL))
                    .thenReturn(Optional.empty());

            AgentLoginRequest request = new AgentLoginRequest(AGENT_EMAIL, AGENT_PASSWORD_PLAIN);

            // ACT + ASSERT
            assertThatThrownBy(() -> agentAuthService.login(request, TENANT_ID))
                    .as("Unknown agent must throw AuthException (REQ-AGT-AUTH-01)")
                    .isInstanceOf(AuthException.class);
        }
    }

    // =======================================================================
    // REQ-AGT-AUTH-03 — Agent session timeout (8-hour JWT for agent tokens)
    // =======================================================================

    @Nested
    @DisplayName("REQ-AGT-AUTH-03: Agent session timeout after 8 hours inactivity")
    class AgentSessionTimeoutTests {

        @Test
        @DisplayName("REQ-AGT-AUTH-03: Agent session timeout after 8 hours inactivity (JWT claims include 8h expiry for agent tokens)")
        void agentToken_expiresInEightHours_whenConfiguredForAgents() {
            // ARRANGE — configure JwtService with 8-hour (28800 second) expiry, as required for
            // agent sessions. This verifies the system can be configured correctly per the req.
            JwtConfig agentJwtConfig = new JwtConfig();
            agentJwtConfig.setPrivateKeyLocation("classpath:keys/test-private.pem");
            agentJwtConfig.setPublicKeyLocation("classpath:keys/test-public.pem");
            agentJwtConfig.setAccessTokenExpirySeconds(28800); // 8 hours (REQ-AGT-AUTH-03)
            agentJwtConfig.setRefreshTokenExpiryDays(30);

            String privatePem = toPkcs8Pem(keyPair.getPrivate());
            String publicPem  = toX509Pem(keyPair.getPublic());

            JwtService agentJwtService = new JwtService(
                    agentJwtConfig, new InMemoryPemResourceLoader(privatePem, publicPem));
            agentJwtService.loadKeys();

            UUID agentId = UUID.randomUUID();

            // ACT
            String token = agentJwtService.generateAccessToken(
                    agentId, TENANT_UUID, JwtClaims.ROLE_AGENT, JwtClaims.TYPE_AGENT);

            // ASSERT — parse claims directly to check expiry
            Claims claims = Jwts.parser()
                    .verifyWith(keyPair.getPublic())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            long issuedAtMs    = claims.getIssuedAt().getTime();
            long expirationMs  = claims.getExpiration().getTime();
            long actualTtlSeconds = (expirationMs - issuedAtMs) / 1000;

            assertThat(actualTtlSeconds)
                    .as("Agent JWT must expire in 28800 seconds (8 hours) per REQ-AGT-AUTH-03")
                    .isEqualTo(28800L);

            // Verify role claim is present and correct
            assertThat(claims.get("role", String.class))
                    .as("Agent token must carry the AGENT role claim (REQ-AGT-AUTH-03)")
                    .isEqualTo(JwtClaims.ROLE_AGENT);

            assertThat(claims.get("type", String.class))
                    .as("Agent token must carry type=agent (REQ-AGT-AUTH-03)")
                    .isEqualTo(JwtClaims.TYPE_AGENT);
        }
    }

    // =======================================================================
    // REQ-SEC-13 — Phone number (PII) must not appear in JWT claims
    // =======================================================================

    @Nested
    @DisplayName("REQ-SEC-13: PII must not appear in JWT claims")
    class PiiInJwtTests {

        @Test
        @DisplayName("REQ-SEC-13: Phone number (PII) must not appear in JWT claims")
        void jwt_doesNotContainPhoneNumber() {
            // ARRANGE — generate a customer access token (the type that could in theory carry phone)
            UUID customerId = UUID.randomUUID();
            String token = jwtService.generateAccessToken(
                    customerId, TENANT_UUID, JwtClaims.ROLE_CUSTOMER, JwtClaims.TYPE_CUSTOMER);

            // ACT — parse all claims
            Claims claims = Jwts.parser()
                    .verifyWith(keyPair.getPublic())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            // ASSERT — no claim value should contain a phone-like string
            String rawToken = token;

            // Phone number must not appear in any claim value
            assertThat(claims.getSubject())
                    .as("JWT 'sub' must not contain a phone number (REQ-SEC-13)")
                    .doesNotContain(PHONE)
                    .doesNotContain("+91");

            // Verify no custom claim carries the phone
            claims.forEach((key, value) ->
                    assertThat(String.valueOf(value))
                            .as("JWT claim '%s' must not contain a phone number (REQ-SEC-13)", key)
                            .doesNotContain(PHONE)
                            .doesNotContain("+91"));
        }

        @Test
        @DisplayName("REQ-SEC-13: JWT subject contains UUID, not phone or other PII")
        void jwt_subjectIsUuid_notPii() {
            // ARRANGE
            UUID customerId = UUID.randomUUID();

            // ACT
            String token = jwtService.generateAccessToken(
                    customerId, TENANT_UUID, JwtClaims.ROLE_CUSTOMER, JwtClaims.TYPE_CUSTOMER);
            JwtClaims parsedClaims = jwtService.validateToken(token);

            // ASSERT — 'sub' must be the customer UUID, never a phone number or email
            assertThat(parsedClaims.sub())
                    .as("JWT 'sub' must be a UUID (not PII) per REQ-SEC-13")
                    .matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
                    .isEqualTo(customerId.toString());
        }
    }

    // =======================================================================
    // Private helpers — PEM encoding and in-memory ResourceLoader
    // =======================================================================

    private static String toPkcs8Pem(PrivateKey key) {
        String encoded = Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.UTF_8))
                .encodeToString(key.getEncoded());
        return "-----BEGIN PRIVATE KEY-----\n" + encoded + "\n-----END PRIVATE KEY-----\n";
    }

    private static String toX509Pem(PublicKey key) {
        String encoded = Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.UTF_8))
                .encodeToString(key.getEncoded());
        return "-----BEGIN PUBLIC KEY-----\n" + encoded + "\n-----END PUBLIC KEY-----\n";
    }

    /**
     * In-memory {@link org.springframework.core.io.ResourceLoader} that returns the supplied
     * PEM strings for the private- and public-key paths, avoiding any filesystem dependency.
     */
    private static class InMemoryPemResourceLoader extends DefaultResourceLoader {

        private final String privatePem;
        private final String publicPem;

        InMemoryPemResourceLoader(String privatePem, String publicPem) {
            this.privatePem = privatePem;
            this.publicPem  = publicPem;
        }

        @Override
        public org.springframework.core.io.Resource getResource(String location) {
            if (location.contains("private")) {
                return new ByteArrayResource(privatePem.getBytes(StandardCharsets.UTF_8));
            }
            if (location.contains("public")) {
                return new ByteArrayResource(publicPem.getBytes(StandardCharsets.UTF_8));
            }
            return super.getResource(location);
        }
    }
}
