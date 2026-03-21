package com.ahogek.cttserver.mail.service;

import com.ahogek.cttserver.common.config.properties.CttMailProperties;
import com.ahogek.cttserver.mail.entity.MailOutbox;
import com.ahogek.cttserver.mail.enums.MailOutboxStatus;
import com.ahogek.cttserver.mail.repository.MailOutboxRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.time.Instant;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MailOutboxPollerTest {

    private static final int BATCH_SIZE = 50;
    private static final long POLL_INTERVAL_MS = 5000;
    private static final long ZOMBIE_INTERVAL_MS = 120000;

    @Mock private MailOutboxRepository outboxRepository;
    @Mock private MailOutboxProcessor outboxProcessor;

    @Captor private ArgumentCaptor<Pageable> pageableCaptor;
    @Captor private ArgumentCaptor<Instant> instantCaptor;

    private MailOutboxPoller poller;

    @BeforeEach
    void setUp() {
        CttMailProperties properties =
                new CttMailProperties(
                        new CttMailProperties.From("test@localhost", "CTT Test"),
                        new CttMailProperties.Outbox(
                                POLL_INTERVAL_MS, BATCH_SIZE, 300, ZOMBIE_INTERVAL_MS),
                        new CttMailProperties.Retry(10, 2.0, 3600, 5),
                        new CttMailProperties.Frontend("http://localhost:5173"));

        poller = new MailOutboxPoller(outboxRepository, outboxProcessor, properties);
    }

    @Nested
    @DisplayName("pollAndDispatch")
    class PollAndDispatchTests {

        @Test
        @DisplayName("should fetch pending jobs with correct batch size and sorting")
        void shouldFetchWithCorrectParameters() {
            // Given
            when(outboxRepository.findPendingJobs(any(), any()))
                    .thenReturn(Collections.emptyList());

            // When
            poller.pollAndDispatch();

            // Then
            verify(outboxRepository)
                    .findPendingJobs(instantCaptor.capture(), pageableCaptor.capture());

            Pageable pageable = pageableCaptor.getValue();
            assertThat(pageable.getPageSize()).isEqualTo(BATCH_SIZE);
            assertThat(pageable.getPageNumber()).isZero();
            assertThat(pageable.getSort()).isEqualTo(Sort.by("createdAt").ascending());

            Instant capturedNow = instantCaptor.getValue();
            assertThat(capturedNow)
                    .isBeforeOrEqualTo(Instant.now())
                    .isAfter(Instant.now().minusSeconds(5));
        }

        @Test
        @DisplayName("should process all pending messages in batch")
        void shouldProcessAllPendingMessages() {
            // Given
            MailOutbox outbox1 = createOutbox(UUID.randomUUID());
            MailOutbox outbox2 = createOutbox(UUID.randomUUID());
            MailOutbox outbox3 = createOutbox(UUID.randomUUID());
            List<MailOutbox> pendingJobs = List.of(outbox1, outbox2, outbox3);

            when(outboxRepository.findPendingJobs(any(), any())).thenReturn(pendingJobs);

            // When
            poller.pollAndDispatch();

            // Then
            verify(outboxProcessor).processSingleMessage(outbox1);
            verify(outboxProcessor).processSingleMessage(outbox2);
            verify(outboxProcessor).processSingleMessage(outbox3);
        }

        @Test
        @DisplayName("should do nothing when no pending jobs")
        void shouldDoNothing_whenNoPendingJobs() {
            // Given
            when(outboxRepository.findPendingJobs(any(), any()))
                    .thenReturn(Collections.emptyList());

            // When
            poller.pollAndDispatch();

            // Then
            verify(outboxProcessor, times(0)).processSingleMessage(any());
        }

        @Test
        @DisplayName("should continue processing remaining messages when one fails")
        void shouldContinueProcessing_whenOneFails() {
            // Given
            MailOutbox outbox1 = createOutbox(UUID.randomUUID());
            MailOutbox outbox2 = createOutbox(UUID.randomUUID());
            MailOutbox outbox3 = createOutbox(UUID.randomUUID());
            List<MailOutbox> pendingJobs = List.of(outbox1, outbox2, outbox3);

            when(outboxRepository.findPendingJobs(any(), any())).thenReturn(pendingJobs);
            doThrow(new RuntimeException("Processing failed"))
                    .when(outboxProcessor)
                    .processSingleMessage(outbox2);

            // When
            poller.pollAndDispatch();

            // Then
            verify(outboxProcessor).processSingleMessage(outbox1);
            verify(outboxProcessor).processSingleMessage(outbox2);
            verify(outboxProcessor).processSingleMessage(outbox3);
        }

        @Test
        @DisplayName("should handle empty batch gracefully")
        void shouldHandleEmptyBatch() {
            // Given
            when(outboxRepository.findPendingJobs(any(), any()))
                    .thenReturn(Collections.emptyList());

            // When
            poller.pollAndDispatch();

            verify(outboxRepository).findPendingJobs(any(), any());
        }

        @Test
        @DisplayName("should process single message when batch size is 1")
        void shouldProcessSingleMessage_whenBatchSizeIsOne() {
            // Given
            MailOutboxPoller smallBatchPoller = createSmallBatchPoller();

            MailOutbox outbox = createOutbox(UUID.randomUUID());
            when(outboxRepository.findPendingJobs(any(), any())).thenReturn(List.of(outbox));

            // When
            smallBatchPoller.pollAndDispatch();

            // Then
            verify(outboxRepository).findPendingJobs(any(), pageableCaptor.capture());
            assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(1);
            verify(outboxProcessor).processSingleMessage(outbox);
        }

        private MailOutboxPoller createSmallBatchPoller() {
            CttMailProperties smallBatchProperties =
                    new CttMailProperties(
                            new CttMailProperties.From("test@localhost", "CTT Test"),
                            new CttMailProperties.Outbox(
                                    POLL_INTERVAL_MS, 1, 300, ZOMBIE_INTERVAL_MS),
                            new CttMailProperties.Retry(10, 2.0, 3600, 5),
                            new CttMailProperties.Frontend("http://localhost:5173"));

            return new MailOutboxPoller(outboxRepository, outboxProcessor, smallBatchProperties);
        }

        @Test
        @DisplayName("should pass current instant to repository query")
        void shouldPassCurrentInstant() {
            // Given
            when(outboxRepository.findPendingJobs(any(), any()))
                    .thenReturn(Collections.emptyList());
            Instant beforeCall = Instant.now();

            // When
            poller.pollAndDispatch();
            Instant afterCall = Instant.now();

            // Then
            verify(outboxRepository).findPendingJobs(instantCaptor.capture(), any());
            Instant capturedInstant = instantCaptor.getValue();
            assertThat(capturedInstant).isAfterOrEqualTo(beforeCall).isBeforeOrEqualTo(afterCall);
        }
    }

    @Nested
    @DisplayName("compensateStuckJobs")
    class CompensateStuckJobsTests {

        @ParameterizedTest
        @CsvSource({
            "3, should reset stuck records",
            "0, should handle no stuck records"
        })
        @DisplayName("should call resetStuckSendingJobs and handle different return values")
        void shouldResetStuckSendingJobs(int recoveredCount) {
            // Given
            when(outboxRepository.resetStuckSendingJobs(any(Instant.class), any(Instant.class)))
                    .thenReturn(recoveredCount);

            // When
            poller.compensateStuckJobs();

            // Then
            verify(outboxRepository).resetStuckSendingJobs(any(Instant.class), any(Instant.class));
        }
    }

    private MailOutbox createOutbox(UUID id) {
        MailOutbox outbox = new MailOutbox();
        outbox.setId(id);
        outbox.setBizId(UUID.randomUUID());
        outbox.setRecipient("test" + id.toString().substring(0, 4) + "@example.com");
        outbox.setSubject("Test Subject");
        outbox.setBodyHtml("<html><body>Test</body></html>");
        outbox.setBodyText("Test plain text");
        outbox.setBizType("REGISTER_VERIFY");
        outbox.setStatus(MailOutboxStatus.PENDING);
        outbox.setRetryCount(0);
        outbox.setMaxRetries(5);
        return outbox;
    }
}
