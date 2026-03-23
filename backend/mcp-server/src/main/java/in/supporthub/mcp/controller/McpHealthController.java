package in.supporthub.mcp.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Lightweight health endpoint for Kubernetes liveness and readiness probes.
 *
 * <p>Returns HTTP 200 with a simple JSON body. This endpoint is permitted without
 * authentication (configured in {@code SecurityConfig}).
 *
 * <p>Spring Boot Actuator also exposes {@code /actuator/health} via its own
 * auto-configuration; this controller provides an additional minimal endpoint
 * that does not depend on the Actuator's health indicator aggregation.
 */
@RestController
public class McpHealthController {

    /**
     * Returns a simple UP status to indicate the service is running.
     *
     * @return 200 OK with {@code {"status":"UP","service":"mcp-server"}}
     */
    @GetMapping("/actuator/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "UP", "service", "mcp-server"));
    }
}
