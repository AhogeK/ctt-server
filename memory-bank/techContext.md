# 技术上下文

## 技术栈

| 类别 | 技术 |
|------|------|
| 语言 | Java 25 (Virtual Threads) |
| 框架 | Spring Boot 4.1.2 |
| 构建 | Gradle 9.9.0 + Kotlin DSL + Version Catalog |
| 数据库 | PostgreSQL **18.6**（生产/开发库实测 `select version()`）；测试容器与 compose 一律用 `postgres:latest`（用户决策：镜像始终最新，含大版本）；Flyway 13.12.1 |
| 缓存 | Redis（测试容器 `redis:latest`） |
| 安全 | Spring Security 7（随 Boot BOM）+ `spring-security-oauth2-jose` |
| 两步验证 | `otp-java` 2.2.0（RFC 6238）+ `commons-codec` 1.23.0（Base32） |
| 通行密钥 | `webauthn-server-core` 2.10.1（Yubico WebAuthn；Jackson 2；Java 11+ bytecode） |
| API文档 | springdoc-openapi 3.2.0 |
| 测试 | JUnit 6.1.3 + Testcontainers 2.0.5 |
| 代码质量 | Spotless 插件 8.11.0（google-java-format 1.36.1）+ JaCoCo 0.8.16 |

> **本表是快照，`gradle/libs.versions.toml` 才是版本的唯一来源** —— 改依赖时同步本表，核对基线：2026-11-08 / v0.93.6（v0.93.1–v0.93.6 都是行为、部署、加固与密钥通道，依赖未变；上一次依赖核对为 2026-11-07 / v0.93.0，含 Flyway 13.12.1、commons-codec 1.23.0、Spotless 8.11.0、ben-manes 0.67.0、Gradle 9.9.0）。

## 架构

```
Package-by-Feature（`com.ahogek.cttserver`）:
├── auth/         # JWT、refresh、OAuth、API Key、TOTP/Passkey、条款、密码重置
├── user/         # 账号、改邮箱、登录会话、数据导出、注销
├── device/       # 注册、吊销、最后活跃
├── sync/         # LWW、变更日志、pull/push、物化
├── stats/        # 聚合、成就
├── leaderboard/  # Redis ZSet、周期、回填
├── language/     # 语言词表与归一
├── audit/        # 审计事件与落库
├── mail/         # Outbox、模板、投递
└── common/       # 安全、限流、幂等、错误处理、日志
```

## 常用命令

| 命令 | 说明 |
|------|------|
| `./gradlew bootRun --args='--spring.profiles.active=local'` | 启动 |
| `./gradlew test` | 测试 |
| `./gradlew build` | 构建 |
| `./gradlew spotlessApply` | 格式化 |
| `./gradlew test jacocoTestCoverageVerification` | 覆盖率 |
| `./deploy/local/up.sh [--profile bluegreen] [--profile obs]` | 本地全栈：Caddy edge(`:18080`) + 两色(`:18081`/`:18082`) +（可选）Prometheus/Grafana/blackbox；镜像 `caddy` / `prom/prometheus` / `grafana/grafana` / `prom/blackbox-exporter` |

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