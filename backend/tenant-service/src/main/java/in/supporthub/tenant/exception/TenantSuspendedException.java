package in.supporthub.tenant.exception;

import in.supporthub.shared.exception.AppException;
import org.springframework.http.HttpStatus;

/**
 * Thrown when an operation is attempted on a suspended tenant.
 */
public class TenantSuspendedException extends AppException {

    /**
     * @param slug the slug of the suspended tenant
     */
    public TenantSuspendedException(String slug) {
        super("TENANT_SUSPENDED", "Tenant '" + slug + "' is suspended. All access is blocked.",
                HttpStatus.FORBIDDEN);
    }
}
