package in.supporthub.ticket.dto;

import in.supporthub.ticket.domain.Priority;
import in.supporthub.ticket.domain.SentimentLabel;
import in.supporthub.ticket.domain.TicketStatus;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Query parameters for filtering the ticket list endpoint.
 *
 * <p>All fields are optional. Multiple {@code status} values are OR-combined.
 * Date range filters are inclusive on both ends.
 */
public record TicketFilterRequest(
        List<TicketStatus> status,
        UUID categoryId,
        Priority priority,
        UUID assignedAgentId,
        UUID customerId,

        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        Instant dateFrom,

        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        Instant dateTo,

        SentimentLabel sentimentLabel,

        int page,
        int size
) {
    /** Default page size. */
    public static final int DEFAULT_PAGE_SIZE = 25;

    /** Maximum page size. */
    public static final int MAX_PAGE_SIZE = 100;

    public TicketFilterRequest {
        if (size <= 0) size = DEFAULT_PAGE_SIZE;
        if (size > MAX_PAGE_SIZE) size = MAX_PAGE_SIZE;
        if (page < 0) page = 0;
    }
}
