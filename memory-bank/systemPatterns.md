# 系统模式

## 设计决策

### 架构风格: Package-by-Feature
**决策**: 采用按功能分包而非按层分包
**理由**:
- 高内聚：相关逻辑内聚在一起
- 可拆分为微服务：未来需要拆分微服务时，直接把整个包拎出去即可
- 清晰边界：每个功能模块独立维护自己的 controller/service/repository

### 同步策略: LWW (Last-Write-Wins) + 软删除
**决策**: 多设备双向同步采用基于时间戳的 LWW 策略
**理由**:
- 简单可靠：冲突解决逻辑清晰
- 数据安全：软删除机制防止数据丢失
- 设备无关：不依赖设备在线状态

### 认证方式: JWT + API Key 双轨制
**决策**: 网站用户用 JWT，插件设备用 API Key
**理由**:
- 场景适配：Web 端适合 Session-based JWT，插件端适合长期有效的 API Key
- 设备管理：API Key 与设备强绑定，支持单独撤销
- 安全隔离：不同客户端使用不同认证机制

## 代码规范

### 项目结构规范
```
com.ahogek.ctt/
├── common/          # 全局共享模块（不包含业务逻辑）
│   ├── config/      # 全局配置
│   ├── exception/   # 全局异常处理
│   ├── response/    # 统一响应结构
│   └── utils/       # 工具类
├── auth/            # 认证模块
├── apikey/          # API Key 模块
├── sync/            # 数据同步模块
├── stats/           # 统计模块
└── leaderboard/     # 排行榜模块
```

### 模块内部规范
每个功能模块独立包含：
- `dto/` - 请求/响应数据传输对象
- `entity/` - JPA 实体类
- `repository/` - Spring Data JPA 接口
- `service/` - 业务逻辑
- `*Controller.java` - REST API 端点
- 如有特殊需要：`filter/`, `config/`, `mapper/`

### 命名规范
- Controller: `XxxController.java`
- Service: `XxxService.java` + `XxxServiceImpl.java`
- Repository: `XxxRepository.java`
- DTO: `XxxRequest.java` / `XxxResponse.java`
- Entity: `Xxx.java` (表名使用下划线命名)

## 组件字典

| ID   | 组件               | 描述                                          | 状态      |
|------|-------------------|----------------------------------------------|-----------|
| C001 | JWT Auth          | JWT 用户认证 (登录/注册/Token刷新)            | planned   |
| C002 | API Key Manager   | API Key 生成/校验/设备绑定/撤销               | planned   |
| C003 | Sync Engine       | 双向同步核心 (Pull/Push/冲突解决)             | planned   |
| C004 | Sync Cursor       | 设备同步状态追踪                              | planned   |
| C005 | Stats Aggregator  | 时序数据聚合查询                              | planned   |
| C006 | Leaderboard       | Redis ZSet 实时排行榜                         | planned   |
| C007 | Rate Limiter      | API 限流保护                                  | planned   |
| C008 | Global Exception  | 全局异常处理器                                 | planned   |
