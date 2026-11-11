package com.ahogek.cttserver.leaderboard.service;

import com.ahogek.cttserver.common.metrics.BusinessMetrics;

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
class LeaderboardMetricsTest {

    @Mock private LeaderboardRecomputeQueue leaderboardRecomputeQueue;

    private SimpleMeterRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
    }

    @Test
    @DisplayName("should publish the dirty-set depth on the business gauge")
    void shouldPublishTheQueueDepth_whenTheGaugeIsRead() {
        when(leaderboardRecomputeQueue.pendingCount()).thenReturn(7L);
        new LeaderboardMetrics(leaderboardRecomputeQueue, new BusinessMetrics(registry));

        assertThat(registry.get(BusinessMetrics.LEADERBOARD_PENDING_RECOMPUTES).gauge().value())
                .isEqualTo(7.0);
    }

    @Test
    @DisplayName("should report NaN instead of failing the scrape when Redis is unavailable")
    void shouldReportNaN_whenTheQueueCannotBeRead() {
        when(leaderboardRecomputeQueue.pendingCount())
                .thenThrow(new IllegalStateException("redis unavailable"));
        new LeaderboardMetrics(leaderboardRecomputeQueue, new BusinessMetrics(registry));

        assertThat(registry.get(BusinessMetrics.LEADERBOARD_PENDING_RECOMPUTES).gauge().value())
                .isNaN();
    }
}
