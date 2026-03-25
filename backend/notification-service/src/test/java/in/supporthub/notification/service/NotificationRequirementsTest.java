package in.supporthub.notification.service;

import in.supporthub.notification.domain.Notification;
import in.supporthub.notification.domain.Notification.Channel;
import in.supporthub.notification.domain.Notification.RecipientType;
import in.supporthub.notification.domain.Notification.Status;
import in.supporthub.notification.repository.NotificationRepository;
import in.supporthub.notification.service.inapp.InAppNotificationService;
import in.supporthub.notification.service.sms.Msg91SmsService;
import in.supporthub.notification.service.whatsapp.WhatsAppService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Requirement-mapped tests for notification-service.
 * Covers: REQ-NOTIF-01, REQ-NOTIF-02, REQ-NOTIF-03, REQ-NOTIF-04,
 *         REQ-NOTIF-05, REQ-NOTIF-06, REQ-SEC-12
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationService — Requirement Tests")
class NotificationRequirementsTest {

    @Mock
    private Msg91SmsService smsService;

    @Mock
    private WhatsAppService whatsAppService;

    @Mock
    private InAppNotificationService inAppNotificationService;

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private PiiDecryptionService piiDecryptionService;

    @InjectMocks
    private NotificationService notificationService;

    private static final String TENANT_ID = "tenant-abc";
    private static final String CUSTOMER_ID = "cust-001";
    private static final String TICKET_NUMBER = "FC-2024-000001";
    private static final String TICKET_ID = "ticket-uuid-001";

    @Nested
    @DisplayName("REQ-NOTIF-01: Ticket created — in-app + channel records persisted")
    class TicketCreatedNotification {

        @Test
        @DisplayName("in-app notification saved for ticket creation")
        void inAppNotificationSavedOnTicketCreated() throws Exception {
            Notification saved = Notification.builder()
                    .id("notif-001")
                    .tenantId(TENANT_ID)
                    .recipientId(CUSTOMER_ID)
                    .channel(Channel.IN_APP)
                    .build();
            when(inAppNotificationService.saveNotification(
                    eq(TENANT_ID), eq(CUSTOMER_ID), eq(RecipientType.CUSTOMER),
                    anyString(), anyString(), any(), anyString(), any()))
                    .thenReturn(saved);
            when(notificationRepository.save(any())).thenReturn(saved);

            notificationService.sendTicketCreatedNotification(TENANT_ID, CUSTOMER_ID, TICKET_NUMBER, "cat-001");

            verify(inAppNotificationService).saveNotification(
                    eq(TENANT_ID), eq(CUSTOMER_ID), eq(RecipientType.CUSTOMER),
                    anyString(), anyString(), any(), eq("TICKET"), any());
        }

        @Test
        @DisplayName("SMS and WhatsApp channel notification records persisted as PENDING")
        void channelRecordsPersisted() throws Exception {
            Notification saved = Notification.builder().id("notif-002").build();
            when(inAppNotificationService.saveNotification(any(), any(), any(), any(), any(), any(), any(), any()))
                    .thenReturn(saved);
            when(notificationRepository.save(any())).thenReturn(saved);

            notificationService.sendTicketCreatedNotification(TENANT_ID, CUSTOMER_ID, TICKET_NUMBER, "cat-001");

            // Two channel records: SMS and WhatsApp
            ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
            verify(notificationRepository, times(2)).save(captor.capture());

            List<Notification> persisted = captor.getAllValues();
            assertThat(persisted).anySatisfy(n -> assertThat(n.getChannel()).isEqualTo(Channel.SMS));
            assertThat(persisted).anySatisfy(n -> assertThat(n.getChannel()).isEqualTo(Channel.WHATSAPP));
            assertThat(persisted).allSatisfy(n -> assertThat(n.getStatus()).isEqualTo(Status.PENDING));
        }

        @Test
        @DisplayName("in-app failure does not block channel record persistence")
        void inAppFailureDoesNotBlockChannelRecords() throws Exception {
            when(inAppNotificationService.saveNotification(any(), any(), any(), any(), any(), any(), any(), any()))
                    .thenThrow(new RuntimeException("MongoDB unavailable"));
            Notification saved = Notification.builder().id("notif-003").build();
            when(notificationRepository.save(any())).thenReturn(saved);

            assertThatCode(() -> notificationService.sendTicketCreatedNotification(
                    TENANT_ID, CUSTOMER_ID, TICKET_NUMBER, "cat-001"))
                    .doesNotThrowAnyException();

            verify(notificationRepository, times(2)).save(any());
        }
    }

    @Nested
    @DisplayName("REQ-NOTIF-02: Ticket created with phone — SMS + WhatsApp sent")
    class TicketCreatedWithPhone {

        @Test
        @DisplayName("decrypts phone and sends SMS on ticket creation")
        void decryptsPhoneAndSendsSms() throws Exception {
            String encryptedPhone = "base64encodedphone==";
            String decryptedPhone = "9876543210";
            when(piiDecryptionService.decryptBase64(encryptedPhone)).thenReturn(decryptedPhone);
            when(smsService.sendTicketCreatedSms(decryptedPhone, TICKET_NUMBER, "Billing")).thenReturn(true);

            notificationService.sendTicketCreatedWithPhone(
                    TENANT_ID, CUSTOMER_ID, encryptedPhone, TICKET_NUMBER, "Billing");

            verify(smsService).sendTicketCreatedSms(decryptedPhone, TICKET_NUMBER, "Billing");
        }

        @Test
        @DisplayName("WhatsApp is attempted even if SMS fails")
        void whatsAppAttemptedEvenIfSmsFails() throws Exception {
            String encryptedPhone = "base64encodedphone==";
            String decryptedPhone = "9876543210";
            when(piiDecryptionService.decryptBase64(encryptedPhone)).thenReturn(decryptedPhone);
            when(smsService.sendTicketCreatedSms(any(), any(), any()))
                    .thenThrow(new RuntimeException("SMS gateway timeout"));

            assertThatCode(() -> notificationService.sendTicketCreatedWithPhone(
                    TENANT_ID, CUSTOMER_ID, encryptedPhone, TICKET_NUMBER, "Billing"))
                    .doesNotThrowAnyException();

            verify(whatsAppService).sendTicketCreatedMessage(decryptedPhone, TICKET_NUMBER, "Billing");
        }

        @Test
        @DisplayName("REQ-SEC-12: phone decryption failure aborts SMS dispatch without throwing")
        void phoneDecryptionFailureAbortsSmsDispatch() throws Exception {
            when(piiDecryptionService.decryptBase64(any()))
                    .thenThrow(new RuntimeException("Decryption key not found"));

            assertThatCode(() -> notificationService.sendTicketCreatedWithPhone(
                    TENANT_ID, CUSTOMER_ID, "baddata", TICKET_NUMBER, "Billing"))
                    .doesNotThrowAnyException();

            verifyNoInteractions(smsService, whatsAppService);
        }
    }

    @Nested
    @DisplayName("REQ-NOTIF-03: Status changed — in-app notification saved")
    class StatusChangedNotification {

        @Test
        @DisplayName("RESOLVED status triggers in-app notification with correct subject")
        void resolvedStatusTriggersInAppNotification() throws Exception {
            Notification saved = Notification.builder().id("notif-004").build();
            when(inAppNotificationService.saveNotification(any(), any(), any(), any(), any(), any(), any(), any()))
                    .thenReturn(saved);
            when(notificationRepository.save(any())).thenReturn(saved);

            notificationService.sendStatusChangedNotification(
                    TENANT_ID, CUSTOMER_ID, RecipientType.CUSTOMER,
                    "IN_PROGRESS", "RESOLVED", TICKET_NUMBER, TICKET_ID);

            ArgumentCaptor<String> subjectCaptor = ArgumentCaptor.forClass(String.class);
            verify(inAppNotificationService).saveNotification(
                    eq(TENANT_ID), eq(CUSTOMER_ID), eq(RecipientType.CUSTOMER),
                    subjectCaptor.capture(), anyString(), eq(TICKET_ID), eq("TICKET"), any());

            assertThat(subjectCaptor.getValue()).contains("Resolved");
        }

        @Test
        @DisplayName("ESCALATED to AGENT: content addresses agent directly")
        void escalatedContentAddressesAgent() throws Exception {
            Notification saved = Notification.builder().id("notif-005").build();
            when(inAppNotificationService.saveNotification(any(), any(), any(), any(), any(), any(), any(), any()))
                    .thenReturn(saved);
            when(notificationRepository.save(any())).thenReturn(saved);

            notificationService.sendStatusChangedNotification(
                    TENANT_ID, "agent-001", RecipientType.AGENT,
                    "IN_PROGRESS", "ESCALATED", TICKET_NUMBER, TICKET_ID);

            ArgumentCaptor<String> contentCaptor = ArgumentCaptor.forClass(String.class);
            verify(inAppNotificationService).saveNotification(
                    any(), any(), any(), any(), contentCaptor.capture(), any(), any(), any());

            assertThat(contentCaptor.getValue()).containsIgnoringCase("escalated");
        }

        @Test
        @DisplayName("tenantId is set on persisted channel notification")
        void tenantIdSetOnChannelNotification() throws Exception {
            Notification saved = Notification.builder().id("notif-006").build();
            when(inAppNotificationService.saveNotification(any(), any(), any(), any(), any(), any(), any(), any()))
                    .thenReturn(saved);
            when(notificationRepository.save(any())).thenReturn(saved);

            notificationService.sendStatusChangedNotification(
                    TENANT_ID, CUSTOMER_ID, RecipientType.CUSTOMER,
                    "OPEN", "RESOLVED", TICKET_NUMBER, TICKET_ID);

            ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
            verify(notificationRepository).save(captor.capture());
            assertThat(captor.getValue().getTenantId()).isEqualTo(TENANT_ID);
        }

        @Test
        @DisplayName("in-app failure does not block status notification flow")
        void inAppFailureDoesNotBlockStatusNotification() throws Exception {
            when(inAppNotificationService.saveNotification(any(), any(), any(), any(), any(), any(), any(), any()))
                    .thenThrow(new RuntimeException("MongoDB down"));
            Notification saved = Notification.builder().id("notif-007").build();
            when(notificationRepository.save(any())).thenReturn(saved);

            assertThatCode(() -> notificationService.sendStatusChangedNotification(
                    TENANT_ID, CUSTOMER_ID, RecipientType.CUSTOMER,
                    "OPEN", "RESOLVED", TICKET_NUMBER, TICKET_ID))
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("REQ-NOTIF-04: Status changed with phone — SMS + WhatsApp dispatched")
    class StatusChangedWithPhone {

        @Test
        @DisplayName("decrypts phone and sends status SMS")
        void decryptsPhoneAndSendsStatusSms() throws Exception {
            String encrypted = "enc==";
            String phone = "9999999999";
            when(piiDecryptionService.decryptBase64(encrypted)).thenReturn(phone);
            when(smsService.sendStatusChangedSms(phone, TICKET_NUMBER, "RESOLVED")).thenReturn(true);

            notificationService.sendStatusChangedWithPhone(TENANT_ID, encrypted, TICKET_NUMBER, "RESOLVED");

            verify(smsService).sendStatusChangedSms(phone, TICKET_NUMBER, "RESOLVED");
            verify(whatsAppService).sendStatusChangedMessage(phone, TICKET_NUMBER, "RESOLVED");
        }

        @Test
        @DisplayName("phone decryption failure aborts dispatch gracefully")
        void phoneDecryptionFailureAbortsDispatch() throws Exception {
            when(piiDecryptionService.decryptBase64(any()))
                    .thenThrow(new RuntimeException("bad key"));

            assertThatCode(() -> notificationService.sendStatusChangedWithPhone(
                    TENANT_ID, "bad", TICKET_NUMBER, "RESOLVED"))
                    .doesNotThrowAnyException();

            verifyNoInteractions(smsService, whatsAppService);
        }
    }

    @Nested
    @DisplayName("REQ-NOTIF-05: Notification content must not contain PII")
    class NoPiiInContent {

        @Test
        @DisplayName("notification content contains only ticket-scoped data, not customer name or phone")
        void contentDoesNotContainPii() throws Exception {
            Notification saved = Notification.builder().id("notif-008").build();
            when(inAppNotificationService.saveNotification(any(), any(), any(), any(), any(), any(), any(), any()))
                    .thenReturn(saved);
            when(notificationRepository.save(any())).thenReturn(saved);

            notificationService.sendTicketCreatedNotification(TENANT_ID, CUSTOMER_ID, TICKET_NUMBER, "cat-001");

            ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
            verify(notificationRepository, atLeastOnce()).save(captor.capture());

            // Content should contain ticket number but not any PII placeholder
            captor.getAllValues().forEach(n -> {
                if (n.getContent() != null) {
                    assertThat(n.getContent()).doesNotContainIgnoringCase("phone");
                    assertThat(n.getContent()).doesNotContainIgnoringCase("email");
                }
            });
        }
    }

    @Nested
    @DisplayName("REQ-NOTIF-06: Multi-channel independence — each channel processed independently")
    class MultiChannelIndependence {

        @Test
        @DisplayName("WhatsApp failure during sendTicketCreatedWithPhone does not propagate exception")
        void whatsAppFailureDoesNotPropagate() throws Exception {
            String encrypted = "enc==";
            String phone = "9000000001";
            when(piiDecryptionService.decryptBase64(encrypted)).thenReturn(phone);
            when(smsService.sendTicketCreatedSms(any(), any(), any())).thenReturn(true);
            doThrow(new RuntimeException("WhatsApp API timeout"))
                    .when(whatsAppService).sendTicketCreatedMessage(any(), any(), any());

            assertThatCode(() -> notificationService.sendTicketCreatedWithPhone(
                    TENANT_ID, CUSTOMER_ID, encrypted, TICKET_NUMBER, "Returns"))
                    .doesNotThrowAnyException();

            verify(smsService).sendTicketCreatedSms(phone, TICKET_NUMBER, "Returns");
        }
    }
}
