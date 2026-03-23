package in.supporthub.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * SupportHub API Gateway — entry point for all inbound traffic.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>JWT authentication and claim extraction on every protected route</li>
 *   <li>Tenant resolution from X-Tenant-ID header or subdomain</li>
 *   <li>Redis-backed rate limiting (per tenant and per IP)</li>
 *   <li>Circuit breaking via Resilience4j for all downstream services</li>
 *   <li>Request ID injection for distributed tracing</li>
 *   <li>CORS enforcement</li>
 * </ul>
 */
@SpringBootApplication
public class ApiGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayApplication.class, args);
    }
}
