# P0.12 生产数据隔离实施计划

> 状态：completed · 2026-11-09
> 目标：开发继续使用 `ctt_server`；新建生产库 `ctt_server_prod` 和独立角色 `ctt_app`；生产 Redis 使用独立逻辑库，开发/本地继续使用 db 0。

## 现状

| 项目 | 实测/当前配置 |
|---|---|
| PostgreSQL | 18.6；现有库 `ctt_server`；现有角色 `postgres` |
| 生产库 | `ctt_server_prod` 已创建，owner `ctt_app`，当前为空 |
| 生产角色 | `ctt_app` 已创建；普通 LOGIN，无 SUPERUSER/CREATEDB/CREATEROLE |
| Redis | 同一实例 6379；db0 为开发数据；db1 已被其他服务的 `check-approver-sort:*` 占据，未清理；db2 为空 |
| 生产 profile | `application-prod.yaml` 使用 `DB_NAME=ctt_server_prod`、`DB_USERNAME=ctt_app`、`DB_PASSWORD`，Redis `database: ${REDIS_DATABASE:2}` |

## 决策

- 新建空库 `ctt_server_prod`，owner 为 `ctt_app`；不复制开发数据，首次生产启动由 Flyway 建表。
- `ctt_app` 仅 LOGIN，不给 SUPERUSER/CREATEDB/CREATEROLE；生产应用使用该角色。
- 角色密码使用独立生成的 `PROD_DB_PASSWORD`，先写入本机 gitignored `.env` 供验证；P0.16 迁移进 SOPS 并成为生产 `DB_PASSWORD`。不提交密码。
- 原计划 Redis db 1 已被其他服务占用，不能清理或混用；生产改用空闲的 db 2。这是有证据的偏离，不是静默改需求。
- 不修改开发库 `ctt_server` 的数据；撤销 `ctt_app` 对开发库的连接权限，`PUBLIC` 也不再能连接两个库。

## 受影响文件

| 文件 | 变更 |
|---|---|
| `src/main/resources/application-prod.yaml` | Redis `database: ${REDIS_DATABASE:2}`；注明生产 DB/角色 |
| `src/main/resources/application-dev.yaml` | Redis `database: ${REDIS_DATABASE:0}` |
| `src/main/resources/application-local.yaml.template` | Redis `database: ${REDIS_DATABASE:0}` |
| `README.md` | 配置表与文档索引补充生产库/角色/Redis db |
| `docs/production-data-isolation.md` | 新建生产数据隔离运行手册 |
| `.plans/p0-12-data-isolation-plan.md` | 本计划 |
| `.env.example` | 本地 Redis 库号与生产逻辑库说明 |
| `AGENTS.md` | 环境数据隔离规则（强制） |
| `.plans/ctt-server-development-plan.md` | P0.12 完成总结与阶段状态 |
| `memory-bank/activeContext.md`、`progress.md`、`systemPatterns.md` | 记录 P0.12 |
| `gradle/libs.versions.toml` | 按新功能升 MINOR 到 `0.97.0` |

## 完成记录

| 项 | 结果 |
|---|---|
| 角色/库 | `ctt_app` 为普通登录角色；`ctt_server_prod` owner 为 `ctt_app`；`public` 表数 0 |
| 生产连接 | `ctt_app` 连接 `ctt_server_prod` 成功，可建表（事务内建表后回滚） |
| 开发隔离 | `ctt_app` 连接 `ctt_server` 被拒绝：`permission denied for database "ctt_server"` |
| Redis | db2 `DBSIZE=0`、`PING=PONG`；db1 保持其他服务数据不动 |
| 配置 | prod profile 使用 db2，dev/local 使用 db0；文档已同步 |

## 验收

- `\du ctt_app` 显示普通登录角色；`\l ctt_server_prod` owner 为 `ctt_app`。
- 用 `ctt_app` 连接 `ctt_server_prod` 成功；连接 `ctt_server` 被拒绝；开发库数据不变。
- 生产配置解析出的 Redis database 为 2；本地/开发为 0。
- 生产库不包含开发表（首次启动前为空）。
- 文档明确生产变量：`DB_NAME=ctt_server_prod`、`DB_USERNAME=ctt_app`、`REDIS_DATABASE=2`。

## 残余与边界

- 生产库当前为空：首次 `prod` 启动由 Flyway 建表。本计划的验收只证明「库、角色、连接与权限正确」，不等于生产可用性已验证。
- 密钥过渡：`PROD_DB_PASSWORD` 只在本机 gitignored `.env`；P0.16 迁入 SOPS 并作为生产 `DB_PASSWORD` 注入。仓库内没有任何生产口令。
- Redis db1 属于其他服务（`check-approver-sort:*`）：禁止 `FLUSHDB`、禁止删除或写入该库；db2 是记录在案的偏离，不是需求变更。
- 权限收紧的副作用：`PUBLIC` 对两个库的连接权限都已撤销，新建库默认不再对所有人可连；后续新增环境需显式授权。
- 未做：生产应用实际启动与业务冒烟（依赖 P0.16 与部署切换）。
