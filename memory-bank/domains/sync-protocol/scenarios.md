# sync-protocol — scenarios

Trigger → judgement → action.

## Changing the wire contract

| Trigger | Judgement | Action |
| --- | --- | --- |
| New response field | The plugin parses with lenient JSON (unknown fields ignored) | Additive only; never rename or remove a field the plugin reads |
| New request field | Old clients omit it | Give it a server-side default that reproduces previous behaviour |
| Behaviour change for existing clients | Backward compatibility is a hard constraint | Ship it behind a new optional param, or verify the old path degrades identically |
| Contract change that the plugin must mirror | The plugin is read-only from here | Write a requirement/bug report (现状 / 期望 / 理由 / 影响面 / 对接方配合) per R3 |

## Diagnosing sync reports

| Trigger | Judgement | Action |
| --- | --- | --- |
| "A new device pulled everything at once" | Unbounded responses are the failure mode paging solves | Check `pull-batch-size`; the server pages and the client must loop on `hasMore` |
| "A session is missing on one device" | Distinguish missing data from a skipped page | Compare the device cursor with the server watermark; a watermark ahead of the client means a page was generated but never applied (principle 8) |
| "The same session exists twice" | Content-level dedup should collapse it | Check `session_uuid` uniqueness and whether the re-push carried identical business content |
| "Editing on device B moved the session out of device A's stats" | Origin vs last-writer confusion | Statistics must filter on `origin_device_id`; if they filter on the writer, that is the defect |
| "Device stops syncing after reinstall" | Revocation or unknown device | Both answer 404 `COMMON_002`; re-registering the same id clears revocation |

## Changing push behaviour

| Trigger | Judgement | Action |
| --- | --- | --- |
| New per-session side effect | The push transaction is the only place mutations are accepted | Decide failure tolerance: a side effect must never roll back the accepted data (materialization, leaderboard, cache invalidation are all failure-tolerant by design) |
| Bulk insert tuning | Per-row statements make PG fall back to single-row inserts | Keep the hand-written multi-row `INSERT` for creates; batch-insert the change log |
| Adding validation | Reject before mutating | Validate device ownership first; a bad device must not leave partial rows |

## Changing cursors or the change log

| Trigger | Judgement | Action |
| --- | --- | --- |
| New change-log consumer | `change_id` is the only ordering guarantee | Never reuse ids, never backdate `happened_at` into ordering decisions |
| Cursor write path | Monotonicity is the safety property | Keep the atomic upsert with `GREATEST`; a plain UPDATE cannot create the first row for a fresh device |
| Retention/cleanup idea | The log is the pull source of truth | Do not prune without a client-facing resync story |
