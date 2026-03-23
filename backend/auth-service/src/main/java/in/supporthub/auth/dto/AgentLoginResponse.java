package in.supporthub.auth.dto;

/**
 * Response body from the agent login endpoint.
 *
 * <p>If 2-FA is required (ADMIN or SUPER_ADMIN role), {@code requires2Fa} is {@code true},
 * {@code agentId} contains the UUID to pass to the 2-FA verify endpoint, and {@code tokens}
 * is {@code null}. When 2-FA is not required, {@code tokens} contains the issued JWT pair.
 *
 * @param requires2Fa {@code true} when the agent must complete a 2-FA challenge.
 * @param agentId     UUID of the agent (populated when {@code requires2Fa} is {@code true}).
 * @param tokens      Issued tokens (populated when authentication is fully complete).
 */
public record AgentLoginResponse(
        boolean requires2Fa,
        String agentId,
        TokenResponse tokens
) {
    /** Factory for responses that require a 2-FA step. */
    public static AgentLoginResponse requiresTwoFa(String agentId) {
        return new AgentLoginResponse(true, agentId, null);
    }

    /** Factory for responses where authentication is fully complete (no 2-FA needed). */
    public static AgentLoginResponse authenticated(TokenResponse tokens) {
        return new AgentLoginResponse(false, null, tokens);
    }
}
