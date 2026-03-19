package com.ahogek.cttserver.mail.entity;

import com.ahogek.cttserver.mail.enums.MailOutboxStatus;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link MailOutbox} entity state machine and business rules.
 *
 * @author AhogeK
 * @since 2026-03-19
 */
@DisplayName("MailOutbox Entity")
class MailOutboxTest {

    @Nested
    @DisplayName("State Machine Transitions")
    class StateMachineTests {

        @Test
        @DisplayName("PENDING → SENDING → SENT: successful delivery flow")
        void shouldTransitionFromPendingToSendingToSent() {
            // Given
            var outbox = createPendingOutbox();

            // When
            outbox.markSending();

            // Then
            assertThat(outbox.getStatus()).isEqualTo(MailOutboxStatus.SENDING);
            assertThat(outbox.getRetryCount()).isZero();

            // When
            outbox.markSent();

            // Then
            assertThat(outbox.getStatus()).isEqualTo(MailOutboxStatus.SENT);
            assertThat(outbox.getSentAt()).isNotNull();
        }

        @Test
        @DisplayName("PENDING → FAILED → PENDING: retry flow")
        void shouldTransitionFromPendingToFailedThenRetry() {
            // Given
            var outbox = createPendingOutbox();
            Instant nextRetry = Instant.now().plusSeconds(60);

            // When
            outbox.markFailed("SMTP error", nextRetry);

            // Then
            assertThat(outbox.getStatus()).isEqualTo(MailOutboxStatus.FAILED);
            assertThat(outbox.getRetryCount()).isEqualTo(1);
            assertThat(outbox.getNextRetryAt()).isEqualTo(nextRetry);
            assertThat(outbox.getLastError()).isEqualTo("SMTP error");
        }

        @Test
        @DisplayName("auto-cancels when max retries exhausted")
        void shouldCancelAfterMaxRetriesExhausted() {
            // Given
            var outbox = createPendingOutbox();
            outbox.setMaxRetries(3);
            Instant nextRetry = Instant.now().plusSeconds(60);

            // When: Attempt 1
            outbox.markFailed("Error 1", nextRetry);
            assertThat(outbox.getStatus()).isEqualTo(MailOutboxStatus.FAILED);

            // When: Attempt 2
            outbox.markFailed("Error 2", nextRetry);
            assertThat(outbox.getStatus()).isEqualTo(MailOutboxStatus.FAILED);

            // When: Attempt 3 (exhausted)
            outbox.markFailed("Error 3", nextRetry);

            // Then: Should be canceled, not FAILED
            assertThat(outbox.getStatus()).isEqualTo(MailOutboxStatus.CANCELLED);
            assertThat(outbox.getRetryCount()).isEqualTo(3);
        }

        @Test
        @DisplayName("CANCELLED can transition to SENDING (no validation in markSending)")
        void shouldAllowSendingFromCancelledState() {
            // Given
            var outbox = createPendingOutbox();
            outbox.cancel();

            // When
            outbox.markSending();

            // Then: markSending() has no state validation, so status becomes SENDING
            assertThat(outbox.getStatus()).isEqualTo(MailOutboxStatus.SENDING);
        }

        @Test
        @DisplayName("SENT is terminal state")
        void shouldThrowWhenOperatingOnSentOutbox() {
            // Given
            var outbox = createPendingOutbox();
            outbox.markSending();
            outbox.markSent();

            // Then
            assertThatThrownBy(outbox::cancel)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("terminal");
        }
    }

    @Nested
    @DisplayName("canRetry() Logic")
    class CanRetryTests {

        @Test
        @DisplayName("should return true when retries remaining")
        void shouldReturnTrueWhenRetriesRemaining() {
            // Given
            var outbox = createPendingOutbox();
            outbox.setMaxRetries(3);

            // When
            outbox.markFailed("Error", Instant.now());

            // Then
            assertThat(outbox.canRetry()).isTrue();
        }

        @Test
        @DisplayName("should return false when max retries reached")
        void shouldReturnFalseWhenMaxRetriesReached() {
            // Given
            var outbox = createPendingOutbox();
            outbox.setMaxRetries(2);
            outbox.markFailed("Error 1", Instant.now());
            outbox.markFailed("Error 2", Instant.now());

            // Then
            assertThat(outbox.canRetry()).isFalse();
        }

        @Test
        @DisplayName("should return false when status is SENT")
        void shouldReturnFalseWhenStatusIsSent() {
            // Given
            var outbox = createPendingOutbox();
            outbox.markSending();
            outbox.markSent();

            // Then
            assertThat(outbox.canRetry()).isFalse();
        }

        @Test
        @DisplayName("should return false when status is CANCELLED")
        void shouldReturnFalseWhenStatusIsCancelled() {
            // Given
            var outbox = createPendingOutbox();
            outbox.cancel();

            // Then
            assertThat(outbox.canRetry()).isFalse();
        }
    }

    @Nested
    @DisplayName("Entity Properties")
    class PropertyTests {

        @Test
        @DisplayName("should initialize with default values")
        void shouldInitializeWithDefaults() {
            // Given
            var outbox = new MailOutbox();

            // Then
            assertThat(outbox.getStatus()).isEqualTo(MailOutboxStatus.PENDING);
            assertThat(outbox.getRetryCount()).isZero();
            assertThat(outbox.getMaxRetries()).isEqualTo(3);
            assertThat(outbox.getPayload()).isEmpty();
            assertThat(outbox.getVersion()).isNull();
        }

        @Test
        @DisplayName("should set all properties")
        void shouldSetAllProperties() {
            // Given
            var outbox = new MailOutbox();
            UUID bizId = UUID.randomUUID();
            Instant now = Instant.now();

            // When
            outbox.setBizType("REGISTER_VERIFY");
            outbox.setBizId(bizId);
            outbox.setRecipient("test@example.com");
            outbox.setSubject("Verify Email");
            outbox.setBodyHtml("<html>...</html>");
            outbox.setBodyText("Plain text");
            outbox.setTemplateCode("email-verification");
            outbox.setPayload(Map.of("username", "test"));
            outbox.setStatus(MailOutboxStatus.SENDING);
            outbox.setRetryCount(1);
            outbox.setMaxRetries(5);
            outbox.setNextRetryAt(now);
            outbox.setSentAt(now);
            outbox.setLastError("Test error");
            outbox.setTraceId("trace-123");

            // Then
            assertThat(outbox.getBizType()).isEqualTo("REGISTER_VERIFY");
            assertThat(outbox.getBizId()).isEqualTo(bizId);
            assertThat(outbox.getRecipient()).isEqualTo("test@example.com");
            assertThat(outbox.getSubject()).isEqualTo("Verify Email");
            assertThat(outbox.getBodyHtml()).isEqualTo("<html>...</html>");
            assertThat(outbox.getBodyText()).isEqualTo("Plain text");
            assertThat(outbox.getTemplateCode()).isEqualTo("email-verification");
            assertThat(outbox.getPayload()).containsEntry("username", "test");
            assertThat(outbox.getStatus()).isEqualTo(MailOutboxStatus.SENDING);
            assertThat(outbox.getRetryCount()).isEqualTo(1);
            assertThat(outbox.getMaxRetries()).isEqualTo(5);
            assertThat(outbox.getNextRetryAt()).isEqualTo(now);
            assertThat(outbox.getSentAt()).isEqualTo(now);
            assertThat(outbox.getLastError()).isEqualTo("Test error");
            assertThat(outbox.getTraceId()).isEqualTo("trace-123");
        }

        @Test
        @DisplayName("should set null payload to empty map")
        void shouldSetNullPayloadToEmptyMap() {
            // Given
            var outbox = new MailOutbox();

            // When
            outbox.setPayload(null);

            // Then
            assertThat(outbox.getPayload()).isEmpty();
            assertThat(outbox.getPayload()).isNotNull();
        }
    }

    // ==========================================
    // Helper Methods
    // ==========================================

    private MailOutbox createPendingOutbox() {
        var outbox = new MailOutbox();
        outbox.setBizType("REGISTER_VERIFY");
        outbox.setRecipient("test@example.com");
        outbox.setSubject("Verify Email");
        outbox.setBodyHtml("<html>Verify</html>");
        outbox.setBodyText("Verify");
        outbox.setTemplateCode("email-verification");
        outbox.setPayload(Map.of("username", "test"));
        return outbox;
    }
}
