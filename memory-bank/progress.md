# 项目进度

## 已完成 ✅

- [x] 创建 AGENTS.md 项目记忆配置文件
- [x] 初始化 memory-bank/ 目录结构
- [x] 项目架构设计与技术选型确定
- [x] README 基础版本
- [x] MIT LICENSE
- [x] 创建 Flyway 数据库迁移脚本 (V20260303210000__init_base_schema.sql)
- [x] 添加代码规范：CONVENTIONS.md、.editorconfig、.gitmessage
- [x] 添加构建工具：Spotless + JaCoCo
- [x] 合并 scaffold 分支到 master
- [x] 拆分包结构 (common, auth, user, device, audit, mail)
- [x] 统一响应模型 (ApiResponse, ErrorResponse, PagedResponse, EmptyResponse)

## 开发计划 (8周) 🗓️

### Week 1-2: 基础设施与认证 🔧

**目标**: 搭建脚手架、数据库、认证系统

- [ ] 搭建 Spring Boot 4.x + Gradle Kotlin 项目结构
- [x] 配置 PostgreSQL + Flyway 数据库迁移 (脚本已创建)
- [ ] 配置 Redis 缓存
- [ ] 实现 JWT 用户认证 (注册/登录)
- [ ] 实现 API Key 生成与管理 (设备绑定)
- [ ] 编写基础单元测试

### Week 3-4: 双向同步引擎 🔄

**目标**: 实现核心同步逻辑 (Pull/Push + 冲突解决)

- [ ] 设计 CodingSession 数据模型 (含软删除、updated_at)
- [ ] 实现 SyncCursor 设备同步状态追踪
- [ ] 实现 SyncPullService (增量拉取)
- [ ] 实现 SyncPushService (数据合并)
- [ ] 实现 ConflictResolver (LWW 冲突解决)
- [ ] 编写详尽的并发/冲突单元测试

### Week 5-6: 插件端集成 🔌

**目标**: 升级插件实现云端同步

- [ ] 升级 SQLite 表结构 (添加软删除字段)
- [ ] 实现先 Pull 后 Push 的调度逻辑
- [ ] 实现空闲触发同步机制
- [ ] 多端冲突场景测试验证

### Week 7: 统计与排行榜 📊

**目标**: 实现数据分析与实时榜单

- [ ] 实现多维时序聚合查询接口
- [ ] 实现设备过滤统计功能
- [ ] 实现 Redis ZSet 排行榜机制
- [ ] 性能优化与缓存策略

### Week 8: 测试与上线 🚀

**目标**: 压测、监控、部署

- [ ] 压力测试 (大规模离线数据同步)
- [ ] 集成 Sentry 错误监控
- [ ] Railway/Fly.io 部署配置
- [ ] API 文档完善
- [ ] 上线文档与运维手册

## 进行中 🔄

- [ ] Week 1 基础设施搭建 - 进度: 70%
    - [x] Flyway 数据库迁移脚本
    - [x] 代码规范文件 (CONVENTIONS.md, .editorconfig)
    - [x] 构建工具配置 (Spotless, JaCoCo)
    - [x] 包结构拆分 (common, auth, user, device, audit, mail)
    - [x] 统一响应模型 (ApiResponse, ErrorResponse, PagedResponse, EmptyResponse)
    - [x] 统一错误码体系 (ErrorCode enum)
    - [x] 统一异常体系 (BusinessException + 7个子类 + GlobalExceptionHandler)
    - [x] 全局 Jackson 配置 (application.yaml non_null)
    - [x] 响应类改为 record (Java 25)
    - [x] 测试覆盖率 80% 达标
    - [x] TraceId 透传机制 (TraceIdFilter + TraceContext + MDC)
    - [x] logback-spring.xml 配置
    - [x] RequestContext 体系 (RequestInfo + RequestContext + ScopedValue)
    - [x] IpUtils 工具类
    - [ ] Spring Boot 项目结构
    - [ ] Redis 缓存配置
    - [ ] JWT 认证实现
    - [ ] API Key 管理

## 待开始 ⏳

- [ ] 项目脚手架初始化

## 已知问题 🐛

- 无
