package in.supporthub.ordersync.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.supporthub.ordersync.domain.OmsConfig;
import in.supporthub.ordersync.dto.OrderResponse;
import in.supporthub.ordersync.repository.OmsConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Client service responsible for fetching order data from a tenant's OMS.
 *
 * <p>Caching strategy (Redis):
 * <ul>
 *   <li>Customer orders: key {@code orders:{tenantId}:{customerId}}, TTL 600 seconds</li>
 *   <li>Single order: key {@code order:{tenantId}:{orderId}}, TTL 600 seconds</li>
 * </ul>
 *
 * <p>On any OMS error (timeout, non-2xx, network failure), the method logs a WARN
 * with the tenant ID — customerId is treated as PII and is NOT included in warn-level logs —
 * and returns an empty result instead of propagating the exception.
 *
 * <p>WebClient timeout: 5 seconds per request.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class OmsClientService {

    private static final long CACHE_TTL_SECONDS = 600L;
    private static final String CACHE_KEY_ORDERS = "orders:%s:%s";
    private static final String CACHE_KEY_ORDER = "order:%s:%s";

    private final WebClient.Builder webClientBuilder;
    private final OmsConfigRepository omsConfigRepository;
    private final PiiEncryptionService piiEncryptionService;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    /**
     * Fetches a customer's recent orders from the tenant's OMS.
     *
     * <p>Flow:
     * <ol>
     *   <li>Load {@link OmsConfig} from DB for the tenant (no Redis caching of config — it is fast).</li>
     *   <li>If no active config exists, return an empty list.</li>
     *   <li>Check Redis for cached result.</li>
     *   <li>On cache miss, call OMS GET {@code {omsBaseUrl}/orders?customerId={customerId}&limit={limit}}.</li>
     *   <li>Cache successful result.</li>
     *   <li>On any error, log WARN (tenantId only — no customerId at WARN level), return empty list.</li>
     * </ol>
     *
     * @param tenantId   the tenant UUID
     * @param customerId the customer identifier as known to the OMS
     * @param limit      maximum number of orders to return
     * @return list of orders, never null; empty on config absence or error
     */
    public List<OrderResponse> fetchCustomerOrders(UUID tenantId, String customerId, int limit) {
        Optional<OmsConfig> configOpt = omsConfigRepository.findByTenantId(tenantId);
        if (configOpt.isEmpty() || !configOpt.get().isActive()) {
            log.debug("No active OMS config found for tenantId={}, returning empty order list", tenantId);
            return Collections.emptyList();
        }
        OmsConfig config = configOpt.get();

        String cacheKey = String.format(CACHE_KEY_ORDERS, tenantId, customerId);
        String cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            log.debug("Cache hit for customer orders: tenantId={}", tenantId);
            return deserializeOrderList(cached);
        }

        try {
            String apiKey = piiEncryptionService.decrypt(config.getApiKeyEncrypted());

            String responseBody = webClientBuilder.build()
                    .get()
                    .uri(config.getOmsBaseUrl() + "/orders?customerId={customerId}&limit={limit}",
                            customerId, limit)
                    .header(config.getHeaderName(), buildAuthHeaderValue(config.getAuthType(), apiKey))
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(5))
                    .block();

            List<OrderResponse> orders = deserializeOrderList(responseBody);
            redisTemplate.opsForValue().set(cacheKey, responseBody, Duration.ofSeconds(CACHE_TTL_SECONDS));
            log.debug("Fetched and cached {} orders from OMS: tenantId={}", orders.size(), tenantId);
            return orders;

        } catch (Exception ex) {
            // customerId is PII — do NOT include in WARN log
            log.warn("Failed to fetch customer orders from OMS: tenantId={}, error={}",
                    tenantId, ex.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Fetches a single order by its OMS order ID.
     *
     * <p>Checks Redis first, then calls OMS on a cache miss.
     *
     * @param tenantId the tenant UUID
     * @param orderId  the OMS order identifier
     * @return an {@link Optional} containing the order, or empty on not-found / error
     */
    public Optional<OrderResponse> fetchOrderById(UUID tenantId, String orderId) {
        String cacheKey = String.format(CACHE_KEY_ORDER, tenantId, orderId);
        String cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            log.debug("Cache hit for order: tenantId={}, orderId={}", tenantId, orderId);
            return deserializeOrder(cached);
        }

        Optional<OmsConfig> configOpt = omsConfigRepository.findByTenantId(tenantId);
        if (configOpt.isEmpty() || !configOpt.get().isActive()) {
            log.debug("No active OMS config found for tenantId={}, returning empty", tenantId);
            return Optional.empty();
        }
        OmsConfig config = configOpt.get();

        try {
            String apiKey = piiEncryptionService.decrypt(config.getApiKeyEncrypted());

            String responseBody = webClientBuilder.build()
                    .get()
                    .uri(config.getOmsBaseUrl() + "/orders/{orderId}", orderId)
                    .header(config.getHeaderName(), buildAuthHeaderValue(config.getAuthType(), apiKey))
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(5))
                    .block();

            Optional<OrderResponse> order = deserializeOrder(responseBody);
            if (order.isPresent()) {
                redisTemplate.opsForValue().set(cacheKey, responseBody, Duration.ofSeconds(CACHE_TTL_SECONDS));
            }
            log.debug("Fetched order from OMS: tenantId={}, orderId={}", tenantId, orderId);
            return order;

        } catch (Exception ex) {
            log.warn("Failed to fetch order from OMS: tenantId={}, orderId={}, error={}",
                    tenantId, orderId, ex.getMessage());
            return Optional.empty();
        }
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    private String buildAuthHeaderValue(String authType, String apiKey) {
        if (authType == null) {
            return "Bearer " + apiKey;
        }
        return switch (authType.toUpperCase()) {
            case "BEARER" -> "Bearer " + apiKey;
            case "BASIC" -> "Basic " + apiKey;
            default -> apiKey; // API_KEY or custom: raw value in header
        };
    }

    private List<OrderResponse> deserializeOrderList(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<OrderResponse>>() {});
        } catch (Exception ex) {
            log.warn("Failed to deserialize order list from OMS response: {}", ex.getMessage());
            return Collections.emptyList();
        }
    }

    private Optional<OrderResponse> deserializeOrder(String json) {
        if (json == null || json.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(json, OrderResponse.class));
        } catch (Exception ex) {
            log.warn("Failed to deserialize order from OMS response: {}", ex.getMessage());
            return Optional.empty();
        }
    }
}
