package com.ahogek.cttserver.mail.dispatch;

import com.ahogek.cttserver.common.config.properties.CttMailProperties;
import com.ahogek.cttserver.mail.entity.MailOutbox;

import jakarta.mail.internet.MimeMessage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MailDispatcherTest {

    private static final String FROM_ADDRESS = "noreply@test.com";
    private static final String FROM_NAME = "Test Sender";

    @Mock private JavaMailSender mailSender;
    @Mock private MimeMessage mimeMessage;

    @Captor private ArgumentCaptor<MimeMessage> messageCaptor;

    private MailDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        CttMailProperties properties = new CttMailProperties(
            new CttMailProperties.From(FROM_ADDRESS, FROM_NAME),
            new CttMailProperties.Outbox(5000, 50, 300),
            new CttMailProperties.Retry(10, 2.0, 3600, 5),
            new CttMailProperties.Frontend("http://localhost:5173"));

        dispatcher = new MailDispatcher(mailSender, properties);
    }

    @Nested
    @DisplayName("dispatch")
    class DispatchTests {

        @Test
        @DisplayName("should send email with correct envelope")
        void shouldSendEmail_withCorrectEnvelope() throws Exception {
            // Given
            MailOutbox outbox = createOutbox();
            when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

            // When
            dispatcher.dispatch(outbox);

            // Then
            verify(mailSender).send(messageCaptor.capture());
            assertThat(messageCaptor.getValue()).isSameAs(mimeMessage);
        }

        @Test
        @DisplayName("should propagate exception when send fails")
        void shouldPropagateException_whenSendFails() {
            // Given
            MailOutbox outbox = createOutbox();
            when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
            doThrow(new MailSendException("SMTP connection failed"))
                    .when(mailSender)
                    .send(any(MimeMessage.class));

            // When & Then
            assertThatThrownBy(() -> dispatcher.dispatch(outbox))
                    .isInstanceOf(MailSendException.class);
        }
    }

    private MailOutbox createOutbox() {
        MailOutbox outbox = new MailOutbox();
        outbox.setId(UUID.randomUUID());
        outbox.setRecipient("recipient@test.com");
        outbox.setSubject("Test Subject");
        outbox.setBodyHtml("<html><body>Test HTML</body></html>");
        outbox.setBodyText("Test plain text");
        outbox.setTraceId("trace-123");
        return outbox;
    }
}
