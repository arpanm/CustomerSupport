package in.supporthub.ordersync;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * SupportHub Order Sync Service entry point.
 *
 * <p>Consumes inbound order events from the OMS (Order Management System) via Kafka
 * webhooks and stores order data in MongoDB for ticket context enrichment.
 * Runs on port 8089 (default).
 */
@SpringBootApplication
public class OrderSyncServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrderSyncServiceApplication.class, args);
    }
}
