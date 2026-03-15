# 技术上下文

## 技术栈

- **语言**: Java 25 (Virtual Threads)
- **框架**: Spring Boot 4.0.3
- **构建工具**: Gradle 9.x with Kotlin DSL
- **版本管理**: Gradle Version Catalog (`gradle/libs.versions.toml`)
- **数据库**: PostgreSQL 16 + Flyway 11.4.0
- **缓存**: Redis 7
- **安全**: Spring Security 7 + JJWT 0.12.6
- **API文档**: springdoc-openapi 2.8.5
- **测试**: JUnit 5 + Testcontainers 1.20.6
- **代码质量**: Spotless 1.35.0 (Google Java Format) + JaCoCo 0.8.14

## 架构图

```
Package-by-Feature Structure:
├── common/          # Shared utilities, config, exceptions
├── auth/            # JWT authentication
├── apikey/          # API Key management
├── sync/            # Bidirectional sync engine
├── stats/           # Statistics aggregation
└── leaderboard/     # Redis-powered ranking
```

## 开发环境

- **启动命令**: `./gradlew bootRun --args='--spring.profiles.active=local'`
- **测试命令**: `./gradlew test`
- **构建命令**: `./gradlew build`
- **依赖检查**: `./gradlew dependencies`
- **版本更新**: `./gradlew dependencyUpdates` (via ben-manes plugin)

## 版本管理

采用 **Gradle Version Catalog** 集中管理所有依赖版本：
- 版本定义: `gradle/libs.versions.toml`
- 应用版本: `appVersion = "0.0.1-SNAPSHOT"`
- 依赖引用: `libs.spring.boot.starter.web`
- Bundles: `libs.bundles.jjwt` (group related deps)
