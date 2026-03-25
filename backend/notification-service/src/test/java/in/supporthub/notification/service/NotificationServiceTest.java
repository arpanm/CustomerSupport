package in.supporthub.notification.service;

import in.supporthub.notification.domain.Notification;
import in.supporthub.notification.domain.Notification.Channel;
import in.supporthub.notification.domain.Notification.RecipientType;
import in.supporthub.notification.domain.Notification.Status;
import in.supporthub.notification.event.TicketEventConsumer;
import in.supporthub.notification.repository.NotificationRepository;
import in.supporthub.notification.service.inapp.InAppNotificationService;
import in.supporthub.notification.service.sms.Msg91SmsService;
import in.supporthub.notification.service.whatsapp.WhatsAppService;
import in.supporthub.shared.event.TicketCreatedEvent;
import in.supporthub.shared.event.TicketStatusChangedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link NotificationService}.
 *
 * <p>All external services (SMS, WhatsApp, MongoDB repository) are mocked.
 * Tests verify orchestration logic, PII protection, and graceful degradation.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NotificationServiceTest {

    // -------------------------------------------------------------------------
    // Mocks
    // -------------------------------------------------------------------------

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

    // -------------------------------------------------------------------------
    // Test data
    // -------------------------------------------------------------------------

    private static final String TENANT_ID = "tenant-001";
    private static final String CUSTOMER_ID = "cust-abc-123";
    private static final String TICKET_NUMBER = "FC-2024-000042";
    private static final String TICKET_ID = "ticket-uuid-001";
    private static final String CATEGORY_ID = "cat-001";
    private static final String ENCRYPTED_PHONE = "base64encodedEncryptedPhone==";
    private static final String DECRYPTED_PHONE = "9876543210";

    private Notification savedNotification;

    @BeforeEach
    void setUp() {
        savedNotification = Notification.builder()
                .id("notif-id-001")
                .tenantId(TENANT_ID)
                .recipientId(CUSTOMER_ID)
                .recipientType(RecipientType.CUSTOMER)
                .channel(Channel.IN_APP)
                .status(Status.SENT)
                .content("Your ticket " + TICKET_NUMBER + " has been created.")
                .referenceType("TICKET")
                .createdAt(Instant.now())
                .build();

        when(notificationRepository.save(any(Notification.class))).thenReturn(savedNotification);
        when(inAppNotificationService.saveNotification(
                anyString(), anyString(), any(RecipientType.class),
                anyString(), anyString(), any(), anyString(), any()))
                .thenReturn(savedNotification);
    }

    // -------------------------------------------------------------------------
    // Tests for sendTicketCreatedNotification
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("sendTicketCreatedNotification saves in-app notification")
    void sendTicketCreatedNotification_savesInAppNotification() {
        notificationService.sendTicketCreatedNotification(TENANT_ID, CUSTOMER_ID, TICKET_NUMBER, CATEGORY_ID);

        verify(inAppNotificationService, times(1)).saveNotification(
                eq(TENANT_ID), eq(CUSTOMER_ID), eq(RecipientType.CUSTOMER),
                anyString(), anyString(), any(), eq("TICKET"), any());
    }

    @Test
    @DisplayName("sendTicketCreatedNotification persists SMS and WhatsApp channel records to MongoDB")
    void sendTicketCreatedNotification_persistsChannelNotificationRecords() {
        notificationService.sendTicketCreatedNotification(TENANT_ID, CUSTOMER_ID, TICKET_NUMBER, CATEGORY_ID);

        // Two channel records persisted: SMS and WhatsApp
        verify(notificationRepository, times(2)).save(any(Notification.class));
    }

    @Test
    @DisplayName("sendTicketCreatedNotification: in-app save failure does not propagate exception")
    void sendTicketCreatedNotification_inAppSaveFailure_doesNotPropagate() {
        when(inAppNotificationService.saveNotification(
                anyString(), anyString(), any(RecipientType.class),
                anyString(), anyString(), any(), anyString(), any()))
                .thenThrow(new RuntimeException("MongoDB unavailable"));

        // Should not throw — in-app failure is caught and logged
        assertThatCode(() ->
                notificationService.sendTicketCreatedNotification(TENANT_ID, CUSTOMER_ID, TICKET_NUMBER, CATEGORY_ID)
        ).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("sendTicketCreatedNotification: channel records still saved when in-app fails")
    void sendTicketCreatedNotification_inAppFails_channelRecordsStillPersisted() {
        when(inAppNotificationService.saveNotification(
                anyString(), anyString(), any(RecipientType.class),
                anyString(), anyString(), any(), anyString(), any()))
                .thenThrow(new RuntimeException("MongoDB unavailable"));

        notificationService.sendTicketCreatedNotification(TENANT_ID, CUSTOMER_ID, TICKET_NUMBER, CATEGORY_ID);

        // Channel notification records are persisted independently of in-app
        verify(notificationRepository, times(2)).save(any(Notification.class));
    }

    // -------------------------------------------------------------------------
    // Tests for sendTicketCreatedWithPhone (SMS + WhatsApp dispatch)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("sendTicketCreatedWithPhone decrypts phone and sends SMS and WhatsApp")
    void sendTicketCreatedWithPhone_sendsSmsAndWhatsApp() {
        when(piiDecryptionService.decryptBase64(ENCRYPTED_PHONE)).thenReturn(DECRYPTED_PHONE);
        when(smsService.sendTicketCreatedSms(DECRYPTED_PHONE, TICKET_NUMBER, "Order Issue")).thenReturn(true);
        when(whatsAppService.sendTicketCreatedMessage(DECRYPTED_PHONE, TICKET_NUMBER, "Order Issue")).thenReturn(true);

        notificationService.sendTicketCreatedWithPhone(TENANT_ID, CUSTOMER_ID, ENCRYPTED_PHONE, TICKET_NUMBER, "Order Issue");

        verify(smsService, times(1)).sendTicketCreatedSms(DECRYPTED_PHONE, TICKET_NUMBER, "Order Issue");
        verify(whatsAppService, times(1)).sendTicketCreatedMessage(DECRYPTED_PHONE, TICKET_NUMBER, "Order Issue");
    }

    @Test
    @DisplayName("sendTicketCreatedWithPhone: SMS failure does not block WhatsApp")
    void sendTicketCreatedWithPhone_smsFailure_doesNotBlockWhatsApp() {
        when(piiDecryptionService.decryptBase64(ENCRYPTED_PHONE)).thenReturn(DECRYPTED_PHONE);
        when(smsService.sendTicketCreatedSms(DECRYPTED_PHONE, TICKET_NUMBER, "Support"))
                .thenThrow(new RuntimeException("MSG91 timeout"));
        when(whatsAppService.sendTicketCreatedMessage(any(), anyString(), anyString())).thenReturn(true);

        assertThatCode(() ->
                notificationService.sendTicketCreatedWithPhone(TENANT_ID, CUSTOMER_ID, ENCRYPTED_PHONE, TICKET_NUMBER, "Support")
        ).doesNotThrowAnyException();

        // WhatsApp is still attempted despite SMS failure
        verify(whatsAppService, times(1)).sendTicketCreatedMessage(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("sendTicketCreatedWithPhone: phone decryption failure exits early without sending")
    void sendTicketCreatedWithPhone_decryptionFailure_skipsAllChannels() {
        when(piiDecryptionService.decryptBase64(ENCRYPTED_PHONE))
                .thenThrow(new in.supporthub.notification.exception.PiiDecryptionException("decryption failed"));

        notificationService.sendTicketCreatedWithPhone(TENANT_ID, CUSTOMER_ID, ENCRYPTED_PHONE, TICKET_NUMBER, "Support");

        verify(smsService, never()).sendTicketCreatedSms(anyString(), anyString(), anyString());
        verify(whatsAppService, never()).sendTicketCreatedMessage(anyString(), anyString(), anyString());
    }

    // -------------------------------------------------------------------------
    // Tests for sendStatusChangedNotification
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("sendStatusChangedNotification saves in-app notification for customer on RESOLVED")
    void sendStatusChangedNotification_resolved_savesInAppForCustomer() {
        notificationService.sendStatusChangedNotification(
                TENANT_ID, CUSTOMER_ID, RecipientType.CUSTOMER,
                "IN_PROGRESS", "RESOLVED", TICKET_NUMBER, TICKET_ID);

        verify(inAppNotificationService, times(1)).saveNotification(
                eq(TENANT_ID), eq(CUSTOMER_ID), eq(RecipientType.CUSTOMER),
                anyString(), anyString(), eq(TICKET_ID), eq("TICKET"), any());
    }

    @Test
    @DisplayName("sendStatusChangedNotification saves in-app notification for agent on ESCALATED")
    void sendStatusChangedNotification_escalated_savesInAppForAgent() {
        String agentId = "agent-001";
        notificationService.sendStatusChangedNotification(
                TENANT_ID, agentId, RecipientType.AGENT,
                "OPEN", "ESCALATED", TICKET_NUMBER, TICKET_ID);

        verify(inAppNotificationService, times(1)).saveNotification(
                eq(TENANT_ID), eq(agentId), eq(RecipientType.AGENT),
                anyString(), anyString(), eq(TICKET_ID), eq("TICKET"), any());
    }

    @Test
    @DisplayName("sendStatusChangedNotification persists SMS channel record")
    void sendStatusChangedNotification_persistsSmsChannelRecord() {
        notificationService.sendStatusChangedNotification(
                TENANT_ID, CUSTOMER_ID, RecipientType.CUSTOMER,
                "IN_PROGRESS", "RESOLVED", TICKET_NUMBER, TICKET_ID);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, times(1)).save(captor.capture());

        Notification saved = captor.getValue();
        assertThat(saved.getChannel()).isEqualTo(Channel.SMS);
        assertThat(saved.getStatus()).isEqualTo(Status.PENDING);
        assertThat(saved.getTenantId()).isEqualTo(TENANT_ID);
        assertThat(saved.getRecipientId()).isEqualTo(CUSTOMER_ID);
    }

    @Test
    @DisplayName("sendStatusChangedNotification: in-app save failure does not propagate")
    void sendStatusChangedNotification_inAppFailure_doesNotPropagate() {
        when(inAppNotificationService.saveNotification(
                anyString(), anyString(), any(RecipientType.class),
                anyString(), anyString(), anyString(), anyString(), any()))
                .thenThrow(new RuntimeException("MongoDB down"));

        assertThatCode(() ->
                notificationService.sendStatusChangedNotification(
                        TENANT_ID, CUSTOMER_ID, RecipientType.CUSTOMER,
                        "OPEN", "RESOLVED", TICKET_NUMBER, TICKET_ID)
        ).doesNotThrowAnyException();
    }

    // -------------------------------------------------------------------------
    // Tests for Kafka consumer idempotency
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("TicketEventConsumer skips duplicate events (idempotency)")
    void ticketEventConsumer_duplicateEvent_isSkipped() {
        // Setup Redis mock
        StringRedisTemplate redisTemplate = org.mockito.Mockito.mock(StringRedisTemplate.class);
        ValueOperations<String, String> valueOps = org.mockito.Mockito.mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(anyString(), anyString(), any())).thenReturn(Boolean.FALSE);

        TicketEventConsumer consumer = new TicketEventConsumer(notificationService, redisTemplate);

        TicketCreatedEvent event = new TicketCreatedEvent(
                "event-001", TENANT_ID, "corr-001", Instant.now(),
                new TicketCreatedEvent.Payload(
                        TICKET_ID, TICKET_NUMBER, CUSTOMER_ID, CATEGORY_ID,
                        null, "Order Issue", "Description", "web", "MEDIUM"));

        consumer.onTicketCreated(event);

        // NotificationService should NOT be called for duplicate events
        verify(inAppNotificationService, never()).saveNotification(
                anyString(), anyString(), any(), anyString(), anyString(), any(), anyString(), any());
    }

    @Test
    @DisplayName("TicketEventConsumer processes new events (idempotency key not present)")
    void ticketEventConsumer_newEvent_isProcessed() {
        StringRedisTemplate redisTemplate = org.mockito.Mockito.mock(StringRedisTemplate.class);
        ValueOperations<String, String> valueOps = org.mockito.Mockito.mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(anyString(), anyString(), any())).thenReturn(Boolean.TRUE);

        TicketEventConsumer consumer = new TicketEventConsumer(notificationService, redisTemplate);

        TicketCreatedEvent event = new TicketCreatedEvent(
                "event-002", TENANT_ID, "corr-002", Instant.now(),
                new TicketCreatedEvent.Payload(
                        TICKET_ID, TICKET_NUMBER, CUSTOMER_ID, CATEGORY_ID,
                        null, "Order Issue", "Description", "web", "MEDIUM"));

        consumer.onTicketCreated(event);

        // NotificationService SHOULD be called for new events
        verify(inAppNotificationService, times(1)).saveNotification(
                anyString(), anyString(), any(), anyString(), anyString(), any(), anyString(), any());
    }

    @Test
    @DisplayName("TicketEventConsumer: status-changed RESOLVED triggers customer notification")
    void ticketEventConsumer_statusChangedResolved_triggersCustomerNotification() {
        StringRedisTemplate redisTemplate = org.mockito.Mockito.mock(StringRedisTemplate.class);
        ValueOperations<String, String> valueOps = org.mockito.Mockito.mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(anyString(), anyString(), any())).thenReturn(Boolean.TRUE);

        TicketEventConsumer consumer = new TicketEventConsumer(notificationService, redisTemplate);

        TicketStatusChangedEvent event = new TicketStatusChangedEvent(
                "event-003", TENANT_ID, "corr-003", Instant.now(),
                new TicketStatusChangedEvent.Payload(
                        TICKET_ID, TICKET_NUMBER, CUSTOMER_ID, null,
                        "IN_PROGRESS", "RESOLVED", "agent-001", "AGENT", "Issue resolved."));

        consumer.onTicketStatusChanged(event);

        verify(inAppNotificationService, times(1)).saveNotification(
                eq(TENANT_ID), eq(CUSTOMER_ID), eq(RecipientType.CUSTOMER),
                anyString(), anyString(), anyString(), eq("TICKET"), any());
    }

    @Test
    @DisplayName("TicketEventConsumer: status-changed ESCALATED with agent triggers agent notification")
    void ticketEventConsumer_statusChangedEscalated_triggersAgentNotification() {
        String agentId = "agent-escalation-001";
        StringRedisTemplate redisTemplate = org.mockito.Mockito.mock(StringRedisTemplate.class);
        ValueOperations<String, String> valueOps = org.mockito.Mockito.mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(anyString(), anyString(), any())).thenReturn(Boolean.TRUE);

        TicketEventConsumer consumer = new TicketEventConsumer(notificationService, redisTemplate);

        TicketStatusChangedEvent event = new TicketStatusChangedEvent(
                "event-004", TENANT_ID, "corr-004", Instant.now(),
                new TicketStatusChangedEvent.Payload(
                        TICKET_ID, TICKET_NUMBER, CUSTOMER_ID, agentId,
                        "OPEN", "ESCALATED", "admin-001", "AGENT", null));

        consumer.onTicketStatusChanged(event);

        verify(inAppNotificationService, times(1)).saveNotification(
                eq(TENANT_ID), eq(agentId), eq(RecipientType.AGENT),
                anyString(), anyString(), anyString(), eq("TICKET"), any());
    }

    @Test
    @DisplayName("TicketEventConsumer: status-changed ESCALATED without assignedAgent — no notification sent")
    void ticketEventConsumer_statusChangedEscalatedNoAgent_noNotification() {
        StringRedisTemplate redisTemplate = org.mockito.Mockito.mock(StringRedisTemplate.class);
        ValueOperations<String, String> valueOps = org.mockito.Mockito.mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(anyString(), anyString(), any())).thenReturn(Boolean.TRUE);

        TicketEventConsumer consumer = new TicketEventConsumer(notificationService, redisTemplate);

        TicketStatusChangedEvent event = new TicketStatusChangedEvent(
                "event-005", TENANT_ID, "corr-005", Instant.now(),
                new TicketStatusChangedEvent.Payload(
                        TICKET_ID, TICKET_NUMBER, CUSTOMER_ID, null,  // No assigned agent
                        "OPEN", "ESCALATED", "admin-001", "AGENT", null));

        consumer.onTicketStatusChanged(event);

        verify(inAppNotificationService, never()).saveNotification(
                anyString(), anyString(), any(), anyString(), anyString(), anyString(), anyString(), any());
    }
}
