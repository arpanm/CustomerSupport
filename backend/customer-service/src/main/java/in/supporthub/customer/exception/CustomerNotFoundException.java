package in.supporthub.customer.exception;

import in.supporthub.shared.exception.AppException;
import org.springframework.http.HttpStatus;

import java.util.UUID;

/**
 * Thrown when a customer lookup fails because no matching customer exists for the given
 * tenant and customer ID combination.
 *
 * <p>Returns HTTP 404 Not Found with error code {@code CUSTOMER_NOT_FOUND}.
 */
public class CustomerNotFoundException extends AppException {

    /**
     * Creates a CustomerNotFoundException for a specific tenant and customer ID.
     *
     * <p>The message intentionally omits PII — it only references the UUID, not name or phone.
     *
     * @param tenantId   the tenant the lookup was performed against
     * @param customerId the customer UUID that was not found
     */
    public CustomerNotFoundException(UUID tenantId, UUID customerId) {
        super(
                "CUSTOMER_NOT_FOUND",
                String.format("Customer not found: customerId=%s, tenantId=%s", customerId, tenantId),
                HttpStatus.NOT_FOUND
        );
    }
}
