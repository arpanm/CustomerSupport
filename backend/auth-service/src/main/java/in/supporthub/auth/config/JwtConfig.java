package in.supporthub.auth.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Typed configuration properties for JWT settings.
 *
 * <p>Values are bound from the {@code jwt.*} namespace in {@code application.yml}.
 */
@Component
@ConfigurationProperties(prefix = "jwt")
@Getter
@Setter
public class JwtConfig {

    /**
     * Location of the RSA private key PEM file used to sign JWTs.
     * Example: {@code classpath:keys/private.pem} or {@code file:/etc/secrets/private.pem}
     */
    private String privateKeyLocation;

    /**
     * Location of the RSA public key PEM file used to verify JWTs.
     * Example: {@code classpath:keys/public.pem}
     */
    private String publicKeyLocation;

    /**
     * Lifetime of an access token in seconds. Default: 3600 (1 hour).
     */
    private long accessTokenExpirySeconds = 3600;

    /**
     * Lifetime of a refresh token in days. Default: 30 days.
     */
    private int refreshTokenExpiryDays = 30;
}
