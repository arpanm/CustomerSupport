package in.supporthub.ticket.service;

import java.time.Instant;

/**
 * Immutable value object returned by {@link SlaEngine#compute} containing
 * the computed SLA deadline timestamps for a ticket.
 *
 * @param firstResponseDueAt deadline by which the agent must first respond
 * @param resolutionDueAt    deadline by which the ticket must be resolved
 */
public record SlaDeadlines(Instant firstResponseDueAt, Instant resolutionDueAt) {}
