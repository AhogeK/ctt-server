package com.ahogek.cttserver.audit;

import com.ahogek.cttserver.common.context.RequestInfo;
import com.ahogek.cttserver.common.exception.ErrorCode;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityAuditEventTest {

    @Test
    void full_constructor_creates_event_with_all_fields() {
        Instant now = Instant.now();
        RequestInfo requestInfo =
            new RequestInfo(
                "trace-123", "127.0.0.1", "Mozilla/5.0", "/api/login", "POST", null);

        SecurityAuditEvent event =
            new SecurityAuditEvent(ErrorCode.AUTH_001, "Invalid token", requestInfo, now);

        assertThat(event.errorCode()).isEqualTo(ErrorCode.AUTH_001);
        assertThat(event.message()).isEqualTo("Invalid token");
        assertThat(event.requestInfo()).isEqualTo(requestInfo);
        assertThat(event.timestamp()).isEqualTo(now);
    }

    @Test
    void partial_constructor_sets_timestamp_to_now() {
        Instant before = Instant.now();
        RequestInfo requestInfo =
            new RequestInfo(
                "trace-456",
                "192.168.1.1",
                "Chrome/1.0",
                "/api/admin",
                "DELETE",
                "device-123");

        SecurityAuditEvent event =
            new SecurityAuditEvent(ErrorCode.AUTH_009, "Forbidden", requestInfo);

        Instant after = Instant.now();

        assertThat(event.errorCode()).isEqualTo(ErrorCode.AUTH_009);
        assertThat(event.message()).isEqualTo("Forbidden");
        assertThat(event.requestInfo()).isEqualTo(requestInfo);
        assertThat(event.timestamp()).isBetween(before, after);
    }

    @Test
    void record_components_are_accessible() {
        RequestInfo requestInfo =
            new RequestInfo(
                "trace-789",
                "10.0.0.1",
                "PostmanRuntime/7.0",
                "/api/data",
                "GET",
                "device-456");

        SecurityAuditEvent event =
            new SecurityAuditEvent(ErrorCode.RATE_LIMIT_001, "Rate limited", requestInfo);

        assertThat(event.errorCode()).isEqualTo(ErrorCode.RATE_LIMIT_001);
        assertThat(event.message()).isEqualTo("Rate limited");
        assertThat(event.requestInfo().clientIp()).isEqualTo("10.0.0.1");
        assertThat(event.requestInfo().requestUri()).isEqualTo("/api/data");
    }

    @Test
    void null_fields_are_accepted_for_message_and_request_info() {
        Instant now = Instant.now();

        SecurityAuditEvent event = new SecurityAuditEvent(ErrorCode.AUTH_001, null, null, now);

        assertThat(event.errorCode()).isEqualTo(ErrorCode.AUTH_001);
        assertThat(event.message()).isNull();
        assertThat(event.requestInfo()).isNull();
        assertThat(event.timestamp()).isEqualTo(now);
    }

    @Test
    void records_are_equal_when_values_equal() {
        Instant now = Instant.now();
        RequestInfo info1 = new RequestInfo("t1", "127.0.0.1", "UA", "/api", "GET", null);
        RequestInfo info2 = new RequestInfo("t1", "127.0.0.1", "UA", "/api", "GET", null);

        SecurityAuditEvent event1 = new SecurityAuditEvent(ErrorCode.AUTH_001, "msg", info1, now);
        SecurityAuditEvent event2 = new SecurityAuditEvent(ErrorCode.AUTH_001, "msg", info2, now);

        assertThat(event1).isEqualTo(event2).hasSameHashCodeAs(event2);
    }

    @Test
    void records_are_not_equal_when_values_differ() {
        Instant now = Instant.now();
        RequestInfo info = new RequestInfo("t1", "127.0.0.1", "UA", "/api", "GET", null);

        SecurityAuditEvent event1 = new SecurityAuditEvent(ErrorCode.AUTH_001, "msg1", info, now);
        SecurityAuditEvent event2 = new SecurityAuditEvent(ErrorCode.AUTH_009, "msg2", info, now);

        assertThat(event1).isNotEqualTo(event2);
    }
}
