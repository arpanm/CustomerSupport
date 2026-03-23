package in.supporthub.customer.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Summary of an order returned from the order-sync-service.
 *
 * <p>This record is used defensively — the order-sync-service may be unavailable,
 * in which case an empty list is returned instead of propagating errors.
 *
 * @param orderId     order UUID from the OMS
 * @param orderNumber human-readable order reference (e.g. "ORD-2026-001234")
 * @param status      current order status (e.g. "PLACED", "SHIPPED", "DELIVERED", "CANCELLED")
 * @param totalAmount total order value
 * @param currency    ISO 4217 currency code (e.g. "INR", "USD")
 * @param placedAt    timestamp when the order was placed
 * @param itemCount   number of distinct line items in the order
 */
public record OrderSummary(
        UUID orderId,
        String orderNumber,
        String status,
        BigDecimal totalAmount,
        String currency,
        Instant placedAt,
        int itemCount
) {}
