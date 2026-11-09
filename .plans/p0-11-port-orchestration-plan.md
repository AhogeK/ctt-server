# P0.11 端口与编排实施计划

> 状态：completed
> 目标：`edge 18080`、蓝 `18081`、绿 `18082` 只绑定回环；`edge` 使用 host 网络；应用直接把 `127.0.0.1` 视为可信上游；`frpc remotePort=18080 → localPort=18080`。

## 结论

只把 `edge` 改为 host 网络不足以满足 `TRUSTED_PROXY_ADDRESSES=127.0.0.1`：host 网络访问 Docker 发布端口时，容器内 `remoteAddr` 实测为 `172.17.0.1`。因此蓝/绿应用也应使用 host 网络并把 Spring 绑定到 `127.0.0.1`，`edge` 直接代理到 `127.0.0.1:18081/18082`。

## 受影响文件

| 文件 | 变更 |
|---|---|
| `docker-compose.local.yaml` | blue/green/edge/obs 改 host 网络；blue/green 设置端口、回环绑定、DB/Redis/Mailpit 地址与可信代理；观测组件改 host 网络并绑定回环 |
| `deploy/local/Caddyfile` | 监听 `127.0.0.1:18080`；默认上游 `127.0.0.1:18081` |
| `deploy/local/prometheus.yml` | blackbox 目标与自身抓取改回环地址 |
| `deploy/local/grafana-datasource.yml` | Prometheus 数据源改 `127.0.0.1:9090` |
| `README.md` | 更新本地全栈端口、host 网络、回环与可信代理说明 |
| `gradle/libs.versions.toml` | `appVersion` 升到 `0.96.1`（含 P0.5 secret 组读修复） |
| `docker-compose.yaml` | 应用 `group_add` 读取 0440 secret；属于本轮发现并修复的 P0.5 问题 |
| `.env.example` | `SECRET_GID`；与 render.sh/Compose 保持一致 |
| `deploy/secrets/render.sh` | 默认 0440、组可读；先 chgrp 再替换 live set |
| `docs/deployment-secrets.md` | 更新组读、GROUP 与验证说明 |
| `.plans/ctt-server-development-plan.md` | P0.11 状态、证据与完成总结 |
| `memory-bank/activeContext.md` | 记录交付与验证事实；按 R13 控制 200 行 |
| `memory-bank/progress.md` | 记录 0.96.1 里程碑 |

## 实施步骤

1. 修改 `docker-compose.local.yaml`：blue/green 使用 host 网络，清理继承的 `ports`，显式设置 `SERVER_ADDRESS=127.0.0.1`、`SERVER_PORT`/`APP_PORT`、host 侧 PostgreSQL/Redis/Mailpit 地址、`TRUSTED_PROXY_ADDRESSES=127.0.0.1` 和颜色专属 readiness healthcheck。
2. 修改 `edge`：host 网络、监听回环、默认上游 blue；移除发布端口。
3. 修改 obs：Prometheus、blackbox、Grafana 使用 host 网络并显式监听回环；更新 scrape 目标与 Grafana datasource。
4. 更新 README 与运行说明；版本升到 `0.96.1`。
5. 更新计划/记忆。
6. 验证：`docker compose config`、`ss -lnt` 只看到回环绑定、HTTP 经 edge 到 blue、host 网络下应用看到的来源为 `127.0.0.1`、obs profile 可启动；测试后清理本轮启动的容器。

## 验收

- `127.0.0.1:18080` 可访问 readiness；`18081` 蓝、`18082` 绿可访问。
- `ss -lnt` 中 18080–18082 只绑定 `127.0.0.1`，不绑定 `0.0.0.0`/`::`。
- 经 edge 的业务请求在应用侧 `remoteAddr` 为 `127.0.0.1`，可信代理判断成立。
- frpc 配置保持 `remotePort=18080` → `localPort=18080`。
- `./gradlew test` 或相关测试通过；纯编排变更按实际可运行验证为准。

## 完成记录（2026-11-09）

| 项 | 结果 |
|---|---|
| `docker compose config` | 通过；blue/green/edge/obs 均为 host 网络，继承的 ports/networks 已清空 |
| Caddy | `caddy validate` 通过；`:18080` + `bind 127.0.0.1` |
| Prometheus | `promtool check config` 通过 |
| Linux 完整 app/edge | Caddy `127.0.0.1:18080` 的 readiness 返回 200；blue `18081` 与 green `18082` 直连 readiness 均为 200；应用日志来源 `127.0.0.1` |
| 回环绑定 | Caddy 的 18080 绑定 `127.0.0.1`；应用由 `SERVER_ADDRESS=127.0.0.1` 绑定 18081/18082 |
| 定向测试 | `IpUtilsTest` 与 `RequestContextInitializerFilterTest` 通过 |
| Mac Docker Desktop | 未执行完整栈；host-network 端口不暴露给 Mac 宿主，不作为 Linux 交付证据 |
| Obs | Prometheus `:9090`、blackbox `:9115`、Grafana `:3000` 均只绑定 `127.0.0.1`；Prometheus 两个 target 为 up；Grafana `database: ok` |
| Secret 修复 | `render.sh` 默认 `0440` + `SECRET_GID`；应用/Grafana `group_add`；Linux appuser 可读 |
