package in.supporthub.mcp.security;

import in.supporthub.shared.security.JwtClaims;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ResourceLoader;
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
 * Loads the RSA public key from PEM and validates RS256-signed JWTs.
 *
 * <p>The public key location is configured via {@code jwt.public-key-location}
 * (default: {@code classpath:keys/public.pem}). The private key is NOT needed
 * here — the MCP server only verifies tokens issued by the auth-service.
 *
 * <p>Throws {@link McpAuthException} when the token is invalid or expired.
 */
@Service
@Slf4j
public class JwtValidationService {

    private final ResourceLoader resourceLoader;
    private final String publicKeyLocation;

    private PublicKey publicKey;

    public JwtValidationService(
            ResourceLoader resourceLoader,
            @Value("${jwt.public-key-location:classpath:keys/public.pem}") String publicKeyLocation) {
        this.resourceLoader = resourceLoader;
        this.publicKeyLocation = publicKeyLocation;
    }

    @PostConstruct
    public void loadPublicKey() {
        try {
            String pem = resourceLoader.getResource(publicKeyLocation)
                    .getContentAsString(StandardCharsets.UTF_8);
            String base64 = pem
                    .replace("-----BEGIN PUBLIC KEY-----", "")
                    .replace("-----END PUBLIC KEY-----", "")
                    .replaceAll("\\s", "");
            byte[] keyBytes = Base64.getDecoder().decode(base64);
            X509EncodedKeySpec spec = new X509EncodedKeySpec(keyBytes);
            publicKey = KeyFactory.getInstance("RSA").generatePublic(spec);
            log.info("MCP server JWT public key loaded: location={}", publicKeyLocation);
        } catch (IOException | NoSuchAlgorithmException | InvalidKeySpecException ex) {
            log.error("Failed to load JWT public key: location={}, error={}", publicKeyLocation, ex.getMessage(), ex);
            throw new IllegalStateException("Cannot start mcp-server: JWT public key loading failed", ex);
        }
    }

    /**
     * Validates the given JWT string and extracts its standard SupportHub claims.
     *
     * @param token the raw JWT Bearer token (without "Bearer " prefix)
     * @return parsed {@link JwtClaims} containing sub, tenantId, role, and type
     * @throws McpAuthException if the token is invalid, expired, or malformed
     */
    public JwtClaims validate(String token) {
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
            log.info("MCP JWT validation failed: token expired");
            throw new McpAuthException("Token has expired. Please obtain a new MCP token.");
        } catch (JwtException ex) {
            log.warn("MCP JWT validation failed: invalid token, error={}", ex.getMessage());
            throw new McpAuthException("Invalid token. Access denied.");
        }
    }
}
