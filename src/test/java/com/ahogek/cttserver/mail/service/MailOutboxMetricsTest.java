package com.ahogek.cttserver.mail.service;

import com.ahogek.cttserver.common.metrics.BusinessMetrics;
import com.ahogek.cttserver.mail.enums.MailOutboxStatus;
import com.ahogek.cttserver.mail.repository.MailOutboxRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MailOutboxMetricsTest {

    @Mock private MailOutboxRepository mailOutboxRepository;

    private SimpleMeterRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
    }

    @Test
    @DisplayName("should publish the backlog statuses on the business gauge")
    void shouldPublishBacklogStatuses_whenTheGaugeIsRead() {
        when(mailOutboxRepository.countByStatus(MailOutboxStatus.PENDING)).thenReturn(3L);
        when(mailOutboxRepository.countByStatus(MailOutboxStatus.FAILED)).thenReturn(1L);
        when(mailOutboxRepository.countByStatus(MailOutboxStatus.CANCELLED)).thenReturn(2L);
        new MailOutboxMetrics(mailOutboxRepository, new BusinessMetrics(registry));

        assertThat(gaugeValue(MailOutboxStatus.PENDING)).isEqualTo(3.0);
        assertThat(gaugeValue(MailOutboxStatus.FAILED)).isEqualTo(1.0);
        assertThat(gaugeValue(MailOutboxStatus.CANCELLED)).isEqualTo(2.0);
    }

    @Test
    @DisplayName("should report NaN for one status instead of failing the scrape")
    void shouldReportNaN_whenOneCountQueryFails() {
        when(mailOutboxRepository.countByStatus(MailOutboxStatus.PENDING))
                .thenThrow(new IllegalStateException("database unavailable"));
        when(mailOutboxRepository.countByStatus(MailOutboxStatus.FAILED)).thenReturn(2L);
        new MailOutboxMetrics(mailOutboxRepository, new BusinessMetrics(registry));

        assertThat(gaugeValue(MailOutboxStatus.PENDING)).isNaN();
        assertThat(gaugeValue(MailOutboxStatus.FAILED)).isEqualTo(2.0);
    }

    private double gaugeValue(MailOutboxStatus status) {
        return registry.get(BusinessMetrics.MAIL_PENDING)
                .tag(BusinessMetrics.TAG_STATUS, status.name())
                .gauge()
                .value();
    }
}
