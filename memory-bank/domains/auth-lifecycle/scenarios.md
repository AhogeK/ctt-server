# auth-lifecycle — scenarios

Trigger → judgement → action.

## Extending the OAuth Action enum

The convention below was promoted out of AGENTS.md R8.5 on 2026-09-11 — it is domain judgement, not a
global rule, and it is the reason this file exists.

| Trigger | Judgement | Action |
| --- | --- | --- |
| Adding a value to `OAuthStatePayload.Action` | The action is validated in one place and branched in three | Update, in the same change: (1) the payload's canonical-constructor validation; (2) `OAuthCallbackController` — both the `authorize` and the `callback` branches; (3) at least `OAuthStatePayloadTest`, `OAuthStateServiceTest`, `OAuthCallbackControllerMockMvcTest`, `OAuthLoginOrRegisterServiceTest` |
| A new action needs tokens | Only `LOGIN` may mint sessions | Encode it in the controller branch and assert it in the callback test |
| A new action needs an authenticated initiator | `BIND`-style actions require `currentUserId` | Validate in the constructor, not in the controller |

**Why it matters**: the enum is a tiny value; the flow lives in three files plus four test classes. A
partial update compiles and passes the happy path while leaving an unhandled action that fails only
in production.

## Adding an authentication flow

| Trigger | Judgement | Action |
| --- | --- | --- |
| New way to log in | Does it authenticate (login) or link (bind)? | Login → issue tokens + set login metadata; bind → no tokens, no metadata |
| New credential change | Do other sessions survive? | Default: revoke refresh tokens; state the choice in the PR/report |
| New verification token use | Reusing the shared table? | Add a purpose value and filter on it in every lookup |

## Diagnosing auth reports

| Trigger | Judgement | Action |
| --- | --- | --- |
| "Locked out although the window passed" | Lazy unlock should have cleared it | Check the attempt log's timestamps and the configured window/lock duration; the sweep only helps accounts with no retries |
| "Last login is empty after signing in with GitHub" | Login metadata must be set on all login paths | Verify the OAuth login branch, not just the password branch |
| "Email change silently did nothing" | Purposes must match | Confirm the token was issued and consumed under the CHANGE_EMAIL purpose |
| "A password reset did not log the user out elsewhere" | Kill switch expected | Confirm refresh-token revocation ran in the reset transaction |
| "Protected calls suddenly demand terms again" | Terms version is carried in the JWT | Compare the token's version with the current configured version; a bump re-gates every existing session by design |

## Touching lockout or captcha configuration

| Trigger | Judgement | Action |
| --- | --- | --- |
| Changing thresholds | They are security parameters, not tuning knobs | Ask before changing; document the reason; keep the sliding-window semantics |
| Adding a captcha-protected endpoint | Verification cost is per request | Resolve the token first, before any DB work |
| Local/test environments | Captcha must not block automated tests | Leave the secret unset (verifier passes with a warning) or use the provider's test key pair; never add a code-level bypass |
