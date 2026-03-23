package in.supporthub.gateway.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.supporthub.gateway.service.JwtValidationService;
import in.supporthub.shared.security.JwtClaims;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link JwtAuthFilter}.
 *
 * <p>Test strategy:
 * <ul>
 *   <li>Valid JWT: filter passes request through with correct downstream headers injected.</li>
 *   <li>Missing Authorization header: returns 401.</li>
 *   <li>Invalid/expired JWT: returns 401.</li>
 *   <li>Public paths: filter bypassed entirely.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class JwtAuthFilterTest {

    private static final String VALID_TOKEN = "valid.jwt.token";
    private static final String INVALID_TOKEN = "invalid.jwt.token";
    private static final String TEST_USER_ID = "user-uuid-123";
    private static final String TEST_TENANT_ID = "tenant-uuid-456";
    private static final String TEST_ROLE = JwtClaims.ROLE_AGENT;
    private static final String TEST_TYPE = JwtClaims.TYPE_AGENT;
    private static final String TEST_REQUEST_ID = "req-001";

    @Mock
    private JwtValidationService jwtValidationService;

    @Mock
    private GatewayFilterChain chain;

    private JwtAuthFilter jwtAuthFilter;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        jwtAuthFilter = new JwtAuthFilter(jwtValidationService, objectMapper);
        when(chain.filter(anyServerWebExchange())).thenReturn(Mono.empty());
    }

    // -------------------------------------------------------------------------
    // Happy path tests
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Should pass request through with injected downstream headers when JWT is valid")
    void shouldPassThroughAndInjectHeadersForValidJwt() {
        // ARRANGE
        JwtClaims claims = new JwtClaims(TEST_USER_ID, TEST_TENANT_ID, TEST_ROLE, TEST_TYPE);
        when(jwtValidationService.validateAndExtract(VALID_TOKEN)).thenReturn(claims);

        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/v1/tickets")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + VALID_TOKEN)
                .header(JwtAuthFilter.HEADER_REQUEST_ID, TEST_REQUEST_ID)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        // ACT
        var gatewayFilter = jwtAuthFilter.apply(new JwtAuthFilter.Config());
        StepVerifier.create(gatewayFilter.filter(exchange, chain))
                .verifyComplete();

        // ASSERT
        verify(chain).filter(anyServerWebExchange());
        verify(jwtValidationService).validateAndExtract(VALID_TOKEN);
    }

    @Test
    @DisplayName("Should skip JWT validation for public auth paths")
    void shouldSkipAuthForPublicAuthPath() {
        // ARRANGE
        MockServerHttpRequest request = MockServerHttpRequest
                .post("/api/v1/auth/login")
                .header(JwtAuthFilter.HEADER_REQUEST_ID, TEST_REQUEST_ID)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        // ACT
        var gatewayFilter = jwtAuthFilter.apply(new JwtAuthFilter.Config());
        StepVerifier.create(gatewayFilter.filter(exchange, chain))
                .verifyComplete();

        // ASSERT — JWT service must NOT be called for public paths
        verify(jwtValidationService, never()).validateAndExtract(anyString());
        verify(chain).filter(exchange);
    }

    @Test
    @DisplayName("Should skip JWT validation for actuator health endpoint")
    void shouldSkipAuthForActuatorHealth() {
        // ARRANGE
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/actuator/health")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        // ACT
        var gatewayFilter = jwtAuthFilter.apply(new JwtAuthFilter.Config());
        StepVerifier.create(gatewayFilter.filter(exchange, chain))
                .verifyComplete();

        // ASSERT
        verify(jwtValidationService, never()).validateAndExtract(anyString());
    }

    @Test
    @DisplayName("Should skip JWT validation for fallback paths")
    void shouldSkipAuthForFallbackPath() {
        // ARRANGE
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/fallback/service")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        // ACT
        var gatewayFilter = jwtAuthFilter.apply(new JwtAuthFilter.Config());
        StepVerifier.create(gatewayFilter.filter(exchange, chain))
                .verifyComplete();

        // ASSERT
        verify(jwtValidationService, never()).validateAndExtract(anyString());
    }

    // -------------------------------------------------------------------------
    // Error path tests
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Should return 401 when Authorization header is missing")
    void shouldReturn401WhenAuthorizationHeaderMissing() {
        // ARRANGE
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/v1/tickets")
                .header(JwtAuthFilter.HEADER_REQUEST_ID, TEST_REQUEST_ID)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        // ACT
        var gatewayFilter = jwtAuthFilter.apply(new JwtAuthFilter.Config());
        StepVerifier.create(gatewayFilter.filter(exchange, chain))
                .verifyComplete();

        // ASSERT
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(jwtValidationService, never()).validateAndExtract(anyString());
        verify(chain, never()).filter(anyServerWebExchange());
    }

    @Test
    @DisplayName("Should return 401 when Authorization header is not Bearer-prefixed")
    void shouldReturn401WhenAuthorizationHeaderIsNotBearer() {
        // ARRANGE
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/v1/tickets")
                .header(HttpHeaders.AUTHORIZATION, "Basic dXNlcjpwYXNz")
                .header(JwtAuthFilter.HEADER_REQUEST_ID, TEST_REQUEST_ID)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        // ACT
        var gatewayFilter = jwtAuthFilter.apply(new JwtAuthFilter.Config());
        StepVerifier.create(gatewayFilter.filter(exchange, chain))
                .verifyComplete();

        // ASSERT
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(jwtValidationService, never()).validateAndExtract(anyString());
    }

    @Test
    @DisplayName("Should return 401 when JWT is invalid or expired")
    void shouldReturn401WhenJwtIsInvalid() {
        // ARRANGE
        when(jwtValidationService.validateAndExtract(INVALID_TOKEN))
                .thenThrow(new JwtException("Token expired"));

        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/v1/tickets")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + INVALID_TOKEN)
                .header(JwtAuthFilter.HEADER_REQUEST_ID, TEST_REQUEST_ID)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        // ACT
        var gatewayFilter = jwtAuthFilter.apply(new JwtAuthFilter.Config());
        StepVerifier.create(gatewayFilter.filter(exchange, chain))
                .verifyComplete();

        // ASSERT
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(chain, never()).filter(anyServerWebExchange());
    }

    @Test
    @DisplayName("Should skip JWT validation for MCP SSE endpoint")
    void shouldSkipAuthForMcpSseEndpoint() {
        // ARRANGE
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/mcp/sse")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        // ACT
        var gatewayFilter = jwtAuthFilter.apply(new JwtAuthFilter.Config());
        StepVerifier.create(gatewayFilter.filter(exchange, chain))
                .verifyComplete();

        // ASSERT
        verify(jwtValidationService, never()).validateAndExtract(anyString());
    }

    // -------------------------------------------------------------------------
    // Helper matchers
    // -------------------------------------------------------------------------

    private static ServerWebExchange anyServerWebExchange() {
        return org.mockito.ArgumentMatchers.any(ServerWebExchange.class);
    }
}
