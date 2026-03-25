package in.supporthub.reporting.dto;

/**
 * Performance metrics for a single support agent.
 *
 * @param agentId                  UUID of the agent.
 * @param agentEmail               Email address of the agent (non-PII display field for admins).
 * @param ticketsResolved          Number of tickets resolved by this agent in the reporting period.
 * @param avgResolutionMinutes     Average resolution time in minutes for resolved tickets.
 * @param firstResponseAvgMinutes  Average first-response time in minutes.
 */
public record AgentPerformanceResult(
        String agentId,
        String agentEmail,
        long ticketsResolved,
        double avgResolutionMinutes,
        double firstResponseAvgMinutes) {
}
