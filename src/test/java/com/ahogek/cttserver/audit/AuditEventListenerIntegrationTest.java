package com.ahogek.cttserver.audit;

import com.ahogek.cttserver.TestcontainersConfiguration;
import com.ahogek.cttserver.audit.entity.AuditLog;
import com.ahogek.cttserver.audit.enums.AuditAction;
import com.ahogek.cttserver.audit.enums.ResourceType;
import com.ahogek.cttserver.audit.enums.SecuritySeverity;
import com.ahogek.cttserver.audit.model.AuditDetails;
import com.ahogek.cttserver.audit.repository.AuditLogRepository;
import com.ahogek.cttserver.user.entity.User;
import com.ahogek.cttserver.user.repository.UserRepository;

import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Integration test for audit event persistence via Testcontainers.
 *
 * <p>Test Coverage:
 *
 * <ul>
 *   <li>PostgreSQL container startup via TestcontainersConfiguration
 *   <li>Flyway migration execution
 *   <li>JPA JSONB mapping with Hibernate 6
 *   <li>Async event listener execution
 *   <li>Full end-to-end: Event publish to Async handle to Database persist
 *   <li>Race condition prevention: Audit events with FK references execute after transaction commit
 * </ul>
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-03-16
 */
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class AuditEventListenerIntegrationTest {

    @Autowired private ApplicationEventPublisher eventPublisher;

    @Autowired private AuditLogRepository repository;

    @Autowired private UserRepository userRepository;

    @Autowired private TransactionTemplate transactionTemplate;

    private static @NonNull SecurityAuditEvent getSecurityAuditEvent() {
        AuditDetails details = AuditDetails.attempt(3, "Invalid credentials");

        return new SecurityAuditEvent(
                null,
                AuditAction.LOGIN_FAILED,
                ResourceType.USER,
                "user-123",
                SecuritySeverity.WARNING,
                "192.168.1.100",
                "Mozilla/5.0 (Test)",
                details);
    }

    @BeforeEach
    void setUp() {
        repository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void shouldPersistAuditLogAsyncWhenEventPublished() {
        SecurityAuditEvent event = getSecurityAuditEvent();

        eventPublisher.publishEvent(event);

        await().atMost(3, TimeUnit.SECONDS)
                .untilAsserted(
                        () -> {
                            List<AuditLog> logs = repository.findAll();
                            assertThat(logs).hasSize(1);

                            AuditLog savedLog = logs.getFirst();

                            assertThat(savedLog.getUserId()).isNull();
                            assertThat(savedLog.getAction()).isEqualTo(AuditAction.LOGIN_FAILED);
                            assertThat(savedLog.getResourceType()).isEqualTo(ResourceType.USER);
                            assertThat(savedLog.getResourceId()).isEqualTo("user-123");
                            assertThat(savedLog.getSeverity()).isEqualTo(SecuritySeverity.WARNING);

                            assertThat(savedLog.getIpAddress()).isEqualTo("192.168.1.100");
                            assertThat(savedLog.getUserAgent()).isEqualTo("Mozilla/5.0 (Test)");

                            AuditDetails savedDetails = savedLog.getDetails();
                            assertThat(savedDetails.attemptCount()).isEqualTo(3);
                            assertThat(savedDetails.reason()).isEqualTo("Invalid credentials");

                            assertThat(savedLog.getCreatedAt()).isNotNull();
                        });
    }

    @Test
    void shouldPersistMinimalEventWithNullValues() {
        AuditDetails details = AuditDetails.error("RATE_LIMIT", "Quota exceeded");
        SecurityAuditEvent event =
                new SecurityAuditEvent(
                        null,
                        AuditAction.RATE_LIMIT_EXCEEDED,
                        ResourceType.API_KEY,
                        null,
                        SecuritySeverity.CRITICAL,
                        null,
                        null,
                        details);

        eventPublisher.publishEvent(event);

        await().atMost(3, TimeUnit.SECONDS)
                .untilAsserted(
                        () -> {
                            List<AuditLog> logs = repository.findAll();
                            assertThat(logs).hasSize(1);

                            AuditLog savedLog = logs.getFirst();

                            assertThat(savedLog.getUserId()).isNull();
                            assertThat(savedLog.getAction())
                                    .isEqualTo(AuditAction.RATE_LIMIT_EXCEEDED);
                            assertThat(savedLog.getResourceType()).isEqualTo(ResourceType.API_KEY);
                            assertThat(savedLog.getSeverity()).isEqualTo(SecuritySeverity.CRITICAL);

                            assertThat(savedLog.getIpAddress()).isEqualTo("UNKNOWN");
                            assertThat(savedLog.getUserAgent()).isEqualTo("UNKNOWN");
                            assertThat(savedLog.getResourceId()).isNull();

                            AuditDetails savedDetails = savedLog.getDetails();
                            assertThat(savedDetails.errorCode()).isEqualTo("RATE_LIMIT");
                            assertThat(savedDetails.reason()).isEqualTo("Quota exceeded");
                        });
    }

    @Test
    void shouldPersistMultipleEventsInOrder() {
        for (int i = 0; i < 5; i++) {
            AuditDetails details = AuditDetails.reason("Created API key " + i);
            SecurityAuditEvent event =
                    new SecurityAuditEvent(
                            null,
                            AuditAction.API_KEY_CREATED,
                            ResourceType.API_KEY,
                            "api-key-" + i,
                            SecuritySeverity.INFO,
                            "10.0.0." + i,
                            "Agent" + i,
                            details);
            eventPublisher.publishEvent(event);
        }

        await().atMost(5, TimeUnit.SECONDS)
                .untilAsserted(
                        () -> {
                            List<AuditLog> logs = repository.findAll();
                            assertThat(logs).hasSize(5);

                            List<String> resourceIds =
                                    logs.stream().map(AuditLog::getResourceId).sorted().toList();
                            assertThat(resourceIds)
                                    .containsExactly(
                                            "api-key-0",
                                            "api-key-1",
                                            "api-key-2",
                                            "api-key-3",
                                            "api-key-4");
                        });
    }

    /**
     * Tests the critical race condition scenario.
     *
     * <p>This test verifies that @TransactionalEventListener(AFTER_COMMIT) correctly prevents FK
     * constraint violations when:
     *
     * <ol>
     *   <li>A user is created inside a transaction
     *   <li>An audit event with userId is published inside the same transaction
     *   <li>The audit event listener should wait until the transaction commits before persisting
     * </ol>
     *
     * <p>Before fix: @EventListener triggered immediately, causing FK violation because user wasn't
     * committed yet.
     *
     * <p>After fix: @TransactionalEventListener(AFTER_COMMIT) ensures audit runs after commit.
     */
    @Nested
    @DisplayName("Race Condition Prevention")
    class RaceConditionPreventionTests {

        @Test
        @DisplayName("should persist audit log with userId after transaction commits, not during")
        void shouldPersistAuditLogWithUserIdAfterTransactionCommits() {
            UUID[] createdUserId = new UUID[1];

            transactionTemplate.executeWithoutResult(
                _ -> {
                        User user = new User();
                        user.setEmail("race-test@example.com");
                        user.setDisplayName("RaceTestUser");
                        user.setPasswordHash("hashed-password");
                        User savedUser = userRepository.save(user);
                        createdUserId[0] = savedUser.getId();

                        AuditDetails details = AuditDetails.reason("User registered");
                        SecurityAuditEvent event =
                                new SecurityAuditEvent(
                                        savedUser.getId(),
                                        AuditAction.REGISTER_REQUESTED,
                                        ResourceType.USER,
                                        savedUser.getId().toString(),
                                        SecuritySeverity.INFO,
                                        "192.168.1.1",
                                        "TestAgent/1.0",
                                        details);
                        eventPublisher.publishEvent(event);
                    });

            await().atMost(3, TimeUnit.SECONDS)
                    .untilAsserted(
                            () -> {
                                List<AuditLog> logs =
                                        repository.findByUserId(createdUserId[0]);
                                assertThat(logs)
                                        .as(
                                                "Audit log should be persisted AFTER transaction commit, "
                                                        + "not during (would cause FK violation)")
                                        .hasSize(1);

                                AuditLog savedLog = logs.getFirst();
                                assertThat(savedLog.getUserId()).isEqualTo(createdUserId[0]);
                                assertThat(savedLog.getAction())
                                        .isEqualTo(AuditAction.REGISTER_REQUESTED);
                                assertThat(savedLog.getResourceType()).isEqualTo(ResourceType.USER);
                            });
        }

        @Test
        @DisplayName("should NOT persist audit log if transaction rolls back")
        void shouldNotPersistAuditLogIfTransactionRollsBack() {
            UUID nonExistentUserId = UUID.randomUUID();

            transactionTemplate.executeWithoutResult(
                    status -> {
                        User user = new User();
                        user.setEmail("rollback-test@example.com");
                        user.setDisplayName("RollbackUser");
                        user.setPasswordHash("hashed-password");
                        User savedUser = userRepository.save(user);

                        AuditDetails details = AuditDetails.reason("Will be rolled back");
                        SecurityAuditEvent event =
                                new SecurityAuditEvent(
                                        savedUser.getId(),
                                        AuditAction.REGISTER_REQUESTED,
                                        ResourceType.USER,
                                        savedUser.getId().toString(),
                                        SecuritySeverity.INFO,
                                        "192.168.1.1",
                                        "TestAgent/1.0",
                                        details);
                        eventPublisher.publishEvent(event);

                        status.setRollbackOnly();
                    });

            await()
                    .pollDelay(500, TimeUnit.MILLISECONDS)
                    .atMost(2, TimeUnit.SECONDS)
                    .untilAsserted(
                            () -> {
                                List<AuditLog> logs = repository.findAll();
                                assertThat(logs)
                                        .as(
                                                "Audit log should NOT be persisted when transaction rolls back")
                                        .isEmpty();
                            });

            assertThat(userRepository.findById(nonExistentUserId)).isEmpty();
        }

        @Test
        @DisplayName("should handle multiple events with same userId from different transactions")
        void shouldHandleMultipleEventsWithSameUserIdFromDifferentTransactions() {
            UUID[] userId = new UUID[1];

            transactionTemplate.executeWithoutResult(
                _ -> {
                        User user = new User();
                        user.setEmail("multi-tx@example.com");
                        user.setDisplayName("MultiTxUser");
                        user.setPasswordHash("hashed-password");
                        User savedUser = userRepository.save(user);
                        userId[0] = savedUser.getId();

                        SecurityAuditEvent event =
                                new SecurityAuditEvent(
                                        savedUser.getId(),
                                        AuditAction.REGISTER_REQUESTED,
                                        ResourceType.USER,
                                        savedUser.getId().toString(),
                                        SecuritySeverity.INFO,
                                        "192.168.1.1",
                                        "TestAgent/1.0",
                                        AuditDetails.reason("First event"));
                        eventPublisher.publishEvent(event);
                    });

            await().atMost(2, TimeUnit.SECONDS)
                    .untilAsserted(
                            () ->
                                    assertThat(repository.findByUserId(userId[0]))
                                            .as("First event should be persisted")
                                            .hasSize(1));

            transactionTemplate.executeWithoutResult(
                _ -> {
                        SecurityAuditEvent event =
                                new SecurityAuditEvent(
                                        userId[0],
                                        AuditAction.EMAIL_VERIFICATION_SENT,
                                        ResourceType.EMAIL_VERIFICATION,
                                        userId[0].toString(),
                                        SecuritySeverity.INFO,
                                        "192.168.1.1",
                                        "TestAgent/1.0",
                                        AuditDetails.reason("Second event"));
                        eventPublisher.publishEvent(event);
                    });

            await().atMost(2, TimeUnit.SECONDS)
                    .untilAsserted(
                            () ->
                                    assertThat(repository.findByUserId(userId[0]))
                                            .as("Both events should be persisted")
                                            .hasSize(2));
        }
    }
}
