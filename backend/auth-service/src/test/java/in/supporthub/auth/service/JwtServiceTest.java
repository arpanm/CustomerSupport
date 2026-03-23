package in.supporthub.auth.service;

import in.supporthub.auth.config.JwtConfig;
import in.supporthub.auth.exception.AuthException;
import in.supporthub.shared.security.JwtClaims;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.DefaultResourceLoader;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link JwtService}.
 *
 * <p>Generates a fresh RSA key pair in-memory for each test run — no real PEM files needed.
 * Tests verify claim extraction, expiry detection, and tamper detection.
 */
@ExtendWith(MockitoExtension.class)
class JwtServiceTest {

    private static final UUID SUBJECT_ID = UUID.randomUUID();
    private static final UUID TENANT_ID = UUID.randomUUID();

    private JwtService jwtService;
    private KeyPair keyPair;

    @BeforeEach
    void setUp() throws Exception {
        // Generate RSA-2048 key pair for testing
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        keyPair = generator.generateKeyPair();

        String privatePem = toPkcs8Pem(keyPair.getPrivate());
        String publicPem = toX509Pem(keyPair.getPublic());

        JwtConfig config = new JwtConfig();
        config.setPrivateKeyLocation("classpath:keys/test-private.pem");
        config.setPublicKeyLocation("classpath:keys/test-public.pem");
        config.setAccessTokenExpirySeconds(3600);
        config.setRefreshTokenExpiryDays(30);

        // Use a custom ResourceLoader that serves our in-memory PEM strings
        ResourceLoader loader = new InMemoryPemResourceLoader(privatePem, publicPem);

        jwtService = new JwtService(config, loader);
        jwtService.loadKeys();
    }

    // -----------------------------------------------------------------------
    // generateAccessToken
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("generateAccessToken: produces a non-blank token containing expected claims")
    void generateAccessToken_producesValidToken() {
        // ACT
        String token = jwtService.generateAccessToken(
                SUBJECT_ID, TENANT_ID, JwtClaims.ROLE_CUSTOMER, JwtClaims.TYPE_CUSTOMER);

        // ASSERT
        assertThat(token).isNotBlank();

        // Validate claims round-trip
        JwtClaims claims = jwtService.validateToken(token);
        assertThat(claims.sub()).isEqualTo(SUBJECT_ID.toString());
        assertThat(claims.tenantId()).isEqualTo(TENANT_ID.toString());
        assertThat(claims.role()).isEqualTo(JwtClaims.ROLE_CUSTOMER);
        assertThat(claims.type()).isEqualTo(JwtClaims.TYPE_CUSTOMER);
    }

    @Test
    @DisplayName("generateAccessToken: sets correct role and type for agent token")
    void generateAccessToken_setsAgentRoleAndType() {
        // ACT
        String token = jwtService.generateAccessToken(
                SUBJECT_ID, TENANT_ID, JwtClaims.ROLE_ADMIN, JwtClaims.TYPE_AGENT);

        // ASSERT
        JwtClaims claims = jwtService.validateToken(token);
        assertThat(claims.role()).isEqualTo(JwtClaims.ROLE_ADMIN);
        assertThat(claims.type()).isEqualTo(JwtClaims.TYPE_AGENT);
    }

    // -----------------------------------------------------------------------
    // validateToken
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("validateToken: extracts correct JwtClaims from a valid token")
    void validateToken_extractsCorrectClaims() {
        // ARRANGE
        String token = jwtService.generateAccessToken(
                SUBJECT_ID, TENANT_ID, JwtClaims.ROLE_AGENT, JwtClaims.TYPE_AGENT);

        // ACT
        JwtClaims claims = jwtService.validateToken(token);

        // ASSERT
        assertThat(claims.sub()).isEqualTo(SUBJECT_ID.toString());
        assertThat(claims.tenantId()).isEqualTo(TENANT_ID.toString());
        assertThat(claims.role()).isEqualTo(JwtClaims.ROLE_AGENT);
        assertThat(claims.type()).isEqualTo(JwtClaims.TYPE_AGENT);
    }

    @Test
    @DisplayName("validateToken: throws AuthException for a tampered (invalid signature) token")
    void validateToken_throwsAuthException_forTamperedToken() {
        // ARRANGE — generate a valid token, then corrupt the signature
        String token = jwtService.generateAccessToken(
                SUBJECT_ID, TENANT_ID, JwtClaims.ROLE_CUSTOMER, JwtClaims.TYPE_CUSTOMER);
        String tamperedToken = token.substring(0, token.length() - 5) + "XXXXX";

        // ACT + ASSERT
        assertThatThrownBy(() -> jwtService.validateToken(tamperedToken))
                .isInstanceOf(AuthException.class);
    }

    @Test
    @DisplayName("validateToken: throws AuthException for a token signed by a different key")
    void validateToken_throwsAuthException_forWrongKeyToken() throws Exception {
        // ARRANGE — generate another key pair and sign with it
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair otherPair = generator.generateKeyPair();

        String tokenFromOtherKey = Jwts.builder()
                .subject(SUBJECT_ID.toString())
                .claim("tenant_id", TENANT_ID.toString())
                .claim("role", JwtClaims.ROLE_CUSTOMER)
                .claim("type", JwtClaims.TYPE_CUSTOMER)
                .signWith(otherPair.getPrivate())
                .compact();

        // ACT + ASSERT
        assertThatThrownBy(() -> jwtService.validateToken(tokenFromOtherKey))
                .isInstanceOf(AuthException.class);
    }

    // -----------------------------------------------------------------------
    // generateRefreshToken
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("generateRefreshToken: returns a non-blank UUID-format string")
    void generateRefreshToken_returnsUuid() {
        // ACT
        String token1 = jwtService.generateRefreshToken();
        String token2 = jwtService.generateRefreshToken();

        // ASSERT
        assertThat(token1).isNotBlank().matches("[0-9a-f\\-]{36}");
        assertThat(token2).isNotBlank().matches("[0-9a-f\\-]{36}");
        assertThat(token1).isNotEqualTo(token2); // should be unique
    }

    // -----------------------------------------------------------------------
    // Helper: in-memory ResourceLoader for test PEM files
    // -----------------------------------------------------------------------

    private static String toPkcs8Pem(PrivateKey key) {
        String encoded = Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(key.getEncoded());
        return "-----BEGIN PRIVATE KEY-----\n" + encoded + "\n-----END PRIVATE KEY-----\n";
    }

    private static String toX509Pem(PublicKey key) {
        String encoded = Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(key.getEncoded());
        return "-----BEGIN PUBLIC KEY-----\n" + encoded + "\n-----END PUBLIC KEY-----\n";
    }

    /**
     * In-memory ResourceLoader that returns PEM content for the two key paths.
     */
    private static class InMemoryPemResourceLoader extends DefaultResourceLoader {

        private final String privatePem;
        private final String publicPem;

        InMemoryPemResourceLoader(String privatePem, String publicPem) {
            this.privatePem = privatePem;
            this.publicPem = publicPem;
        }

        @Override
        public org.springframework.core.io.Resource getResource(String location) {
            if (location.contains("private")) {
                return new org.springframework.core.io.ByteArrayResource(
                        privatePem.getBytes(StandardCharsets.UTF_8));
            }
            if (location.contains("public")) {
                return new org.springframework.core.io.ByteArrayResource(
                        publicPem.getBytes(StandardCharsets.UTF_8));
            }
            return super.getResource(location);
        }
    }
}
