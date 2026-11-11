package com.ahogek.cttserver.common.metrics;

import com.ahogek.cttserver.audit.enums.SecuritySeverity;
import com.ahogek.cttserver.common.metrics.BusinessMetrics.AuditWriteOutcome;
import com.ahogek.cttserver.common.metrics.BusinessMetrics.SyncOperation;
import com.ahogek.cttserver.common.metrics.BusinessMetrics.SyncOutcome;
import com.ahogek.cttserver.common.metrics.BusinessMetrics.SyncPayload;
import com.ahogek.cttserver.common.ratelimit.RateLimitType;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import static org.assertj.core.api.Assertions.assertThat;

class BusinessMetricsTest {

    private SimpleMeterRegistry registry;
    private BusinessMetrics metrics;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        metrics = new BusinessMetrics(registry);
    }

    @Test
    @DisplayName("should record counter and timer when a sync operation finishes")
    void shouldRecordBothMeters_whenSyncOperationFinishes() {
        metrics.recordSync(
                SyncOperation.PUSH,
                SyncOutcome.CONFLICT,
                SyncPayload.EMPTY,
                Duration.ofMillis(120));

        assertThat(counter(BusinessMetrics.SYNC_COUNT).count()).isEqualTo(1.0);
        assertThat(
                        registry.get(BusinessMetrics.SYNC_DURATION)
                                .tag(BusinessMetrics.TAG_OPERATION, "PUSH")
                                .tag(BusinessMetrics.TAG_OUTCOME, "CONFLICT")
                                .tag(BusinessMetrics.TAG_PAYLOAD, "EMPTY")
                                .timer()
                                .count())
                .isEqualTo(1);
    }

    @Test
    @DisplayName("should keep the outcome and payload labels on the sync counter")
    void shouldKeepLabels_whenTheSyncCounterIsRecorded() {
        metrics.recordSync(
                SyncOperation.PULL, SyncOutcome.SUCCESS, SyncPayload.CHANGED, Duration.ofMillis(5));

        Meter meter = counter(BusinessMetrics.SYNC_COUNT);
        assertThat(meter.getId().getTag(BusinessMetrics.TAG_OPERATION)).isEqualTo("PULL");
        assertThat(meter.getId().getTag(BusinessMetrics.TAG_OUTCOME)).isEqualTo("SUCCESS");
        assertThat(meter.getId().getTag(BusinessMetrics.TAG_PAYLOAD)).isEqualTo("CHANGED");
    }

    @Test
    @DisplayName("should count rejections per dimension and the installation ceiling separately")
    void shouldCountRejectionsPerScope_whenBothReject() {
        metrics.recordRateLimitRejection(RateLimitType.USER);
        metrics.recordGlobalCeilingRejection();
        metrics.recordGlobalCeilingRejection();

        assertThat(rejectionCount("USER")).isEqualTo(1.0);
        assertThat(rejectionCount("GLOBAL")).isEqualTo(2.0);
    }

    @Test
    @DisplayName("should count audit events per severity and outcome")
    void shouldCountAuditEvents_whenTheEventIsRecorded() {
        metrics.recordAuditWrite(SecuritySeverity.WARNING, AuditWriteOutcome.DROPPED);

        assertThat(
                        registry.get(BusinessMetrics.AUDIT_EVENTS)
                                .tag(BusinessMetrics.TAG_SEVERITY, "WARNING")
                                .tag(BusinessMetrics.TAG_OUTCOME, "DROPPED")
                                .counter()
                                .count())
                .isEqualTo(1.0);
    }

    @Test
    @DisplayName("should tag an unknown severity when the event carries none")
    void shouldTagUnknownSeverity_whenTheEventCarriesNone() {
        metrics.recordAuditWrite(null, AuditWriteOutcome.DROPPED);

        assertThat(
                        registry.get(BusinessMetrics.AUDIT_EVENTS)
                                .tag(BusinessMetrics.TAG_SEVERITY, "UNKNOWN")
                                .tag(BusinessMetrics.TAG_OUTCOME, "DROPPED")
                                .counter()
                                .count())
                .isEqualTo(1.0);
    }

    @Test
    @DisplayName("should report the supplier value when a gauge is registered")
    void shouldReportTheSupplierValue_whenAGaugeIsRegistered() {
        metrics.registerGauge(
                "cttserver.business.test.gauge", "Test gauge", Tags.of("state", "ready"), () -> 42);

        assertThat(
                        registry.get("cttserver.business.test.gauge")
                                .tag("state", "ready")
                                .gauge()
                                .value())
                .isEqualTo(42.0);
    }

    @Test
    @DisplayName("should report NaN instead of failing the scrape when a gauge supplier throws")
    void shouldReportNaN_whenTheGaugeSupplierThrows() {
        metrics.registerGauge(
                "cttserver.business.test.failing-gauge",
                "Test gauge",
                Tags.empty(),
                () -> {
                    throw new IllegalStateException("database unavailable");
                });

        assertThat(registry.get("cttserver.business.test.failing-gauge").gauge().value()).isNaN();
    }

    @Test
    @DisplayName("should keep every business meter within three tags")
    void shouldKeepEveryBusinessMeterWithinThreeTags_whenEveryMeterIsRecorded() {
        metrics.recordSync(
                SyncOperation.PUSH, SyncOutcome.SUCCESS, SyncPayload.CHANGED, Duration.ofMillis(1));
        metrics.recordSync(
                SyncOperation.PULL, SyncOutcome.FAILURE, SyncPayload.UNKNOWN, Duration.ofMillis(1));
        metrics.recordRateLimitRejection(RateLimitType.IP);
        metrics.recordGlobalCeilingRejection();
        metrics.recordAuditWrite(SecuritySeverity.CRITICAL, AuditWriteOutcome.WRITTEN);
        metrics.registerGauge(
                "cttserver.business.test.gauge", "Test gauge", Tags.of("state", "x"), () -> 1);

        List<String> overLimit =
                registry.getMeters().stream()
                        .filter(meter -> meter.getId().getTags().size() > 3)
                        .map(meter -> meter.getId().getName() + meter.getId().getTags())
                        .toList();

        assertThat(overLimit).isEmpty();
        assertThat(registry.getMeters()).isNotEmpty();
    }

    private Counter counter(String name) {
        return registry.get(name).counter();
    }

    private double rejectionCount(String scope) {
        return registry.get(BusinessMetrics.RATE_LIMIT_REJECTIONS)
                .tag(BusinessMetrics.TAG_TYPE, scope)
                .counter()
                .count();
    }
}
