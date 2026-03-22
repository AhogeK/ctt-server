# 项目简述

## 核心目标

Code Time Tracker (CTT) 服务端 - 为 JetBrains IDE 插件提供代码时间追踪数据的云同步、统计分析和排行榜服务。

## 关键约束

- **数据同步**: 多设备双向同步，LWW (Last-Write-Wins) 策略
- **安全认证**: JWT (Web) + API Key (插件) 双轨制
- **时间处理**: UTC-First，强制 `Instant`，禁止 `LocalDateTime`

## 利益相关者

- IDE 插件用户 (开发者)
- Web 管理后台用户