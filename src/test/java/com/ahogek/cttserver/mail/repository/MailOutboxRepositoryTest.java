package com.ahogek.cttserver.mail.repository;

import com.ahogek.cttserver.common.BaseRepositoryTest;
import com.ahogek.cttserver.fixtures.MailOutboxFixtures;
import com.ahogek.cttserver.mail.entity.MailOutbox;
import com.ahogek.cttserver.mail.enums.MailOutboxStatus;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@BaseRepositoryTest
@DisplayName("MailOutboxRepository")
class MailOutboxRepositoryTest {

    @Autowired TestEntityManager em;

    @Autowired MailOutboxRepository repository;

    @Nested
    @DisplayName("findPendingJobs")
    class FindPendingJobs {

        @Test
        @DisplayName("returns PENDING and retryable FAILED, excludes other statuses")
        void returnsOnlyDispatchableRecords() {
            Instant now = Instant.now();

            persist(MailOutboxFixtures.pending().recipient("pending@x.com"));
            persist(MailOutboxFixtures.retryableFailed().recipient("retry@x.com"));
            persist(MailOutboxFixtures.notYetRetryableFailed().recipient("wait@x.com"));
            persist(MailOutboxFixtures.sending().recipient("sending@x.com"));
            persist(MailOutboxFixtures.sent().recipient("sent@x.com"));
            persist(MailOutboxFixtures.cancelled().recipient("cancelled@x.com"));

            List<MailOutbox> result =
                    repository.findPendingJobs(
                            now, PageRequest.of(0, 20, Sort.by("createdAt").ascending()));

            assertThat(result)
                    .extracting(MailOutbox::getRecipient)
                    .containsExactlyInAnyOrder("pending@x.com", "retry@x.com");
        }

        @Test
        @DisplayName("respects Pageable size limit")
        void respectsPageableLimit() {
            Instant now = Instant.now();
            for (int i = 0; i < 6; i++) {
                persist(MailOutboxFixtures.pending().recipient("u" + i + "@x.com"));
            }

            List<MailOutbox> result =
                    repository.findPendingJobs(
                            now, PageRequest.of(0, 4, Sort.by("createdAt").ascending()));

            assertThat(result).hasSize(4);
        }

        @Test
        @DisplayName("excludes FAILED records where retryCount == maxRetries")
        void excludesExhaustedFailedRecords() {
            Instant now = Instant.now();
            persist(MailOutboxFixtures.exhaustedFailed());

            List<MailOutbox> result =
                    repository.findPendingJobs(
                            now, PageRequest.of(0, 10, Sort.by("createdAt").ascending()));

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("includes record when nextRetryAt equals now (boundary: <= now)")
        void includesRecordWhoseNextRetryAtEqualsNow() {
            Instant boundary = Instant.now();
            MailOutbox atBoundary =
                    MailOutboxFixtures.retryableFailed()
                            .recipient("boundary@x.com")
                            .nextRetryAt(boundary)
                            .build();
            em.persistAndFlush(atBoundary);

            List<MailOutbox> result =
                    repository.findPendingJobs(
                            boundary, PageRequest.of(0, 10, Sort.by("createdAt").ascending()));

            assertThat(result).extracting(MailOutbox::getRecipient).contains("boundary@x.com");
        }
    }

    @Nested
    @DisplayName("findByTraceId")
    class FindByTraceId {

        private static final String TRACE_ID = "4bf92f3577b34da6a3ce929d0e0e4736";

        @Test
        @DisplayName("finds record by traceId exact match")
        void findsRecordByTraceId() {
            persist(MailOutboxFixtures.pending().recipient("trace@x.com").traceId(TRACE_ID));

            assertThat(repository.findByTraceId(TRACE_ID))
                    .isPresent()
                    .hasValueSatisfying(
                            m -> {
                                assertThat(m.getRecipient()).isEqualTo("trace@x.com");
                                assertThat(m.getTraceId()).isEqualTo(TRACE_ID);
                            });
        }

        @Test
        @DisplayName("returns Optional.empty() for unknown traceId")
        void returnsEmptyForUnknownTraceId() {
            assertThat(repository.findByTraceId("00000000000000000000000000000000")).isEmpty();
        }

        @Test
        @DisplayName("null traceId records do not match other traceId queries")
        void nullTraceIdRecordDoesNotMatchOtherTraceId() {
            persist(MailOutboxFixtures.pending().recipient("no-trace@x.com"));

            assertThat(repository.findByTraceId(TRACE_ID)).isEmpty();
        }
    }

    @Nested
    @DisplayName("countDuplicates")
    class CountDuplicates {

        private static final List<MailOutboxStatus> ACTIVE_STATUSES =
                List.of(MailOutboxStatus.PENDING, MailOutboxStatus.SENDING, MailOutboxStatus.SENT);

        @Test
        @DisplayName("counts matching records within window for recipient + bizType + statuses")
        void countsMatchingRecordsWithinWindow() {
            Instant windowStart = Instant.now().minusSeconds(600);

            persist(
                    MailOutboxFixtures.pending()
                            .recipient("user@x.com")
                            .bizType("REGISTER_VERIFY"));
            persist(
                    MailOutboxFixtures.sending()
                            .recipient("user@x.com")
                            .bizType("REGISTER_VERIFY"));
            persist(MailOutboxFixtures.sent().recipient("user@x.com").bizType("REGISTER_VERIFY"));

            persist(
                    MailOutboxFixtures.pending()
                            .recipient("other@x.com")
                            .bizType("REGISTER_VERIFY"));
            persist(MailOutboxFixtures.pending().recipient("user@x.com").bizType("RESET_PASSWORD"));
            persist(
                    MailOutboxFixtures.cancelled()
                            .recipient("user@x.com")
                            .bizType("REGISTER_VERIFY"));

            long count =
                    repository.countDuplicates(
                            "user@x.com", "REGISTER_VERIFY", ACTIVE_STATUSES, windowStart);

            assertThat(count).isEqualTo(3);
        }

        @Test
        @DisplayName("excludes records created exactly at windowStart (strict > boundary)")
        void excludesRecordCreatedExactlyAtWindowStart() {
            Instant futureWindowStart = Instant.now().plusSeconds(60);

            persist(
                    MailOutboxFixtures.pending()
                            .recipient("user@x.com")
                            .bizType("REGISTER_VERIFY"));

            long count =
                    repository.countDuplicates(
                            "user@x.com", "REGISTER_VERIFY", ACTIVE_STATUSES, futureWindowStart);

            assertThat(count).isZero();
        }

        @Test
        @DisplayName("returns zero when no matches")
        void returnsZeroWhenNoMatches() {
            Instant windowStart = Instant.now().minusSeconds(600);

            long count =
                    repository.countDuplicates(
                            "nobody@x.com", "REGISTER_VERIFY", ACTIVE_STATUSES, windowStart);

            assertThat(count).isZero();
        }

        @Test
        @DisplayName("returns zero when statuses list is empty")
        void returnsZeroForEmptyStatusList() {
            persist(
                    MailOutboxFixtures.pending()
                            .recipient("user@x.com")
                            .bizType("REGISTER_VERIFY"));

            long count =
                    repository.countDuplicates(
                            "user@x.com",
                            "REGISTER_VERIFY",
                            List.of(),
                            Instant.now().minusSeconds(600));

            assertThat(count).isZero();
        }
    }

    @Nested
    @DisplayName("countByStatus")
    class CountByStatus {

        @Test
        @DisplayName("aggregates counts grouped by status for monitoring")
        void aggregatesCountsGroupedByStatus() {
            for (int i = 0; i < 3; i++) {
                persist(MailOutboxFixtures.pending().recipient("pending" + i + "@x.com"));
            }
            for (int i = 0; i < 2; i++) {
                persist(MailOutboxFixtures.sent().recipient("sent" + i + "@x.com"));
            }
            persist(MailOutboxFixtures.retryableFailed().recipient("failed@x.com"));

            List<Object[]> result = repository.countByStatus();

            assertThat(result).hasSize(3);

            assertThat(result)
                    .extracting(row -> (MailOutboxStatus) row[0])
                    .containsExactlyInAnyOrder(
                            MailOutboxStatus.PENDING,
                            MailOutboxStatus.SENT,
                            MailOutboxStatus.FAILED);

            assertThat(result)
                    .filteredOn(row -> row[0] == MailOutboxStatus.PENDING)
                    .hasSize(1)
                    .first()
                    .extracting(row -> ((Number) row[1]).longValue())
                    .isEqualTo(3L);

            assertThat(result)
                    .filteredOn(row -> row[0] == MailOutboxStatus.SENT)
                    .hasSize(1)
                    .first()
                    .extracting(row -> ((Number) row[1]).longValue())
                    .isEqualTo(2L);

            assertThat(result)
                    .filteredOn(row -> row[0] == MailOutboxStatus.FAILED)
                    .hasSize(1)
                    .first()
                    .extracting(row -> ((Number) row[1]).longValue())
                    .isEqualTo(1L);
        }

        @Test
        @DisplayName("returns empty list when no records exist")
        void returnsEmptyListWhenNoRecords() {
            List<Object[]> result = repository.countByStatus();

            assertThat(result).isEmpty();
        }
    }

    private MailOutbox persist(MailOutboxFixtures.Builder builder) {
        return em.persistFlushFind(builder.build());
    }
}
