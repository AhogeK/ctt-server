# CTT Server

Cloud-based synchronization backend for Coding Time Tracker (CTT) - a JetBrains IDE plugin that tracks coding activities and provides personal analytics dashboards.

## Overview

CTT Server provides:

- **Data Synchronization**: Secure API for JetBrains plugin to sync coding sessions
- **API Key Management**: Product-grade authentication with scope-based permissions
- **Statistics & Analytics**: Aggregation queries for coding metrics and trends
- **Global Leaderboard**: Redis-powered real-time ranking system

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Language | Java 21 (Virtual Threads) |
| Framework | Spring Boot 3.x (MVC) |
| Authentication | Spring Security 6 + JWT + API Key |
| Database | PostgreSQL 16 |
| Migration | Flyway |
| Cache | Redis 7 |
| API Docs | springdoc-openapi |
| Testing | JUnit 5 + Testcontainers |

## Architecture

```
┌───────────────────────────────────────────┐
│ JetBrains Plugin                           │
│ - Time tracking & session management       │
│ - Delta sync with retry logic              │
└───────────────┬───────────────────────────┘
                │ HTTPS (Bearer API Key)
                ▼
┌───────────────────────────────────────────┐
│ CTT Cloud API (Spring Boot)               │
│ - API Key Auth + Rate Limiting            │
│ - Idempotent sync ingest                  │
│ - Stats aggregation queries               │
│ - Redis-backed leaderboard                │
└───────────────┬───────────────────────────┘
                │
        ┌───────┴────────┐
        ▼                ▼
┌───────────────┐  ┌───────────────┐
│ PostgreSQL     │  │ Redis          │
│ - sessions     │  │ - key cache    │
│ - users        │  │ - rate limit   │
│ - api_keys     │  │ - leaderboard  │
└───────────────┘  └───────────────┘
```

## Getting Started

### Prerequisites

- Java 21+
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

- [CTT Plugin](https://github.com/AhogeK/coding-time-tracker) - JetBrains IDE plugin
- [CTT Web](https://github.com/AhogeK/ctt-web) - Vue.js dashboard frontend

---

Built with ☕ by [AhogeK](https://github.com/AhogeK)
