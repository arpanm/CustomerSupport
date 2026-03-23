package in.supporthub.ordersync.dto;

import java.util.List;

/**
 * Aggregated response containing all orders for a customer.
 *
 * @param orders      List of orders fetched from the OMS for the customer.
 * @param totalOrders Total number of orders returned (may reflect a limit applied by the caller).
 */
public record CustomerOrdersResponse(
        List<OrderResponse> orders,
        int totalOrders
) {

    /**
     * Factory method to create a response from a list of orders.
     *
     * @param orders the orders to include
     * @return a populated response
     */
    public static CustomerOrdersResponse of(List<OrderResponse> orders) {
        return new CustomerOrdersResponse(orders, orders.size());
    }
}
