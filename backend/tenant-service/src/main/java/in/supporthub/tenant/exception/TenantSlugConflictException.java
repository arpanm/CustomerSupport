package in.supporthub.tenant.exception;

import in.supporthub.shared.exception.AppException;
import org.springframework.http.HttpStatus;

/**
 * Thrown when a tenant creation request uses a slug that is already taken.
 */
public class TenantSlugConflictException extends AppException {

    /**
     * @param slug the slug that conflicts with an existing tenant
     */
    public TenantSlugConflictException(String slug) {
        super("TENANT_SLUG_CONFLICT", "A tenant with slug '" + slug + "' already exists.",
                HttpStatus.CONFLICT);
    }
}
