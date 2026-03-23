package in.supporthub.reporting.dto;

/**
 * Performance metrics for a single support agent.
 *
 * @param agentId              UUID of the agent.
 * @param assignedCount        Total tickets assigned to this agent in the period.
 * @param resolvedCount        Tickets resolved by this agent in the period.
 * @param avgResolutionMinutes Average resolution time in minutes for this agent's resolved tickets.
 */
public record AgentMetrics(
        String agentId,
        long assignedCount,
        long resolvedCount,
        double avgResolutionMinutes) {
}
