package com.ahogek.cttserver.mail.service;

import com.ahogek.cttserver.common.metrics.BusinessMetrics;
import com.ahogek.cttserver.mail.enums.MailOutboxStatus;
import com.ahogek.cttserver.mail.repository.MailOutboxRepository;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import io.micrometer.core.instrument.Tags;

/**
 * Publishes the outbox backlog as a gauge, one series per status that needs attention.
 *
 * <p>Three statuses are exported. {@code PENDING} rows have not been attempted yet, {@code FAILED}
 * rows failed an attempt and are scheduled for a retry, and {@code CANCELLED} rows exhausted their
 * retries — {@code markFailed} cancels them instead of scheduling another attempt — so that last
 * series is the dead-letter count. {@code SENDING} is deliberately not exported: it is a
 * single-attempt state that flaps during every delivery, and a worker that dies mid-attempt returns
 * its rows to {@code PENDING} through the zombie reset, so the backlog never hides them for longer
 * than that interval.
 *
 * <p>The value is counted when Prometheus scrapes, so it reflects the queue at that moment instead
 * of a counter that drifts from the table. A failed query reports {@code NaN} rather than the last
 * successful value: a stale backlog depth hides exactly the incident the gauge exists to catch.
 *
 * <p>A second gauge reports the age in seconds of the oldest entry waiting to be dispatched, using
 * the same predicate as {@link MailOutboxRepository#findOldestAwaitingDispatchCreatedAt}. An empty
 * queue reports {@code 0} — nothing has been waiting — so a non-finite value can only mean the
 * query failed, which {@link BusinessMetrics#registerGauge} turns into {@code NaN}. The two states
 * must stay distinguishable: the alert selects {@code NaN} with {@code age != age} as well as
 * firing on a large age, and an unreadable queue is exactly the case where silence would hurt.
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-11-11
 */
@Component
public class MailOutboxMetrics {

    /** The statuses an operator can act on; everything else is transient or terminal history. */
    private static final List<MailOutboxStatus> EXPORTED =
            List.of(MailOutboxStatus.PENDING, MailOutboxStatus.FAILED, MailOutboxStatus.CANCELLED);

    public MailOutboxMetrics(MailOutboxRepository repository, BusinessMetrics metrics) {
        for (MailOutboxStatus status : EXPORTED) {
            metrics.registerGauge(
                    BusinessMetrics.MAIL_PENDING,
                    "Mail outbox entries by backlog status",
                    Tags.of(BusinessMetrics.TAG_STATUS, status.name()),
                    () -> repository.countByStatus(status));
        }
        metrics.registerGauge(
                BusinessMetrics.MAIL_PENDING_OLDEST_SECONDS,
                "Seconds since the oldest outbox entry awaiting dispatch was created",
                Tags.empty(),
                () -> oldestAwaitingDispatchAge(repository));
    }

    private static double oldestAwaitingDispatchAge(MailOutboxRepository repository) {
        Instant now = Instant.now();
        Optional<Instant> oldest = repository.findOldestAwaitingDispatchCreatedAt(now);
        return oldest.map(
                        createdAt ->
                                (double) Math.max(0, Duration.between(createdAt, now).getSeconds()))
                .orElse(0.0);
    }
}
