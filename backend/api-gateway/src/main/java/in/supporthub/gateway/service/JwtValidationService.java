package in.supporthub.gateway.service;

import in.supporthub.shared.security.JwtClaims;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * Service responsible for validating JWT tokens and extracting typed claims.
 *
 * <p>Tokens are signed with RSA-256 (RS256). The RSA public key is loaded once on startup
 * from the location configured in {@code jwt.public-key-location}.
 *
 * <p>Usage:
 * <pre>{@code
 *   JwtClaims claims = jwtValidationService.validateAndExtract(token);
 * }</pre>
 *
 * @throws JwtException if the token is missing, expired, tampered with, or otherwise invalid
 */
@Slf4j
@Service
public class JwtValidationService {

    @Value("${jwt.public-key-location:classpath:keys/public.pem}")
    private Resource publicKeyResource;

    private PublicKey publicKey;

    /**
     * Loads the RSA public key from the configured resource location on startup.
     *
     * @throws IllegalStateException if the key cannot be loaded (fatal — service will not start)
     */
    @PostConstruct
    public void init() {
        try {
            String pemContent = publicKeyResource.getContentAsString(StandardCharsets.UTF_8);
            this.publicKey = parseRsaPublicKey(pemContent);
            log.info("JWT public key loaded successfully from: {}", publicKeyResource.getDescription());
        } catch (IOException | NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new IllegalStateException(
                    "Failed to load JWT public key from: " + publicKeyResource.getDescription(), e);
        }
    }

    /**
     * Validates the JWT token and extracts the typed claims.
     *
     * <p>Validates: signature (RS256), expiry ({@code exp}), not-before ({@code nbf}).
     * Does NOT log the token value.
     *
     * @param token the raw JWT string (without "Bearer " prefix)
     * @return typed {@link JwtClaims} extracted from the validated token
     * @throws JwtException if the token is invalid, expired, or cannot be parsed
     */
    public JwtClaims validateAndExtract(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(publicKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        String sub = claims.getSubject();
        String tenantId = claims.get("tenantId", String.class);
        String role = claims.get("role", String.class);
        String type = claims.get("type", String.class);

        if (sub == null || tenantId == null || role == null || type == null) {
            throw new JwtException(
                    "JWT is missing required claims: sub, tenantId, role, and type are all mandatory.");
        }

        return new JwtClaims(sub, tenantId, role, type);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Parses an RSA public key from a PEM-formatted string.
     *
     * @param pem PEM content including header/footer lines
     * @return the parsed {@link PublicKey}
     */
    private PublicKey parseRsaPublicKey(String pem)
            throws NoSuchAlgorithmException, InvalidKeySpecException {

        String stripped = pem
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s+", "");

        byte[] keyBytes = Base64.getDecoder().decode(stripped);
        X509EncodedKeySpec keySpec = new X509EncodedKeySpec(keyBytes);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        return keyFactory.generatePublic(keySpec);
    }
}
