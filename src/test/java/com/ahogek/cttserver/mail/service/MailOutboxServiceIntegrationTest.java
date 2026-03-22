package com.ahogek.cttserver.mail.service;

import com.ahogek.cttserver.audit.entity.AuditLog;
import com.ahogek.cttserver.audit.enums.AuditAction;
import com.ahogek.cttserver.audit.enums.SecuritySeverity;
import com.ahogek.cttserver.audit.model.AuditDetails;
import com.ahogek.cttserver.audit.repository.AuditLogRepository;
import com.ahogek.cttserver.common.BaseIntegrationTest;
import com.ahogek.cttserver.common.exception.ErrorCode;
import com.ahogek.cttserver.common.exception.TooManyRequestsException;
import com.ahogek.cttserver.mail.entity.MailOutbox;
import com.ahogek.cttserver.mail.enums.MailOutboxStatus;
import com.ahogek.cttserver.mail.repository.MailOutboxRepository;
import com.ahogek.cttserver.user.entity.User;
import com.ahogek.cttserver.user.repository.UserRepository;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.test.context.TestPropertySource;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for {@link MailOutboxService}.
 *
 * <p>Verifies transactional consistency between MailOutbox persistence and AuditLog events.
 *
 * @author AhogeK
 * @since 2026-03-22
 */
@BaseIntegrationTest
@EnableAsync
@TestPropertySource(
        properties = {
            "ctt.mail.outbox.poll-interval-ms=999999999",
            "ctt.mail.outbox.zombie-interval-ms=999999999"
        })
class MailOutboxServiceIntegrationTest {

    @Autowired private MailOutboxService mailOutboxService;

    @Autowired private MailOutboxRepository mailOutboxRepository;

    @Autowired private AuditLogRepository auditLogRepository;

    @Autowired private UserRepository userRepository;

    private static UUID testUserId;
    private static String testUserEmail;

    private static final String TEST_USERNAME = "TestUser";
    private static final String TEST_TOKEN = "test-verification-token-123";

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        mailOutboxRepository.deleteAll();
        auditLogRepository.deleteAll();

        // Create test user to satisfy audit_logs.user_id FK constraint
        User testUser = new User();
        testUser.setEmail("test-" + System.currentTimeMillis() + "@domain.com");
        testUser.setDisplayName(TEST_USERNAME);
        userRepository.saveAndFlush(testUser);

        // Store for use in tests
        testUserId = testUser.getId();
        testUserEmail = testUser.getEmail();
    }

    @AfterEach
    void tearDown() {
        userRepository.deleteAll();
        mailOutboxRepository.deleteAll();
        auditLogRepository.deleteAll();
    }

    @Nested
    @DisplayName("enqueueVerificationEmail - Happy Path")
    class HappyPathTests {

        @Test
        @DisplayName(
                "should enqueue verification email and persist audit log with desensitized recipient")
        void shouldEnqueueVerificationEmailAndAuditSuccessfully() {
            // When
            mailOutboxService.enqueueVerificationEmail(
                    testUserId, TEST_USERNAME, testUserEmail, TEST_TOKEN);

            // Then 1: MailOutbox persisted
            List<MailOutbox> outboxes = mailOutboxRepository.findAll();
            assertThat(outboxes).hasSize(1);
            MailOutbox outbox = outboxes.getFirst();
            assertThat(outbox.getBizId()).isEqualTo(testUserId);
            assertThat(outbox.getRecipient()).isEqualTo(testUserEmail);
            assertThat(outbox.getBizType()).isEqualTo("REGISTER_VERIFY");
            assertThat(outbox.getSubject()).isEqualTo("Verify Your Email Address");
            assertThat(outbox.getStatus()).isEqualTo(MailOutboxStatus.PENDING);
            assertThat(outbox.getRetryCount()).isZero();

            // Then 2: AuditLog persisted (await async event)
            Awaitility.await()
                    .atMost(10, TimeUnit.SECONDS)
                    .pollDelay(1, TimeUnit.SECONDS)
                    .untilAsserted(
                            () -> {
                                List<AuditLog> audits = auditLogRepository.findAll();
                                assertThat(audits).hasSize(1);

                                AuditLog audit = audits.getFirst();
                                assertThat(audit.getAction()).isEqualTo(AuditAction.MAIL_ENQUEUED);
                                assertThat(audit.getSeverity()).isEqualTo(SecuritySeverity.INFO);
                                assertThat(audit.getUserId()).isEqualTo(testUserId);

                                AuditDetails details = audit.getDetails();
                                assertThat(details.ext())
                                        .containsEntry("mailOutboxId", outbox.getId().toString())
                                        .containsEntry("templateName", "email-verification")
                                        .containsKey("recipientMasked")
                                        .containsEntry("retryCount", 0);

                                String maskedEmail = (String) details.ext().get("recipientMasked");
                                assertThat(maskedEmail).contains("***").endsWith("@domain.com");
                                assertThat(details.ext().toString()).doesNotContain(testUserEmail);
                            });
        }
    }

    @Nested
    @DisplayName("enqueueVerificationEmail - Idempotency")
    class IdempotencyTests {

        @Test
        @DisplayName("should skip duplicate enqueue within 10-minute idempotency window and audit")
        void shouldSkipDuplicateWithinIdempotentWindowAndAudit() {
            // Given
            mailOutboxService.enqueueVerificationEmail(
                    testUserId, TEST_USERNAME, testUserEmail, TEST_TOKEN);

            Awaitility.await()
                    .atMost(3, TimeUnit.SECONDS)
                    .untilAsserted(
                            () -> {
                                assertThat(mailOutboxRepository.count()).isEqualTo(1);
                                assertThat(auditLogRepository.count()).isEqualTo(1);
                            });

            long initialAuditCount = auditLogRepository.count();

            // When
            mailOutboxService.enqueueVerificationEmail(
                    testUserId, TEST_USERNAME, testUserEmail, TEST_TOKEN);

            // Then 1: No duplicate MailOutbox
            assertThat(mailOutboxRepository.count()).isEqualTo(1);

            // Then 2: Additional MAIL_IDEMPOTENT_SKIP audit
            Awaitility.await()
                    .atMost(3, TimeUnit.SECONDS)
                    .untilAsserted(
                            () -> {
                                assertThat(auditLogRepository.count())
                                        .isEqualTo(initialAuditCount + 1);

                                List<AuditLog> audits = auditLogRepository.findAll();
                                AuditLog latestAudit =
                                        audits.stream()
                                                .filter(
                                                        a ->
                                                                a.getAction()
                                                                        == AuditAction
                                                                                .MAIL_IDEMPOTENT_SKIP)
                                                .findFirst()
                                                .orElseThrow(
                                                        () ->
                                                                new AssertionError(
                                                                        "Missing IDEMPOTENT_SKIP audit"));

                                assertThat(latestAudit.getSeverity())
                                        .isEqualTo(SecuritySeverity.INFO);
                                assertThat(latestAudit.getUserId()).isEqualTo(testUserId);

                                AuditDetails details = latestAudit.getDetails();
                                assertThat(details.ext())
                                        .containsEntry("bizType", "REGISTER_VERIFY")
                                        .containsEntry("windowMinutes", 10);

                                String maskedEmail = (String) details.ext().get("recipientMasked");
                                assertThat(maskedEmail).contains("***");
                                assertThat(details.ext().toString()).doesNotContain(testUserEmail);
                            });
        }
    }

    @Nested
    @DisplayName("enqueueVerificationEmail - Rate Limiting")
    class RateLimitingTests {

        @Test
        @DisplayName(
                "should throw TooManyRequestsException when exceeding 3 requests per minute rate limit")
        void shouldThrowException_whenRateLimitExceeded() {
            // Given: Send 3 emails to the SAME address within the rate limit window
            // Use different bizId for each to avoid idempotency check
            String email = "ratelimit-test@domain.com";
            for (int i = 0; i < 3; i++) {
                UUID differentUserId = UUID.randomUUID();
                mailOutboxService.enqueueVerificationEmail(
                        differentUserId, TEST_USERNAME, email, TEST_TOKEN + i);
            }

            Awaitility.await()
                    .atMost(3, TimeUnit.SECONDS)
                    .untilAsserted(() -> assertThat(mailOutboxRepository.count()).isEqualTo(3));

            // When: 4th request to same address (same bizId to avoid idempotency)
            UUID fourthUserId = UUID.randomUUID();
            // Then: Should throw TooManyRequestsException
            assertThatThrownBy(
                            () ->
                                    mailOutboxService.enqueueVerificationEmail(
                                            fourthUserId,
                                            TEST_USERNAME,
                                            email,
                                            TEST_TOKEN + "_4th"))
                    .isInstanceOf(TooManyRequestsException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.MAIL_004);

            // Verify: No 4th record created
            assertThat(mailOutboxRepository.count()).isEqualTo(3);
        }
    }

    @Nested
    @DisplayName("Email Desensitization")
    class DesensitizationTests {

        @Test
        @DisplayName("should desensitize email in audit log JSONB field")
        void shouldMaskEmailInAuditDetails() {
            // Given
            String[] testEmails = {"user@domain.com", "a@b.com", "test.user@example.org"};

            // When
            for (String email : testEmails) {
                mailOutboxService.enqueueVerificationEmail(
                        testUserId, TEST_USERNAME, email, TEST_TOKEN);
            }

            // Then
            Awaitility.await()
                    .atMost(3, TimeUnit.SECONDS)
                    .untilAsserted(
                            () -> {
                                List<AuditLog> audits = auditLogRepository.findAll();
                                assertThat(audits).hasSize(3);

                                for (AuditLog audit : audits) {
                                    AuditDetails details = audit.getDetails();
                                    String maskedEmail =
                                            (String) details.ext().get("recipientMasked");

                                    assertThat(maskedEmail).contains("***");

                                    String detailsString = details.ext().toString();
                                    for (String email : testEmails) {
                                        if (!email.equals(maskedEmail)) {
                                            assertThat(detailsString).doesNotContain(email);
                                        }
                                    }
                                }
                            });
        }
    }
}
