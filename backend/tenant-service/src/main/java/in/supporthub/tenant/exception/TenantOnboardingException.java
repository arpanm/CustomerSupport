package in.supporthub.tenant.exception;

import in.supporthub.shared.exception.AppException;
import org.springframework.http.HttpStatus;

/**
 * Thrown when a Kafka event fails to publish during tenant onboarding.
 */
public class TenantOnboardingException extends AppException {

    /**
     * @param tenantId the UUID of the tenant being onboarded
     * @param cause    the underlying exception from Kafka
     */
    public TenantOnboardingException(String tenantId, Throwable cause) {
        super("TENANT_ONBOARDING_FAILED",
                "Failed to publish onboarding event for tenant: " + tenantId,
                HttpStatus.INTERNAL_SERVER_ERROR,
                cause);
    }
}
