package com.ahogek.cttserver.auth.lockout;

import com.ahogek.cttserver.auth.dto.LoginRequest;
import com.ahogek.cttserver.common.BaseIntegrationTest;
import com.ahogek.cttserver.fixtures.UserFixtures;
import com.ahogek.cttserver.user.entity.User;
import com.ahogek.cttserver.user.enums.UserStatus;
import com.ahogek.cttserver.user.repository.UserRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for account lockout behavior during login.
 *
 * <p>These tests verify the end-to-end lockout flow including:
 *
 * <ul>
 *   <li>Account lockout after max failed attempts
 *   <li>Auto-unlock after lockout duration expires
 *   <li>Failed attempt counter reset after successful login
 *   <li>Sliding window behavior for failure counter
 * </ul>
 *
 * <p>Uses real database (Testcontainers) and full Spring ApplicationContext.
 *
 * @author AhogeK
 * @since 2026-04-09
 */
@BaseIntegrationTest
@Transactional
class LockoutIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;

    private static final String TEST_PASSWORD = "Test@1234";
    private static final String WRONG_PASSWORD = "WrongPass123!";
    private static final String DEVICE_ID = "test-device-" + UUID.randomUUID();

    private String testEmail;

    @BeforeEach
    void setUp() {
        testEmail = "lockout-" + UUID.randomUUID() + "@test.example";
        userRepository.deleteAll();
    }

    private User createAndPersistUser() {
        User user = UserFixtures.regularUser()
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
            // Given: A registered active user
            createAndPersistUser();

            // When: Login with wrong password 5 times (default max attempts)
            for (int i = 0; i < 5; i++) {
                mockMvc.perform(post("/api/v1/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(loginRequestJson(testEmail, WRONG_PASSWORD)))
                        .andExpect(status().isUnauthorized())
                        .andExpect(jsonPath("$.code").value("AUTH_001"));
            }

            // Then: 6th login attempt returns 403 Forbidden (account locked)
            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(loginRequestJson(testEmail, TEST_PASSWORD)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("AUTH_004"));
        }

        @Test
        @DisplayName("should auto-unlock after lockout duration expires")
        void shouldAutoUnlock_afterLockoutDurationExpires() throws Exception {
            // Given: A registered user that is manually locked (simulate expired lock)
            User user = createAndPersistUser();
            ReflectionTestUtils.setField(user, "status", UserStatus.LOCKED);
            user.setFailedLoginAttempts(5);
            ReflectionTestUtils.setField(user, "lockedUntil", Instant.now().minusSeconds(3600));
            userRepository.saveAndFlush(user);

            // When: Login with correct password
            // Then: Login succeeds (auto-unlock triggered)
            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(loginRequestJson(testEmail, TEST_PASSWORD)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                    .andExpect(jsonPath("$.data.refreshToken").isNotEmpty());

            // Verify user state was reset
            User refreshed = userRepository.findByEmailIgnoreCase(testEmail).orElseThrow();
            assertThat(refreshed.getStatus()).isEqualTo(UserStatus.ACTIVE);
            assertThat(refreshed.getFailedLoginAttempts()).isZero();
            assertThat(refreshed.getLockedUntil()).isNull();
        }
    }

    @Nested
    @DisplayName("Successful Login Clears Lockout")
    class SuccessfulLoginClearsLockout {

        @Test
        @DisplayName("should reset failed attempts after successful login")
        void shouldResetFailedAttempts_afterSuccessfulLogin() throws Exception {
            // Given: User has 4 failed login attempts
            User user = createAndPersistUser();
            user.setFailedLoginAttempts(4);
            userRepository.saveAndFlush(user);

            // When: User logs in successfully
            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(loginRequestJson(testEmail, TEST_PASSWORD)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.accessToken").isNotEmpty());

            // Then: Failed attempts reset to 0
            User refreshed = userRepository.findByEmailIgnoreCase(testEmail).orElseThrow();
            assertThat(refreshed.getFailedLoginAttempts()).isZero();
            assertThat(refreshed.getLastFailureTime()).isNull();
        }

        @Test
        @DisplayName("should allow login after partial failures then success")
        void shouldAllowLogin_afterPartialFailuresThenSuccess() throws Exception {
            // Given: User has 3 failed attempts (below threshold)
            User user = createAndPersistUser();
            user.setFailedLoginAttempts(3);
            userRepository.saveAndFlush(user);

            // When: User logs in successfully
            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(loginRequestJson(testEmail, TEST_PASSWORD)))
                    .andExpect(status().isOk());

            // Then: Counter resets, user can attempt again without risk of immediate lockout
            User refreshed = userRepository.findByEmailIgnoreCase(testEmail).orElseThrow();
            assertThat(refreshed.getFailedLoginAttempts()).isZero();

            // Verify user can fail again without immediate lockout
            for (int i = 0; i < 4; i++) {
                mockMvc.perform(post("/api/v1/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(loginRequestJson(testEmail, WRONG_PASSWORD)))
                        .andExpect(status().isUnauthorized());
            }

            // 5th failure should trigger lockout (counter started from 0 after success)
            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(loginRequestJson(testEmail, WRONG_PASSWORD)))
                    .andExpect(status().isUnauthorized());

            // Next attempt should be forbidden (locked)
            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(loginRequestJson(testEmail, TEST_PASSWORD)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("AUTH_004"));
        }
    }

    @Nested
    @DisplayName("Sliding Window")
    class SlidingWindow {

        @Test
        @DisplayName("should reset counter when sliding window expires")
        void shouldResetCounter_whenSlidingWindowExpires() throws Exception {
            // Given: User has some failed attempts but with old failure time (window expired)
            User user = createAndPersistUser();
            user.setFailedLoginAttempts(3);
            ReflectionTestUtils.setField(user, "lastFailureTime", Instant.now().minusSeconds(3600));
            userRepository.saveAndFlush(user);

            // When: User attempts login again with wrong password
            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(loginRequestJson(testEmail, WRONG_PASSWORD)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("AUTH_001"));

            // Then: Counter resets due to sliding window expiry, so only 1 failure counted
            User refreshed = userRepository.findByEmailIgnoreCase(testEmail).orElseThrow();
            assertThat(refreshed.getFailedLoginAttempts()).isEqualTo(1);
            assertThat(refreshed.getStatus()).isEqualTo(UserStatus.ACTIVE);
        }

        @Test
        @DisplayName("should accumulate failures within sliding window")
        void shouldAccumulateFailures_withinSlidingWindow() throws Exception {
            // Given: User has 3 failed attempts within the sliding window
            User user = createAndPersistUser();
            user.setFailedLoginAttempts(3);
            ReflectionTestUtils.setField(user, "lastFailureTime", Instant.now().minusSeconds(60));
            userRepository.saveAndFlush(user);

            // When: User fails 2 more times (total 5, within window)
            for (int i = 0; i < 2; i++) {
                mockMvc.perform(post("/api/v1/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(loginRequestJson(testEmail, WRONG_PASSWORD)))
                        .andExpect(status().isUnauthorized());
            }

            // Then: Account should be locked (5 failures within window)
            mockMvc.perform(post("/api/v1/auth/login")
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
            // Given: A locked user
            User user = createAndPersistUser();
            ReflectionTestUtils.setField(user, "status", UserStatus.LOCKED);
            user.setFailedLoginAttempts(5);
            ReflectionTestUtils.setField(user, "lockedUntil", Instant.now().plusSeconds(1800));
            userRepository.saveAndFlush(user);

            // When: Attempt login
            // Then: Returns 403 with AUTH_004 error code
            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(loginRequestJson(testEmail, TEST_PASSWORD)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("AUTH_004"))
                    .andExpect(jsonPath("$.httpStatus").value(403))
                    .andExpect(jsonPath("$.message").exists());
        }
    }
}
