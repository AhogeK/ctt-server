# CTT Server

Cloud-based synchronization backend for Code Time Tracker (CTT) - a JetBrains IDE plugin that tracks coding activities
and provides personal analytics dashboards.

## Overview

CTT Server provides:

- **Email Verification**: One-time token-based verification with 24h expiration and SHA-256 token hashing
- **Transactional Email Queue**: Outbox pattern with retry scheduling, exponential backoff, and audit logging
- **Bidirectional Sync Engine**: Multi-device data synchronization with LWW conflict resolution
- **API Key Management**: Product-grade authentication with device-binding and revocation
- **Statistics & Analytics**: Time-series aggregation queries with device filtering
- **Global Leaderboard**: Redis-powered real-time ranking system
- **Soft Delete Architecture**: Safe data handling with `is_deleted` flags for sync integrity
- **OWASP Security Headers**: X-Content-Type-Options, X-XSS-Protection, X-Frame-Options, HSTS, CSP
- **Account Lockout Strategy**: Brute-force attack protection with automatic temporary lockout
- **Password Reset**: Token-based password recovery with mail outbox delivery, session revocation (Kill Switch), and automatic account unlock

## Tech Stack

| Layer          | Technology                           |
|----------------|--------------------------------------|
| Language       | Java 25 (Virtual Threads)            |
| Framework      | Spring Boot 4+ (MVC)                 |
| Authentication | Spring Security 7 + JWT + API Key    |
| Database       | PostgreSQL (latest)                  |
| Migration      | Flyway                               |
| Cache          | Redis (latest)                       |
| API Docs       | springdoc-openapi                    |
| Templates      | Thymeleaf (email rendering)          |
| Testing        | JUnit 5 + Testcontainers             |
| Configuration  | @ConfigurationProperties (Type-safe) |

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
│ PostgreSQL       │  │ Redis            │
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

| Table                   | Description                        | Key Columns                                                            |
|-------------------------|------------------------------------|------------------------------------------------------------------------|
| `users`                 | User accounts (Web & OAuth)        | `id`, `email`, `github_id`, `status`, `last_login_at`                  |
| `devices`               | Client device registration         | `id`, `user_id`, `platform`, `ide_name`, `last_seen_at`                |
| `api_keys`              | Authentication keys (device-bound) | `id`, `key_hash`, `revoked_at`, `last_used_at`                         |
| `coding_sessions`       | Core time-tracking data            | `session_uuid`, `start_time`, `end_time`, `client_version`             |
| `session_changes`       | Sync change log (watermark base)   | `change_id`, `op`, `server_version`, `happened_at`                     |
| `sync_cursors`          | Per-device sync state              | `last_pulled_change_id`, `last_push_at`                                |
| `audit_logs`            | Security audit trail               | `action`, `resource_type`, `details (JSONB)`, `ip_address`             |
| `mail_outbox`           | Transactional email queue          | `id`, `recipient`, `status`, `retry_count`, `trace_id`                 |
| `login_attempts`        | Brute-force protection audit trail | `id`, `email_hash` (SHA-256), `ip_hash`, `attempt_at`                  |
| `password_reset_tokens` | Password reset token storage       | `id`, `email`, `token_hash`, `expires_at`, `consumed_at`, `revoked_at` |

**Key Design Features**:

- **Soft Delete**: `coding_sessions` uses `is_deleted` flag for sync integrity
- **LWW Sync**: `client_version` + `server_version` for conflict resolution
- **Audit Trail**: `audit_logs` with JSONB for flexible querying
- **OAuth Ready**: `github_id`/`github_login` columns in `users` table

### Package Structure (Package-by-Feature)

```
ctt-server/
├── audit/               # Security audit events & persistence
│   ├── entity/          # AuditLog JPA entity (JSONB support)
│   ├── enums/           # AuditAction, ResourceType, SecuritySeverity
│   ├── listener/        # Async AuditEventListener
│   ├── model/           # AuditDetails (strongly-typed JSONB carrier)
│   ├── repository/      # AuditLogRepository
│   └── service/         # AuditLogService (facade for business layer)
├── auth/                # JWT authentication module
│   ├── dto/             # Auth request/response DTOs
│   ├── entity/          # Token entities (EmailVerificationToken, RefreshToken)
│   ├── enums/           # TokenStatus enum
│   └── AuthController.java  # Authentication REST endpoints
├── user/                # User management module
│   ├── entity/          # User JPA entity with state machine
│   ├── enums/           # UserStatus state machine enum
│   ├── repository/      # UserRepository
│   ├── service/         # UserService (application service)
│   └── validator/       # UserValidator (domain rules)
├── common/              # Shared utilities and cross-cutting concerns
│   ├── context/         # Request context (ClientIdentity, RequestInfo, ScopedValue)
│   ├── config/          # Global configuration (Jackson, Security, etc)
│   ├── exception/       # Global exception handling
│   ├── ratelimit/       # Declarative rate limiting framework (@RateLimit)
│   ├── idempotent/      # Declarative idempotency framework (@Idempotent)
│   ├── response/        # Unified response models (ApiResponse, ErrorResponse)
│   ├── logging/         # Structured logging (LogRecord)
│   └── utils/           # Utility classes (IpUtils, SpelExpressionResolver)
├── mail/                # Email infrastructure
│   ├── config/          # MailTemplateConfig (standalone TemplateEngine)
│   ├── entity/          # MailOutbox entity (transactional outbox pattern)
│   ├── enums/           # MailOutboxStatus (delivery state machine)
│   ├── repository/      # MailOutboxRepository
│   └── template/        # MailTemplateRenderer (Thymeleaf), DTOs (sealed interface + records)
```

## Documentation

- [Developer Handbook](docs/developer-handbook.md) - Standard operations guide for error codes, audit events, exceptions, and protected APIs
- [Time Strategy](docs/time-strategy.md) - UTC-first time handling specification for distributed systems
- [Case Normalization](docs/case-normalization.md) - Email case normalization strategy for identity systems
- [API Governance](docs/api-governance.md) - API security tiers, rate limiting, and idempotency guidelines

## Getting Started

### Prerequisites

- Java 25+
- PostgreSQL (latest)
- Redis (latest)

### Configuration

This project follows **12-Factor App** methodology with configuration layering:

```
src/main/resources/
├── application.yaml              # Global baseline (in Git)
├── application-dev.yaml          # Dev/Test environment (env vars)
├── application-prod.yaml          # Production (env vars)
└── application-local.yaml        # Local development (.gitignore)
```

**Setup for Local Development:**

1. Copy `.env.example` to `.env` and customize passwords:
   ```bash
   cp .env.example .env
   ```

   Edit `.env` with your preferred passwords (default values are examples).

2. Start local infrastructure and application (PostgreSQL + Redis + Mailpit + CTT Server):
    ```bash
    docker compose up -d --build
    ```

3. Access Mailpit Web UI to view emails: http://localhost:8025
4. Access CTT Server API: http://localhost:8080/ctt-server
5. Access Swagger UI: http://localhost:8080/ctt-server/swagger-ui.html

> **Security Note**: Both `.env` and `application-local.yaml` are gitignored. Never commit sensitive data.

**Environment Variables (Dev/Prod):**

| Variable                 | Description                 | Default             |
|--------------------------|-----------------------------|---------------------|
| `DB_HOST`                | Database host               | `localhost`         |
| `DB_PORT`                | Database port               | `5432`              |
| `DB_NAME`                | Database name               | `ctt_server`        |
| `DB_USERNAME`            | Database username           | (required)          |
| `DB_PASSWORD`            | Database password           | (required)          |
| `REDIS_HOST`             | Redis host                  | `localhost`         |
| `REDIS_PORT`             | Redis port                  | `6379`              |
| `REDIS_PASSWORD`         | Redis password              | (required)          |
| `RESEND_API_KEY`         | Resend SMTP API key         | (required for prod) |
| `JWT_SECRET_KEY`         | JWT signing key (256+ bits) | (required for prod) |
| `MAIL_FROM_ADDRESS`      | Sender email address        | (required for prod) |
| `MAIL_FROM_NAME`         | Sender display name         | `CTT`               |
| `SPRING_PROFILES_ACTIVE` | Active Spring profile       | `local`             |

**Docker Compose Port Mappings (Local):**

| Variable                     | Description              | Default  |
|------------------------------|--------------------------|----------|
| `POSTGRES_EXTERNAL_PORT`     | PostgreSQL host port     | `15432`  |
| `REDIS_EXTERNAL_PORT`        | Redis host port          | `16379`  |
| `MAIL_SMTP_EXTERNAL_PORT`    | Mailpit SMTP host port   | `1025`   |
| `MAIL_UI_EXTERNAL_PORT`      | Mailpit Web UI host port | `8025`   |
| `APP_EXTERNAL_PORT`          | CTT Server host port     | `8080`   |

> **Note**: Production environment requires all variables to be set. Local development uses sensible defaults from `application-local.yaml`.

### Run

```bash
# Local development (auto-loads application-local.yaml)
./gradlew bootRun

# Dev/Test environment
SPRING_PROFILES_ACTIVE=dev ./gradlew bootRun

# Production environment
SPRING_PROFILES_ACTIVE=prod \
  DB_HOST=prod-db.example.com \
  DB_PASSWORD=secret \
  REDIS_HOST=prod-redis.example.com \
  REDIS_PASSWORD=secret \
  RESEND_API_KEY=re_xxx \
  JWT_SECRET_KEY=your-secret-key \
  ./gradlew bootRun
```

**Production Deployment:**

For production deployments (Kubernetes, Docker, Railway, etc.), set environment variables via your platform's secret management:

```bash
# Example: Docker Compose production override
docker compose -f docker-compose.prod.yml up -d

# Example: Kubernetes Secrets
kubectl create secret generic ctt-mail-secret \
  --from-literal=RESEND_API_KEY=re_xxx

# Example: Railway / Fly.io
railway variables set RESEND_API_KEY=re_xxx
```

## API Endpoints

### Authentication

| Endpoint                              | Method | Description                                                                |
|---------------------------------------|--------|----------------------------------------------------------------------------|
| `/api/v1/auth/register`               | POST   | Register new user (rate limited: 60/hour per IP)                           |
| `/api/v1/auth/login`                  | POST   | User login with JWT tokens (rate limited: 30/hour per IP)                  |
| `/api/v1/auth/refresh`                | POST   | Refresh access token using refresh token (rotation)                        |
| `/api/v1/auth/verify-email`           | GET    | Verify email with token param (public, 24h token TTL)                      |
| `/api/v1/auth/resend-verification`    | POST   | Resend verification email (rate limited: 3/1min per email)                 |
| `/api/v1/auth/logout`                 | POST   | Logout user and revoke refresh token (idempotent, BOLA-protected)          |
| `/api/v1/auth/logout-all`             | POST   | **Kill Switch**: Revoke all active sessions (requires JWT, 5/min per user) |
| `/api/v1/auth/password-reset/request` | POST   | Request password reset (rate limited: 3/10min per email)                   |
| `/api/v1/auth/password-reset/confirm` | POST   | Confirm password reset (rate limited: 15/10min per IP)                     |

### Email Verification Flow

```
User registers → System sends verification email
                      ↓
              User clicks link
                      ↓
GET /verify-email?token=xxx → User status: ACTIVE
```

**Security**: Tokens are SHA-256 hashed before storage. Raw tokens never stored in database.

### Account Lockout Strategy

Protects against brute-force attacks with automatic temporary lockout:

- **Max Failed Attempts**: 5 (configurable)
- **Sliding Window**: 15 minutes (configurable)
- **Lockout Duration**: 30 minutes (configurable)
- **Storage**: DB (default) — login attempts stored in `login_attempts` table with SHA-256 hashed email/IP
- **Hybrid Unlock**:
  - **Lazy unlock**: On next login attempt, precise sliding window check unlocks if lockout expired
  - **Scheduled sweep**: Hourly background task unlocks abandoned locked accounts (no recent attempts in window)
- **Audit Trail**: All lock/unlock events emit `ACCOUNT_LOCKED` / `ACCOUNT_UNLOCKED` audit actions
- **Locked Response**: Returns HTTP 403 with `retryAfter` timestamp in response body
  and `Retry-After` header (seconds until unlock) for frontend countdown UI

Locked accounts are automatically unlocked after lockout period expires, either on next login or via scheduled cleanup.

## API Documentation

Once running, access OpenAPI docs at: `http://localhost:8080/swagger-ui.html`

## Development

```bash
# Run tests
./gradlew test

# Build
./gradlew build

# Code formatting
./gradlew spotlessApply

# Coverage verification
./gradlew test jacocoTestCoverageVerification
```

### Test Baseline

This project uses a layered testing strategy with Testcontainers for integration tests.

**Test Base Classes:**

| Annotation                 | Layer      | Schema Strategy              | Context                    |
|----------------------------|------------|------------------------------|----------------------------|
| `@BaseControllerSliceTest` | Controller | Mock MVC, no DB              | Web slice only             |
| `@BaseRepositoryTest`      | Repository | Hibernate create-drop        | JPA slice + Testcontainers |
| `@BaseIntegrationTest`     | E2E        | Flyway migrations + validate | Full ApplicationContext    |

**Smoke Test (New Developer Setup):**

```bash
# Verify test infrastructure is working
./gradlew test --tests "*TestBaselineSmokeTest"
```

**Testcontainers Reuse (Optional but Recommended):**

Enable container reuse for faster local test cycles:

```properties
# ~/.testcontainers.properties
testcontainers.reuse.enable=true
```

This keeps PostgreSQL and Redis containers running between test runs, significantly reducing startup time. Containers are automatically reused in local development (disabled in CI environments).

## License

MIT License - see [LICENSE](LICENSE) for details.

## Related Projects

- [CTT JetBrains Plugin](https://github.com/AhogeK/code-time-tracker) - JetBrains IDE plugin
- [CTT Web](https://github.com/AhogeK/ctt-web) - Vue.js dashboard frontend

---

Built with ☕ by [AhogeK](https://link.ahogek.com/)
