# 技术上下文

## 技术栈

| 类别 | 技术 |
|------|------|
| 语言 | Java 25 (Virtual Threads) |
| 框架 | Spring Boot 4.0.3 |
| 构建 | Gradle 9.x + Kotlin DSL + Version Catalog |
| 数据库 | PostgreSQL 16 + Flyway 11.4.0 |
| 缓存 | Redis 7 |
| 安全 | Spring Security 7 + JJWT 0.12.6 |
| API文档 | springdoc-openapi 2.8.5 |
| 测试 | JUnit 5 + Testcontainers 1.20.6 |
| 代码质量 | Spotless 1.35.0 + JaCoCo 0.8.14 |

## 架构

```
Package-by-Feature:
├── common/      # Shared utilities, config, exceptions
├── auth/        # JWT authentication
├── apikey/      # API Key management
├── sync/        # Bidirectional sync engine
├── stats/       # Statistics aggregation
└── leaderboard/ # Redis-powered ranking
```

## 常用命令

| 命令 | 说明 |
|------|------|
| `./gradlew bootRun --args='--spring.profiles.active=local'` | 启动 |
| `./gradlew test` | 测试 |
| `./gradlew build` | 构建 |
| `./gradlew spotlessApply` | 格式化 |
| `./gradlew test jacocoTestCoverageVerification` | 覆盖率 |

## 配置分层 (12-Factor App)

| Profile | 文件 | 用途 |
|---------|------|------|
| default | application.yaml | 全局基线 |
| local | application-local.yaml | 本地开发 (禁止入库) |
| dev | application-dev.yaml | 测试环境 |
| prod | application-prod.yaml | 生产环境 |

敏感配置通过环境变量注入：`DB_*`, `REDIS_*`, `SPRING_PROFILES_ACTIVE`

## 安全配置 (ctt.security.*)

| 配置项 | 默认值 |
|--------|--------|
| jwt.secret-key | `${JWT_SECRET_KEY}` |
| jwt.issuer | `ctt-identity-provider` |
| jwt.access-token-ttl | `15m` |
| jwt.refresh-token-ttl-plugin | `14d` |
| jwt.refresh-token-ttl-web | `30d` |
| password.bcrypt-rounds | `12` |
| password.max-failed-attempts | `5` |
| password.lock-duration | `30m` |
| rate-limit.enabled | `true` |
| rate-limit.global-max-requests-per-second | `200` |