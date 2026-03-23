package in.supporthub.ordersync.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Represents a customer order returned from the OMS.
 *
 * @param orderId         Internal OMS order identifier.
 * @param orderNumber     Human-readable order number (e.g., "ORD-2026-001234").
 * @param status          Current order status (e.g., "PLACED", "SHIPPED", "DELIVERED").
 * @param totalAmount     Total order value.
 * @param currency        ISO 4217 currency code (e.g., "INR", "USD").
 * @param placedAt        Timestamp when the order was placed.
 * @param itemCount       Total number of items in this order.
 * @param items           Line items in the order.
 * @param deliveryAddress Delivery address as a formatted string.
 */
public record OrderResponse(
        String orderId,
        String orderNumber,
        String status,
        BigDecimal totalAmount,
        String currency,
        Instant placedAt,
        int itemCount,
        List<OrderItemResponse> items,
        String deliveryAddress
) {
}
