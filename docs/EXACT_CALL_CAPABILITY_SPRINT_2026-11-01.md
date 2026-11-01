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

## Final accepted results and closeout

The installed Mac Host Control 4.20.16 published independent, immutable results on `MichalMatu/local-agent:agent-control`.

- **Final canonical host gate:** `aicalls-exact-capabilities-closeout-host-20261101-v2` — `done`, exit 0; **exact source SHA `31bc42f42c2f0c0145a6c8badaad147395df84ee`**, Gradle unit tests, Android lint, `assembleDebug`, `assembleDebugAndroidTest`, **199 Python tests PASS**, `host_quality_gate_green=true`, `EXACT_CALL_FEASIBILITY_HOST_GATE=PASS`. The final commit after this receipt is docs-only.
- **Physical S22 + Shizuku/media no-call gate:** `aicalls-exact-capabilities-final-s22-20261101-v1` — `done`, exit 0 on `23c331704c66a1d11cb13a78f5e49d4cd6a1926c` (same runtime logic as final SHA; final change is test data only). Android **SDK 36**, AI Calls installed, default dialer **`com.google.android.dialer`**, both `get-role-holders` and `get-default-dialer` queries succeeded and agreed, phone `IDLE`, Shizuku process visible, app Shizuku binder and permissions `true`, microphone granted, `live_call_readiness=true`, `COMPLETE_SPRINT_NO_CALL_DEVICE_GATE=PASS`.
- **Read-only Telecom syntax diagnosis:** `aicalls-telecom-readonly-role-fallback-20261101-v1` — `done`/0. On the S22 `cmd role holders` failed as unsupported; supported syntax is `cmd role get-role-holders android.app.role.DIALER`. `cmd telecom get-default-dialer` returned `com.google.android.dialer`. The runtime probe now checks both sources and conservatively reports `UNKNOWN` if they disagree.
- **Honest failed-attempt history:** `aicalls-exact-capabilities-final-host-20261101-v1` had 3 test-fixture failures caused by incorrectly escaped newline literals, corrected at `31bc42f`. `aicalls-exact-capabilities-closeout-host-20261101-v1` reported a misleading exit 0 because its `trap rm -rf` masked an undefined shell variable; no host gate ran. The corrected `v2` task preserved exit status and actually ran the complete gate. Neither historical attempt is a PASS.

**Sprint result: COMPLETE for the diagnostic/negative-safety scope.** No dialing, hangup, role change, CLIR mutation, app installation or carrier transaction was performed. The product's app-owned controller remains unavailable; it must not be called completed by the tooling gate.

## Next product gate

Design and separately accept a call-specific controller that preserves the current default dialer, binds an actual outgoing session to the authorized account/target and disconnects only that exact session. Before any real call, prove revocation, callback duplication, unrelated-call preservation and exact terminal cleanup. Do not silently take over `ROLE_DIALER`, introduce device-wide hangup or classify carrier statements as independently verified CLIR state.
