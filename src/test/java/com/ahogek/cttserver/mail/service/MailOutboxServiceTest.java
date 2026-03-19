package com.ahogek.cttserver.mail.service;

import com.ahogek.cttserver.common.config.properties.CttMailProperties;
import com.ahogek.cttserver.common.exception.ErrorCode;
import com.ahogek.cttserver.common.exception.TooManyRequestsException;
import com.ahogek.cttserver.mail.entity.MailOutbox;
import com.ahogek.cttserver.mail.enums.MailOutboxStatus;
import com.ahogek.cttserver.mail.repository.MailOutboxRepository;
import com.ahogek.cttserver.mail.template.MailTemplateRenderer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
class MailOutboxServiceTest {

    private static final String FRONTEND_BASE_URL = "http://localhost:5173";
    private static final int MAX_RETRIES = 5;

    @Mock private MailOutboxRepository repository;
    @Mock private MailTemplateRenderer renderer;

    @Captor private ArgumentCaptor<MailOutbox> outboxCaptor;

    private MailOutboxService service;

    @BeforeEach
    void setUp() {
        CttMailProperties properties =
                new CttMailProperties(
                        new CttMailProperties.From("test@localhost", "CTT Test"),
                        new CttMailProperties.Outbox(5000, 50, 300),
                        new CttMailProperties.Retry(10, 2.0, 3600, MAX_RETRIES),
                        new CttMailProperties.Frontend(FRONTEND_BASE_URL));

        service = new MailOutboxService(repository, renderer, properties);
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
            String expectedLink = FRONTEND_BASE_URL + "/verify-email?token=abc123";
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
            String expectedLink = FRONTEND_BASE_URL + "/reset-password?token=xyz789";
            assertThat(payload).containsEntry("resetLink", expectedLink);
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
                                    service.enqueuePasswordResetEmail(
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
            service.enqueuePasswordResetEmail(UUID.randomUUID(), "user", email, "token");

            // Then
            verify(repository)
                    .countDuplicates(
                            eq(email),
                            eq("RESET_PASSWORD"),
                            eq(
                                    List.of(
                                            MailOutboxStatus.PENDING,
                                            MailOutboxStatus.SENDING,
                                            MailOutboxStatus.SENT)),
                            any());
        }
    }
}
