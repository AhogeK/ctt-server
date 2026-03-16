# Audit vs Business Logging Boundary Specification

> Cloud-native architecture guideline for distinguishing between compliance-grade audit trails and operational observability logs.

## Decision Matrix

| Dimension            | Audit Log                                               | Business Log                                                        |
|----------------------|---------------------------------------------------------|---------------------------------------------------------------------|
| **Primary Audience** | Security experts, legal compliance, risk engines        | Developers, SRE, APM monitoring                                     |
| **Lifecycle**        | Long-term archival (1-5 years, potentially permanent)   | Short-term (7-30 days)                                              |
| **Storage Medium**   | Relational database (PostgreSQL), immutable append-only | Text streams, full-text search (ELK, Loki)                          |
| **Core Concern**     | **Accountability**: Who, when, to what, did what        | **Observability**: State machine transitions, latency, stack traces |
| **Fault Tolerance**  | Extremely high, loss may cause compliance disaster      | Acceptable to drop under extreme load via degradation               |

## Golden Rules

### Must Use `AuditLogService` (Database Persistence)

Any action triggering **Control Plane** changes or crossing trust boundaries:

- **Identity & Credential Lifecycle**: Registration, logout, password change, reset
- **Access Control Transfer**: Create/revoke API Key, bind/unbind new device
- **Security Violations**: Consecutive password failures, rate limit triggers, 401/403 intercepts
- **High-Value Resource Destruction**: Account deletion, permanent core asset removal

### Use `log.atInfo()` (ELK/Loki Observability)

**Data Plane** routine CRUD and middleware states:

- **High-Frequency Data Sync**: Client pushing 50 Coding Sessions (high volume, no direct security risk)
- **Background Async Tasks**: Scheduled token cleanup, data alignment tasks
- **External RPC/API Calls**: Email provider latency and response codes
- **Cache & Middleware Behavior**: Redis cache misses, DB connection pool retries

## Code Practice: Dual Logging Strategy

Both logging types often work together in the same service method:

```java

@Service
public class DeviceSyncService {
    private static final Logger log = LoggerFactory.getLogger(DeviceSyncService.class);
    private final AuditLogService auditLog;

    @Transactional
    public void pushSessions(UUID deviceId, List<CodingSessionDto> sessions) {
        UUID currentUserId = RequestContext.currentRequired().userId();

        // Business Log: System performance observability
        // High volume data written to ELK for "syncs per second" monitoring
        log.atInfo()
            .addKeyValue("device_id", deviceId.toString())
            .addKeyValue("payload_size", sessions.size())
            .log("Starting coding session sync push");

        for (CodingSessionDto session : sessions) {
            if (isMaliciousPayload(session)) {
                // Audit Log: Security boundary crossed, must persist evidence
                auditLog.logCritical(
                    currentUserId,
                    AuditAction.MALICIOUS_PAYLOAD_DETECTED,
                    ResourceType.CODING_SESSION,
                    session.getSessionUuid().toString(),
                    AuditDetails.reason("Detected SQL injection pattern")
                );

                // Business Log: Still log for ops troubleshooting
                log.atWarn().log("Sync payload rejected due to malicious content");
                continue;
            }

            sessionRepository.upsert(session);
        }

        log.atInfo().log("Sync push completed successfully");
    }
}
```

## The Architect's Heuristic

Ask yourself: *"If this log were completely lost in memory due to system OOM crash, would the company face legal disputes, unverifiable user asset damage, or inability to provide hacker source IP to authorities?"*

- **YES** → Mandatory `AuditLogService` (transactional or independent persistence)
- **NO** → Just makes developers lose hair debugging, use `SLF4J / MDC`

## References

- `AuditLogService`: Facade for compliance-grade audit trails
- `AuditAction`: Standardized actions following [DOMAIN]_[STATE] grammar
- `SecuritySeverity`: INFO / WARNING / CRITICAL classification
- `AuditDetails`: Strongly-typed JSONB carrier for audit context
