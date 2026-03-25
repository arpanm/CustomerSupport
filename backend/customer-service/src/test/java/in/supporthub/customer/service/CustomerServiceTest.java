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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link CustomerService}.
 *
 * <p>All dependencies are mocked. No Spring context is loaded.
 */
@ExtendWith(MockitoExtension.class)
class CustomerServiceTest {

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private CustomerAddressRepository customerAddressRepository;

    @InjectMocks
    private CustomerService customerService;

    private UUID tenantId;
    private UUID customerId;
    private Customer customer;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        customerId = UUID.randomUUID();
        customer = Customer.builder()
                .id(customerId)
                .tenantId(tenantId)
                .displayName("Test User")
                .preferredLanguage("en")
                .timezone("Asia/Kolkata")
                .isActive(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    // -------------------------------------------------------------------------
    // getProfile
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("getProfile")
    class GetProfileTests {

        @Test
        @DisplayName("returns profile when customer exists")
        void getProfile_found_returnsProfile() {
            when(customerRepository.findByTenantIdAndId(tenantId, customerId))
                    .thenReturn(Optional.of(customer));

            CustomerProfileResponse response = customerService.getProfile(tenantId, customerId);

            assertThat(response).isNotNull();
            assertThat(response.id()).isEqualTo(customerId);
            assertThat(response.tenantId()).isEqualTo(tenantId);
            assertThat(response.preferredLanguage()).isEqualTo("en");
            assertThat(response.isActive()).isTrue();
        }

        @Test
        @DisplayName("throws CustomerNotFoundException when customer not found")
        void getProfile_notFound_throwsException() {
            when(customerRepository.findByTenantIdAndId(tenantId, customerId))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> customerService.getProfile(tenantId, customerId))
                    .isInstanceOf(CustomerNotFoundException.class)
                    .hasMessageContaining("Customer not found")
                    .extracting("errorCode")
                    .isEqualTo("CUSTOMER_NOT_FOUND");
        }
    }

    // -------------------------------------------------------------------------
    // updateProfile
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("updateProfile")
    class UpdateProfileTests {

        @Test
        @DisplayName("updates only provided fields — null fields are ignored")
        void updateProfile_partialUpdate_onlyChangesProvidedFields() {
            when(customerRepository.findByTenantIdAndId(tenantId, customerId))
                    .thenReturn(Optional.of(customer));
            when(customerRepository.save(any(Customer.class))).thenAnswer(inv -> inv.getArgument(0));

            UpdateProfileRequest req = new UpdateProfileRequest("New Name", null, null);
            CustomerProfileResponse response = customerService.updateProfile(tenantId, customerId, req);

            assertThat(response.preferredLanguage()).isEqualTo("en");      // unchanged
            assertThat(response.timezone()).isEqualTo("Asia/Kolkata");     // unchanged
            verify(customerRepository).save(any(Customer.class));
        }

        @Test
        @DisplayName("updates all fields when all are provided")
        void updateProfile_allFields_updatesAll() {
            when(customerRepository.findByTenantIdAndId(tenantId, customerId))
                    .thenReturn(Optional.of(customer));
            when(customerRepository.save(any(Customer.class))).thenAnswer(inv -> inv.getArgument(0));

            UpdateProfileRequest req = new UpdateProfileRequest("Updated Name", "hi", "UTC");
            CustomerProfileResponse response = customerService.updateProfile(tenantId, customerId, req);

            assertThat(response.preferredLanguage()).isEqualTo("hi");
            assertThat(response.timezone()).isEqualTo("UTC");
            verify(customerRepository).save(any(Customer.class));
        }

        @Test
        @DisplayName("throws CustomerNotFoundException when customer not found")
        void updateProfile_notFound_throwsException() {
            when(customerRepository.findByTenantIdAndId(tenantId, customerId))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> customerService.updateProfile(tenantId, customerId,
                    new UpdateProfileRequest("Name", null, null)))
                    .isInstanceOf(CustomerNotFoundException.class);

            verify(customerRepository, never()).save(any());
        }
    }

    // -------------------------------------------------------------------------
    // addAddress
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("addAddress")
    class AddAddressTests {

        @Test
        @DisplayName("saves new address and returns response")
        void addAddress_valid_savesAndReturns() {
            when(customerRepository.findByTenantIdAndId(tenantId, customerId))
                    .thenReturn(Optional.of(customer));

            CreateAddressRequest req = new CreateAddressRequest(
                    "Home", "123 Main St", null, "Mumbai", "Maharashtra", "400001", false);

            CustomerAddress saved = CustomerAddress.builder()
                    .id(UUID.randomUUID())
                    .tenantId(tenantId)
                    .customerId(customerId)
                    .label("Home")
                    .addressLine1("123 Main St")
                    .city("Mumbai")
                    .state("Maharashtra")
                    .pincode("400001")
                    .isDefault(false)
                    .createdAt(Instant.now())
                    .updatedAt(Instant.now())
                    .build();

            when(customerAddressRepository.save(any(CustomerAddress.class))).thenReturn(saved);

            CustomerAddressResponse response = customerService.addAddress(tenantId, customerId, req);

            assertThat(response).isNotNull();
            assertThat(response.city()).isEqualTo("Mumbai");
            assertThat(response.isDefault()).isFalse();
            verify(customerAddressRepository).save(any(CustomerAddress.class));
        }

        @Test
        @DisplayName("clears existing defaults when new address is set as default")
        void addAddress_isDefault_clearsExistingDefaults() {
            when(customerRepository.findByTenantIdAndId(tenantId, customerId))
                    .thenReturn(Optional.of(customer));

            CustomerAddress existingDefault = CustomerAddress.builder()
                    .id(UUID.randomUUID())
                    .tenantId(tenantId)
                    .customerId(customerId)
                    .isDefault(true)
                    .createdAt(Instant.now())
                    .updatedAt(Instant.now())
                    .build();

            when(customerAddressRepository.findAllByTenantIdAndCustomerId(tenantId, customerId))
                    .thenReturn(List.of(existingDefault));

            CustomerAddress newAddress = CustomerAddress.builder()
                    .id(UUID.randomUUID())
                    .tenantId(tenantId)
                    .customerId(customerId)
                    .label("Office")
                    .addressLine1("456 Work Rd")
                    .city("Pune")
                    .state("Maharashtra")
                    .pincode("411001")
                    .isDefault(true)
                    .createdAt(Instant.now())
                    .updatedAt(Instant.now())
                    .build();

            when(customerAddressRepository.save(any(CustomerAddress.class))).thenReturn(newAddress);

            CreateAddressRequest req = new CreateAddressRequest(
                    "Office", "456 Work Rd", null, "Pune", "Maharashtra", "411001", true);

            customerService.addAddress(tenantId, customerId, req);

            // Verify saveAll was called to clear the old default
            verify(customerAddressRepository).saveAll(any());
            verify(customerAddressRepository).save(any(CustomerAddress.class));
        }
    }

    // -------------------------------------------------------------------------
    // setDefaultAddress
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("setDefaultAddress")
    class SetDefaultAddressTests {

        @Test
        @DisplayName("sets the address as default and clears previous default")
        void setDefaultAddress_valid_clearsPreviousAndSetsNew() {
            UUID addressId = UUID.randomUUID();
            UUID oldDefaultId = UUID.randomUUID();

            CustomerAddress oldDefault = CustomerAddress.builder()
                    .id(oldDefaultId)
                    .tenantId(tenantId)
                    .customerId(customerId)
                    .isDefault(true)
                    .createdAt(Instant.now())
                    .updatedAt(Instant.now())
                    .build();

            CustomerAddress targetAddress = CustomerAddress.builder()
                    .id(addressId)
                    .tenantId(tenantId)
                    .customerId(customerId)
                    .isDefault(false)
                    .label("Home")
                    .addressLine1("1 Test St")
                    .city("Delhi")
                    .state("Delhi")
                    .pincode("110001")
                    .createdAt(Instant.now())
                    .updatedAt(Instant.now())
                    .build();

            when(customerAddressRepository.findByTenantIdAndIdAndCustomerId(tenantId, addressId, customerId))
                    .thenReturn(Optional.of(targetAddress));
            when(customerAddressRepository.findAllByTenantIdAndCustomerId(tenantId, customerId))
                    .thenReturn(List.of(oldDefault, targetAddress));
            when(customerAddressRepository.save(any(CustomerAddress.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            CustomerAddressResponse response = customerService.setDefaultAddress(tenantId, customerId, addressId);

            assertThat(response.isDefault()).isTrue();
            // saveAll is called to clear old defaults, then save for the new default
            verify(customerAddressRepository).saveAll(any());
            verify(customerAddressRepository).save(any(CustomerAddress.class));
        }

        @Test
        @DisplayName("throws AddressNotFoundException when address not found")
        void setDefaultAddress_notFound_throwsException() {
            UUID addressId = UUID.randomUUID();

            when(customerAddressRepository.findByTenantIdAndIdAndCustomerId(tenantId, addressId, customerId))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> customerService.setDefaultAddress(tenantId, customerId, addressId))
                    .isInstanceOf(AddressNotFoundException.class)
                    .extracting("errorCode")
                    .isEqualTo("ADDRESS_NOT_FOUND");

            verify(customerAddressRepository, never()).save(any());
        }
    }

    // -------------------------------------------------------------------------
    // deleteAddress
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("deleteAddress")
    class DeleteAddressTests {

        @Test
        @DisplayName("deletes address when found")
        void deleteAddress_found_deletesSuccessfully() {
            UUID addressId = UUID.randomUUID();
            CustomerAddress address = CustomerAddress.builder()
                    .id(addressId)
                    .tenantId(tenantId)
                    .customerId(customerId)
                    .isDefault(false)
                    .createdAt(Instant.now())
                    .updatedAt(Instant.now())
                    .build();

            when(customerAddressRepository.findByTenantIdAndIdAndCustomerId(tenantId, addressId, customerId))
                    .thenReturn(Optional.of(address));

            customerService.deleteAddress(tenantId, customerId, addressId);

            verify(customerAddressRepository).delete(address);
        }

        @Test
        @DisplayName("throws AddressNotFoundException when address not found")
        void deleteAddress_notFound_throwsException() {
            UUID addressId = UUID.randomUUID();

            when(customerAddressRepository.findByTenantIdAndIdAndCustomerId(tenantId, addressId, customerId))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> customerService.deleteAddress(tenantId, customerId, addressId))
                    .isInstanceOf(AddressNotFoundException.class)
                    .extracting("errorCode")
                    .isEqualTo("ADDRESS_NOT_FOUND");

            verify(customerAddressRepository, never()).delete(any(CustomerAddress.class));
        }
    }
}
