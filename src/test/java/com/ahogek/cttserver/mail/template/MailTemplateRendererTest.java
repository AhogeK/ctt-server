package com.ahogek.cttserver.mail.template;

import com.ahogek.cttserver.mail.config.MailTemplateConfig;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link MailTemplateRenderer}.
 *
 * @author AhogeK
 * @since 2026-03-19
 */
@SpringBootTest(classes = {MailTemplateConfig.class, ThymeleafMailTemplateRenderer.class})
class MailTemplateRendererTest {

    @Autowired private MailTemplateRenderer renderer;

    @Nested
    @DisplayName("Email Verification Template")
    class EmailVerificationTests {

        @Test
        @DisplayName("HTML rendering should inject variables correctly")
        void shouldRenderEmailVerificationHtml() {
            // Given
            var data =
                    new EmailVerificationTemplateData(
                            "AhogeK",
                            "https://api.cttserver.com/verify?token=abc123",
                            Duration.ofMinutes(15));

            // When
            String htmlOutput = renderer.renderHtml(data);

            // Then
            assertThat(htmlOutput)
                    .isNotBlank()
                    .contains("AhogeK")
                    .contains("https://api.cttserver.com/verify?token=abc123")
                    .contains("15</strong>")
                    .contains("Verify Email Address");
        }

        @Test
        @DisplayName("HTML rendering should include layout header and footer")
        void shouldIncludeLayoutInHtml() {
            // Given
            var data =
                    new EmailVerificationTemplateData(
                            "TestUser", "https://example.com/verify", Duration.ofMinutes(30));

            // When
            String htmlOutput = renderer.renderHtml(data);

            // Then
            assertThat(htmlOutput)
                    .contains("CTT Server") // Header
                    .contains("All rights reserved") // Footer
                    .contains("<html")
                    .contains("</html>");
        }

        @Test
        @DisplayName("HTML rendering should not contain Thymeleaf directives")
        void shouldNotContainThymeleafDirectivesInHtml() {
            // Given
            var data =
                    new EmailVerificationTemplateData(
                            "TestUser", "https://example.com/verify", Duration.ofMinutes(15));

            // When
            String htmlOutput = renderer.renderHtml(data);

            // Then
            assertThat(htmlOutput)
                    .doesNotContain("th:text")
                    .doesNotContain("th:href")
                    .doesNotContain("layout:");
        }

        @Test
        @DisplayName("Text rendering should produce plain text output")
        void shouldRenderEmailVerificationText() {
            // Given
            var data =
                    new EmailVerificationTemplateData(
                            "AhogeK",
                            "https://api.cttserver.com/verify?token=xyz789",
                            Duration.ofMinutes(30));

            // When
            String textOutput = renderer.renderText(data);

            // Then
            assertThat(textOutput)
                    .isNotBlank()
                    .contains("AhogeK")
                    .contains("https://api.cttserver.com/verify?token=xyz789")
                    .contains("30")
                    .doesNotContain("<html>")
                    .doesNotContain("</");
        }
    }

    @Nested
    @DisplayName("Password Reset Template")
    class PasswordResetTests {

        @Test
        @DisplayName("HTML rendering should inject variables correctly")
        void shouldRenderPasswordResetHtml() {
            // Given
            var data =
                    new PasswordResetTemplateData(
                            "TestUser",
                            "https://api.cttserver.com/reset?token=reset123",
                            Duration.ofMinutes(30));

            // When
            String htmlOutput = renderer.renderHtml(data);

            // Then
            assertThat(htmlOutput)
                    .isNotBlank()
                    .contains("TestUser")
                    .contains("https://api.cttserver.com/reset?token=reset123")
                    .contains("30</strong>")
                    .contains("Reset Password");
        }

        @Test
        @DisplayName("HTML rendering should include security notice")
        void shouldIncludeSecurityNoticeInHtml() {
            // Given
            var data =
                    new PasswordResetTemplateData(
                            "TestUser", "https://example.com/reset", Duration.ofMinutes(30));

            // When
            String htmlOutput = renderer.renderHtml(data);

            // Then
            assertThat(htmlOutput)
                    .contains("didn't request")
                    .contains("password will remain unchanged");
        }

        @Test
        @DisplayName("Text rendering should produce plain text output")
        void shouldRenderPasswordResetText() {
            // Given
            var data =
                    new PasswordResetTemplateData(
                            "TestUser",
                            "https://api.cttserver.com/reset?token=reset456",
                            Duration.ofMinutes(60));

            // When
            String textOutput = renderer.renderText(data);

            // Then
            assertThat(textOutput)
                    .isNotBlank()
                    .contains("TestUser")
                    .contains("https://api.cttserver.com/reset?token=reset456")
                    .contains("60")
                    .doesNotContain("<html>")
                    .doesNotContain("</");
        }
    }
}
