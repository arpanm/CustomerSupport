package in.supporthub.gateway.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link TenantResolutionFilter}.
 *
 * <p>Test strategy:
 * <ul>
 *   <li>X-Tenant-ID header present: header is passed downstream unchanged.</li>
 *   <li>No header but valid supporthub.in subdomain: tenant extracted and injected.</li>
 *   <li>No header, no subdomain, protected route: returns 400.</li>
 *   <li>Exempt paths (actuator health, fallback): filter skipped.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class TenantResolutionFilterTest {

    private static final String TEST_TENANT_ID = "acmecorp";
    private static final String TEST_REQUEST_ID = "req-tenanttest-001";

    @Mock
    private GatewayFilterChain chain;

    private TenantResolutionFilter tenantResolutionFilter;

    @BeforeEach
    void setUp() {
        tenantResolutionFilter = new TenantResolutionFilter(new ObjectMapper());
        when(chain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());
    }

    // -------------------------------------------------------------------------
    // Happy path tests
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Should pass X-Tenant-ID header through to downstream unchanged")
    void shouldPassThroughExistingTenantIdHeader() {
        // ARRANGE
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/v1/tickets")
                .header(TenantResolutionFilter.HEADER_TENANT_ID, TEST_TENANT_ID)
                .header(TenantResolutionFilter.HEADER_REQUEST_ID, TEST_REQUEST_ID)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        // ACT
        var gatewayFilter = tenantResolutionFilter.apply(new TenantResolutionFilter.Config());
        StepVerifier.create(gatewayFilter.filter(exchange, chain))
                .verifyComplete();

        // ASSERT — chain.filter must be called (request proceeds)
        verify(chain).filter(any(ServerWebExchange.class));
    }

    @Test
    @DisplayName("Should extract tenant slug from supporthub.in subdomain")
    void shouldExtractTenantFromSubdomain() {
        // ARRANGE
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/v1/tickets")
                .header("Host", "acmecorp.supporthub.in")
                .header(TenantResolutionFilter.HEADER_REQUEST_ID, TEST_REQUEST_ID)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        // ACT
        var gatewayFilter = tenantResolutionFilter.apply(new TenantResolutionFilter.Config());
        StepVerifier.create(gatewayFilter.filter(exchange, chain))
                .verifyComplete();

        // ASSERT — request should proceed
        verify(chain).filter(any(ServerWebExchange.class));
    }

    @Test
    @DisplayName("Should extract tenant slug from subdomain when port is included in Host header")
    void shouldExtractTenantFromSubdomainWithPort() {
        // ARRANGE
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/v1/tickets")
                .header("Host", "acmecorp.supporthub.in:8080")
                .header(TenantResolutionFilter.HEADER_REQUEST_ID, TEST_REQUEST_ID)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        // ACT
        var gatewayFilter = tenantResolutionFilter.apply(new TenantResolutionFilter.Config());
        StepVerifier.create(gatewayFilter.filter(exchange, chain))
                .verifyComplete();

        // ASSERT
        verify(chain).filter(any(ServerWebExchange.class));
    }

    @Test
    @DisplayName("Should skip tenant resolution for actuator health path")
    void shouldSkipTenantResolutionForActuatorHealth() {
        // ARRANGE — no tenant header or subdomain, but path is exempt
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/actuator/health")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        // ACT
        var gatewayFilter = tenantResolutionFilter.apply(new TenantResolutionFilter.Config());
        StepVerifier.create(gatewayFilter.filter(exchange, chain))
                .verifyComplete();

        // ASSERT — chain proceeds without 400
        verify(chain).filter(any(ServerWebExchange.class));
        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }

    @Test
    @DisplayName("Should skip tenant resolution for fallback paths")
    void shouldSkipTenantResolutionForFallbackPath() {
        // ARRANGE
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/fallback/service")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        // ACT
        var gatewayFilter = tenantResolutionFilter.apply(new TenantResolutionFilter.Config());
        StepVerifier.create(gatewayFilter.filter(exchange, chain))
                .verifyComplete();

        // ASSERT
        verify(chain).filter(any(ServerWebExchange.class));
        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }

    // -------------------------------------------------------------------------
    // Error path tests
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Should return 400 when no tenant found on protected route")
    void shouldReturn400WhenNoTenantOnProtectedRoute() {
        // ARRANGE — no X-Tenant-ID header, no supporthub.in subdomain, protected path
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/v1/tickets")
                .header("Host", "localhost:8080")
                .header(TenantResolutionFilter.HEADER_REQUEST_ID, TEST_REQUEST_ID)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        // ACT
        var gatewayFilter = tenantResolutionFilter.apply(new TenantResolutionFilter.Config());
        StepVerifier.create(gatewayFilter.filter(exchange, chain))
                .verifyComplete();

        // ASSERT
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(chain, never()).filter(any(ServerWebExchange.class));
    }

    @Test
    @DisplayName("Should return 400 when subdomain slug fails validation (too short)")
    void shouldReturn400WhenSubdomainSlugIsInvalid() {
        // ARRANGE — slug "a" is only 1 char, fails the 2-63 char validation
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/v1/tickets")
                .header("Host", "a.supporthub.in")
                .header(TenantResolutionFilter.HEADER_REQUEST_ID, TEST_REQUEST_ID)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        // ACT
        var gatewayFilter = tenantResolutionFilter.apply(new TenantResolutionFilter.Config());
        StepVerifier.create(gatewayFilter.filter(exchange, chain))
                .verifyComplete();

        // ASSERT
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(chain, never()).filter(any(ServerWebExchange.class));
    }

    @Test
    @DisplayName("Should return 400 when X-Tenant-ID header is blank")
    void shouldReturn400WhenTenantIdHeaderIsBlank() {
        // ARRANGE — header present but empty string
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/v1/tickets")
                .header(TenantResolutionFilter.HEADER_TENANT_ID, "   ")
                .header("Host", "localhost")
                .header(TenantResolutionFilter.HEADER_REQUEST_ID, TEST_REQUEST_ID)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        // ACT
        var gatewayFilter = tenantResolutionFilter.apply(new TenantResolutionFilter.Config());
        StepVerifier.create(gatewayFilter.filter(exchange, chain))
                .verifyComplete();

        // ASSERT
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(chain, never()).filter(any(ServerWebExchange.class));
    }
}
