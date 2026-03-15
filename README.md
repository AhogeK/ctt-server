# CTT Server

Cloud-based synchronization backend for Code Time Tracker (CTT) - a JetBrains IDE plugin that tracks coding activities
and provides personal analytics dashboards.

## Overview

CTT Server provides:

- **Bidirectional Sync Engine**: Multi-device data synchronization with LWW conflict resolution
- **API Key Management**: Product-grade authentication with device-binding and revocation
- **Statistics & Analytics**: Time-series aggregation queries with device filtering
- **Global Leaderboard**: Redis-powered real-time ranking system
- **Soft Delete Architecture**: Safe data handling with `is_deleted` flags for sync integrity

## Tech Stack

| Layer          | Technology                        |
|----------------|-----------------------------------|
| Language       | Java 25 (Virtual Threads)         |
| Framework      | Spring Boot 4.0.3 (MVC)           |
| Authentication | Spring Security 7 + JWT + API Key |
| Database       | PostgreSQL 16                     |
| Migration      | Flyway                            |
| Cache          | Redis 7                           |
| API Docs       | springdoc-openapi                 |
| Testing        | JUnit 5 + Testcontainers          |

## Architecture

### System Overview

```
┌─────────────────────────────────────────────────────────────────┐
│ JetBrains Plugin                                                 │
│ - Local SQLite storage                                           │
│ - Time tracking & idle detection                                 │
│ - Bidirectional sync (Pull → Push with conflict resolution)      │
└───────────────────┬─────────────────────────────────────────────┘
                    │ HTTPS (Authorization: Bearer <api_key>)
                    ▼
┌─────────────────────────────────────────────────────────────────┐
│ CTT Cloud API (Spring Boot) - Package-by-Feature Architecture    │
│ ├── auth/          - JWT authentication (Web users)              │
│ ├── apikey/        - API Key management (Device auth)            │
│ ├── sync/          - Bidirectional sync engine (LWW strategy)    │
│ ├── stats/         - Time-series aggregation queries             │
│ └── leaderboard/   - Redis ZSet ranking system                   │
└───────────────────┬─────────────────────────────────────────────┘
                    │
        ┌───────────┴───────────┐
        ▼                       ▼
┌──────────────────┐  ┌──────────────────┐
│ PostgreSQL 16    │  │ Redis 7          │
│ - users          │  │ - key cache      │
│ - api_keys       │  │ - rate limiting  │
│ - sessions       │  │ - leaderboard    │
│ - sync_cursors   │  │                  │
└──────────────────┘  └──────────────────┘

# Web Dashboard (Vue.js) - Planned in separate project
# Frontend development will be handled in: github.com/AhogeK/ctt-web
```

### Database Schema

**PostgreSQL Tables** (`src/main/resources/db/migration/V20260303210000__init_base_schema.sql`):

| Table | Description | Key Columns |
|-------|-------------|-------------|
| `users` | User accounts (Web & OAuth) | `id`, `email`, `github_id`, `status`, `last_login_at` |
| `devices` | Client device registration | `id`, `user_id`, `platform`, `ide_name`, `last_seen_at` |
| `api_keys` | Authentication keys (device-bound) | `id`, `key_hash`, `revoked_at`, `last_used_at` |
| `coding_sessions` | Core time-tracking data | `session_uuid`, `start_time`, `end_time`, `client_version` |
| `session_changes` | Sync change log (watermark base) | `change_id`, `op`, `server_version`, `happened_at` |
| `sync_cursors` | Per-device sync state | `last_pulled_change_id`, `last_push_at` |
| `audit_logs` | Security audit trail | `action`, `resource_type`, `details (JSONB)`, `ip_address` |

**Key Design Features**:
- **Soft Delete**: `coding_sessions` uses `is_deleted` flag for sync integrity
- **LWW Sync**: `client_version` + `server_version` for conflict resolution
- **Audit Trail**: `audit_logs` with JSONB for flexible querying
- **OAuth Ready**: `github_id`/`github_login` columns in `users` table

### Package Structure (Package-by-Feature)

```
ctt-server/
├── common/              # Global shared utilities
│   ├── config/          # Infrastructure configuration
│   │   ├── jackson/     # JSON serialization (MaskSerializer for data masking)
│   │   └── logging/     # Logback converters (MaskingMessageConverter)
│   ├── context/         # Request context (RequestInfo, ScopedValue, RequestLoggingFilter)
│   ├── exception/       # Global exception handling
│   ├── logging/         # Structured business logging (LogRecord)
│   ├── response/        # Unified API response wrappers
│   └── utils/           # Utility classes (IpUtils, DesensitizeUtils)
├── audit/               # Security audit events (SecurityAuditEvent)
├── auth/                # JWT authentication module
├── apikey/              # API Key management module
├── sync/                # Bidirectional sync engine
├── stats/               # Statistics aggregation
└── leaderboard/         # Redis-powered ranking
```

## Getting Started

### Prerequisites

- Java 25+
- PostgreSQL 16+
- Redis 7+

### Configuration

Create `application-local.yaml`:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/ctt
    username: your_username
    password: your_password
  redis:
    host: localhost
    port: 6379
```

### Run

```bash
./gradlew bootRun --args='--spring.profiles.active=local'
```

## API Documentation

Once running, access OpenAPI docs at: `http://localhost:8080/swagger-ui.html`

## Development

```bash
# Run tests
./gradlew test

# Build
./gradlew build
```

## License

MIT License - see [LICENSE](LICENSE) for details.

## Related Projects

- [CTT JetBrains Plugin](../code-time-tracker) - JetBrains IDE plugin (local)
- [CTT Web](https://github.com/AhogeK/ctt-web) - Vue.js dashboard frontend (planned)

---

Built with ☕ by [AhogeK](https://link.ahogek.com/)
