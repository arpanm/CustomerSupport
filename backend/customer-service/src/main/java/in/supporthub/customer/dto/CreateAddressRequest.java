package in.supporthub.customer.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for creating or replacing a customer shipping/billing address.
 *
 * <p>Used for both {@code POST /me/addresses} (create) and
 * {@code PUT /me/addresses/{addressId}} (full replace).
 *
 * @param label        short label for the address (e.g. "Home", "Office")
 * @param addressLine1 first line — required
 * @param addressLine2 second line — optional
 * @param city         city name — required
 * @param state        state or province — required
 * @param pincode      postal code — required
 * @param isDefault    whether to set this as the customer's default address
 */
public record CreateAddressRequest(
        @Size(max = 50, message = "Label must not exceed 50 characters")
        String label,

        @NotBlank(message = "Address line 1 is required")
        @Size(max = 200, message = "Address line 1 must not exceed 200 characters")
        String addressLine1,

        @Size(max = 200, message = "Address line 2 must not exceed 200 characters")
        String addressLine2,

        @NotBlank(message = "City is required")
        @Size(max = 100, message = "City must not exceed 100 characters")
        String city,

        @NotBlank(message = "State is required")
        @Size(max = 100, message = "State must not exceed 100 characters")
        String state,

        @NotBlank(message = "Pincode is required")
        @Size(max = 10, message = "Pincode must not exceed 10 characters")
        String pincode,

        boolean isDefault
) {}
