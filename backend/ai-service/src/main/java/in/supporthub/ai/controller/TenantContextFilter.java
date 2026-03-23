package in.supporthub.ai.controller;

import in.supporthub.shared.security.TenantContextHolder;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * HTTP filter that extracts the tenant identifier from the {@code X-Tenant-ID} request header
 * and sets it in {@link TenantContextHolder} for the duration of the request.
 *
 * <p>The {@code X-Tenant-ID} header is injected by the API gateway after validating the JWT.
 * Services MUST NOT accept tenant ID directly from user input.
 *
 * <p>Note: The ai-service uses MongoDB (not PostgreSQL), so no database session variable
 * is set here, unlike the ticket-service filter.
 *
 * <p>The context is always cleared in a {@code finally} block to prevent tenant leakage
 * across virtual threads.
 */
@Component
@Slf4j
public class TenantContextFilter extends OncePerRequestFilter {

    /** Header name injected by the API gateway after JWT validation. */
    static final String TENANT_ID_HEADER = "X-Tenant-ID";

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        String tenantId = request.getHeader(TENANT_ID_HEADER);

        if (tenantId != null && !tenantId.isBlank()) {
            TenantContextHolder.setTenantId(tenantId);
        }

        try {
            filterChain.doFilter(request, response);
        } finally {
            TenantContextHolder.clear();
        }
    }
}
