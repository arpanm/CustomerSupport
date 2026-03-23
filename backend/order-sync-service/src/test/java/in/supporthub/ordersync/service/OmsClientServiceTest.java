package in.supporthub.ordersync.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import in.supporthub.ordersync.domain.OmsConfig;
import in.supporthub.ordersync.dto.OrderResponse;
import in.supporthub.ordersync.repository.OmsConfigRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link OmsClientService}.
 *
 * <p>Test scenarios:
 * <ul>
 *   <li>Cache hit — OMS is NOT called; cached result is returned directly.</li>
 *   <li>OMS error (runtime exception) — empty list returned; no exception propagated.</li>
 *   <li>No OMS config — empty list/empty Optional returned without any OMS call.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("OmsClientService")
class OmsClientServiceTest {

    @Mock
    private WebClient.Builder webClientBuilder;
    @Mock
    private WebClient webClient;
    @Mock
    private WebClient.RequestHeadersUriSpec<?> requestHeadersUriSpec;
    @Mock
    private WebClient.RequestHeadersSpec<?> requestHeadersSpec;
    @Mock
    private WebClient.ResponseSpec responseSpec;

    @Mock
    private OmsConfigRepository omsConfigRepository;
    @Mock
    private PiiEncryptionService piiEncryptionService;
    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;

    private OmsClientService omsClientService;
    private ObjectMapper objectMapper;

    private static final UUID TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final String CUSTOMER_ID = "cust-abc-123";
    private static final String ORDER_ID = "ord-xyz-456";

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        omsClientService = new OmsClientService(
                webClientBuilder,
                omsConfigRepository,
                piiEncryptionService,
                redisTemplate,
                objectMapper
        );

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    // =========================================================================
    // fetchCustomerOrders
    // =========================================================================

    @Nested
    @DisplayName("fetchCustomerOrders")
    class FetchCustomerOrdersTests {

        @Test
        @DisplayName("returns cached orders and does NOT call OMS on cache hit")
        void cacheHit_returnsOrders_noOmsCall() throws Exception {
            // Arrange
            List<OrderResponse> expectedOrders = List.of(buildSampleOrder("ord-001"));
            String cachedJson = objectMapper.writeValueAsString(expectedOrders);
            String expectedCacheKey = "orders:" + TENANT_ID + ":" + CUSTOMER_ID;

            when(omsConfigRepository.findByTenantId(TENANT_ID))
                    .thenReturn(Optional.of(buildActiveConfig()));
            when(valueOperations.get(expectedCacheKey)).thenReturn(cachedJson);

            // Act
            List<OrderResponse> result = omsClientService.fetchCustomerOrders(TENANT_ID, CUSTOMER_ID, 10);

            // Assert
            assertThat(result).hasSize(1);
            assertThat(result.get(0).orderId()).isEqualTo("ord-001");

            // OMS WebClient must not have been invoked
            verify(webClientBuilder, never()).build();
        }

        @Test
        @DisplayName("returns empty list when no OMS config exists for tenant")
        void noConfig_returnsEmptyList_noOmsCall() {
            // Arrange
            when(omsConfigRepository.findByTenantId(TENANT_ID)).thenReturn(Optional.empty());

            // Act
            List<OrderResponse> result = omsClientService.fetchCustomerOrders(TENANT_ID, CUSTOMER_ID, 10);

            // Assert
            assertThat(result).isEmpty();
            verify(webClientBuilder, never()).build();
        }

        @Test
        @DisplayName("returns empty list when OMS config is inactive")
        void inactiveConfig_returnsEmptyList() {
            // Arrange
            OmsConfig inactiveConfig = buildActiveConfig();
            inactiveConfig.setActive(false);
            when(omsConfigRepository.findByTenantId(TENANT_ID)).thenReturn(Optional.of(inactiveConfig));

            // Act
            List<OrderResponse> result = omsClientService.fetchCustomerOrders(TENANT_ID, CUSTOMER_ID, 10);

            // Assert
            assertThat(result).isEmpty();
            verify(webClientBuilder, never()).build();
        }

        @Test
        @DisplayName("returns empty list and does NOT throw when OMS call throws an exception")
        @SuppressWarnings("unchecked")
        void omsError_returnsEmptyList_noExceptionPropagated() {
            // Arrange
            when(omsConfigRepository.findByTenantId(TENANT_ID))
                    .thenReturn(Optional.of(buildActiveConfig()));
            when(valueOperations.get(anyString())).thenReturn(null); // cache miss
            when(piiEncryptionService.decrypt(any())).thenReturn("test-api-key");
            when(webClientBuilder.build()).thenReturn(webClient);
            when(webClient.get()).thenReturn((WebClient.RequestHeadersUriSpec) requestHeadersUriSpec);
            when(requestHeadersUriSpec.uri(anyString(), anyString(), any(Integer.class)))
                    .thenReturn((WebClient.RequestHeadersSpec) requestHeadersSpec);
            when(requestHeadersSpec.header(anyString(), anyString()))
                    .thenReturn((WebClient.RequestHeadersSpec) requestHeadersSpec);
            when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
            when(responseSpec.bodyToMono(String.class))
                    .thenReturn(Mono.error(new RuntimeException("OMS connection refused")));

            // Act — must NOT throw
            List<OrderResponse> result = omsClientService.fetchCustomerOrders(TENANT_ID, CUSTOMER_ID, 10);

            // Assert
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("returns empty list when OMS returns empty JSON array")
        @SuppressWarnings("unchecked")
        void omsReturnsEmptyArray_returnsEmptyList() {
            // Arrange
            when(omsConfigRepository.findByTenantId(TENANT_ID))
                    .thenReturn(Optional.of(buildActiveConfig()));
            when(valueOperations.get(anyString())).thenReturn(null); // cache miss
            when(piiEncryptionService.decrypt(any())).thenReturn("test-api-key");
            when(webClientBuilder.build()).thenReturn(webClient);
            when(webClient.get()).thenReturn((WebClient.RequestHeadersUriSpec) requestHeadersUriSpec);
            when(requestHeadersUriSpec.uri(anyString(), anyString(), any(Integer.class)))
                    .thenReturn((WebClient.RequestHeadersSpec) requestHeadersSpec);
            when(requestHeadersSpec.header(anyString(), anyString()))
                    .thenReturn((WebClient.RequestHeadersSpec) requestHeadersSpec);
            when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
            when(responseSpec.bodyToMono(String.class))
                    .thenReturn(Mono.just("[]").timeout(Duration.ofSeconds(5)));

            // Act
            List<OrderResponse> result = omsClientService.fetchCustomerOrders(TENANT_ID, CUSTOMER_ID, 10);

            // Assert
            assertThat(result).isEmpty();
        }
    }

    // =========================================================================
    // fetchOrderById
    // =========================================================================

    @Nested
    @DisplayName("fetchOrderById")
    class FetchOrderByIdTests {

        @Test
        @DisplayName("returns cached order and does NOT call OMS on cache hit")
        void cacheHit_returnsOrder_noOmsCall() throws Exception {
            // Arrange
            OrderResponse expectedOrder = buildSampleOrder(ORDER_ID);
            String cachedJson = objectMapper.writeValueAsString(expectedOrder);
            String expectedCacheKey = "order:" + TENANT_ID + ":" + ORDER_ID;

            when(valueOperations.get(expectedCacheKey)).thenReturn(cachedJson);

            // Act
            Optional<OrderResponse> result = omsClientService.fetchOrderById(TENANT_ID, ORDER_ID);

            // Assert
            assertThat(result).isPresent();
            assertThat(result.get().orderId()).isEqualTo(ORDER_ID);
            verify(webClientBuilder, never()).build();
        }

        @Test
        @DisplayName("returns empty Optional when no OMS config exists for tenant")
        void noConfig_returnsEmpty_noOmsCall() {
            // Arrange
            when(valueOperations.get(anyString())).thenReturn(null); // cache miss
            when(omsConfigRepository.findByTenantId(TENANT_ID)).thenReturn(Optional.empty());

            // Act
            Optional<OrderResponse> result = omsClientService.fetchOrderById(TENANT_ID, ORDER_ID);

            // Assert
            assertThat(result).isEmpty();
            verify(webClientBuilder, never()).build();
        }

        @Test
        @DisplayName("returns empty Optional and does NOT throw when OMS call throws an exception")
        @SuppressWarnings("unchecked")
        void omsError_returnsEmpty_noExceptionPropagated() {
            // Arrange
            when(valueOperations.get(anyString())).thenReturn(null); // cache miss
            when(omsConfigRepository.findByTenantId(TENANT_ID))
                    .thenReturn(Optional.of(buildActiveConfig()));
            when(piiEncryptionService.decrypt(any())).thenReturn("test-api-key");
            when(webClientBuilder.build()).thenReturn(webClient);
            when(webClient.get()).thenReturn((WebClient.RequestHeadersUriSpec) requestHeadersUriSpec);
            when(requestHeadersUriSpec.uri(anyString(), eq(ORDER_ID)))
                    .thenReturn((WebClient.RequestHeadersSpec) requestHeadersSpec);
            when(requestHeadersSpec.header(anyString(), anyString()))
                    .thenReturn((WebClient.RequestHeadersSpec) requestHeadersSpec);
            when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
            when(responseSpec.bodyToMono(String.class))
                    .thenReturn(Mono.error(new RuntimeException("OMS 503 error")));

            // Act — must NOT throw
            Optional<OrderResponse> result = omsClientService.fetchOrderById(TENANT_ID, ORDER_ID);

            // Assert
            assertThat(result).isEmpty();
        }
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private OmsConfig buildActiveConfig() {
        OmsConfig config = new OmsConfig();
        config.setTenantId(TENANT_ID);
        config.setOmsBaseUrl("https://oms.example.com/api/v1");
        config.setApiKeyEncrypted(new byte[]{1, 2, 3});
        config.setAuthType("BEARER");
        config.setHeaderName("Authorization");
        config.setActive(true);
        return config;
    }

    private OrderResponse buildSampleOrder(String orderId) {
        return new OrderResponse(
                orderId,
                "ORD-2026-000001",
                "DELIVERED",
                new BigDecimal("1299.00"),
                "INR",
                Instant.parse("2026-03-01T10:00:00Z"),
                2,
                List.of(),
                "123 Main Street, Mumbai"
        );
    }
}
