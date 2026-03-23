package in.supporthub.faq.controller;

import in.supporthub.shared.security.TenantContextHolder;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.sql.DataSource;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * HTTP filter that extracts the tenant identifier from the {@code X-Tenant-ID} request header
 * and sets it in {@link TenantContextHolder} for the duration of the request.
 *
 * <p>Also sets the PostgreSQL session variable {@code app.current_tenant} so that
 * Row-Level Security (RLS) policies are enforced at the database level.
 *
 * <p>For public endpoints (FAQ list, search) the tenant ID may be absent.
 * In that case the filter sets nothing and business logic must handle the anonymous case.
 *
 * <p>The tenant context is always cleared in a {@code finally} block to prevent
 * tenant leakage across requests (critical with virtual thread pool reuse).
 */
@Component
@Slf4j
public class TenantContextFilter extends OncePerRequestFilter {

    static final String TENANT_ID_HEADER = "X-Tenant-ID";

    private final DataSource dataSource;

    public TenantContextFilter(DataSource dataSource) {
        this.dataSource = dataSource;
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

    private void setPostgresSessionTenant(String tenantId) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement("SET app.current_tenant = ?")) {
            stmt.setString(1, tenantId);
            stmt.execute();
        } catch (SQLException ex) {
            log.error("Failed to set PostgreSQL session tenant: tenantId={}, error={}",
                    tenantId, ex.getMessage(), ex);
        }
    }
}
