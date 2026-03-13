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

### Java 新特性规范 (JDK 25)
由于项目使用 JDK 25，应优先使用现代 Java 特性：

- **Record**: 替代冗余的 immutable 数据类（如 `FieldError`、`DTO`）
- **Sealed Class**: 替代枚举用于有限状态集
- **Pattern Matching**: 简化 instanceof + cast 模式
- **Switch 表达式**: 优先使用 modern switch 语法
- **Virtual Threads**: IO 密集型操作使用虚拟线程

**示例**:
```java
// ❌ 冗余写法
public static final class FieldError {
    private final String field;
    private final String message;
    public FieldError(String field, String message) { ... }
    public String field() { return field; }
    public String message() { return message; }
}

// ✅ 使用 Record (JDK 25+)
public record FieldError(String field, String message) {}
```

### Javadoc 注释规范
所有类文件必须包含 Javadoc 注释，模板如下：

```java
package com.ahogek.cttserver.xxx;

import xxx;

/**
 * [简短描述类的功能]
 *
 * <p>[详细描述，包含使用场景、注意事项等]</p>
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-03-14
 */
public class Xxx {
}
```

**注意**:
- Javadoc 必须在 `package` + `import` 之后，类声明之前
- `@since` 使用简化格式：`YYYY-MM-DD`（无需具体时间）
- 保持一行简短描述在 80 字符以内
- 使用 HTML 标签 `<p>`、`<ul>` 等进行结构化描述

**错误示例**（已发生AI越界）:
```java
// ❌ 错误：Javadoc 在 import 上面
package com.ahogek.cttserver.xxx;

/**
 * Class description
 */
import xxx;

/public class Xxx {}

// ✅ 正确：package → import → Javadoc → class
package com.ahogek.cttserver.xxx;

import xxx;

/**
 * Class description
 */
public class Xxx {}
```

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
| C009 | Code Conventions  | 代码规范：Spotless + JaCoCo + Conventional Commits | stable   |
