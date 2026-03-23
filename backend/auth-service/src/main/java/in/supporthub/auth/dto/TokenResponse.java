package in.supporthub.auth.dto;

/**
 * JWT token response returned on successful authentication.
 *
 * <p>The refresh token is NOT included here — it is delivered via an httpOnly cookie
 * set by the controller to prevent XSS theft.
 *
 * @param accessToken  RS256-signed JWT access token.
 * @param tokenType    Always {@code "Bearer"}.
 * @param expiresIn    Access token lifetime in seconds (typically 3600).
 * @param role         Role encoded in the token (CUSTOMER, AGENT, ADMIN, SUPER_ADMIN).
 */
public record TokenResponse(
        String accessToken,
        String tokenType,
        long expiresIn,
        String role
) {
    public static TokenResponse of(String accessToken, long expiresIn, String role) {
        return new TokenResponse(accessToken, "Bearer", expiresIn, role);
    }
}
