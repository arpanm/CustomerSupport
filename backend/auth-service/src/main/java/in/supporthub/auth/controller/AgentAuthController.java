package in.supporthub.auth.controller;

import in.supporthub.auth.dto.AgentLoginRequest;
import in.supporthub.auth.dto.AgentLoginResponse;
import in.supporthub.auth.dto.TokenResponse;
import in.supporthub.auth.dto.TwoFaVerifyRequest;
import in.supporthub.auth.service.AgentAuthService;
import in.supporthub.shared.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for agent email/password authentication with optional 2-FA.
 *
 * <p>ADMIN and SUPER_ADMIN agents must complete a 2-FA email OTP challenge after
 * password verification before tokens are issued.
 */
@RestController
@RequestMapping("/api/v1/auth/agent")
@Tag(name = "Agent Auth", description = "Agent email/password login with optional 2-FA for privileged roles")
@Slf4j
@RequiredArgsConstructor
public class AgentAuthController {

    private final AgentAuthService agentAuthService;

    /**
     * Authenticates an agent with email and password.
     *
     * <p>For ADMIN/SUPER_ADMIN: returns {@code requires2Fa: true} and dispatches an email OTP.
     * For AGENT/TEAM_LEAD: returns the issued tokens immediately.
     *
     * @param request  login credentials — email and password (never logged)
     * @param tenantId tenant identifier from {@code X-Tenant-ID} header
     * @return login response
     */
    @PostMapping("/login")
    @Operation(summary = "Agent login", description = "Authenticates an agent with email and password")
    public ResponseEntity<ApiResponse<AgentLoginResponse>> login(
            @Valid @RequestBody AgentLoginRequest request,
            @RequestHeader("X-Tenant-ID") String tenantId) {

        log.info("Agent login request received: tenantId={}", tenantId);
        AgentLoginResponse loginResponse = agentAuthService.login(request, tenantId);

        return ResponseEntity.ok(ApiResponse.of(loginResponse));
    }

    /**
     * Completes 2-FA authentication for ADMIN/SUPER_ADMIN agents.
     *
     * @param request  contains agentId and 6-digit 2-FA code from email
     * @param tenantId tenant identifier from {@code X-Tenant-ID} header
     * @return issued token response
     */
    @PostMapping("/2fa/verify")
    @Operation(summary = "Verify 2FA", description = "Completes 2FA for privileged agent roles")
    public ResponseEntity<ApiResponse<TokenResponse>> verify2fa(
            @Valid @RequestBody TwoFaVerifyRequest request,
            @RequestHeader("X-Tenant-ID") String tenantId) {

        log.info("Agent 2FA verification request: agentId={}, tenantId={}", request.agentId(), tenantId);
        TokenResponse tokens = agentAuthService.verify2fa(request, tenantId);

        return ResponseEntity.ok(ApiResponse.of(tokens));
    }

}
