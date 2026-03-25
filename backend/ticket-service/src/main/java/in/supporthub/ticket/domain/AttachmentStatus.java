package in.supporthub.ticket.domain;

/**
 * Lifecycle state of an {@link Attachment}.
 *
 * <ul>
 *   <li>{@code PENDING} — presigned URL has been issued but the client has not yet uploaded the file.</li>
 *   <li>{@code LINKED}  — the attachment has been associated with a ticket after ticket creation.</li>
 * </ul>
 */
public enum AttachmentStatus {
    PENDING,
    LINKED
}
