package com.ahogek.cttserver.audit;

import com.ahogek.cttserver.TestcontainersConfiguration;
import com.ahogek.cttserver.audit.entity.AuditLog;
import com.ahogek.cttserver.audit.enums.AuditAction;
import com.ahogek.cttserver.audit.enums.ResourceType;
import com.ahogek.cttserver.audit.enums.SecuritySeverity;
import com.ahogek.cttserver.audit.repository.AuditLogRepository;

import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;
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
 *   <li>Full end-to-end: Event publish → Async handle → Database persist
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

    private static @NonNull SecurityAuditEvent getSecurityAuditEvent() {
        Map<String, Object> details = Map.of("attemptCount", 3, "reason", "Invalid credentials");

        return new SecurityAuditEvent(
                null,
                AuditAction.LOGIN_FAILED,
                ResourceType.USER_ACCOUNT,
                "user-123",
                SecuritySeverity.WARNING,
                "192.168.1.100",
                "Mozilla/5.0 (Test)",
                details);
    }

    @BeforeEach
    void setUp() {
        repository.deleteAll();
    }

    @Test
    @DisplayName("Should persist audit log asynchronously with full context")
    void shouldPersistAuditLogAsyncWhenEventPublished() {
        // Given: Construct a security audit event with full context
        // Note: userId is null to avoid FK constraint violation (users table)
        SecurityAuditEvent event = getSecurityAuditEvent();

        // When: Publish event (async processing)
        eventPublisher.publishEvent(event);

        // Then: Use Awaitility to wait for async operation and verify database state
        await().atMost(3, TimeUnit.SECONDS)
                .untilAsserted(
                        () -> {
                            List<AuditLog> logs = repository.findAll();
                            assertThat(logs).hasSize(1);

                            AuditLog savedLog = logs.getFirst();

                            // Verify basic field mapping
                            assertThat(savedLog.getUserId()).isNull();
                            assertThat(savedLog.getAction()).isEqualTo(AuditAction.LOGIN_FAILED);
                            assertThat(savedLog.getResourceType())
                                    .isEqualTo(ResourceType.USER_ACCOUNT);
                            assertThat(savedLog.getResourceId()).isEqualTo("user-123");
                            assertThat(savedLog.getSeverity()).isEqualTo(SecuritySeverity.WARNING);

                            // Verify IP address and user agent storage
                            assertThat(savedLog.getIpAddress()).isEqualTo("192.168.1.100");
                            assertThat(savedLog.getUserAgent()).isEqualTo("Mozilla/5.0 (Test)");

                            // Verify JSONB deserialization (critical test point)
                            Map<String, Object> savedDetails = savedLog.getDetails();
                            assertThat(savedDetails)
                                    .containsEntry("attemptCount", 3)
                                    .containsEntry("reason", "Invalid credentials");

                            // Verify automatic timestamp generation
                            assertThat(savedLog.getCreatedAt()).isNotNull();
                        });
    }

    @Test
    @DisplayName("Should persist minimal event with null values using defaults")
    void shouldPersistMinimalEventWithNullValues() {
        // Given: Minimal event with nulls
        SecurityAuditEvent event =
                new SecurityAuditEvent(
                        null,
                        AuditAction.RATE_LIMIT_EXCEEDED,
                        ResourceType.API_KEY,
                        null,
                        SecuritySeverity.CRITICAL,
                        null,
                        null,
                        Map.of("limit", 100));

        // When: Publish event
        eventPublisher.publishEvent(event);

        // Then: Verify persistence with defaults for nulls
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

                            // Uses UNKNOWN defaults when null
                            assertThat(savedLog.getIpAddress()).isEqualTo("UNKNOWN");
                            assertThat(savedLog.getUserAgent()).isEqualTo("UNKNOWN");
                            assertThat(savedLog.getResourceId()).isNull();

                            // Verify JSONB
                            Map<String, Object> savedDetails = savedLog.getDetails();
                            assertThat(savedDetails).containsEntry("limit", 100);
                        });
    }

    @Test
    @DisplayName("Should persist multiple events asynchronously in order")
    void shouldPersistMultipleEventsInOrder() {
        // Given: Publish multiple events
        for (int i = 0; i < 5; i++) {
            SecurityAuditEvent event =
                    new SecurityAuditEvent(
                            null,
                            AuditAction.API_KEY_CREATED,
                            ResourceType.API_KEY,
                            "api-key-" + i,
                            SecuritySeverity.INFO,
                            "10.0.0." + i,
                            "Agent" + i,
                            Map.of("index", i));
            eventPublisher.publishEvent(event);
        }

        // Then: Wait and verify all events persisted
        await().atMost(5, TimeUnit.SECONDS)
                .untilAsserted(
                        () -> {
                            List<AuditLog> logs = repository.findAll();
                            assertThat(logs).hasSize(5);

                            // Verify each record has unique resourceId
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
}
