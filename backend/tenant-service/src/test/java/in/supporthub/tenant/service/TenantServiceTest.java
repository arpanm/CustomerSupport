package in.supporthub.tenant.service;

import in.supporthub.tenant.domain.PlanType;
import in.supporthub.tenant.domain.Tenant;
import in.supporthub.tenant.domain.TenantConfig;
import in.supporthub.tenant.domain.TenantStatus;
import in.supporthub.tenant.dto.CreateTenantRequest;
import in.supporthub.tenant.dto.TenantConfigResponse;
import in.supporthub.tenant.dto.TenantResponse;
import in.supporthub.tenant.dto.UpdateTenantConfigRequest;
import in.supporthub.tenant.dto.UpdateTenantStatusRequest;
import in.supporthub.tenant.event.TenantEventPublisher;
import in.supporthub.tenant.event.TenantOnboardedEvent;
import in.supporthub.tenant.exception.TenantNotFoundException;
import in.supporthub.tenant.exception.TenantSlugConflictException;
import in.supporthub.tenant.repository.TenantConfigRepository;
import in.supporthub.tenant.repository.TenantRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link TenantService} — the core tenant business logic layer.
 *
 * <p>All external dependencies are mocked; no Spring context is loaded.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TenantService")
class TenantServiceTest {

    @Mock
    private TenantRepository tenantRepository;

    @Mock
    private TenantConfigRepository tenantConfigRepository;

    @Mock
    private TenantEventPublisher tenantEventPublisher;

    @InjectMocks
    private TenantService tenantService;

    private static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    // =========================================================================
    // CREATE TENANT
    // =========================================================================

    @Nested
    @DisplayName("createTenant")
    class CreateTenantTests {

        @Test
        @DisplayName("success — returns populated TenantResponse and publishes Kafka event")
        void createTenant_success_returnsResponse() {
            // Arrange
            CreateTenantRequest req = new CreateTenantRequest(
                    "acme-corp", "Acme Corp", PlanType.STARTER, "Asia/Kolkata", "en-IN");

            Tenant savedTenant = Tenant.builder()
                    .id(TENANT_ID)
                    .tenantId(TENANT_ID)
                    .slug("acme-corp")
                    .name("Acme Corp")
                    .planType(PlanType.STARTER)
                    .status(TenantStatus.PENDING)
                    .timezone("Asia/Kolkata")
                    .locale("en-IN")
                    .createdAt(Instant.now())
                    .build();

            when(tenantRepository.existsBySlug("acme-corp")).thenReturn(false);
            // First save: without tenantId; second save: with tenantId set
            when(tenantRepository.save(any(Tenant.class))).thenReturn(savedTenant);

            // Act
            TenantResponse response = tenantService.createTenant(req);

            // Assert response fields
            assertThat(response).isNotNull();
            assertThat(response.slug()).isEqualTo("acme-corp");
            assertThat(response.name()).isEqualTo("Acme Corp");
            assertThat(response.planType()).isEqualTo(PlanType.STARTER);
            assertThat(response.status()).isEqualTo(TenantStatus.PENDING);

            // Verify save called twice (initial + tenantId self-reference set)
            verify(tenantRepository, times(2)).save(any(Tenant.class));

            // Verify default configs seeded
            verify(tenantConfigRepository).saveAll(any());

            // Verify Kafka event published with correct tenantId
            ArgumentCaptor<TenantOnboardedEvent> eventCaptor =
                    ArgumentCaptor.forClass(TenantOnboardedEvent.class);
            verify(tenantEventPublisher).publishTenantOnboarded(eventCaptor.capture());
            TenantOnboardedEvent publishedEvent = eventCaptor.getValue();
            assertThat(publishedEvent.tenantId()).isEqualTo(TENANT_ID);
            assertThat(publishedEvent.slug()).isEqualTo("acme-corp");
            assertThat(publishedEvent.planType()).isEqualTo(PlanType.STARTER);
        }

        @Test
        @DisplayName("duplicate slug — throws TenantSlugConflictException without saving")
        void createTenant_duplicateSlug_throwsConflict() {
            // Arrange
            CreateTenantRequest req = new CreateTenantRequest(
                    "existing-slug", "Some Tenant", PlanType.FREE, null, null);

            when(tenantRepository.existsBySlug("existing-slug")).thenReturn(true);

            // Act + Assert
            assertThatThrownBy(() -> tenantService.createTenant(req))
                    .isInstanceOf(TenantSlugConflictException.class)
                    .hasMessageContaining("existing-slug");

            verify(tenantRepository, never()).save(any());
            verify(tenantEventPublisher, never()).publishTenantOnboarded(any());
        }
    }

    // =========================================================================
    // GET BY SLUG
    // =========================================================================

    @Nested
    @DisplayName("getBySlug")
    class GetBySlugTests {

        @Test
        @DisplayName("found — returns mapped TenantResponse")
        void getBySlug_found_returnsResponse() {
            // Arrange
            Tenant tenant = buildTenant(TENANT_ID, "acme-corp");
            when(tenantRepository.findBySlug("acme-corp")).thenReturn(Optional.of(tenant));

            // Act
            TenantResponse response = tenantService.getBySlug("acme-corp");

            // Assert
            assertThat(response).isNotNull();
            assertThat(response.id()).isEqualTo(TENANT_ID);
            assertThat(response.slug()).isEqualTo("acme-corp");
            assertThat(response.planType()).isEqualTo(PlanType.GROWTH);
            assertThat(response.status()).isEqualTo(TenantStatus.ACTIVE);
        }

        @Test
        @DisplayName("not found — throws TenantNotFoundException")
        void getBySlug_notFound_throwsException() {
            // Arrange
            when(tenantRepository.findBySlug("unknown")).thenReturn(Optional.empty());

            // Act + Assert
            assertThatThrownBy(() -> tenantService.getBySlug("unknown"))
                    .isInstanceOf(TenantNotFoundException.class)
                    .hasMessageContaining("unknown");
        }
    }

    // =========================================================================
    // UPDATE CONFIG
    // =========================================================================

    @Nested
    @DisplayName("updateConfig")
    class UpdateConfigTests {

        @Test
        @DisplayName("upserts — updates existing key and inserts new key")
        void updateConfig_upserts_existingAndNew() {
            // Arrange
            Tenant tenant = buildTenant(TENANT_ID, "acme-corp");
            when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));

            // Existing config for "branding.primary_color"
            TenantConfig existingConfig = TenantConfig.builder()
                    .id(UUID.randomUUID())
                    .tenantId(TENANT_ID)
                    .configKey("branding.primary_color")
                    .configValue("#007AFF")
                    .build();
            when(tenantConfigRepository.findByTenantIdAndConfigKey(TENANT_ID, "branding.primary_color"))
                    .thenReturn(Optional.of(existingConfig));

            // No existing config for "branding.logo_url" — will be inserted
            when(tenantConfigRepository.findByTenantIdAndConfigKey(TENANT_ID, "branding.logo_url"))
                    .thenReturn(Optional.empty());

            // For getConfig after update — return updated entries
            TenantConfig updatedColor = TenantConfig.builder()
                    .tenantId(TENANT_ID).configKey("branding.primary_color").configValue("#FF0000").build();
            TenantConfig newLogo = TenantConfig.builder()
                    .tenantId(TENANT_ID).configKey("branding.logo_url").configValue("https://cdn.example.com/logo.png").build();
            when(tenantConfigRepository.findAllByTenantId(TENANT_ID))
                    .thenReturn(List.of(updatedColor, newLogo));

            UpdateTenantConfigRequest req = new UpdateTenantConfigRequest(Map.of(
                    "branding.primary_color", "#FF0000",
                    "branding.logo_url", "https://cdn.example.com/logo.png"
            ));

            // Act
            TenantConfigResponse response = tenantService.updateConfig(TENANT_ID, req);

            // Assert
            assertThat(response.tenantId()).isEqualTo(TENANT_ID);
            assertThat(response.configs()).containsEntry("branding.primary_color", "#FF0000");
            assertThat(response.configs()).containsEntry("branding.logo_url", "https://cdn.example.com/logo.png");

            // Verify save called for each key (2 keys)
            verify(tenantConfigRepository, times(2)).save(any(TenantConfig.class));
        }
    }

    // =========================================================================
    // UPDATE STATUS
    // =========================================================================

    @Nested
    @DisplayName("updateStatus")
    class UpdateStatusTests {

        @Test
        @DisplayName("suspend — sets status to SUSPENDED and saves")
        void updateStatus_suspend_savesCorrectly() {
            // Arrange
            Tenant tenant = buildTenant(TENANT_ID, "acme-corp");
            // tenant is ACTIVE; we want to SUSPEND it

            Tenant suspendedTenant = Tenant.builder()
                    .id(TENANT_ID)
                    .tenantId(TENANT_ID)
                    .slug("acme-corp")
                    .name("Acme Corp")
                    .planType(PlanType.GROWTH)
                    .status(TenantStatus.SUSPENDED)
                    .timezone("Asia/Kolkata")
                    .locale("en-IN")
                    .createdAt(tenant.getCreatedAt())
                    .build();

            when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));
            when(tenantRepository.save(any(Tenant.class))).thenReturn(suspendedTenant);

            UpdateTenantStatusRequest req = new UpdateTenantStatusRequest(TenantStatus.SUSPENDED);

            // Act
            TenantResponse response = tenantService.updateStatus(TENANT_ID, req);

            // Assert
            assertThat(response.status()).isEqualTo(TenantStatus.SUSPENDED);

            // Verify the tenant status was set before save
            ArgumentCaptor<Tenant> tenantCaptor = ArgumentCaptor.forClass(Tenant.class);
            verify(tenantRepository).save(tenantCaptor.capture());
            assertThat(tenantCaptor.getValue().getStatus()).isEqualTo(TenantStatus.SUSPENDED);
        }
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private Tenant buildTenant(UUID id, String slug) {
        return Tenant.builder()
                .id(id)
                .tenantId(id)
                .slug(slug)
                .name("Acme Corp")
                .planType(PlanType.GROWTH)
                .status(TenantStatus.ACTIVE)
                .timezone("Asia/Kolkata")
                .locale("en-IN")
                .createdAt(Instant.now())
                .build();
    }
}
