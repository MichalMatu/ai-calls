# AI Calls — remove redundant total-call attempt limit (2026-11-03)

Owner explicitly removed the former limit of 5 calls per valid CLIR authorization; it was a redundant blocker to physical test iterations. Existing grants remain valid after upgrade.

## Changes
- Remove `maxAttempts` field, enforcement, Android UI text and obsolete quota tests.
- Preserve internal `attemptsUsed` only as durable in-flight reservation evidence; no permission limit. It saturates at `Int.MAX_VALUE` rather than wrapping.
- Ignore old `max_attempts` persisted key; it is discarded on subsequent save. Never require renewed grant for this migration.
- Add unit regressions: 100 and max-int reserved calls remain allowed; expired, revoked, wrong-account/target, wrong-effect or disclosure scope are still denied.

## Unchanged safety
Valid signed-in-app grant for `*100`, `SET_SERVICE(CLIR=true)`, default voice SIM, 24h expiry, revocation, Android default-dialer role, readiness, exactly one app-owned active call, exact session disconnection and separate factual external-result check. **No unbounded autonomous retry loop is introduced.**

## Before change
S22 BT-off `*100` real call reached active and released its exact Call handle, but Samsung Telecom on Wi-Fi Calling (`mUseIWLAN=true`) selected `MODE_IN_COMMUNICATION` and blocked proven Samsung RX/TX path. The user then disabled Wi-Fi Calling. The source fail-fast `fbcb53d` build/lint/unit and S22 install were PASS; next cellular audio acceptance is still open. Never treat old stored STT report as second-call evidence.
