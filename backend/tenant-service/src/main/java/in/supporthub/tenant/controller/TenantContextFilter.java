package in.supporthub.tenant.controller;

import in.supporthub.shared.security.TenantContextHolder;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

import javax.sql.DataSource;

/**
 * HTTP filter that extracts the tenant identifier from the {@code X-Tenant-ID} request header
 * and sets it in {@link TenantContextHolder} for the duration of the request.
 *
 * <p>Also sets the PostgreSQL session variable {@code app.tenant_id} so that
 * Row-Level Security (RLS) policies on the {@code tenants} and {@code tenant_configs} tables
 * are enforced at the database level.
 *
 * <p>The filter is skipped for:
 * <ul>
 *   <li>{@code /api/v1/tenants/**} — public slug-resolution endpoint (no tenant context yet)</li>
 *   <li>{@code /actuator/**} — health and observability probes</li>
 * </ul>
 *
 * <p>The context is always cleared in a {@code finally} block to prevent tenant leakage
 * across virtual threads.
 *
 * <p>The {@code X-Tenant-ID} header is set by the API gateway after validating the JWT.
 * Services MUST NOT accept tenant ID directly from user input.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class TenantContextFilter extends OncePerRequestFilter {

    /** Header name injected by the API gateway after JWT validation. */
    static final String TENANT_ID_HEADER = "X-Tenant-ID";

    private final DataSource dataSource;

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/api/v1/tenants/") || path.startsWith("/actuator/");
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        String tenantId = request.getHeader(TENANT_ID_HEADER);

        if (tenantId != null && !tenantId.isBlank()) {
            TenantContextHolder.setTenantId(tenantId);
            setPostgresSessionTenant(tenantId);
        }

        try {
            filterChain.doFilter(request, response);
        } finally {
            TenantContextHolder.clear();
        }
    }

    /**
     * Sets the PostgreSQL session variable {@code app.tenant_id} so that RLS policies
     * on all tables can use {@code current_setting('app.tenant_id', true)}.
     *
     * <p>This is executed on a separate connection obtained from the pool.
     * The variable is session-scoped and will be reset when the connection is returned.
     */
    private void setPostgresSessionTenant(String tenantId) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement("SET app.tenant_id = ?")) {
            stmt.setString(1, tenantId);
            stmt.execute();
        } catch (SQLException ex) {
            log.error("Failed to set PostgreSQL session tenant: tenantId={}, error={}",
                    tenantId, ex.getMessage(), ex);
        }
    }
}
