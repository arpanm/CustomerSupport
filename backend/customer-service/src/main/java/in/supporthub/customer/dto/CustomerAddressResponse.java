package in.supporthub.customer.dto;

import java.util.UUID;

/**
 * Response DTO for a customer shipping/billing address.
 *
 * @param id           address UUID
 * @param label        short label, e.g. "Home", "Office"
 * @param addressLine1 first line of the address
 * @param addressLine2 second line of the address (may be null)
 * @param city         city name
 * @param state        state or province name
 * @param pincode      postal code
 * @param isDefault    whether this is the customer's default address
 */
public record CustomerAddressResponse(
        UUID id,
        String label,
        String addressLine1,
        String addressLine2,
        String city,
        String state,
        String pincode,
        boolean isDefault
) {}
