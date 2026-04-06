package com.ahogek.cttserver.auth.controller;

import com.ahogek.cttserver.auth.entity.PasswordResetToken;
import com.ahogek.cttserver.auth.repository.PasswordResetTokenRepository;
import com.ahogek.cttserver.common.BaseIntegrationTest;
import com.ahogek.cttserver.mail.entity.MailOutbox;
import com.ahogek.cttserver.mail.enums.MailOutboxStatus;
import com.ahogek.cttserver.mail.repository.MailOutboxRepository;
import com.ahogek.cttserver.user.entity.User;
import com.ahogek.cttserver.user.repository.UserRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@BaseIntegrationTest
@DisplayName("AuthController Forgot Password Integration Tests")
class AuthControllerForgotPasswordIntegrationTest {

    @Autowired private MockMvcTester mvc;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordResetTokenRepository tokenRepository;
    @Autowired private MailOutboxRepository mailOutboxRepository;

    @BeforeEach
    void setUp() {
        mailOutboxRepository.deleteAll();
        tokenRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("Should process forgot-password request and send email")
    void shouldProcessForgotPasswordRequestAndSendEmail() {
        User user = createActiveUser("test@example.com");
        userRepository.save(user);

        String request =
                """
                {
                    "email": "test@example.com"
                }
                """;

        assertThat(
                        mvc.post()
                                .uri("/api/v1/auth/forgot-password")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(request))
                .hasStatusOk();

        List<PasswordResetToken> tokens = tokenRepository.findAll();
        assertThat(tokens).hasSize(1);
        PasswordResetToken token = tokens.getFirst();
        assertThat(token.getUserId()).isEqualTo(user.getId());
        assertThat(token.getEmail()).isEqualTo("test@example.com");
        assertThat(token.getTokenHash()).isNotBlank();
        assertThat(token.getExpiresAt()).isNotNull();

        List<MailOutbox> outboxes = mailOutboxRepository.findAll();
        assertThat(outboxes).hasSize(1);
        MailOutbox outbox = outboxes.getFirst();
        assertThat(outbox.getRecipient()).isEqualTo("test@example.com");
        assertThat(outbox.getBizType()).isEqualTo("RESET_PASSWORD");
        assertThat(outbox.getStatus()).isEqualTo(MailOutboxStatus.PENDING);
    }

    @Test
    @DisplayName("Should return 200 for non-existent email (anti-enumeration)")
    void shouldReturn200ForNonExistentEmail() {
        String request =
                """
                {
                    "email": "nonexistent@example.com"
                }
                """;

        assertThat(
                        mvc.post()
                                .uri("/api/v1/auth/forgot-password")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(request))
                .hasStatusOk();

        assertThat(tokenRepository.count()).isZero();
        assertThat(mailOutboxRepository.count()).isZero();
    }

    @Test
    @DisplayName("Should return 200 for inactive user (anti-enumeration)")
    void shouldReturn200ForInactiveUser() {
        User user = createPendingUser("inactive@example.com");
        userRepository.save(user);

        String request =
                """
                {
                    "email": "inactive@example.com"
                }
                """;

        assertThat(
                        mvc.post()
                                .uri("/api/v1/auth/forgot-password")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(request))
                .hasStatusOk();

        assertThat(tokenRepository.count()).isZero();
        assertThat(mailOutboxRepository.count()).isZero();
    }

    @Test
    @DisplayName("Should return 400 for invalid email format")
    void shouldReturn400ForInvalidEmailFormat() {
        String request =
                """
                {
                    "email": "invalid-email"
                }
                """;

        assertThat(
                        mvc.post()
                                .uri("/api/v1/auth/forgot-password")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(request))
                .hasStatus(400)
                .bodyJson()
                .extractingPath("$.code")
                .isEqualTo("COMMON_003");
    }

    @Test
    @DisplayName("Should return 400 for blank email")
    void shouldReturn400ForBlankEmail() {
        String request =
                """
                {
                    "email": ""
                }
                """;

        assertThat(
                        mvc.post()
                                .uri("/api/v1/auth/forgot-password")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(request))
                .hasStatus(400)
                .bodyJson()
                .extractingPath("$.code")
                .isEqualTo("COMMON_003");
    }

    private User createActiveUser(String email) {
        User user = new User();
        user.setEmail(email);
        user.setDisplayName("Test User");
        user.setPasswordHash("hashedPassword");
        user.verifyEmail();
        return user;
    }

    private User createPendingUser(String email) {
        User user = new User();
        user.setEmail(email);
        user.setDisplayName("Test User");
        user.setPasswordHash("hashedPassword");
        return user;
    }
}
