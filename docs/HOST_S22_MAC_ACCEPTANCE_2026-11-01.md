# AI Calls — Mac 4.20.16 Wireless ADB preflight and host QA sprint (2026-11-01)

## Correct task transport

The active Mac Host Control queue lives in private `MichalMatu/local-agent`, branch `agent-control`, under
`.agent/hosts/macbook-michal/supervisor/cfea4cc3-b54c-4b6d-b9a6-758d12733e98/`.
This is **not** the legacy `MichalMatu/ai-calls:agent-control/.agent/tasks` project queue.
The former project queue's Oct 19 heartbeat cannot establish Mac host liveness.

The AI Calls source migration to the new contract was committed as `cf6fe805a2ce36bad43ab246cbb8824f5a8251d4`. Read `docs/LOCAL_AGENT_HOST_FLOW_2026-11-01.md` and current `AGENTS.md`.

## Real Mac -> S22 acceptance

Task `aicalls-mac-wireless-preflight-20261101-v1` was published on the proper Mac host queue and finished **done / exit 0** on **2026-11-01T09:54:29Z**, with the result in the exact `task-results/` namespace.

Observed:
- Initial `adb devices -l`: no attached devices.
- `adb mdns services`: `adb-RFCT70L7E8J-HNMIWH` / `_adb-tls-connect._tcp` at `192.168.0.100:41703`.
- `adb connect 192.168.0.100:41703` succeeded.
- New `adb devices -l`: `192.168.0.100:41703 device model:SM_S906B`.
- Exact `ro.product.model=SM-S906B` and `ro.serialno=RFCT70L7E8J` verified on the connected transport.
- `dumpsys telephony.registry` indicated `mCallState=0` (`IDLE`).
- This was a **read-only telephone preflight**, not a voice call, Shizuku/audio/CLIR test or app/Gradle acceptance.

The Wireless Debugging TCP port is not static. Always discover it from live mDNS or another verified connected ADB target, never reuse archived October addresses.

## Following host tasks

Two separate tasks were submitted to the same Mac namespace:

1. `aicalls-mac-host-quality-20261101-v1`: fresh disposable checkout of `MichalMatu/ai-calls:main`; record tested SHA, Python unittest, then canonical `scripts/local_agent/host_verify.sh` (Gradle unit tests, lint, APK assemble, Python). No telephone operations.
2. `aicalls-mac-s22-app-readiness-20261101-v1`: disposable fresh checkout, `scripts/local_agent/device_readiness.sh` for exact S22 phone `IDLE` and app/Shizuku readiness; no dialing.

At this checkpoint, no terminal `task-results` have yet been observed for these two tasks. Their publication is **not** a test PASS. Read exact results before debugging or marking `HOST_GREEN` / app `PROVEN_S22`. Do not replay an uncertain task. Host execution may be delayed by work on other queues.

## Remaining product blocker

The Android `CampaignCallControllerRegistry` lacks a real call-specific controller, so autonomous CLIR dialing is still fail-closed. This task did **not** start `*100`, change CLIR, or globally disconnect any calls. Don't bypass targeted call lifecycle or explicit grants to force a physical test.


## Physical Mac QA and discovered corrections — same-day continuation

These terminal host results came from the real Mac 4.20.16 installation, not a simulated sandbox.

- `aicalls-mac-host-quality-20261101-v1`, tested AI Calls source `cf6fe805`: **185 Python unit tests PASS**. Gradle's Android JVM unit test stage **BUILD SUCCESSFUL**. The combined host gate then **FAILED at `:app:lintDebug`**, 4 errors and 105 warnings. First `MissingPermission` occurred in `AppOwnedClirCampaignExecutor` global `telecomManager.isInCall` cleanup. Source fix `a8df31f` eliminated global-call-state post-cleanup inference; it now reports only `targeted_disconnect_requested`.
- `aicalls-mac-lint-all-errors-20261101-v1`, tested `a8df31f`: 3 remaining lint errors, all in `CampaignPhoneAccountResolver`: `getDefaultOutgoingPhoneAccount` and `callCapablePhoneAccounts` require checked `READ_PHONE_STATE`, and `TelephonyManager.subscriptionId` is API 30 while minSdk is 29. Source fixes `bd9aec7`, `0ce0d65` enforce permission checking and a fail-closed Android 10 (API 29) branch.
- `aicalls-mac-s22-app-readiness-20261101-v1`, tested `4b23ee5`: Mac's Bash 3.2 with `set -u` treats the empty `DISCOVERY_ARGS[@]` as unbound, so discovery failed before contacting phone. Source fix `4576160` uses safe positional arguments.
- `aicalls-mac-s22-app-readiness-20261101-v2`, tested `4576160`: Wireless ADB device discovery and telephony IDLE passed far enough for Android app probe; **FAILED with `shizuku_binder_unavailable`**. This proves a separate Shizuku availability issue, not a loss of Wireless ADB.

Follow-up host tasks:
- `aicalls-mac-host-quality-20261101-v2` — repeat full canonical host QA on corrected source.
- `aicalls-mac-shizuku-readonly-diagnose-20261101-v1` — inspect installed Shizuku/server and AI Calls package, read-only.

At this revision those two jobs have no confirmed terminal result. Do not mark final host gate green. Do not attempt any Orange `*100` call or global hangup. Shizuku health is an independent prerequisite and must not be silently bypassed.

## Shizuku restored — verified Mac/S22 acceptance, 2026-11-01 10:22:13 UTC

After the operator manually started Shizuku on S22, the new host task `aicalls-shizuku-user-start-readiness-20261101-v1` in `MichalMatu/local-agent:agent-control` published `done`, exit 0. The task checked AI Calls source `cb4ebdd6a94033c80dc6cbec2942983833076c90` from a disposable checkout.

- Wireless ADB transport `192.168.0.100:41703`; verified exact hardware identity by `s22_adb_discovery`.
- `shizuku_server` process exists; `moe.shizuku.privileged.api` running.
- Android app probe reported `record_audio_granted=true`, `shizuku_binder_available=true`, `shizuku_supported=true`, `shizuku_permission_granted=true`, `live_call_readiness=true`.
- Wrapper reported `device_readiness=true`, `call_state=IDLE`, `readiness_gate=PASS`.
- Independently, host quality task `aicalls-mac-host-quality-20261101-v2` published `done`, exit 0 at 10:06:41 UTC for exact **source** `0ce0d6586b59f8956fd14f1266f14f75ac9548a4`: all canonical Android Gradle unit tests/lint/assembleDebug/assembleDebugAndroidTest passed, and 185 Python tests passed. Later commits before Shizuku readiness were docs-only.

**Accepted now:** Mac host quality at the tested source SHA; verified Wireless ADB and app-level Shizuku/audio/no-call readiness at the recorded timestamp. **Not accepted:** real call-specific Android controller, autonomous dial, any live operator call, or independent CLIR status. Shizuku startup may need repetition after reboot; do not extrapolate one status snapshot to future runtime availability.
