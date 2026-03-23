package in.supporthub.customer.service;

import in.supporthub.customer.domain.Customer;
import in.supporthub.customer.domain.CustomerAddress;
import in.supporthub.customer.dto.CreateAddressRequest;
import in.supporthub.customer.dto.CustomerAddressResponse;
import in.supporthub.customer.dto.CustomerProfileResponse;
import in.supporthub.customer.dto.UpdateProfileRequest;
import in.supporthub.customer.exception.AddressNotFoundException;
import in.supporthub.customer.exception.CustomerNotFoundException;
import in.supporthub.customer.repository.CustomerAddressRepository;
import in.supporthub.customer.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Business logic for customer profile and address management.
 *
 * <p>Logging policy: log tenantId and customerId/addressId where relevant.
 * NEVER log displayName, phone, email, or any other PII.
 *
 * <p>Tenant isolation: all repository calls include tenantId.
 * tenantId is obtained from TenantContextHolder (set by filter), never from request params.
 */
@Service
@Transactional
@Slf4j
@RequiredArgsConstructor
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final CustomerAddressRepository customerAddressRepository;

    // -------------------------------------------------------------------------
    // Profile operations
    // -------------------------------------------------------------------------

    /**
     * Returns the profile for the specified customer.
     *
     * @param tenantId   tenant UUID from TenantContextHolder
     * @param customerId customer UUID from JWT sub claim
     * @return the customer profile
     * @throws CustomerNotFoundException if no customer exists for the given tenant+id
     */
    @Transactional(readOnly = true)
    public CustomerProfileResponse getProfile(UUID tenantId, UUID customerId) {
        log.debug("Fetching profile: tenantId={}, customerId={}", tenantId, customerId);

        Customer customer = customerRepository.findByTenantIdAndId(tenantId, customerId)
                .orElseThrow(() -> new CustomerNotFoundException(tenantId, customerId));

        return toProfileResponse(customer);
    }

    /**
     * Updates the mutable fields of a customer's profile.
     *
     * <p>Phone number is immutable and cannot be changed via this method.
     * Only {@code displayName}, {@code preferredLanguage}, and {@code timezone} may change.
     * Null fields in the request are ignored (no change).
     *
     * @param tenantId   tenant UUID from TenantContextHolder
     * @param customerId customer UUID from JWT sub claim
     * @param req        fields to update
     * @return updated customer profile
     * @throws CustomerNotFoundException if no customer exists for the given tenant+id
     */
    public CustomerProfileResponse updateProfile(UUID tenantId, UUID customerId, UpdateProfileRequest req) {
        log.info("Updating profile: tenantId={}, customerId={}", tenantId, customerId);

        Customer customer = customerRepository.findByTenantIdAndId(tenantId, customerId)
                .orElseThrow(() -> new CustomerNotFoundException(tenantId, customerId));

        if (req.displayName() != null) {
            customer.setDisplayName(req.displayName());
        }
        if (req.preferredLanguage() != null) {
            customer.setPreferredLanguage(req.preferredLanguage());
        }
        if (req.timezone() != null) {
            customer.setTimezone(req.timezone());
        }
        customer.setUpdatedAt(Instant.now());

        Customer saved = customerRepository.save(customer);
        log.info("Profile updated: tenantId={}, customerId={}", tenantId, customerId);

        return toProfileResponse(saved);
    }

    // -------------------------------------------------------------------------
    // Address operations
    // -------------------------------------------------------------------------

    /**
     * Returns all saved addresses for the customer.
     *
     * @param tenantId   tenant UUID
     * @param customerId customer UUID
     * @return list of addresses (empty if none)
     * @throws CustomerNotFoundException if no customer exists for the given tenant+id
     */
    @Transactional(readOnly = true)
    public List<CustomerAddressResponse> getAddresses(UUID tenantId, UUID customerId) {
        log.debug("Fetching addresses: tenantId={}, customerId={}", tenantId, customerId);

        // Verify the customer exists before returning addresses
        if (!customerRepository.findByTenantIdAndId(tenantId, customerId).isPresent()) {
            throw new CustomerNotFoundException(tenantId, customerId);
        }

        return customerAddressRepository.findAllByTenantIdAndCustomerId(tenantId, customerId)
                .stream()
                .map(this::toAddressResponse)
                .toList();
    }

    /**
     * Creates a new address for the customer.
     *
     * <p>If {@code isDefault} is true in the request, all existing addresses are set to
     * non-default before saving the new one.
     *
     * @param tenantId   tenant UUID
     * @param customerId customer UUID
     * @param req        address details
     * @return the created address
     * @throws CustomerNotFoundException if no customer exists for the given tenant+id
     */
    public CustomerAddressResponse addAddress(UUID tenantId, UUID customerId, CreateAddressRequest req) {
        log.info("Adding address: tenantId={}, customerId={}", tenantId, customerId);

        // Verify the customer exists
        if (!customerRepository.findByTenantIdAndId(tenantId, customerId).isPresent()) {
            throw new CustomerNotFoundException(tenantId, customerId);
        }

        if (req.isDefault()) {
            clearDefaultAddresses(tenantId, customerId);
        }

        Instant now = Instant.now();
        CustomerAddress address = CustomerAddress.builder()
                .tenantId(tenantId)
                .customerId(customerId)
                .label(req.label())
                .addressLine1(req.addressLine1())
                .addressLine2(req.addressLine2())
                .city(req.city())
                .state(req.state())
                .pincode(req.pincode())
                .isDefault(req.isDefault())
                .createdAt(now)
                .updatedAt(now)
                .build();

        CustomerAddress saved = customerAddressRepository.save(address);
        log.info("Address added: tenantId={}, customerId={}, addressId={}", tenantId, customerId, saved.getId());

        return toAddressResponse(saved);
    }

    /**
     * Replaces all fields of an existing address.
     *
     * @param tenantId   tenant UUID
     * @param customerId customer UUID
     * @param addressId  address UUID to update
     * @param req        new address details
     * @return the updated address
     * @throws AddressNotFoundException if no address exists for the given tenant+customer+id
     */
    public CustomerAddressResponse updateAddress(
            UUID tenantId, UUID customerId, UUID addressId, CreateAddressRequest req) {

        log.info("Updating address: tenantId={}, customerId={}, addressId={}", tenantId, customerId, addressId);

        CustomerAddress address = customerAddressRepository
                .findByTenantIdAndIdAndCustomerId(tenantId, addressId, customerId)
                .orElseThrow(() -> new AddressNotFoundException(tenantId, customerId, addressId));

        if (req.isDefault() && !address.isDefault()) {
            clearDefaultAddresses(tenantId, customerId);
        }

        address.setLabel(req.label());
        address.setAddressLine1(req.addressLine1());
        address.setAddressLine2(req.addressLine2());
        address.setCity(req.city());
        address.setState(req.state());
        address.setPincode(req.pincode());
        address.setDefault(req.isDefault());
        address.setUpdatedAt(Instant.now());

        CustomerAddress saved = customerAddressRepository.save(address);
        log.info("Address updated: tenantId={}, customerId={}, addressId={}", tenantId, customerId, addressId);

        return toAddressResponse(saved);
    }

    /**
     * Deletes an address.
     *
     * @param tenantId   tenant UUID
     * @param customerId customer UUID
     * @param addressId  address UUID to delete
     * @throws AddressNotFoundException if no address exists for the given tenant+customer+id
     */
    public void deleteAddress(UUID tenantId, UUID customerId, UUID addressId) {
        log.info("Deleting address: tenantId={}, customerId={}, addressId={}", tenantId, customerId, addressId);

        CustomerAddress address = customerAddressRepository
                .findByTenantIdAndIdAndCustomerId(tenantId, addressId, customerId)
                .orElseThrow(() -> new AddressNotFoundException(tenantId, customerId, addressId));

        customerAddressRepository.delete(address);
        log.info("Address deleted: tenantId={}, customerId={}, addressId={}", tenantId, customerId, addressId);
    }

    /**
     * Sets the specified address as the customer's default, clearing any previous default.
     *
     * @param tenantId   tenant UUID
     * @param customerId customer UUID
     * @param addressId  address UUID to set as default
     * @return the updated address
     * @throws AddressNotFoundException if no address exists for the given tenant+customer+id
     */
    public CustomerAddressResponse setDefaultAddress(UUID tenantId, UUID customerId, UUID addressId) {
        log.info("Setting default address: tenantId={}, customerId={}, addressId={}", tenantId, customerId, addressId);

        CustomerAddress address = customerAddressRepository
                .findByTenantIdAndIdAndCustomerId(tenantId, addressId, customerId)
                .orElseThrow(() -> new AddressNotFoundException(tenantId, customerId, addressId));

        // Clear any existing default before setting the new one
        clearDefaultAddresses(tenantId, customerId);

        address.setDefault(true);
        address.setUpdatedAt(Instant.now());

        CustomerAddress saved = customerAddressRepository.save(address);
        log.info("Default address set: tenantId={}, customerId={}, addressId={}", tenantId, customerId, addressId);

        return toAddressResponse(saved);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Clears the default flag on all addresses for a customer.
     * Called before setting a new default to enforce the single-default invariant.
     */
    private void clearDefaultAddresses(UUID tenantId, UUID customerId) {
        List<CustomerAddress> addresses =
                customerAddressRepository.findAllByTenantIdAndCustomerId(tenantId, customerId);

        addresses.stream()
                .filter(CustomerAddress::isDefault)
                .forEach(a -> {
                    a.setDefault(false);
                    a.setUpdatedAt(Instant.now());
                });

        if (!addresses.isEmpty()) {
            customerAddressRepository.saveAll(addresses);
        }
    }

    private CustomerProfileResponse toProfileResponse(Customer customer) {
        return new CustomerProfileResponse(
                customer.getId(),
                customer.getTenantId(),
                customer.getDisplayName(),
                customer.getPreferredLanguage(),
                customer.getTimezone(),
                customer.isActive(),
                customer.getCreatedAt()
        );
    }

    private CustomerAddressResponse toAddressResponse(CustomerAddress address) {
        return new CustomerAddressResponse(
                address.getId(),
                address.getLabel(),
                address.getAddressLine1(),
                address.getAddressLine2(),
                address.getCity(),
                address.getState(),
                address.getPincode(),
                address.isDefault()
        );
    }
}
