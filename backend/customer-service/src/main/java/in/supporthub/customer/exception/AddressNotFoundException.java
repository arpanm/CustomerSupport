package in.supporthub.customer.exception;

import in.supporthub.shared.exception.AppException;
import org.springframework.http.HttpStatus;

import java.util.UUID;

/**
 * Thrown when an address lookup fails because no matching address exists for the given
 * tenant, customer, and address ID combination.
 *
 * <p>Returns HTTP 404 Not Found with error code {@code ADDRESS_NOT_FOUND}.
 */
public class AddressNotFoundException extends AppException {

    /**
     * Creates an AddressNotFoundException for a specific tenant, customer, and address.
     *
     * @param tenantId   the tenant the lookup was performed against
     * @param customerId the owning customer UUID
     * @param addressId  the address UUID that was not found
     */
    public AddressNotFoundException(UUID tenantId, UUID customerId, UUID addressId) {
        super(
                "ADDRESS_NOT_FOUND",
                String.format("Address not found: addressId=%s, customerId=%s, tenantId=%s",
                        addressId, customerId, tenantId),
                HttpStatus.NOT_FOUND
        );
    }
}
