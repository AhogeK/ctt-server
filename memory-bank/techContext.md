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

## 配置分层 (12-Factor App)

遵循 **代码与配置分离** 原则，配置文件按环境分层：

```
src/main/resources/
├── application.yaml          # 全局基线配置 (强制入库)
├── application-dev.yaml      # 开发/测试环境 (环境变量驱动)
├── application-prod.yaml     # 生产环境 (环境变量驱动)
├── application-local.yaml    # 本地开发 (禁止入库 - .gitignore)
└── application-local.yaml.template  # 本地配置模板 (新人参考)
```

### 配置加载规则

| Profile | 配置文件 | 用途 | 敏感数据 |
|---------|----------|------|----------|
| (default) | application.yaml | 全局默认值、时区策略、序列化规则 | 禁止 |
| local | application-local.yaml | 开发者本地调试 | 允许明文 |
| dev | application-dev.yaml | 测试/开发服务器 | 环境变量注入 |
| prod | application-prod.yaml | 生产环境 | 环境变量注入 |

### 环境变量规范

敏感配置通过环境变量注入：
- `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD`
- `REDIS_HOST`, `REDIS_PORT`, `REDIS_PASSWORD`
- `SPRING_PROFILES_ACTIVE`

启动示例：
```bash
SPRING_PROFILES_ACTIVE=dev \
DB_PASSWORD=secret \
REDIS_PASSWORD=secret \
./gradlew bootRun
```
