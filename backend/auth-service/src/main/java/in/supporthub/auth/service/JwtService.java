package in.supporthub.auth.service;

import in.supporthub.auth.config.JwtConfig;
import in.supporthub.auth.exception.AuthException;
import in.supporthub.shared.security.JwtClaims;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

/**
 * Service responsible for issuing and validating RS256 JWTs.
 *
 * <p>Keys are loaded from PEM files on startup (paths from {@link JwtConfig}).
 * The private key is used to sign access tokens; the public key to verify them.
 *
 * <p>Refresh tokens are opaque UUIDs — they are NOT JWTs. Their state is managed
 * entirely in Redis.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class JwtService {

    private final JwtConfig jwtConfig;
    private final ResourceLoader resourceLoader;

    private PrivateKey privateKey;
    private PublicKey publicKey;

    @PostConstruct
    public void loadKeys() {
        try {
            privateKey = loadPrivateKey(jwtConfig.getPrivateKeyLocation());
            publicKey = loadPublicKey(jwtConfig.getPublicKeyLocation());
            log.info("JWT keys loaded successfully: privateKeyLocation={}, publicKeyLocation={}",
                    jwtConfig.getPrivateKeyLocation(), jwtConfig.getPublicKeyLocation());
        } catch (Exception ex) {
            log.error("Failed to load JWT keys: privateKeyLocation={}, publicKeyLocation={}, error={}",
                    jwtConfig.getPrivateKeyLocation(), jwtConfig.getPublicKeyLocation(), ex.getMessage(), ex);
            throw new IllegalStateException("Cannot start auth-service: JWT key loading failed", ex);
        }
    }

    /**
     * Generates an RS256-signed access token with the standard SupportHub claims.
     *
     * @param subject  UUID of the authenticated user (customer or agent)
     * @param tenantId UUID of the tenant
     * @param role     Role string (e.g., CUSTOMER, AGENT, ADMIN)
     * @param type     Token type (customer, agent, mcp)
     * @return signed JWT string
     */
    public String generateAccessToken(UUID subject, UUID tenantId, String role, String type) {
        Instant now = Instant.now();
        Instant expiry = now.plusSeconds(jwtConfig.getAccessTokenExpirySeconds());

        String token = Jwts.builder()
                .subject(subject.toString())
                .claim("tenant_id", tenantId.toString())
                .claim("role", role)
                .claim("type", type)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(privateKey)
                .compact();

        log.info("Access token issued: subjectId={}, tenantId={}, role={}, type={}, expiresAt={}",
                subject, tenantId, role, type, expiry);
        return token;
    }

    /**
     * Generates an opaque refresh token (UUID v4).
     *
     * <p>The token itself carries no claims — all state is stored in Redis under
     * key {@code refresh:{tokenId}}.
     *
     * @return random UUID string to be used as the refresh token identifier
     */
    public String generateRefreshToken() {
        return UUID.randomUUID().toString();
    }

    /**
     * Validates and parses a JWT, returning its standard claims.
     *
     * @param token signed JWT string
     * @return parsed {@link JwtClaims}
     * @throws AuthException if the token is invalid, expired, or tampered
     */
    public JwtClaims validateToken(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(publicKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            return new JwtClaims(
                    claims.getSubject(),
                    claims.get("tenant_id", String.class),
                    claims.get("role", String.class),
                    claims.get("type", String.class)
            );
        } catch (ExpiredJwtException ex) {
            log.info("JWT validation failed: token expired");
            throw new AuthException("Token has expired. Please log in again.");
        } catch (JwtException ex) {
            log.warn("JWT validation failed: invalid token, error={}", ex.getMessage());
            throw new AuthException("Invalid token. Please log in again.");
        }
    }

    /**
     * Returns the configured access token expiry in seconds.
     */
    public long getAccessTokenExpiry() {
        return jwtConfig.getAccessTokenExpirySeconds();
    }

    /**
     * Extracts the subject (user UUID) from a JWT without full validation.
     * Use {@link #validateToken(String)} for full validation.
     */
    public String extractSubject(String token) {
        return validateToken(token).sub();
    }

    /**
     * Extracts the tenant ID from a JWT without full validation.
     * Use {@link #validateToken(String)} for full validation.
     */
    public String extractTenantId(String token) {
        return validateToken(token).tenantId();
    }

    // -----------------------------------------------------------------------
    // Private helpers — PEM key loading
    // -----------------------------------------------------------------------

    private PrivateKey loadPrivateKey(String location)
            throws IOException, NoSuchAlgorithmException, InvalidKeySpecException {
        String pem = readPemContent(location);
        String base64 = pem
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        byte[] keyBytes = Base64.getDecoder().decode(base64);
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(keyBytes);
        return KeyFactory.getInstance("RSA").generatePrivate(spec);
    }

    private PublicKey loadPublicKey(String location)
            throws IOException, NoSuchAlgorithmException, InvalidKeySpecException {
        String pem = readPemContent(location);
        String base64 = pem
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s", "");
        byte[] keyBytes = Base64.getDecoder().decode(base64);
        X509EncodedKeySpec spec = new X509EncodedKeySpec(keyBytes);
        return KeyFactory.getInstance("RSA").generatePublic(spec);
    }

    private String readPemContent(String location) throws IOException {
        return resourceLoader.getResource(location)
                .getContentAsString(StandardCharsets.UTF_8);
    }
}
