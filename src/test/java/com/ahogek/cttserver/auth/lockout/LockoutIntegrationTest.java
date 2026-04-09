package com.ahogek.cttserver.auth.lockout;

import com.ahogek.cttserver.auth.dto.LoginRequest;
import com.ahogek.cttserver.auth.entity.LoginAttempt;
import com.ahogek.cttserver.auth.repository.LoginAttemptRepository;
import com.ahogek.cttserver.common.BaseIntegrationTest;
import com.ahogek.cttserver.common.utils.TokenUtils;
import com.ahogek.cttserver.fixtures.UserFixtures;
import com.ahogek.cttserver.user.entity.User;
import com.ahogek.cttserver.user.enums.UserStatus;
import com.ahogek.cttserver.user.repository.UserRepository;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for account lockout behavior during login.
 *
 * <p>Uses real database (Testcontainers) and full Spring ApplicationContext.
 *
 * <p>NOTE: This test class is NOT @Transactional because LoginAttemptService methods use
 * REQUIRES_NEW propagation. Each test method manages its own transaction boundaries and cleans up
 * explicitly.
 *
 * @author AhogeK
 * @since 2026-04-09
 */
@BaseIntegrationTest
class LockoutIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private LoginAttemptRepository loginAttemptRepository;
    @Autowired private EntityManager entityManager;

    private static final String TEST_PASSWORD = "Test@1234";
    private static final String WRONG_PASSWORD = "WrongPass123!";
    private static final String DEVICE_ID = "test-device-" + UUID.randomUUID();

    private String testEmail;
    private String testEmailHash;

    @BeforeEach
    void setUp() {
        testEmail = "lockout-" + UUID.randomUUID() + "@test.example";
        testEmailHash = TokenUtils.hashToken(testEmail.toLowerCase());
    }

    @AfterEach
    void tearDown() {
        loginAttemptRepository.deleteAll();
        userRepository.deleteAll();
    }

    private User createAndPersistUser() {
        User user =
                UserFixtures.regularUser()
                        .email(testEmail)
                        .rawPassword(TEST_PASSWORD)
                        .status(UserStatus.ACTIVE)
                        .emailVerified(true)
                        .build();
        return userRepository.saveAndFlush(user);
    }

    private String loginRequestJson(String email, String password) throws Exception {
        LoginRequest request = new LoginRequest(email, password, DEVICE_ID);
        return objectMapper.writeValueAsString(request);
    }

    @Nested
    @DisplayName("Account Lockout")
    class AccountLockout {

        @Test
        @DisplayName("should return 403 after max failed login attempts")
        void shouldReturn403_afterMaxFailedAttempts() throws Exception {
            createAndPersistUser();

            for (int i = 0; i < 5; i++) {
                mockMvc.perform(
                                post("/api/v1/auth/login")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(loginRequestJson(testEmail, WRONG_PASSWORD)))
                        .andExpect(status().isUnauthorized())
                        .andExpect(jsonPath("$.code").value("AUTH_001"));
            }

            mockMvc.perform(
                            post("/api/v1/auth/login")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(loginRequestJson(testEmail, TEST_PASSWORD)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("AUTH_004"));
        }

        @Test
        @DisplayName("should auto-unlock after lockout duration expires")
        @Transactional
        void shouldAutoUnlock_afterLockoutDurationExpires() throws Exception {
            User user = createAndPersistUser();
            user.lockAccount();
            userRepository.saveAndFlush(user);

            loginAttemptRepository.deleteByEmailHash(testEmailHash);
            entityManager
                    .createNativeQuery(
                            "INSERT INTO login_attempts (email_hash, ip_hash, attempt_at) VALUES (?, ?, ?)")
                    .setParameter(1, testEmailHash)
                    .setParameter(2, TokenUtils.hashToken("10.0.0.1"))
                    .setParameter(3, Instant.now().minus(Duration.ofMinutes(31)))
                    .executeUpdate();

            TestTransaction.flagForCommit();
            TestTransaction.end();
            TestTransaction.start();

            mockMvc.perform(
                            post("/api/v1/auth/login")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(loginRequestJson(testEmail, TEST_PASSWORD)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                    .andExpect(jsonPath("$.data.refreshToken").isNotEmpty());

            User refreshed = userRepository.findByEmailIgnoreCase(testEmail).orElseThrow();
            assertThat(refreshed.getStatus()).isEqualTo(UserStatus.ACTIVE);
        }
    }

    @Nested
    @DisplayName("Successful Login Clears Lockout")
    class SuccessfulLoginClearsLockout {

        @Test
        @DisplayName("should reset failed attempts after successful login")
        void shouldResetFailedAttempts_afterSuccessfulLogin() throws Exception {
            createAndPersistUser();
            for (int i = 0; i < 4; i++) {
                loginAttemptRepository.save(
                        new LoginAttempt(testEmailHash, TokenUtils.hashToken("10.0.0.1")));
            }

            mockMvc.perform(
                            post("/api/v1/auth/login")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(loginRequestJson(testEmail, TEST_PASSWORD)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.accessToken").isNotEmpty());

            assertThat(
                            loginAttemptRepository.countAttemptsInWindow(
                                    testEmailHash, Instant.now().minus(Duration.ofMinutes(15))))
                    .isZero();
        }

        @Test
        @DisplayName("should allow login after partial failures then success")
        void shouldAllowLogin_afterPartialFailuresThenSuccess() throws Exception {
            createAndPersistUser();
            for (int i = 0; i < 3; i++) {
                loginAttemptRepository.save(
                        new LoginAttempt(testEmailHash, TokenUtils.hashToken("10.0.0.1")));
            }

            mockMvc.perform(
                            post("/api/v1/auth/login")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(loginRequestJson(testEmail, TEST_PASSWORD)))
                    .andExpect(status().isOk());

            for (int i = 0; i < 5; i++) {
                mockMvc.perform(
                                post("/api/v1/auth/login")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(loginRequestJson(testEmail, WRONG_PASSWORD)))
                        .andExpect(status().isUnauthorized());
            }

            mockMvc.perform(
                            post("/api/v1/auth/login")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(loginRequestJson(testEmail, TEST_PASSWORD)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("AUTH_004"));
        }
    }

    @Nested
    @DisplayName("Error Response Format")
    class ErrorResponseFormat {

        @Test
        @DisplayName("should return AUTH_004 error code with forbidden status when locked")
        void shouldReturnAuth004_whenAccountLocked() throws Exception {
            User user = createAndPersistUser();
            user.lockAccount();
            for (int i = 0; i < 5; i++) {
                loginAttemptRepository.save(
                        new LoginAttempt(testEmailHash, TokenUtils.hashToken("10.0.0.1")));
            }
            userRepository.saveAndFlush(user);

            mockMvc.perform(
                            post("/api/v1/auth/login")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(loginRequestJson(testEmail, TEST_PASSWORD)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("AUTH_004"))
                    .andExpect(jsonPath("$.httpStatus").value(403))
                    .andExpect(jsonPath("$.message").exists());
        }
    }
}
