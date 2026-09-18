# Handoff — Milestone D frozen / Telephone Agent v1 next

Date: 2026-09-18

Repository: `MichalMatu/android-ai-call-bridge`

Work branch: `work/phase1-live-call-probes`

Local Agent binding:

```text
agent_binding: c25f88c0-4682-414c-8062-c47fa4034cb0
repository_id: android-ai-call-bridge
control_branch: agent-control
```

## Start rule

Milestone D is closed. Do not rerun Phase 2 physical gates or expand the diagnostic harness unless a concrete regression requires it.

Read `AGENTS.md`, this handoff, `docs/PHASE2D_FREEZE_2026-09-18.md`, `docs/ROADMAP.md`, `docs/ARCHITECTURE.md`, `docs/SECURITY_PRIVACY.md`, and `docs/PHASE2_DEEP_AUDIT_2026-09-16.md`.

## Frozen earlier checkpoints

```text
Phase 2B:
milestone/phase2b-proven-s22-20260916
c10f8dde29f245f8f98fb008a3572c21fe73fe35

Phase 2C:
milestone/phase2c-shizuku-live-proven-20260916
9c136fc05c5b33f383d72b0b7080ad5b9a754bb4
```

## Milestone D final evidence

Physical GREEN on the target S22+: normal app death during active media, helper/UserService death, transferred RX and TX PFD close, 20/20 start/abort cycles, natural call end after the `CallModeWatchdog` fix, and 600 seconds total live bidirectional media as 10 x 60 seconds.

Resource trend was closed with one additional short live run, not another ten calls:
- 24 external samples / 50.1 seconds, call active throughout;
- app: RSS 123128 -> 124196 KiB, FD 41 -> 41, threads 27 -> 25;
- helper: RSS 152932 -> 153948 KiB, FD 41 -> 41, threads 19 -> 19;
- stable app/helper PIDs;
- cleanup left call idle, helper absent and Bluetooth restored.

Final regression:
- 48/48 Python tests PASS;
- full Gradle unit tests + `:app:assembleDebug` GREEN;
- security-shape GREEN;
- `git diff --check` GREEN;
- clean product tree.

Do not claim the 50.1-second resource trend is 600 seconds of resource telemetry. The 600-second proof is media-plane endurance.

## Preserve these invariants

Preserve direct-shell RX ordering, Shizuku attribution ordering, RX system attribution, TX `com.android.shell` attribution, CALL_ASSISTANT / TELEPHONY_TX, internal mono PCM16LE, stereo duplication only at the Samsung TX boundary, PFD AutoClose ownership, one shared RX+TX fail-safe generation, endpoint loss -> whole-generation cleanup, no per-frame Binder, local TAKE OVER, helper heartbeat, and `CallModeWatchdog` unless concrete regression evidence requires change.

## Next product work — Telephone Agent v1

Do not make `MainActivity` or diagnostic probes the production lifecycle owner.

First introduce production app-side call-media orchestration:
- `CallMediaSessionCoordinator`;
- `IDLE / BINDING / PREPARING / ACTIVE / STOPPING / FAILED`;
- generation/session id and failure reason;
- direct Binder death handling;
- structured telemetry;
- owned heartbeat and PFD lifetime;
- local immediate TAKE OVER.

Then add the user-level task/workflow model with target/action/service, date/time/price/insurance/NFZ/private constraints, authorized user facts, business resolution, `RESEARCHING -> READY_TO_DIAL -> DIALING -> ACTIVE_NEGOTIATION -> NEEDS_USER_DECISION? -> COMPLETED/FAILED`, and structured outcome.

Realtime AI is a conversation engine inside that orchestrator. Before implementing it, verify current official OpenAI Realtime documentation. Never put a long-lived OpenAI API key in the APK; use short-lived/server-mediated credentials. No call recording by default.

Safe test number `510100100` remains authorized only if a future physical regression genuinely requires it. Keep the phone silent, Bluetooth off during the call, mute before dial and after ACTIVE, speakerphone off, and restore Bluetooth afterward.
