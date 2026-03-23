package in.supporthub.tenant.exception;

import in.supporthub.shared.exception.AppException;
import org.springframework.http.HttpStatus;

/**
 * Thrown when a tenant cannot be found by slug or ID.
 */
public class TenantNotFoundException extends AppException {

    /**
     * @param identifier the slug or UUID string that was not found
     */
    public TenantNotFoundException(String identifier) {
        super("TENANT_NOT_FOUND", "Tenant not found: " + identifier, HttpStatus.NOT_FOUND);
    }
}
