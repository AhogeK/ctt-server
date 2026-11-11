package com.ahogek.cttserver.leaderboard.service;

import com.ahogek.cttserver.common.metrics.BusinessMetrics;

import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.Tags;

/**
 * Publishes the leaderboard recompute backlog as a gauge.
 *
 * <p>The queue coalesces marks per user, so the value is a backlog depth rather than a request
 * count: it grows with the users whose scores are stale and shrinks as the scheduled drain
 * recomputes them. A backlog that grows without draining means the drain is not running, which is
 * the failure this series exists to surface.
 *
 * <p>The value is read when Prometheus scrapes. A failed Redis read reports {@code NaN} instead of
 * the previous value, so an unreachable Redis stays visible in the series instead of freezing the
 * last healthy number.
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-11-11
 */
@Component
public class LeaderboardMetrics {

    public LeaderboardMetrics(LeaderboardRecomputeQueue queue, BusinessMetrics metrics) {
        metrics.registerGauge(
                BusinessMetrics.LEADERBOARD_PENDING_RECOMPUTES,
                "Users whose leaderboard scores are stale and waiting for a recompute",
                Tags.empty(),
                queue::pendingCount);
    }
}
