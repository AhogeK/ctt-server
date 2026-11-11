package com.ahogek.cttserver.mail.service;

import com.ahogek.cttserver.common.metrics.BusinessMetrics;
import com.ahogek.cttserver.mail.enums.MailOutboxStatus;
import com.ahogek.cttserver.mail.repository.MailOutboxRepository;

import org.springframework.stereotype.Component;

import java.util.List;

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
    }
}
