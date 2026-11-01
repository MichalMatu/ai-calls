# AI Calls — exact-call controller feasibility sprint (2026-11-01)

## Objective and boundaries

The purpose of this sprint is to determine, without placing a phone call or changing the default dialer, which Android Telecom call-ownership capabilities actually exist on Samsung S22. The known Shizuku/media readiness PASS **does not** imply call-specific control.

- Working base: `main@194bd9dfaf859d97a81d16a2ec0cdfd19b9be216`.
- Source changes: `scripts/aicall_tools/device/s22_call_capabilities.py`, `scripts/tests/device/test_s22_call_capabilities.py`, CLI `scripts/bin/aicalls call-capabilities`.
- The Python CLI only runs exact-device discovery plus five bounded read-only shell commands: Android SDK, default dialer role, app package existence, cellular call state and Shizuku process presence. It suppresses raw call-state dumps and never dials, requests a role, mutates permissions, installs apps, starts a phone call or hangs up.
- Diagnostic output always contains `exact_call_controller_proven=false` and `autonomous_dial_authorized=false`. **These are not dynamically inferred permissions:** they are intentionally fail-closed product statuses until a separate exact-call backend has independently satisfied its acceptance contract.
- The test suite covers default-dialer mismatch, Shizuku-without-call-ownership, unknown role, malformed Android version, ambiguous roles, missing package/process, contradictory call states, shell argument injection and absence of mutating commands.

## Actual hardware evidence

Mac Host Control transport: `MichalMatu/local-agent:agent-control`,
`.agent/hosts/macbook-michal/supervisor/cfea4cc3-b54c-4b6d-b9a6-758d12733e98/`.

- `aicalls-exact-capabilities-s22-20261101-v1`: **done / exit 0** against `main@717232d4d23ebba0dd1680ee6b6b1f387c978b25`. Report showed Android SDK **36** (Android 16), AI Calls installed, Shizuku process visible, cellular `IDLE`, exact-call controller not proven and autonomous dialing not authorized. `cmd role holders android.app.role.DIALER` was unsuccessful; default-dialer identity is **UNKNOWN**, not a claim that AI Calls is the default dialer.
- `aicalls-telecom-readonly-role-fallback-20261101-v1`: queued independent read-only alternate Telecom queries to diagnose the failed role syntax. Require its terminal result to make any further role claim.
- `aicalls-exact-capabilities-host-gate-20261101-v1`: queued fresh-source full Python + canonical Gradle/lint/APK gate. A queue commit is not a test receipt; do not declare the sprint source `HOST_GREEN` without this exact task's terminal result.

## Architecture decision

Official Android `InCallService` binds call-specific `Call` handles for the system's default phone app. Becoming that default app requires a dialer activity and complete incoming/ongoing call UI, and changes the user's default dialer. Android `TelecomManager.endCall()` is device-wide/deprecated and is **not** an acceptable substitute for targeted `Call.disconnect()`. Sources:
- https://developer.android.com/reference/android/telecom/InCallService
- https://developer.android.com/reference/android/telecom/TelecomManager

The current product constraint is to **preserve the normal/default dialer**. Therefore do not silently change Android roles or add a partial InCallService to make old `ACTION_CALL` code appear accepted.

Shizuku may enable special device-specific integration, but a Shizuku Binder/process alone never proves a stable, call-specific Android Telecom handle. A Shizuku integration is a separate capability requiring reviewed API/privilege compatibility, per-SIM dial identity, exact session correlation and an independently validated selective hangup. It has **not** been delivered by this sprint.

## Explicit acceptance matrix for any future backend

1. Own a unique handle for exactly the app-initiated outgoing call and distinguish the call during dialing, active, disconnecting and terminated states; no global `isInCall` ownership claim.
2. Bind the call to the authorized target, account/SIM and single reserved campaign attempt; refuse stale/revoked grants at the irreversible dial boundary.
3. Disconnect **only** the owned call. Demonstrate an unrelated existing call, incoming call or second call is never disconnected; never use `KEYCODE_ENDCALL` or `cmd telecom end-call` as fallback.
4. Prove terminal completion independently of disconnect-request acknowledgment. On uncertainty, fail closed and retain recovery/diagnostics.
5. Recover safely from app process death, timeout, Bluetooth/Shizuku loss, callback duplication and race with grant revocation; no automatic dial replay.
6. Do not change default-dialer role, install app or call a real carrier number without a distinct scoped authorization and appropriate hardware acceptance step.
7. Retain user-facing source of truth: `CampaignCallControllerRegistry.available()==null` must block the app-owned campaign until a real backend is installed and accepted.

## Next gate

Read `task-results` for host QA and the independent role fallback. If the role command is unsupported, preserve `UNKNOWN` or implement only a **read-only** verified fallback. When source and device tests are green, close this feasibility sprint as a completed diagnostic milestone, **not** completed autonomous calling. Android exact-call implementation remains an unsatisfied product dependency.
