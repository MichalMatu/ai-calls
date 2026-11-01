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
