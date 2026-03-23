package in.supporthub.ordersync.dto;

import java.math.BigDecimal;

/**
 * Represents a single line item within an order.
 *
 * @param name      Product name.
 * @param quantity  Quantity ordered.
 * @param price     Unit price for this item.
 * @param imageUrl  Optional product image URL.
 */
public record OrderItemResponse(
        String name,
        int quantity,
        BigDecimal price,
        String imageUrl
) {
}
