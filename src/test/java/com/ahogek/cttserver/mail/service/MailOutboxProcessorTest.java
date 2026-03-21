package com.ahogek.cttserver.mail.service;

import com.ahogek.cttserver.audit.enums.AuditAction;
import com.ahogek.cttserver.audit.enums.ResourceType;
import com.ahogek.cttserver.audit.enums.SecuritySeverity;
import com.ahogek.cttserver.audit.model.AuditDetails;
import com.ahogek.cttserver.audit.service.AuditLogService;
import com.ahogek.cttserver.mail.dispatch.ExponentialBackoffRetryStrategy;
import com.ahogek.cttserver.mail.dispatch.MailDispatcher;
import com.ahogek.cttserver.mail.entity.MailOutbox;
import com.ahogek.cttserver.mail.enums.MailOutboxStatus;
import com.ahogek.cttserver.mail.repository.MailOutboxRepository;

import jakarta.mail.MessagingException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.OptimisticLockingFailureException;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MailOutboxProcessorTest {

    private static final int BASE_DELAY_SECONDS = 10;
    private static final double MULTIPLIER = 2.0;
    private static final int MAX_DELAY_SECONDS = 3600;
    private static final int MAX_ATTEMPTS = 5;

    @Mock private MailOutboxRepository outboxRepository;
    @Mock private MailDispatcher mailDispatcher;
    @Mock private AuditLogService auditLogService;
    @Mock private ExponentialBackoffRetryStrategy retryStrategy;

    @Captor private ArgumentCaptor<MailOutbox> outboxCaptor;
    @Captor private ArgumentCaptor<AuditDetails> auditDetailsCaptor;

    private MailOutboxProcessor processor;

    @BeforeEach
    void setUp() {
        processor =
                new MailOutboxProcessor(
                        outboxRepository, mailDispatcher, auditLogService, retryStrategy);
    }

    @Nested
    @DisplayName("processSingleMessage - Success Path")
    class SuccessPathTests {

        @Test
        @DisplayName("should mark sending, dispatch, mark sent and publish audit on success")
        void shouldProcessSuccessfully() throws Exception {
            // Given
            MailOutbox outbox = createPendingOutbox();

            // When
            processor.processSingleMessage(outbox);

            var inOrder = inOrder(outboxRepository, mailDispatcher);
            inOrder.verify(outboxRepository).saveAndFlush(outbox);
            inOrder.verify(mailDispatcher).dispatch(outbox);
            inOrder.verify(outboxRepository).save(outbox);

            assertThat(outbox.getStatus()).isEqualTo(MailOutboxStatus.SENT);
            assertThat(outbox.getSentAt()).isNotNull();

            verify(auditLogService)
                    .log(
                            eq(outbox.getBizId()),
                            eq(AuditAction.MAIL_SENT),
                            eq(ResourceType.MAIL_OUTBOX),
                            eq(outbox.getId().toString()),
                            eq(SecuritySeverity.INFO),
                            auditDetailsCaptor.capture());

            AuditDetails details = auditDetailsCaptor.getValue();
            assertThat(details.ext()).containsEntry("recipient", outbox.getRecipient());
            assertThat(details.ext()).containsEntry("subject", outbox.getSubject());
        }
    }

    @Nested
    @DisplayName("processSingleMessage - Retry Path")
    class RetryPathTests {

        @Test
        @DisplayName("should schedule retry with strategy on failure")
        void shouldScheduleRetry_onFailure() throws Exception {
            // Given
            MailOutbox outbox = createPendingOutbox();
            outbox.setRetryCount(0);
            doThrow(new MessagingException("SMTP error")).when(mailDispatcher).dispatch(outbox);

            Instant expectedRetryTime = Instant.now().plusSeconds(60);
            when(retryStrategy.calculateNextRetryTime(0)).thenReturn(expectedRetryTime);

            // When
            processor.processSingleMessage(outbox);

            // Then
            verify(retryStrategy).calculateNextRetryTime(0);
            verify(outboxRepository).save(outboxCaptor.capture());
            MailOutbox saved = outboxCaptor.getValue();

            assertThat(saved.getStatus()).isEqualTo(MailOutboxStatus.FAILED);
            assertThat(saved.getRetryCount()).isEqualTo(1);
            assertThat(saved.getLastError()).isEqualTo("SMTP error");
            assertThat(saved.getNextRetryAt()).isEqualTo(expectedRetryTime);

            verify(auditLogService)
                    .log(
                            eq(outbox.getBizId()),
                            eq(AuditAction.MAIL_DELIVERY_FAILED),
                            eq(ResourceType.MAIL_OUTBOX),
                            eq(outbox.getId().toString()),
                            eq(SecuritySeverity.WARNING),
                            any(AuditDetails.class));
        }

        @Test
        @DisplayName("should call strategy with correct retry count")
        void shouldCallStrategyWithCorrectRetryCount() throws Exception {
            // Given
            MailOutbox outbox = createPendingOutbox();
            outbox.setRetryCount(2);
            doThrow(new MessagingException("SMTP error")).when(mailDispatcher).dispatch(outbox);

            Instant expectedRetryTime = Instant.now().plusSeconds(120);
            when(retryStrategy.calculateNextRetryTime(2)).thenReturn(expectedRetryTime);

            // When
            processor.processSingleMessage(outbox);

            // Then
            verify(retryStrategy).calculateNextRetryTime(2);
            assertThat(outbox.getNextRetryAt()).isEqualTo(expectedRetryTime);
        }
    }

    @Nested
    @DisplayName("processSingleMessage - Exhausted Path")
    class ExhaustedPathTests {

        @Test
        @DisplayName("should mark cancelled and publish exhausted audit when max retries reached")
        void shouldMarkCancelled_whenMaxRetriesReached() throws Exception {
            // Given
            MailOutbox outbox = createPendingOutbox();
            outbox.setRetryCount(MAX_ATTEMPTS - 1);
            outbox.setMaxRetries(MAX_ATTEMPTS);
            doThrow(new MessagingException("SMTP error")).when(mailDispatcher).dispatch(outbox);
            when(retryStrategy.calculateNextRetryTime(MAX_ATTEMPTS - 1))
                    .thenReturn(Instant.now().plusSeconds(60));

            // When
            processor.processSingleMessage(outbox);

            // Then
            verify(outboxRepository).save(outboxCaptor.capture());
            MailOutbox saved = outboxCaptor.getValue();

            assertThat(saved.getStatus()).isEqualTo(MailOutboxStatus.CANCELLED);
            assertThat(saved.getRetryCount()).isEqualTo(MAX_ATTEMPTS);

            verify(auditLogService)
                    .log(
                            eq(outbox.getBizId()),
                            eq(AuditAction.MAIL_DELIVERY_EXHAUSTED),
                            eq(ResourceType.MAIL_OUTBOX),
                            eq(outbox.getId().toString()),
                            eq(SecuritySeverity.CRITICAL),
                            auditDetailsCaptor.capture());

            AuditDetails details = auditDetailsCaptor.getValue();
            assertThat(details.ext()).containsEntry("totalAttempts", MAX_ATTEMPTS);
            assertThat(details.ext()).containsEntry("finalError", "SMTP error");
        }
    }

    @Nested
    @DisplayName("processSingleMessage - Optimistic Locking")
    class OptimisticLockingTests {

        @Test
        @DisplayName("should silently skip when optimistic lock conflict occurs")
        void shouldSilentlySkip_onOptimisticLockConflict() {
            // Given
            MailOutbox outbox = createPendingOutbox();
            when(outboxRepository.saveAndFlush(any()))
                    .thenThrow(new OptimisticLockingFailureException("Conflict"));

            // When
            processor.processSingleMessage(outbox);

            // Then
            verifyNoInteractions(mailDispatcher);
            verify(auditLogService, never())
                    .log(any(), any(), any(), any(), any(), any(AuditDetails.class));
        }
    }

    @Nested
    @DisplayName("Audit Event Details")
    class AuditEventDetailsTests {

        @Test
        @DisplayName("should include traceId in MAIL_SENT audit")
        void shouldIncludeTraceId_inMailSentAudit() {
            // Given
            MailOutbox outbox = createPendingOutbox();
            outbox.setTraceId("trace-abc-123");

            // When
            processor.processSingleMessage(outbox);

            // Then
            verify(auditLogService)
                    .log(
                            any(),
                            eq(AuditAction.MAIL_SENT),
                            any(),
                            any(),
                            any(),
                            auditDetailsCaptor.capture());

            AuditDetails details = auditDetailsCaptor.getValue();
            assertThat(details.ext()).containsEntry("traceId", "trace-abc-123");
        }

        @Test
        @DisplayName("should include retry information in MAIL_DELIVERY_FAILED audit")
        void shouldIncludeRetryInfo_inFailedAudit() throws Exception {
            // Given
            MailOutbox outbox = createPendingOutbox();
            outbox.setRetryCount(1);
            outbox.setMaxRetries(5);
            doThrow(new MessagingException("Connection timeout"))
                    .when(mailDispatcher)
                    .dispatch(outbox);
            when(retryStrategy.calculateNextRetryTime(1))
                    .thenReturn(Instant.now().plusSeconds(120));

            // When
            processor.processSingleMessage(outbox);

            // Then
            verify(auditLogService)
                    .log(
                            any(),
                            eq(AuditAction.MAIL_DELIVERY_FAILED),
                            any(),
                            any(),
                            any(),
                            auditDetailsCaptor.capture());

            AuditDetails details = auditDetailsCaptor.getValue();
            assertThat(details.ext()).containsEntry("retryCount", 2);
            assertThat(details.ext()).containsEntry("maxRetries", 5);
            assertThat(details.ext()).containsEntry("error", "Connection timeout");
            assertThat(details.ext()).containsKey("nextRetryAt");
        }

        @Test
        @DisplayName("should use N/A for null traceId")
        void shouldUseNaForNullTraceId() {
            // Given
            MailOutbox outbox = createPendingOutbox();
            outbox.setTraceId(null);

            // When
            processor.processSingleMessage(outbox);

            // Then
            verify(auditLogService)
                    .log(
                            any(),
                            eq(AuditAction.MAIL_SENT),
                            any(),
                            any(),
                            any(),
                            auditDetailsCaptor.capture());

            AuditDetails details = auditDetailsCaptor.getValue();
            assertThat(details.ext()).containsEntry("traceId", "N/A");
        }
    }

    private MailOutbox createPendingOutbox() {
        MailOutbox outbox = new MailOutbox();
        outbox.setId(UUID.randomUUID());
        outbox.setBizId(UUID.randomUUID());
        outbox.setRecipient("test@example.com");
        outbox.setSubject("Test Subject");
        outbox.setBodyHtml("<html><body>Test</body></html>");
        outbox.setBodyText("Test plain text");
        outbox.setBizType("REGISTER_VERIFY");
        outbox.setStatus(MailOutboxStatus.PENDING);
        outbox.setRetryCount(0);
        outbox.setMaxRetries(MAX_ATTEMPTS);
        outbox.setTraceId("trace-123");
        return outbox;
    }
}
