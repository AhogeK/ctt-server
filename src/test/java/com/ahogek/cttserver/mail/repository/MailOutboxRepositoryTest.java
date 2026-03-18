package com.ahogek.cttserver.mail.repository;

import com.ahogek.cttserver.common.BaseIntegrationTest;
import com.ahogek.cttserver.mail.entity.MailOutbox;
import com.ahogek.cttserver.mail.enums.MailOutboxStatus;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@BaseIntegrationTest
@DisplayName("MailOutboxRepository Integration Tests")
class MailOutboxRepositoryTest {

    @Autowired
    private MailOutboxRepository repository;

    @BeforeEach
    void clearOutbox() {
        repository.deleteAll();
    }

    @Test
    @DisplayName("findPendingJobs should return PENDING and retryable FAILED records")
    void findPendingJobs_shouldReturnPendingAndRetryableFailedRecords() {
        Instant now = Instant.now();

        MailOutbox pending = createOutbox("pending@test.com", MailOutboxStatus.PENDING, null);
        MailOutbox failedRetryable = createOutbox("failed-retry@test.com", MailOutboxStatus.FAILED, now.minusSeconds(10));
        MailOutbox failedNotYet = createOutbox("failed-wait@test.com", MailOutboxStatus.FAILED, now.plusSeconds(10));
        MailOutbox sending = createOutbox("sending@test.com", MailOutboxStatus.SENDING, null);
        MailOutbox sent = createOutbox("sent@test.com", MailOutboxStatus.SENT, null);

        repository.saveAll(List.of(pending, failedRetryable, failedNotYet, sending, sent));

        List<MailOutbox> result = repository.findPendingJobs(now, PageRequest.of(0, 10, Sort.by("createdAt").ascending()));

        assertThat(result)
                .as("Should return PENDING and retryable FAILED records only")
                .hasSize(2)
                .extracting(MailOutbox::getRecipient)
                .containsExactlyInAnyOrder("pending@test.com", "failed-retry@test.com");
    }

    @Test
    @DisplayName("findPendingJobs should respect Pageable limit")
    void findPendingJobs_shouldRespectPageableLimit() {
        Instant now = Instant.now();

        for (int i = 0; i < 5; i++) {
            repository.save(createOutbox("user" + i + "@test.com", MailOutboxStatus.PENDING, null));
        }

        List<MailOutbox> result = repository.findPendingJobs(now, PageRequest.of(0, 3, Sort.by("createdAt").ascending()));

        assertThat(result)
                .as("Should return only 3 records as per Pageable limit")
                .hasSize(3);
    }

    @Test
    @DisplayName("findPendingJobs should not return FAILED records with exhausted retries")
    void findPendingJobs_shouldNotReturnExhaustedFailedRecords() {
        Instant now = Instant.now();

        MailOutbox exhausted = createOutbox("exhausted@test.com", MailOutboxStatus.FAILED, now.minusSeconds(10));
        exhausted.setRetryCount(3);
        exhausted.setMaxRetries(3);
        repository.save(exhausted);

        List<MailOutbox> result = repository.findPendingJobs(now, PageRequest.of(0, 10, Sort.by("createdAt").ascending()));

        assertThat(result)
                .as("Should not return FAILED records with exhausted retries")
                .isEmpty();
    }

    @Test
    @DisplayName("findByTraceId should find record by trace ID")
    void findByTraceId_shouldFindRecordByTraceId() {
        MailOutbox outbox = createOutbox("trace@test.com", MailOutboxStatus.PENDING, null);
        String traceId = "1234567890abcdef1234567890abcdef";
        outbox.setTraceId(traceId);
        repository.save(outbox);

        var result = repository.findByTraceId(traceId);

        assertThat(result)
                .as("Should find record by trace ID")
                .isPresent()
                .hasValueSatisfying(m -> assertThat(m.getRecipient()).isEqualTo("trace@test.com"));
    }

    @Test
    @DisplayName("findByTraceId should return empty for non-existent trace ID")
    void findByTraceId_shouldReturnEmptyForNonExistentTraceId() {
        var result = repository.findByTraceId("nonexistent1234567890abcdef12");

        assertThat(result)
                .as("Should return empty for non-existent trace ID")
                .isEmpty();
    }

    @Test
    @DisplayName("countDuplicates should count records matching recipient, bizType, statuses and window")
    void countDuplicates_shouldCountMatchingRecords() {
        Instant now = Instant.now();
        Instant windowStart = now.minusSeconds(600);

        MailOutbox match1 = createOutbox("user@test.com", MailOutboxStatus.PENDING, null, now.minusSeconds(300));
        match1.setBizType("REGISTER_VERIFY");

        MailOutbox match2 = createOutbox("user@test.com", MailOutboxStatus.SENDING, null, now.minusSeconds(200));
        match2.setBizType("REGISTER_VERIFY");

        MailOutbox match3 = createOutbox("user@test.com", MailOutboxStatus.SENT, null, now.minusSeconds(100));
        match3.setBizType("REGISTER_VERIFY");

        MailOutbox wrongEmail = createOutbox("other@test.com", MailOutboxStatus.PENDING, null, now.minusSeconds(50));
        wrongEmail.setBizType("REGISTER_VERIFY");

        MailOutbox wrongType = createOutbox("user@test.com", MailOutboxStatus.PENDING, null, now.minusSeconds(50));
        wrongType.setBizType("RESET_PASSWORD");

        MailOutbox oldRecord = createOutbox("user@test.com", MailOutboxStatus.PENDING, null, now.minusSeconds(700));
        oldRecord.setBizType("REGISTER_VERIFY");

        repository.saveAll(List.of(match1, match2, match3, wrongEmail, wrongType, oldRecord));

        List<MailOutboxStatus> statuses = List.of(MailOutboxStatus.PENDING, MailOutboxStatus.SENDING, MailOutboxStatus.SENT);
        long count = repository.countDuplicates("user@test.com", "REGISTER_VERIFY", statuses, windowStart);

        assertThat(count)
                .as("Should count only records matching all criteria within the window")
                .isEqualTo(3);
    }

    @Test
    @DisplayName("countDuplicates should return zero when no matches")
    void countDuplicates_shouldReturnZeroWhenNoMatches() {
        Instant now = Instant.now();
        Instant windowStart = now.minusSeconds(600);

        List<MailOutboxStatus> statuses = List.of(MailOutboxStatus.PENDING, MailOutboxStatus.SENDING, MailOutboxStatus.SENT);
        long count = repository.countDuplicates("nonexistent@test.com", "REGISTER_VERIFY", statuses, windowStart);

        assertThat(count)
                .as("Should return zero when no matches")
                .isZero();
    }

    @Test
    @DisplayName("countByStatus should aggregate counts grouped by status")
    void countByStatus_shouldAggregateCountsGroupedByStatus() {
        for (int i = 0; i < 3; i++) {
            repository.save(createOutbox("pending" + i + "@test.com", MailOutboxStatus.PENDING, null));
        }
        for (int i = 0; i < 2; i++) {
            repository.save(createOutbox("sent" + i + "@test.com", MailOutboxStatus.SENT, null));
        }
        repository.save(createOutbox("failed@test.com", MailOutboxStatus.FAILED, null));

        List<Object[]> result = repository.countByStatus();

        assertThat(result)
                .as("Should return counts grouped by status")
                .hasSize(3);

        assertThat(result)
                .extracting(row -> (MailOutboxStatus) row[0])
                .containsExactlyInAnyOrder(MailOutboxStatus.PENDING, MailOutboxStatus.SENT, MailOutboxStatus.FAILED);

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

    private MailOutbox createOutbox(String recipient, MailOutboxStatus status, Instant nextRetryAt) {
        return createOutbox(recipient, status, nextRetryAt, Instant.now());
    }

    private MailOutbox createOutbox(String recipient, MailOutboxStatus status, Instant nextRetryAt, Instant createdAt) {
        MailOutbox outbox = new MailOutbox();
        outbox.setRecipient(recipient);
        outbox.setSubject("Test Email");
        outbox.setBodyHtml("<html><body>Test</body></html>");
        outbox.setBodyText("Test");
        outbox.setTemplateCode("TEST_TEMPLATE");
        outbox.setStatus(status);
        outbox.setNextRetryAt(nextRetryAt);
        outbox.setBizType("TEST_TYPE");
        outbox.setCreatedAt(createdAt);
        outbox.setUpdatedAt(createdAt);
        return outbox;
    }
}
