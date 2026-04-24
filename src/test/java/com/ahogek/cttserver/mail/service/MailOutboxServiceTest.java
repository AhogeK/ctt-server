package com.ahogek.cttserver.mail.service;

import com.ahogek.cttserver.audit.enums.AuditAction;
import com.ahogek.cttserver.audit.enums.ResourceType;
import com.ahogek.cttserver.audit.enums.SecuritySeverity;
import com.ahogek.cttserver.audit.model.AuditDetails;
import com.ahogek.cttserver.audit.service.AuditLogService;
import com.ahogek.cttserver.common.config.properties.CttMailProperties;
import com.ahogek.cttserver.common.exception.ErrorCode;
import com.ahogek.cttserver.common.exception.TooManyRequestsException;
import com.ahogek.cttserver.mail.entity.MailOutbox;
import com.ahogek.cttserver.mail.enums.MailOutboxStatus;
import com.ahogek.cttserver.mail.repository.MailOutboxRepository;
import com.ahogek.cttserver.mail.template.MailTemplateRenderer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.slf4j.MDC;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MailOutboxServiceTest {

    private static final String FRONTEND_BASE_URL = "http://localhost:5173";
    private static final String VERIFY_EMAIL_PATH = "/auth/verify-email";
    private static final String RESET_PASSWORD_PATH = "/auth/reset-password";
    private static final int MAX_RETRIES = 5;

    @Mock private MailOutboxRepository repository;
    @Mock private MailTemplateRenderer renderer;
    @Mock private AuditLogService auditLog;

    @Captor private ArgumentCaptor<MailOutbox> outboxCaptor;
    @Captor private ArgumentCaptor<AuditDetails> auditDetailsCaptor;

    private MailOutboxService service;

    @BeforeEach
    void setUp() {
        CttMailProperties properties =
                new CttMailProperties(
                        new CttMailProperties.From("test@localhost", "CTT Test"),
                        new CttMailProperties.Outbox(5000, 50, 300, 120000),
                        new CttMailProperties.Retry(10, 2.0, 3600, MAX_RETRIES, 0.1),
                        new CttMailProperties.Frontend(
                                FRONTEND_BASE_URL, VERIFY_EMAIL_PATH, RESET_PASSWORD_PATH));

        service = new MailOutboxService(repository, renderer, properties, auditLog);

        // Mock repository.save() to set ID on entity to support MAIL_ENQUEUED audit event
        when(repository.save(any(MailOutbox.class)))
                .thenAnswer(
                        invocation -> {
                            MailOutbox outbox = invocation.getArgument(0);
                            if (outbox.getId() == null) {
                                outbox.setId(UUID.randomUUID());
                            }
                            return outbox;
                        });
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Nested
    @DisplayName("enqueueVerificationEmail")
    class EnqueueVerificationEmailTests {

        @Test
        @DisplayName("should enqueue email when rate limit not exceeded")
        void shouldEnqueueEmail_whenRateLimitNotExceeded() {
            // Given
            UUID userId = UUID.randomUUID();
            String username = "testuser";
            String email = "test@example.com";
            String token = "verification-token-123";

            when(repository.countDuplicates(anyString(), anyString(), anyList(), any()))
                    .thenReturn(0L);
            when(renderer.renderHtml(any())).thenReturn("<html>verification</html>");
            when(renderer.renderText(any())).thenReturn("verification text");

            // When
            service.enqueueVerificationEmail(userId, username, email, token);

            // Then
            verify(repository).save(outboxCaptor.capture());
            MailOutbox saved = outboxCaptor.getValue();

            assertThat(saved.getBizId()).isEqualTo(userId);
            assertThat(saved.getRecipient()).isEqualTo(email);
            assertThat(saved.getBizType()).isEqualTo("REGISTER_VERIFY");
            assertThat(saved.getSubject()).isEqualTo("Verify Your Email Address");
            assertThat(saved.getTemplateCode()).isEqualTo("email-verification");
            assertThat(saved.getBodyHtml()).isEqualTo("<html>verification</html>");
            assertThat(saved.getBodyText()).isEqualTo("verification text");
            assertThat(saved.getMaxRetries()).isEqualTo(MAX_RETRIES);
            assertThat(saved.getStatus()).isEqualTo(MailOutboxStatus.PENDING);
            assertThat(saved.getRetryCount()).isZero();
            assertThat(saved.getNextRetryAt()).isNotNull();
        }

        @Test
        @DisplayName("should build correct verification link")
        void shouldBuildCorrectVerificationLink() {
            // Given
            when(repository.countDuplicates(anyString(), anyString(), anyList(), any()))
                    .thenReturn(0L);
            when(renderer.renderHtml(any())).thenReturn("<html></html>");
            when(renderer.renderText(any())).thenReturn("text");

            // When
            service.enqueueVerificationEmail(UUID.randomUUID(), "user", "email@test.com", "abc123");

            // Then
            verify(renderer).renderHtml(any());
            verify(repository).save(outboxCaptor.capture());

            Map<String, Object> payload = outboxCaptor.getValue().getPayload();
            String expectedLink = FRONTEND_BASE_URL + VERIFY_EMAIL_PATH + "?token=abc123";
            assertThat(payload).containsEntry("verificationLink", expectedLink);
        }

        @Test
        @DisplayName("should throw TooManyRequestsException when rate limit exceeded")
        void shouldThrowException_whenRateLimitExceeded() {
            // Given
            when(repository.countDuplicates(anyString(), anyString(), anyList(), any()))
                    .thenReturn(3L);

            // When & Then
            var thrown =
                    assertThatThrownBy(
                            () ->
                                    service.enqueueVerificationEmail(
                                            UUID.randomUUID(),
                                            "user",
                                            "test@example.com",
                                            "token"));
            thrown.isInstanceOf(TooManyRequestsException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.MAIL_004);

            verify(repository, never()).save(any());
        }

        @Test
        @DisplayName("should check rate limit with correct parameters")
        void shouldCheckRateLimit_withCorrectParameters() {
            // Given
            String email = "test@example.com";
            when(repository.countDuplicates(anyString(), anyString(), anyList(), any()))
                    .thenReturn(0L);
            when(renderer.renderHtml(any())).thenReturn("<html></html>");
            when(renderer.renderText(any())).thenReturn("text");

            // When
            service.enqueueVerificationEmail(UUID.randomUUID(), "user", email, "token");

            // Then
            verify(repository)
                    .countDuplicates(
                            eq(email),
                            eq("REGISTER_VERIFY"),
                            eq(
                                    List.of(
                                            MailOutboxStatus.PENDING,
                                            MailOutboxStatus.SENDING,
                                            MailOutboxStatus.SENT)),
                            any());
        }
    }

    @Nested
    @DisplayName("enqueuePasswordResetEmail")
    class EnqueuePasswordResetEmailTests {

        @Test
        @DisplayName("should enqueue email when rate limit not exceeded")
        void shouldEnqueueEmail_whenRateLimitNotExceeded() {
            // Given
            UUID userId = UUID.randomUUID();
            String username = "testuser";
            String email = "test@example.com";
            String token = "reset-token-456";

            when(repository.countDuplicates(anyString(), anyString(), anyList(), any()))
                    .thenReturn(0L);
            when(renderer.renderHtml(any())).thenReturn("<html>reset</html>");
            when(renderer.renderText(any())).thenReturn("reset text");

            // When
            service.enqueuePasswordResetEmail(userId, username, email, token);

            // Then
            verify(repository).save(outboxCaptor.capture());
            MailOutbox saved = outboxCaptor.getValue();

            assertThat(saved.getBizId()).isEqualTo(userId);
            assertThat(saved.getRecipient()).isEqualTo(email);
            assertThat(saved.getBizType()).isEqualTo("RESET_PASSWORD");
            assertThat(saved.getSubject()).isEqualTo("Reset Your Password");
            assertThat(saved.getTemplateCode()).isEqualTo("password-reset");
            assertThat(saved.getBodyHtml()).isEqualTo("<html>reset</html>");
            assertThat(saved.getBodyText()).isEqualTo("reset text");
            assertThat(saved.getMaxRetries()).isEqualTo(MAX_RETRIES);
            assertThat(saved.getStatus()).isEqualTo(MailOutboxStatus.PENDING);
            assertThat(saved.getRetryCount()).isZero();
            assertThat(saved.getNextRetryAt()).isNotNull();
        }

        @Test
        @DisplayName("should build correct password reset link")
        void shouldBuildCorrectPasswordResetLink() {
            // Given
            when(repository.countDuplicates(anyString(), anyString(), anyList(), any()))
                    .thenReturn(0L);
            when(renderer.renderHtml(any())).thenReturn("<html></html>");
            when(renderer.renderText(any())).thenReturn("text");

            // When
            service.enqueuePasswordResetEmail(
                    UUID.randomUUID(), "user", "email@test.com", "xyz789");

            // Then
            verify(repository).save(outboxCaptor.capture());

            Map<String, Object> payload = outboxCaptor.getValue().getPayload();
            String expectedLink = FRONTEND_BASE_URL + RESET_PASSWORD_PATH + "?token=xyz789";
            assertThat(payload).containsEntry("resetLink", expectedLink);
        }

        @Test
        @DisplayName("should skip enqueue when duplicate exists in idempotent window")
        void shouldSkipEnqueue_whenDuplicateExistsInWindow() {
            // Given
            UUID userId = UUID.randomUUID();
            String email = "test@example.com";
            when(repository.existsByRecipientAndBizTypeAndBizIdAndStatusInAndCreatedAtAfter(
                            anyString(), anyString(), any(UUID.class), anyList(), any()))
                    .thenReturn(true);

            // When
            service.enqueuePasswordResetEmail(userId, "user", email, "token");

            // Then
            verify(repository, never()).save(any());
            verify(repository, never()).countDuplicates(anyString(), anyString(), anyList(), any());
            verify(auditLog)
                    .log(
                            eq(userId),
                            eq(AuditAction.MAIL_IDEMPOTENT_SKIP),
                            eq(ResourceType.MAIL_OUTBOX),
                            eq(email),
                            eq(SecuritySeverity.INFO),
                            any(AuditDetails.class));
        }
    }

    @Nested
    @DisplayName("idempotent protection")
    class IdempotentProtectionTests {

        @Test
        @DisplayName("should skip verification email when duplicate exists in window")
        void shouldSkipVerificationEmail_whenDuplicateExistsInWindow() {
            // Given
            UUID userId = UUID.randomUUID();
            String email = "test@example.com";
            when(repository.existsByRecipientAndBizTypeAndBizIdAndStatusInAndCreatedAtAfter(
                            anyString(), anyString(), any(UUID.class), anyList(), any()))
                    .thenReturn(true);

            // When
            service.enqueueVerificationEmail(userId, "user", email, "token");

            // Then
            verify(repository, never()).save(any());
            verify(repository, never()).countDuplicates(anyString(), anyString(), anyList(), any());
            verify(auditLog)
                    .log(
                            eq(userId),
                            eq(AuditAction.MAIL_IDEMPOTENT_SKIP),
                            eq(ResourceType.MAIL_OUTBOX),
                            eq(email),
                            eq(SecuritySeverity.INFO),
                            any(AuditDetails.class));
        }

        @Test
        @DisplayName("should skip password reset email when duplicate exists in window")
        void shouldSkipPasswordResetEmail_whenDuplicateExistsInWindow() {
            // Given
            UUID userId = UUID.randomUUID();
            String email = "test@example.com";
            when(repository.existsByRecipientAndBizTypeAndBizIdAndStatusInAndCreatedAtAfter(
                            anyString(), anyString(), any(UUID.class), anyList(), any()))
                    .thenReturn(true);

            // When
            service.enqueuePasswordResetEmail(userId, "user", email, "token");

            // Then
            verify(repository, never()).save(any());
            verify(auditLog)
                    .log(
                            eq(userId),
                            eq(AuditAction.MAIL_IDEMPOTENT_SKIP),
                            eq(ResourceType.MAIL_OUTBOX),
                            eq(email),
                            eq(SecuritySeverity.INFO),
                            any(AuditDetails.class));
        }

        @Test
        @DisplayName("should check idempotency before rate limit")
        void shouldCheckIdempotency_beforeRateLimit() {
            // Given
            UUID userId = UUID.randomUUID();
            String email = "test@example.com";
            when(repository.existsByRecipientAndBizTypeAndBizIdAndStatusInAndCreatedAtAfter(
                            anyString(), anyString(), any(UUID.class), anyList(), any()))
                    .thenReturn(true);

            // When
            service.enqueueVerificationEmail(userId, "user", email, "token");

            // Then - idempotent skip should win, rate limit never checked
            verify(repository, never()).countDuplicates(anyString(), anyString(), anyList(), any());
            verify(repository, never()).save(any());
        }

        @Test
        @DisplayName("should check idempotency with correct parameters")
        void shouldCheckIdempotency_withCorrectParameters() {
            // Given
            UUID userId = UUID.randomUUID();
            String email = "test@example.com";
            when(repository.existsByRecipientAndBizTypeAndBizIdAndStatusInAndCreatedAtAfter(
                            anyString(), anyString(), any(UUID.class), anyList(), any()))
                    .thenReturn(true);

            // When
            service.enqueueVerificationEmail(userId, "user", email, "token");

            // Then
            verify(repository)
                    .existsByRecipientAndBizTypeAndBizIdAndStatusInAndCreatedAtAfter(
                            eq(email),
                            eq("REGISTER_VERIFY"),
                            eq(userId),
                            eq(
                                    List.of(
                                            MailOutboxStatus.PENDING,
                                            MailOutboxStatus.SENDING,
                                            MailOutboxStatus.SENT)),
                            any());
        }

        @Test
        @DisplayName("should include bizType and recipient in audit details")
        void shouldIncludeDetails_inAuditEvent() {
            // Given
            UUID userId = UUID.randomUUID();
            String email = "test@example.com";
            when(repository.existsByRecipientAndBizTypeAndBizIdAndStatusInAndCreatedAtAfter(
                            anyString(), anyString(), any(UUID.class), anyList(), any()))
                    .thenReturn(true);

            // When
            service.enqueuePasswordResetEmail(userId, "user", email, "token");

            // Then
            verify(auditLog)
                    .log(
                            any(UUID.class),
                            any(AuditAction.class),
                            any(ResourceType.class),
                            anyString(),
                            any(SecuritySeverity.class),
                            auditDetailsCaptor.capture());

            AuditDetails details = auditDetailsCaptor.getValue();
            assertThat(details.ext()).containsEntry("bizType", "RESET_PASSWORD");
            assertThat(details.ext()).containsEntry("recipientMasked", "te***@example.com");
            assertThat(details.ext()).containsEntry("windowMinutes", 10L);
        }
    }

    @Nested
    @DisplayName("error handling")
    class ErrorHandlingTests {

        @Test
        @DisplayName("should propagate exception and not save when template rendering fails")
        void shouldPropagateException_whenTemplateRenderingFails() {
            // Given
            UUID userId = UUID.randomUUID();
            String email = "test@example.com";
            when(repository.countDuplicates(anyString(), anyString(), anyList(), any()))
                    .thenReturn(0L);
            when(renderer.renderHtml(any()))
                    .thenThrow(new RuntimeException("Template processing failed"));

            // When & Then
            assertThatThrownBy(
                            () -> service.enqueueVerificationEmail(userId, "user", email, "token"))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("Template processing failed");

            verify(repository, never()).save(any());
        }

        @Test
        @DisplayName("should propagate exception when template text rendering fails")
        void shouldPropagateException_whenTextRenderingFails() {
            // Given
            UUID userId = UUID.randomUUID();
            String email = "test@example.com";
            when(repository.countDuplicates(anyString(), anyString(), anyList(), any()))
                    .thenReturn(0L);
            when(renderer.renderHtml(any())).thenReturn("<html></html>");
            when(renderer.renderText(any()))
                    .thenThrow(new RuntimeException("Text rendering failed"));

            // When & Then
            assertThatThrownBy(
                            () -> service.enqueuePasswordResetEmail(userId, "user", email, "token"))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("Text rendering failed");

            verify(repository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("MAIL_ENQUEUED audit event")
    class MailEnqueuedAuditTests {

        @Test
        @DisplayName("should publish MAIL_ENQUEUED when verification email is enqueued")
        void shouldPublishMailEnqueued_whenVerificationEmailEnqueued() {
            // Given
            UUID userId = UUID.randomUUID();
            String email = "test@example.com";
            when(repository.countDuplicates(anyString(), anyString(), anyList(), any()))
                    .thenReturn(0L);
            when(renderer.renderHtml(any())).thenReturn("<html></html>");
            when(renderer.renderText(any())).thenReturn("text");

            // When
            service.enqueueVerificationEmail(userId, "user", email, "token");

            // Then
            verify(auditLog)
                    .log(
                            eq(userId),
                            eq(AuditAction.MAIL_ENQUEUED),
                            eq(ResourceType.MAIL_OUTBOX),
                            anyString(),
                            eq(SecuritySeverity.INFO),
                            any(AuditDetails.class));
        }

        @Test
        @DisplayName("should publish MAIL_ENQUEUED when password reset email is enqueued")
        void shouldPublishMailEnqueued_whenPasswordResetEmailEnqueued() {
            // Given
            UUID userId = UUID.randomUUID();
            String email = "test@example.com";
            when(repository.countDuplicates(anyString(), anyString(), anyList(), any()))
                    .thenReturn(0L);
            when(renderer.renderHtml(any())).thenReturn("<html></html>");
            when(renderer.renderText(any())).thenReturn("text");

            // When
            service.enqueuePasswordResetEmail(userId, "user", email, "token");

            // Then
            verify(auditLog)
                    .log(
                            eq(userId),
                            eq(AuditAction.MAIL_ENQUEUED),
                            eq(ResourceType.MAIL_OUTBOX),
                            anyString(),
                            eq(SecuritySeverity.INFO),
                            any(AuditDetails.class));
        }

        @Test
        @DisplayName("should include standardized fields in MAIL_ENQUEUED audit details")
        void shouldIncludeDetails_inMailEnqueuedAudit() {
            // Given
            UUID userId = UUID.randomUUID();
            String email = "test@example.com";
            when(repository.countDuplicates(anyString(), anyString(), anyList(), any()))
                    .thenReturn(0L);
            when(renderer.renderHtml(any())).thenReturn("<html></html>");
            when(renderer.renderText(any())).thenReturn("text");

            // When
            service.enqueueVerificationEmail(userId, "user", email, "token");

            // Then
            verify(repository).save(outboxCaptor.capture());
            MailOutbox saved = outboxCaptor.getValue();

            verify(auditLog)
                    .log(
                            any(UUID.class),
                            eq(AuditAction.MAIL_ENQUEUED),
                            eq(ResourceType.MAIL_OUTBOX),
                            anyString(),
                            eq(SecuritySeverity.INFO),
                            auditDetailsCaptor.capture());

            AuditDetails details = auditDetailsCaptor.getValue();
            assertThat(details.ext()).containsEntry("mailOutboxId", saved.getId().toString());
            assertThat(details.ext()).containsEntry("templateName", "email-verification");
            assertThat(details.ext()).containsEntry("recipientMasked", "te***@example.com");
            assertThat(details.ext()).containsEntry("retryCount", 0);
        }

        @Test
        @DisplayName("should not publish MAIL_ENQUEUED when idempotent skip occurs")
        void shouldNotPublishMailEnqueued_whenIdempotentSkip() {
            // Given
            UUID userId = UUID.randomUUID();
            String email = "test@example.com";
            when(repository.existsByRecipientAndBizTypeAndBizIdAndStatusInAndCreatedAtAfter(
                            anyString(), anyString(), any(UUID.class), anyList(), any()))
                    .thenReturn(true);

            // When
            service.enqueueVerificationEmail(userId, "user", email, "token");

            // Then - should publish MAIL_IDEMPOTENT_SKIP, not MAIL_ENQUEUED
            verify(auditLog)
                    .log(
                            eq(userId),
                            eq(AuditAction.MAIL_IDEMPOTENT_SKIP),
                            eq(ResourceType.MAIL_OUTBOX),
                            eq(email),
                            eq(SecuritySeverity.INFO),
                            any(AuditDetails.class));
            verify(auditLog, never())
                    .log(
                            any(UUID.class),
                            eq(AuditAction.MAIL_ENQUEUED),
                            any(ResourceType.class),
                            anyString(),
                            any(SecuritySeverity.class),
                            any(AuditDetails.class));
        }
    }

    @Nested
    @DisplayName("configurable paths")
    class ConfigurablePathTests {

        private static final String CUSTOM_BASE_URL = "https://custom.example.com";
        private static final String CUSTOM_VERIFY_PATH = "/custom/verify";
        private static final String CUSTOM_RESET_PATH = "/custom/reset";

        private MailOutboxService createCustomService() {
            CttMailProperties customProperties =
                    new CttMailProperties(
                            new CttMailProperties.From("test@localhost", "CTT Test"),
                            new CttMailProperties.Outbox(5000, 50, 300, 120000),
                            new CttMailProperties.Retry(10, 2.0, 3600, MAX_RETRIES, 0.1),
                            new CttMailProperties.Frontend(
                                    CUSTOM_BASE_URL, CUSTOM_VERIFY_PATH, CUSTOM_RESET_PATH));
            return new MailOutboxService(repository, renderer, customProperties, auditLog);
        }

        private void setupCommonMocks() {
            when(repository.countDuplicates(anyString(), anyString(), anyList(), any()))
                    .thenReturn(0L);
            when(renderer.renderHtml(any())).thenReturn("<html></html>");
            when(renderer.renderText(any())).thenReturn("text");
        }

        @Test
        @DisplayName("should use custom frontend paths for verification email link")
        void shouldUseCustomPaths_forVerificationEmailLink() {
            // Given
            MailOutboxService customService = createCustomService();
            setupCommonMocks();
            String token = "custom-token-123";

            // When
            customService.enqueueVerificationEmail(
                    UUID.randomUUID(), "user", "email@test.com", token);

            // Then
            verify(repository).save(outboxCaptor.capture());
            Map<String, Object> payload = outboxCaptor.getValue().getPayload();
            String expectedLink = CUSTOM_BASE_URL + CUSTOM_VERIFY_PATH + "?token=" + token;
            assertThat(payload).containsEntry("verificationLink", expectedLink);
        }

        @Test
        @DisplayName("should use custom frontend paths for password reset email link")
        void shouldUseCustomPaths_forPasswordResetEmailLink() {
            // Given
            MailOutboxService customService = createCustomService();
            setupCommonMocks();
            String token = "custom-reset-token-456";

            // When
            customService.enqueuePasswordResetEmail(
                    UUID.randomUUID(), "user", "email@test.com", token);

            // Then
            verify(repository).save(outboxCaptor.capture());
            Map<String, Object> payload = outboxCaptor.getValue().getPayload();
            String expectedLink = CUSTOM_BASE_URL + CUSTOM_RESET_PATH + "?token=" + token;
            assertThat(payload).containsEntry("resetLink", expectedLink);
        }
    }
}
