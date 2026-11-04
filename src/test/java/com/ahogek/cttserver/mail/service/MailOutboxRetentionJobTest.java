package com.ahogek.cttserver.mail.service;

import com.ahogek.cttserver.mail.enums.MailOutboxStatus;
import com.ahogek.cttserver.mail.repository.MailOutboxRepository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Mail outbox retention")
class MailOutboxRetentionJobTest {

    @Mock private MailOutboxRepository repository;

    @Test
    @DisplayName("Should remove only terminal rows older than seven days")
    void shouldPurgeTerminalRowsOlderThanRetention() {
        when(repository.deleteTerminalBefore(
                        eq(List.of(MailOutboxStatus.SENT, MailOutboxStatus.CANCELLED)), any()))
                .thenReturn(2);
        MailOutboxRetentionJob job = new MailOutboxRetentionJob(repository);

        job.purgeExpired();

        ArgumentCaptor<Instant> cutoff = ArgumentCaptor.forClass(Instant.class);
        verify(repository)
                .deleteTerminalBefore(
                        eq(List.of(MailOutboxStatus.SENT, MailOutboxStatus.CANCELLED)),
                        cutoff.capture());
        assertThat(cutoff.getValue()).isBefore(Instant.now().minusSeconds(6 * 24 * 3600));
    }
}
