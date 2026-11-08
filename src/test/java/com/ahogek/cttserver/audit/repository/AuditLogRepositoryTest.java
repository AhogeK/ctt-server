package com.ahogek.cttserver.audit.repository;

import com.ahogek.cttserver.audit.entity.AuditLog;
import com.ahogek.cttserver.audit.enums.AuditAction;
import com.ahogek.cttserver.audit.enums.ResourceType;
import com.ahogek.cttserver.audit.enums.SecuritySeverity;
import com.ahogek.cttserver.common.BaseRepositoryTest;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@BaseRepositoryTest
@DisplayName("AuditLogRepository")
class AuditLogRepositoryTest {

    @Autowired private TestEntityManager em;
    @Autowired private AuditLogRepository repository;

    @Test
    @DisplayName("Should delete only rows older than the cutoff")
    void shouldDeleteOnlyOldRows_whenCutoffIsGiven() {
        Instant cutoff = Instant.now().minus(Duration.ofDays(30));
        AuditLog old = persistAt(Instant.now().minus(Duration.ofDays(40)));
        AuditLog recent = persistAt(Instant.now());
        em.clear();

        int deleted = repository.deleteBatchBefore(cutoff, 1);

        assertThat(deleted).isEqualTo(1);
        assertThat(repository.findAll())
                .extracting(AuditLog::getId)
                .containsExactly(recent.getId())
                .doesNotContain(old.getId());
    }

    @Test
    @DisplayName("Should delete every eligible row when the batch is larger than the backlog")
    void shouldDeleteAllEligibleRows_whenBatchIsLargerThanBacklog() {
        Instant cutoff = Instant.now().minus(Duration.ofDays(30));
        persistAt(Instant.now().minus(Duration.ofDays(40)));
        persistAt(Instant.now().minus(Duration.ofDays(40)));
        em.clear();

        assertThat(repository.deleteBatchBefore(cutoff, 10)).isEqualTo(2);
        assertThat(repository.deleteBatchBefore(cutoff, 10)).isZero();
    }

    @Test
    @DisplayName("Should delete exactly the batch size when more rows are eligible")
    void shouldDeleteBatchSizeRows_whenMoreRowsAreEligible() {
        Instant cutoff = Instant.now().minus(Duration.ofDays(30));
        for (int i = 0; i < 3; i++) {
            persistAt(Instant.now().minus(Duration.ofDays(40)));
        }
        em.clear();

        assertThat(repository.deleteBatchBefore(cutoff, 2)).isEqualTo(2);
        assertThat(repository.count()).isEqualTo(1);
    }

    private AuditLog persistAt(Instant createdAt) {
        AuditLog log =
                new AuditLog()
                        .setAction(AuditAction.LOGIN_SUCCESS)
                        .setResourceType(ResourceType.USER)
                        .setSeverity(SecuritySeverity.INFO);
        em.persistAndFlush(log);
        em.getEntityManager()
                .createNativeQuery("UPDATE audit_logs SET created_at = ? WHERE id = ?")
                .setParameter(1, Timestamp.from(createdAt))
                .setParameter(2, log.getId())
                .executeUpdate();
        return log;
    }
}
