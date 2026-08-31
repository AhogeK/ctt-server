# Active Context 归档 — 2026-03

> 冷数据归档（R13）。内容为该月已完成的条目，按时间倒序；仅供回溯，不再更新。

- [2026-03-21] - 邮件生命周期审计完整覆盖：ENQUEUED → SENT/FAILED/EXHAUSTED
- [2026-03-21] - Audit Details 标准化 (GDPR 合规): `recipientMasked` 字段脱敏
- [2026-03-21] - 指数退避重试策略：`delay = min(base * multiplier^attempt, maxDelay) ± jitter`
- [2026-03-21] - 僵尸记录恢复机制：`resetStuckSendingJobs()` 批量更新 SENDING → PENDING
- [2026-03-20] - MailOutboxPoller + MailOutboxProcessor: @Scheduled 轮询 + REQUIRES_NEW 事务隔离
