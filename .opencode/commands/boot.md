---
description: 初始化会话 - 读取项目文件了解上下文
---


请读取以下文件了解项目上下文：

## 1. 读取项目记忆

- @memory-bank/projectbrief.md
- @memory-bank/techContext.md
- @memory-bank/systemPatterns.md
- @memory-bank/activeContext.md
- @memory-bank/progress.md

## 2. 读取项目说明

- @README.md

## 3. 读取行为约束

- @AGENTS.md

## 4. 输出摘要

读取完成后，输出：

```
=== 项目状态 ===

项目: CTT Server - Code Time Tracker 服务端

当前进度:
- [来自 progress.md]

正在处理:
- [来自 activeContext.md]

技术栈:
- [来自 techContext.md]

核心约束:
- Git 操作需人工确认
- 技术决策需人工确认
- 自动更新记忆文件（按 AGENTS.md 规则）
```
