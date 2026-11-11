package com.ahogek.cttserver.common.metrics;

import com.ahogek.cttserver.audit.enums.SecuritySeverity;
import com.ahogek.cttserver.common.ratelimit.RateLimitType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;

/**
 * The business signals the application publishes about itself.
 *
 * <p>Every meter lives under the {@code cttserver.business} prefix, so one matcher selects the
 * whole set, and every meter carries at most three tags whose values come from the enums declared
 * here or from an existing domain enum. A tag whose value could grow with the traffic — a user, a
 * device, a session, a URL, an error message — is not a tag: it belongs in a log line, where it
 * costs one document instead of one time series per value.
 *
 * <p>Counters only move forward and are reset by a restart, so a dashboard reads them with {@code
 * rate()} or {@code increase()} rather than as a running total. Gauges are computed when Prometheus
 * scrapes; when their query fails the scrape reports no value instead of the previous one, because
 * a stale queue depth is worse than a missing one.
 *
 * <p>The domain components own the gauges that read their own data — {@code MailOutboxMetrics} and
 * {@code LeaderboardMetrics} — while this class owns the names, so a metric has exactly one
 * definition and one owner.
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-11-11
 */
@Component
public class BusinessMetrics {

    private static final Logger log = LoggerFactory.getLogger(BusinessMetrics.class);

    /** Count of sync operations, tagged by operation, outcome and payload. */
    public static final String SYNC_COUNT = "cttserver.business.sync.count";

    /** Duration of sync operations, tagged like {@link #SYNC_COUNT}. */
    public static final String SYNC_DURATION = "cttserver.business.sync.duration";

    /** Count of requests the rate limiter refused, tagged by limiter scope. */
    public static final String RATE_LIMIT_REJECTIONS = "cttserver.business.ratelimit.rejections";

    /** Outbox entries waiting for delivery, tagged by status. */
    public static final String MAIL_PENDING = "cttserver.business.mail.pending";

    /** Users whose leaderboard scores are stale and waiting for a recompute. */
    public static final String LEADERBOARD_PENDING_RECOMPUTES =
            "cttserver.business.leaderboard.pending.recomputes";

    /** Count of audit events, tagged by severity and by whether the row was stored. */
    public static final String AUDIT_EVENTS = "cttserver.business.audit.events";

    /** Tag key for the sync operation. */
    public static final String TAG_OPERATION = "operation";

    /** Tag key for the result of an operation. */
    public static final String TAG_OUTCOME = "outcome";

    /** Tag key for whether the request carried or applied data. */
    public static final String TAG_PAYLOAD = "payload";

    /** Tag key for the rate limiter scope. */
    public static final String TAG_TYPE = "type";

    /** Tag key for the severity of an audit event. */
    public static final String TAG_SEVERITY = "severity";

    /** Tag key for the mail status. */
    public static final String TAG_STATUS = "status";

    /** Which sync endpoint a measurement belongs to. */
    public enum SyncOperation {
        PUSH,
        PULL
    }

    /** How a sync operation ended. */
    public enum SyncOutcome {
        /** The request completed and the server state was updated or read. */
        SUCCESS,
        /** The request completed, but at least one session was rejected by conflict resolution. */
        CONFLICT,
        /** The request failed; nothing was applied. */
        FAILURE
    }

    /** Whether a sync request carried or applied any data. */
    public enum SyncPayload {
        /** At least one change was returned or applied. */
        CHANGED,
        /** The request carried or produced nothing. */
        EMPTY,
        /** The operation failed before its payload could be known. */
        UNKNOWN
    }

    /** Whether an audit event reached the database. */
    public enum AuditWriteOutcome {
        /** The row was stored. */
        WRITTEN,
        /** The writer refused the row; the event is lost, and the log line says why. */
        DROPPED
    }

    private final MeterRegistry meterRegistry;

    /**
     * Micrometer holds the observed object of a gauge weakly, so a supplier that only the gauge
     * references could be collected and the gauge would report {@code NaN} for the rest of the
     * process lifetime. These references keep the suppliers alive for as long as the registry is.
     */
    private final List<Supplier<Number>> strongReferences = new ArrayList<>();

    public BusinessMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    /**
     * Records one sync operation.
     *
     * @param operation the endpoint the request went to
     * @param outcome how the operation ended
     * @param payload whether the request carried or applied any data
     * @param duration how long the operation took
     */
    public void recordSync(
            SyncOperation operation, SyncOutcome outcome, SyncPayload payload, Duration duration) {
        Tags tags =
                Tags.of(
                        TAG_OPERATION, operation.name(),
                        TAG_OUTCOME, outcome.name(),
                        TAG_PAYLOAD, payload.name());
        Counter.builder(SYNC_COUNT)
                .description("Sync operations by endpoint, outcome and payload")
                .tags(tags)
                .register(meterRegistry)
                .increment();
        Timer.builder(SYNC_DURATION)
                .description("Duration of sync operations by endpoint, outcome and payload")
                .tags(tags)
                .publishPercentileHistogram()
                .register(meterRegistry)
                .record(duration);
    }

    /**
     * Records a request refused by a per-dimension rate limit bucket.
     *
     * @param type the bucket dimension that refused the request
     */
    public void recordRateLimitRejection(RateLimitType type) {
        recordRateLimitRejection(type.name());
    }

    /**
     * Records a request refused by the installation-wide ceiling.
     *
     * <p>The ceiling is not a {@link RateLimitType} — it has no dimension, it caps the instance as
     * a whole — so it gets its own scope value rather than pretending to be one of the buckets.
     */
    public void recordGlobalCeilingRejection() {
        recordRateLimitRejection("GLOBAL");
    }

    private void recordRateLimitRejection(String scope) {
        Counter.builder(RATE_LIMIT_REJECTIONS)
                .description("Requests refused by the rate limiter")
                .tag(TAG_TYPE, scope)
                .register(meterRegistry)
                .increment();
    }

    /**
     * Records an audit event and whether it reached the database.
     *
     * <p>A null severity is tagged {@code UNKNOWN} instead of failing here: the caller records the
     * dropped event from inside its own error handler, where a second exception would replace the
     * original cause and lose the only record that the row never landed.
     *
     * @param severity the severity the event was raised with, possibly null
     * @param outcome whether the row was stored
     */
    public void recordAuditWrite(SecuritySeverity severity, AuditWriteOutcome outcome) {
        Counter.builder(AUDIT_EVENTS)
                .description("Audit events by severity and whether the row was stored")
                .tag(TAG_SEVERITY, severity == null ? "UNKNOWN" : severity.name())
                .tag(TAG_OUTCOME, outcome.name())
                .register(meterRegistry)
                .increment();
    }

    /**
     * Registers a gauge that reads its value when Prometheus scrapes.
     *
     * <p>The supplier runs on the scrape thread, so it must be a cheap, read-only query. A query
     * that throws becomes {@link Double#NaN} for that series only: an exception escaping the
     * supplier would fail the whole scrape, hiding every other series behind one broken query,
     * while {@code NaN} marks just this gauge as unmeasured.
     *
     * @param name the meter name
     * @param description the human-readable description Prometheus exports as {@code # HELP}
     * @param tags the tags and their values, built from this class's tag keys
     * @param value the supplier that reads the current value
     * @return the registered gauge
     */
    public Gauge registerGauge(String name, String description, Tags tags, Supplier<Number> value) {
        Supplier<Number> measured =
                () -> {
                    try {
                        return value.get();
                    } catch (RuntimeException ex) {
                        log.debug("Could not read the gauge {} {}", name, tags, ex);
                        return Double.NaN;
                    }
                };
        strongReferences.add(measured);
        return Gauge.builder(name, measured, supplier -> supplier.get().doubleValue())
                .description(description)
                .tags(tags)
                .register(meterRegistry);
    }
}
