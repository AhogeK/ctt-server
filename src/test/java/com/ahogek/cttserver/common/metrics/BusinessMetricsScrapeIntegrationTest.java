package com.ahogek.cttserver.common.metrics;

import com.ahogek.cttserver.audit.enums.SecuritySeverity;
import com.ahogek.cttserver.common.BaseIntegrationTest;
import com.ahogek.cttserver.common.LoopbackHttpProbe;
import com.ahogek.cttserver.common.metrics.BusinessMetrics.AuditWriteOutcome;
import com.ahogek.cttserver.common.metrics.BusinessMetrics.SyncOperation;
import com.ahogek.cttserver.common.metrics.BusinessMetrics.SyncOutcome;
import com.ahogek.cttserver.common.metrics.BusinessMetrics.SyncPayload;
import com.ahogek.cttserver.common.ratelimit.RateLimitType;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.io.IOException;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@BaseIntegrationTest
class BusinessMetricsScrapeIntegrationTest {

    @Autowired private BusinessMetrics businessMetrics;

    @LocalManagementPort private int managementPort;

    @LocalServerPort private int serverPort;

    @Test
    @DisplayName("should expose HTTP latency buckets once a request reaches the application")
    void shouldExposeHttpLatencyBuckets_whenARequestHitsTheApplication() throws IOException {
        // The p95 SLO alert (deploy/observability/prometheus/rules/alerts.yml) reads
        // http_server_requests_seconds_bucket; Spring Boot 4 publishes those buckets only when
        // the percentiles-histogram for http.server.requests is enabled (application.yaml). A real
        // request must land on the application port before the timer has anything to record.
        HttpResponse<String> response =
                LoopbackHttpProbe.get(serverPort, "/ctt-server/api/v1/config/public");
        assertThat(response.statusCode()).isEqualTo(200);

        String scrape = scrape();
        assertThat(seriesOf(scrape, "http_server_requests_seconds_bucket")).isNotEmpty();
    }

    @Test
    @DisplayName("should expose every business metric family on the management port")
    void shouldExposeEveryFamily_whenTheScrapeRuns() throws IOException {
        businessMetrics.recordSync(
                SyncOperation.PUSH,
                SyncOutcome.SUCCESS,
                SyncPayload.CHANGED,
                Duration.ofMillis(12));
        businessMetrics.recordRateLimitRejection(RateLimitType.USER);
        businessMetrics.recordAuditWrite(SecuritySeverity.INFO, AuditWriteOutcome.WRITTEN);

        String scrape = scrape();

        assertThat(seriesOf(scrape, "cttserver_business_sync_count_total"))
                .anySatisfy(
                        line ->
                                assertThat(line)
                                        .contains("operation=\"PUSH\"")
                                        .contains("outcome=\"SUCCESS\"")
                                        .contains("payload=\"CHANGED\""));
        assertThat(seriesOf(scrape, "cttserver_business_sync_duration_seconds_count")).isNotEmpty();
        assertThat(seriesOf(scrape, "cttserver_business_ratelimit_rejections_total"))
                .anySatisfy(line -> assertThat(line).contains("type=\"USER\""));
        assertThat(seriesOf(scrape, "cttserver_business_audit_events_total"))
                .anySatisfy(
                        line ->
                                assertThat(line)
                                        .contains("severity=\"INFO\"")
                                        .contains("outcome=\"WRITTEN\""));
        assertThat(seriesOf(scrape, "cttserver_business_mail_pending"))
                .anySatisfy(line -> assertThat(line).contains("status=\"PENDING\""));
        assertThat(seriesOf(scrape, "cttserver_business_mail_pending_oldest_seconds")).isNotEmpty();
        assertThat(seriesOf(scrape, "cttserver_business_leaderboard_pending_recomputes"))
                .isNotEmpty();
    }

    @Test
    @DisplayName("should keep every business series within three labels")
    void shouldKeepEverySeriesWithinThreeLabels_whenTheScrapeRuns() throws IOException {
        businessMetrics.recordSync(
                SyncOperation.PULL, SyncOutcome.FAILURE, SyncPayload.UNKNOWN, Duration.ofMillis(3));

        List<String> businessSeries =
                scrape().lines()
                        .filter(line -> line.startsWith("cttserver_business_"))
                        .filter(line -> !line.contains("_bucket{"))
                        .toList();

        assertThat(businessSeries).isNotEmpty();
        assertThat(businessSeries)
                .allSatisfy(line -> assertThat(labelCount(line)).isLessThanOrEqualTo(3));
    }

    private String scrape() throws IOException {
        HttpResponse<String> response =
                LoopbackHttpProbe.get(managementPort, "/actuator/prometheus");
        assertThat(response.statusCode()).isEqualTo(200);
        return response.body();
    }

    private List<String> seriesOf(String scrape, String family) {
        return scrape.lines().filter(line -> line.startsWith(family)).toList();
    }

    private int labelCount(String seriesLine) {
        int open = seriesLine.indexOf('{');
        int close = seriesLine.indexOf('}');
        if (open < 0 || close <= open) {
            return 0;
        }
        return (int) seriesLine.substring(open + 1, close).chars().filter(c -> c == '=').count();
    }
}
