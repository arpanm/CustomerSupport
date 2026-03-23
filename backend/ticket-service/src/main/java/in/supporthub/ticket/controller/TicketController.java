package in.supporthub.ticket.controller;

import in.supporthub.shared.dto.ApiResponse;
import in.supporthub.shared.dto.PagedApiResponse;
import in.supporthub.ticket.domain.ActorType;
import in.supporthub.ticket.domain.Ticket;
import in.supporthub.ticket.domain.TicketActivity;
import in.supporthub.ticket.dto.ActivityResponse;
import in.supporthub.ticket.dto.AddActivityRequest;
import in.supporthub.ticket.dto.CreateTicketRequest;
import in.supporthub.ticket.dto.ReopenTicketRequest;
import in.supporthub.ticket.dto.ResolveTicketRequest;
import in.supporthub.ticket.dto.TicketFilterRequest;
import in.supporthub.ticket.dto.TicketListResponse;
import in.supporthub.ticket.dto.TicketResponse;
import in.supporthub.ticket.dto.UpdateTicketRequest;
import in.supporthub.ticket.service.TicketService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * REST controller for ticket lifecycle management.
 *
 * <p>All requests require:
 * <ul>
 *   <li>{@code X-Tenant-ID} header — tenant UUID, set by the API gateway after JWT validation.</li>
 *   <li>{@code X-User-Id} header — authenticated user UUID, set by the API gateway.</li>
 *   <li>{@code X-User-Role} header — user role, set by the API gateway.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/tickets")
@Tag(name = "Ticket API", description = "Ticket lifecycle management — create, update, status transitions, activities")
@Slf4j
@RequiredArgsConstructor
public class TicketController {

    private static final String API_VERSION = "v1";
    static final String HEADER_TENANT_ID = "X-Tenant-ID";
    static final String HEADER_USER_ID = "X-User-Id";
    static final String HEADER_USER_ROLE = "X-User-Role";

    private final TicketService ticketService;

    // =========================================================================
    // CREATE
    // =========================================================================

    @PostMapping
    @Operation(summary = "Create a new support ticket")
    public ResponseEntity<ApiResponse<TicketResponse>> create(
            @RequestHeader(HEADER_TENANT_ID) String tenantId,
            @RequestHeader(HEADER_USER_ID) String userId,
            @RequestHeader(value = HEADER_USER_ROLE, defaultValue = "CUSTOMER") String userRole,
            @Valid @RequestBody CreateTicketRequest request) {

        UUID tenantUuid = UUID.fromString(tenantId);
        UUID customerUuid = UUID.fromString(userId);

        Ticket ticket = ticketService.create(request, customerUuid, tenantUuid);

        log.info("Ticket created via API: ticketId={}, tenantId={}, ticketNumber={}",
                ticket.getId(), tenantId, ticket.getTicketNumber());

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.of(TicketResponse.from(ticket)));
    }

    // =========================================================================
    // LIST
    // =========================================================================

    @GetMapping
    @Operation(summary = "List tickets with optional filters")
    public ResponseEntity<PagedApiResponse<TicketListResponse>> list(
            @RequestHeader(HEADER_TENANT_ID) String tenantId,
            @RequestHeader(HEADER_USER_ID) String userId,
            @RequestParam(required = false) List<in.supporthub.ticket.domain.TicketStatus> status,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) in.supporthub.ticket.domain.Priority priority,
            @RequestParam(required = false) UUID assignedAgentId,
            @RequestParam(required = false) UUID customerId,
            @RequestParam(required = false) in.supporthub.ticket.domain.SentimentLabel sentimentLabel,
            @RequestParam(required = false) Instant dateFrom,
            @RequestParam(required = false) Instant dateTo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {

        UUID tenantUuid = UUID.fromString(tenantId);

        TicketFilterRequest filter = new TicketFilterRequest(
                status, categoryId, priority, assignedAgentId, customerId,
                dateFrom, dateTo, sentimentLabel, page, size);

        Page<Ticket> ticketPage = ticketService.list(filter, tenantUuid);

        List<TicketListResponse> items = ticketPage.getContent()
                .stream()
                .map(TicketListResponse::from)
                .toList();

        // Use cursor-based pagination metadata as required by PagedApiResponse
        String nextCursor = ticketPage.hasNext()
                ? java.util.Base64.getEncoder().encodeToString(
                        String.valueOf(ticketPage.getNumber() + 1).getBytes())
                : null;

        PagedApiResponse.Pagination pagination = new PagedApiResponse.Pagination(
                nextCursor,
                ticketPage.hasNext(),
                ticketPage.getSize(),
                ticketPage.getTotalElements() < 10_000 ? ticketPage.getTotalElements() : null
        );

        return ResponseEntity.ok(PagedApiResponse.of(items, pagination));
    }

    // =========================================================================
    // GET BY TICKET NUMBER
    // =========================================================================

    @GetMapping("/{ticketNumber}")
    @Operation(summary = "Get a ticket by its ticket number")
    public ResponseEntity<ApiResponse<TicketResponse>> getByTicketNumber(
            @RequestHeader(HEADER_TENANT_ID) String tenantId,
            @RequestHeader(HEADER_USER_ID) String userId,
            @PathVariable String ticketNumber) {

        UUID tenantUuid = UUID.fromString(tenantId);
        Ticket ticket = ticketService.getByTicketNumber(ticketNumber, tenantUuid);
        return ResponseEntity.ok(ApiResponse.of(TicketResponse.from(ticket)));
    }

    // =========================================================================
    // UPDATE
    // =========================================================================

    @PutMapping("/{ticketNumber}")
    @Operation(summary = "Update mutable ticket fields (priority, assignment, tags, etc.)")
    public ResponseEntity<ApiResponse<TicketResponse>> update(
            @RequestHeader(HEADER_TENANT_ID) String tenantId,
            @RequestHeader(HEADER_USER_ID) String userId,
            @RequestHeader(value = HEADER_USER_ROLE, defaultValue = "AGENT") String userRole,
            @PathVariable String ticketNumber,
            @Valid @RequestBody UpdateTicketRequest request) {

        UUID tenantUuid = UUID.fromString(tenantId);
        UUID actorUuid = UUID.fromString(userId);
        ActorType actorType = resolveActorType(userRole);

        Ticket updated = ticketService.update(ticketNumber, request, actorUuid, actorType, tenantUuid);
        return ResponseEntity.ok(ApiResponse.of(TicketResponse.from(updated)));
    }

    // =========================================================================
    // ACTIVITIES
    // =========================================================================

    @GetMapping("/{ticketNumber}/activities")
    @Operation(summary = "Get all activities for a ticket")
    public ResponseEntity<ApiResponse<List<ActivityResponse>>> getActivities(
            @RequestHeader(HEADER_TENANT_ID) String tenantId,
            @RequestHeader(HEADER_USER_ID) String userId,
            @PathVariable String ticketNumber) {

        UUID tenantUuid = UUID.fromString(tenantId);
        List<TicketActivity> activities = ticketService.getActivities(ticketNumber, tenantUuid);
        List<ActivityResponse> response = activities.stream().map(ActivityResponse::from).toList();
        return ResponseEntity.ok(ApiResponse.of(response));
    }

    @PostMapping("/{ticketNumber}/activities")
    @Operation(summary = "Add a new activity (comment, note) to a ticket")
    public ResponseEntity<ApiResponse<ActivityResponse>> addActivity(
            @RequestHeader(HEADER_TENANT_ID) String tenantId,
            @RequestHeader(HEADER_USER_ID) String userId,
            @RequestHeader(value = HEADER_USER_ROLE, defaultValue = "CUSTOMER") String userRole,
            @PathVariable String ticketNumber,
            @Valid @RequestBody AddActivityRequest request) {

        UUID tenantUuid = UUID.fromString(tenantId);
        UUID actorUuid = UUID.fromString(userId);
        ActorType actorType = resolveActorType(userRole);

        TicketActivity activity = ticketService.addActivity(
                ticketNumber, request, actorUuid, actorType, tenantUuid);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.of(ActivityResponse.from(activity)));
    }

    // =========================================================================
    // ACTIONS
    // =========================================================================

    @PostMapping("/{ticketNumber}/actions/resolve")
    @Operation(summary = "Resolve a ticket with a resolution note")
    public ResponseEntity<ApiResponse<TicketResponse>> resolve(
            @RequestHeader(HEADER_TENANT_ID) String tenantId,
            @RequestHeader(HEADER_USER_ID) String userId,
            @PathVariable String ticketNumber,
            @Valid @RequestBody ResolveTicketRequest request) {

        UUID tenantUuid = UUID.fromString(tenantId);
        UUID agentUuid = UUID.fromString(userId);

        Ticket ticket = ticketService.resolve(ticketNumber, request, agentUuid, tenantUuid);
        return ResponseEntity.ok(ApiResponse.of(TicketResponse.from(ticket)));
    }

    @PostMapping("/{ticketNumber}/actions/reopen")
    @Operation(summary = "Reopen a resolved or closed ticket")
    public ResponseEntity<ApiResponse<TicketResponse>> reopen(
            @RequestHeader(HEADER_TENANT_ID) String tenantId,
            @RequestHeader(HEADER_USER_ID) String userId,
            @RequestHeader(value = HEADER_USER_ROLE, defaultValue = "CUSTOMER") String userRole,
            @PathVariable String ticketNumber,
            @Valid @RequestBody ReopenTicketRequest request) {

        UUID tenantUuid = UUID.fromString(tenantId);
        UUID actorUuid = UUID.fromString(userId);
        ActorType actorType = resolveActorType(userRole);

        Ticket ticket = ticketService.reopen(ticketNumber, request, actorUuid, actorType, tenantUuid);
        return ResponseEntity.ok(ApiResponse.of(TicketResponse.from(ticket)));
    }

    @PostMapping("/{ticketNumber}/actions/escalate")
    @Operation(summary = "Escalate a ticket")
    public ResponseEntity<ApiResponse<TicketResponse>> escalate(
            @RequestHeader(HEADER_TENANT_ID) String tenantId,
            @RequestHeader(HEADER_USER_ID) String userId,
            @PathVariable String ticketNumber) {

        UUID tenantUuid = UUID.fromString(tenantId);
        UUID agentUuid = UUID.fromString(userId);

        Ticket ticket = ticketService.escalate(ticketNumber, agentUuid, tenantUuid);
        return ResponseEntity.ok(ApiResponse.of(TicketResponse.from(ticket)));
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    private ActorType resolveActorType(String role) {
        if (role == null) {
            return ActorType.CUSTOMER;
        }
        return switch (role.toUpperCase()) {
            case "AGENT", "ADMIN", "SUPER_ADMIN" -> ActorType.AGENT;
            case "AI_BOT" -> ActorType.AI_BOT;
            case "SYSTEM" -> ActorType.SYSTEM;
            default -> ActorType.CUSTOMER;
        };
    }
}
