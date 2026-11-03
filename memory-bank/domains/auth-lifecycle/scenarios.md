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
| New endpoint that changes a credential or identity | Account state has to be valid as of the token | Call `getActiveUserRequired()` (Tier 3 in `docs/api-governance.md`); reads and protective operations stay on `getCurrentUserRequired()` — a locked account must still be able to log out and revoke keys |
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

## Touching 2FA

| Trigger | Judgement | Action |
| --- | --- | --- |
| Enabling 2FA | A stolen session could bind an attacker's authenticator | Require current password for password accounts; OAuth-only requires a session younger than 10 minutes (`AUTH_031`) |
| Disabling 2FA | Possession factor is the credential; password is not required | Accept TOTP or an unused recovery code, then revoke other sessions |
| Rotating recovery codes | Old codes must die atomically | Replace the whole batch in one transaction; audit without the codes |
| Adding an API-key operation | API keys can otherwise mint permanent credentials | Require `WEB_SESSION`; when 2FA is on, require a code (`AUTH_030`/`AUTH_027`) |
| OAuth login | GitHub alone must not bypass the factor | Check `totp_confirmed_at` before issuing tokens; deliver the challenge in the URL fragment |

## Touching passkeys

| Trigger | Judgement | Action |
| --- | --- | --- |
| Registering a passkey | A stolen browser session could bind an attacker's authenticator | Require `AUTH_031` recent authentication; reject API keys with `AUTH_025`; notify by email |
| Logging in with a passkey | This is an independent factor, not a second step | Complete the assertion without TOTP; issue the same `LoginResponse` and cookies as password login |
| Deleting the last passkey | The account must keep a usable login path | Allow only while a password or OAuth binding remains; otherwise reject with `AUTH_018` (409) |
| Changing `PASSKEY_RP_ID` | Every credential is bound to the RP ID | Treat as a breaking migration; plan re-registration before changing it |
| Assertion failure | Must not reveal whether the credential exists | Return `AUTH_033` for every failure; log the reason without credential ID or challenge |
| Signature counter is zero | Synced passkeys legitimately report zero | Accept it; do not compare zero to the stored value as a regression |

## Touching second-factor passkeys

| Trigger | Judgement | Action |
| --- | --- | --- |
| Account has a 2FA challenge and passkeys | The account is known; discoverable assertion is wrong | Start assertion with `.userHandle(userId)` so Yubico builds account-bound `allowCredentials` |
| Reading `challengeToken` for options | Must not spend the code fallback | Use `TwoFactorChallengeService.peek`, never `consume` |
| Passkey assertion fails | User must still be able to enter TOTP | Consume only the WebAuthn challenge; keep the login challenge |
| Passkey assertion succeeds | Both factors are satisfied | Consume the 2FA challenge before issuing tokens; replay is `AUTH_026` |
| Account has passkeys but no TOTP | No 2FA challenge is issued today | Do not invent a second step; that is a separate policy change |
| Login challenge expired while options are open | Assertion must not mint tokens | `peek`/`consume` raises `AUTH_026`; WebAuthn challenge may already be spent |
