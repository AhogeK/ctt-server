package com.ahogek.cttserver.audit.service;

import com.ahogek.cttserver.TestcontainersConfiguration;
import com.ahogek.cttserver.audit.entity.AuditLog;
import com.ahogek.cttserver.audit.enums.AuditAction;
import com.ahogek.cttserver.audit.enums.ResourceType;
import com.ahogek.cttserver.audit.enums.SecuritySeverity;
import com.ahogek.cttserver.audit.repository.AuditLogRepository;
import com.ahogek.cttserver.common.config.properties.AuditRetentionProperties;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for audit retention against the real database.
 *
 * <p>Unit tests mock the repository, so the batching loop never meets real SQL. This test runs the
 * sweep with the production wiring (repository, transaction manager, PostgreSQL) and checks that a
 * backlog spanning several batches is fully removed while rows inside the period survive.
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-11-11
 */
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@DisplayName("Audit retention")
class AuditRetentionIntegrationTest {

    private static final int RETENTION_DAYS = 180;
    private static final int BATCH_SIZE = 2;
    private static final long HOUR_MS = 3_600_000L;

    @Autowired private AuditLogRepository repository;

    @Autowired private JdbcTemplate jdbcTemplate;

    @Autowired private PlatformTransactionManager transactionManager;

    private AuditRetentionJob job;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
        job =
                new AuditRetentionJob(
                        repository,
                        new AuditRetentionProperties(RETENTION_DAYS, BATCH_SIZE),
                        transactionManager,
                        new MockEnvironment(),
                        HOUR_MS,
                        HOUR_MS);
    }

    @AfterEach
    void cleanUp() {
        repository.deleteAll();
    }

    @Test
    @DisplayName("Should delete every expired row across batches and keep rows inside the period")
    void shouldDeleteEveryExpiredRow_whenTheBacklogSpansSeveralBatches() {
        List<Long> expired =
                IntStream.range(0, 5)
                        .mapToObj(
                                index ->
                                        persistAt(
                                                Instant.now()
                                                        .minus(
                                                                Duration.ofDays(
                                                                        RETENTION_DAYS + 20))))
                        .toList();
        Long insidePeriod = persistAt(Instant.now().minus(Duration.ofDays(100)));
        Long recent = persistAt(Instant.now());

        job.purgeExpired();

        assertThat(repository.findAll())
                .extracting(AuditLog::getId)
                .containsExactlyInAnyOrder(insidePeriod, recent)
                .doesNotContainAnyElementsOf(expired);
    }

    @Test
    @DisplayName("Should keep every row when nothing is older than the retention period")
    void shouldKeepEveryRow_whenNothingIsOlderThanThePeriod() {
        Long recent = persistAt(Instant.now().minus(Duration.ofDays(10)));

        job.purgeExpired();

        assertThat(repository.findAll()).extracting(AuditLog::getId).containsExactly(recent);
    }

    private Long persistAt(Instant createdAt) {
        AuditLog saved =
                repository.save(
                        new AuditLog()
                                .setAction(AuditAction.LOGIN_SUCCESS)
                                .setResourceType(ResourceType.USER)
                                .setSeverity(SecuritySeverity.INFO));
        jdbcTemplate.update(
                "UPDATE audit_logs SET created_at = ? WHERE id = ?",
                Timestamp.from(createdAt),
                saved.getId());
        return saved.getId();
    }
}
