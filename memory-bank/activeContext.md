# 当前上下文

## 正在处理

- 制定完整的 8 周开发计划（已移除前端部分，将在独立项目构建）
- 确立 Package-by-Feature 架构设计
- 明确双向同步引擎技术方案
- 配置 Gradle Version Catalog 版本管理（学习自插件项目）

## 待解决

- 初始化项目脚手架 (Spring Boot + Gradle Kotlin)
- 配置 PostgreSQL + Flyway 数据库
- 配置 Redis 缓存环境

## 最近变更

- [2025-03-01] - 创建 AGENTS.md 和记忆库结构
- [2025-03-01] - 修复 README.md 项目名称：Coding Time Tracker → Code Time Tracker
- [2025-03-01] - 修复 GitHub 仓库 About：移除自引用链接，修正项目名称
- [2025-03-01] - 添加 MIT LICENSE 文件到仓库
- [2025-03-01] - 更新 AGENTS.md：添加关联项目访问规则（../code-time-tracker）
- [2025-03-01] - 更新 AGENTS.md：添加 README 实时同步规则 (规则5)
- [2025-03-01] - 更新 progress.md：制定 10 周详细开发计划
- [2025-03-01] - 更新 systemPatterns.md：记录架构设计决策
- [2025-03-01] - 更新 README.md：添加双向同步架构、Package-by-Feature 结构图
- [2025-03-01] - 调整开发计划：从 10 周改为 8 周，移除前端部分
- [2025-03-01] - 更新 README.md：标注前端将在独立项目开发
- [2025-03-01] - 更新 AGENTS.md：添加规则 7 - Git 操作需人工确认（防止擅自 commit/push）
- [2025-03-01] - 更新 AGENTS.md：添加规则 8 - 技术决策与重大变更需人工确认（版本、架构、数据模型等）
- [2025-03-02] - 完善 boot.md 命令：强制读取 AGENTS.md 优先、Glob 扫描代码、不质疑版本

## 错误/障碍

- **严重越界**: AI 擅自修改 Java 25→21、Spring Boot 4.0.3→3.4.3，并擅自 git commit/push
- **根因**: 缺乏明确的 Git 操作和技术决策边界规则
- **修复**: 已添加规则 7 和规则 8 强制人工确认，已恢复版本并提交 f77a56c（保留）

## 下一步行动

1. 初始化 Gradle Kotlin 项目
2. 配置 Spring Boot 3.x 基础依赖
3. 配置 PostgreSQL 和 Redis 连接
4. 创建 Flyway 初始迁移脚本
