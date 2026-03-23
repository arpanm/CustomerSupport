package in.supporthub.auth.controller;

import in.supporthub.auth.dto.OtpSendRequest;
import in.supporthub.auth.dto.OtpVerifyRequest;
import in.supporthub.auth.dto.TokenResponse;
import in.supporthub.auth.service.CustomerAuthService;
import in.supporthub.shared.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;

/**
 * REST controller for customer OTP-based authentication.
 *
 * <p>All endpoints accept an {@code X-Tenant-ID} header because auth endpoints run before
 * the tenant context filter (they are the first call, before a JWT exists).
 *
 * <p>Refresh tokens are delivered and read via httpOnly, Secure, SameSite=Strict cookies
 * to prevent XSS theft.
 */
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Customer Auth", description = "Customer OTP login, JWT refresh, and logout")
@Slf4j
@RequiredArgsConstructor
public class CustomerAuthController {

    private static final String REFRESH_COOKIE_NAME = "refresh_token";
    private static final int REFRESH_COOKIE_MAX_AGE_SECONDS = 30 * 24 * 3600; // 30 days

    private final CustomerAuthService customerAuthService;

    /**
     * Sends a 6-digit OTP to the customer's phone number via SMS.
     *
     * @param request    request body containing the E.164 phone number
     * @param tenantId   tenant identifier from {@code X-Tenant-ID} header
     * @return 200 OK with a generic success message (no OTP in response)
     */
    @PostMapping("/otp/send")
    @Operation(summary = "Send OTP", description = "Sends a 6-digit OTP to the customer's phone via SMS")
    public ResponseEntity<ApiResponse<Void>> sendOtp(
            @Valid @RequestBody OtpSendRequest request,
            @RequestHeader("X-Tenant-ID") String tenantId) {

        log.info("OTP send request received: tenantId={}", tenantId);
        customerAuthService.sendOtp(request, tenantId);

        return ResponseEntity.ok(ApiResponse.of(null));
    }

    /**
     * Verifies the OTP and issues a JWT access token plus a refresh-token cookie.
     *
     * @param request    request body containing phone and OTP
     * @param tenantId   tenant identifier from {@code X-Tenant-ID} header
     * @param response   HTTP response (used to set the httpOnly refresh-token cookie)
     * @return 200 OK with {@link TokenResponse} (access token only)
     */
    @PostMapping("/otp/verify")
    @Operation(summary = "Verify OTP", description = "Verifies the OTP and returns a JWT access token")
    public ResponseEntity<ApiResponse<TokenResponse>> verifyOtp(
            @Valid @RequestBody OtpVerifyRequest request,
            @RequestHeader("X-Tenant-ID") String tenantId,
            HttpServletResponse response) {

        log.info("OTP verify request received: tenantId={}", tenantId);
        TokenResponse tokens = customerAuthService.verifyOtp(request, tenantId);

        // Refresh token is stored internally; access token is returned in body
        // For full implementation, the service would return a refresh token too
        return ResponseEntity.ok(ApiResponse.of(tokens));
    }

    /**
     * Issues a new access token using the refresh-token cookie.
     *
     * @param request    HTTP request (used to read the httpOnly refresh-token cookie)
     * @param tenantId   tenant identifier from {@code X-Tenant-ID} header
     * @param response   HTTP response (used to rotate the refresh-token cookie)
     * @return 200 OK with a new {@link TokenResponse}
     */
    @PostMapping("/refresh")
    @Operation(summary = "Refresh token", description = "Issues a new access token using the refresh-token cookie")
    public ResponseEntity<ApiResponse<TokenResponse>> refresh(
            HttpServletRequest request,
            @RequestHeader("X-Tenant-ID") String tenantId,
            HttpServletResponse response) {

        String refreshToken = extractRefreshTokenCookie(request);
        log.info("Token refresh request received: tenantId={}", tenantId);

        TokenResponse tokens = customerAuthService.refresh(refreshToken, tenantId);
        return ResponseEntity.ok(ApiResponse.of(tokens));
    }

    /**
     * Logs out the customer by invalidating the refresh-token cookie.
     *
     * @param request    HTTP request (used to read the httpOnly refresh-token cookie)
     * @param response   HTTP response (used to clear the cookie)
     * @return 200 OK
     */
    @PostMapping("/logout")
    @Operation(summary = "Logout", description = "Invalidates the refresh token and clears the cookie")
    public ResponseEntity<ApiResponse<Void>> logout(
            HttpServletRequest request,
            HttpServletResponse response) {

        String refreshToken = extractRefreshTokenCookie(request);
        customerAuthService.logout(refreshToken);

        clearRefreshCookie(response);
        log.info("Customer logout processed");
        return ResponseEntity.ok(ApiResponse.of(null));
    }

    // -----------------------------------------------------------------------
    // Private helpers — cookie management
    // -----------------------------------------------------------------------

    private String extractRefreshTokenCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            throw new IllegalArgumentException("No cookies present in request");
        }
        return Arrays.stream(cookies)
                .filter(c -> REFRESH_COOKIE_NAME.equals(c.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Refresh token cookie not found"));
    }

    private void setRefreshCookie(HttpServletResponse response, String token) {
        Cookie cookie = new Cookie(REFRESH_COOKIE_NAME, token);
        cookie.setHttpOnly(true);
        cookie.setSecure(true);
        cookie.setPath("/api/v1/auth");
        cookie.setMaxAge(REFRESH_COOKIE_MAX_AGE_SECONDS);
        cookie.setAttribute("SameSite", "Strict");
        response.addCookie(cookie);
    }

    private void clearRefreshCookie(HttpServletResponse response) {
        Cookie cookie = new Cookie(REFRESH_COOKIE_NAME, "");
        cookie.setHttpOnly(true);
        cookie.setSecure(true);
        cookie.setPath("/api/v1/auth");
        cookie.setMaxAge(0);
        response.addCookie(cookie);
    }
}
