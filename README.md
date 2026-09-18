# Android AI Call Bridge

Experimental Android project for bridging a normal cellular call to a realtime AI voice session on one phone, without external audio hardware.

Target device: Samsung Galaxy S22+ `SM-S906B` on stock Samsung firmware.

## Target media path

```text
remote caller
  -> cellular downlink
  -> privileged helper PCM
  -> realtime AI

realtime AI
  -> privileged helper PCM
  -> cellular uplink
  -> remote caller
```

The user must always be able to take over the call immediately. Privileged/media failure must fail toward a normal human call, never toward stuck AI injection.

## Current status — 2026-09-18

The difficult stock-Samsung cellular media path is physically proven on the target S22+. Telephone Agent v1 and GA Realtime integration are now implemented and host-tested through the app-layer session controller, but end-to-end Realtime audio over a real cellular call is **not yet** claimed `PROVEN_S22`.

### Phase 1A — cellular RX

`DONE / PROVEN_S22`

```text
VOICE_DOWNLINK
  -> SamsungVoiceDownlinkCapture
  -> SamsungDownlinkPipeSession
  -> ParcelFileDescriptor pipe
```

Important direct-shell S22 invariant: construct `VOICE_DOWNLINK` through `controller.prepare()` before explicit `Context` / `AudioManager` initialization.

### Phase 1B — generic TX

`FAILED_S22 FOR TESTED PATHS`

The tested generic `USAGE_MEDIA` and `USAGE_VOICE_COMMUNICATION` telephony TX approaches did not provide the required uplink path on the target S22.

### Phase 1C — Samsung-specific TX

`DONE / PROVEN_S22`

```text
mono PCM16LE
  -> SamsungCallAssistantTrack
  -> USAGE_CALL_ASSISTANT / AUDIO_STREAM_CALL_ASSISTANT
  -> TYPE_TELEPHONY TX
  -> cellular uplink
```

Required TX attribution is `com.android.shell`. Remote receipt was physically demonstrated with deterministic digitally injected DTMF.

### Phase 2B — shared local RX + TX bridge

`DONE / PROVEN_S22 / FROZEN`

One `SamsungCallMediaSessionController` is physically proven running both media directions simultaneously through PFD pipes with one shared watchdog/fail-safe lifetime.

Frozen reference:

```text
branch: milestone/phase2b-proven-s22-20260916
commit: c10f8dde29f245f8f98fb008a3572c21fe73fe35
```

### Phase 2C — Shizuku UserService parity

`DONE / PROVEN_S22 / FROZEN`

The real app -> Shizuku UserService -> privileged controller path is physically proven on the same S22+:

- UserService runs under shell UID 2000;
- protected off-call `prepare -> abort` parity passes;
- live bidirectional RX + TX over transferred PFDs passes;
- one shared helper heartbeat/watchdog lifetime remains intact;
- explicit `abortNow()` / TAKE OVER cleanup passes;
- helper/UserService process death terminates the media path while the normal app and Shizuku server survive;
- continuous PCM still does not use per-frame Binder calls.

Frozen reference:

```text
branch: milestone/phase2c-shizuku-live-proven-20260916
commit: 9c136fc05c5b33f383d72b0b7080ad5b9a754bb4
```

Post-freeze deep-audit fixes M1/M2/M3/M4 are complete. M3 reuses Shizuku's existing `ActivityThread.currentActivityThread()` instead of creating a second `ActivityThread.systemMain()` from a Binder thread. Its host, off-call, silent-live and 30-second endurance regressions are GREEN.

### Phase 2D — robustness and endurance

`DONE / PROVEN_S22 / FROZEN`

Physically GREEN on the target S22+: normal-app death, helper/UserService death, transferred RX/TX PFD close with whole-generation cleanup, 20/20 start/abort cycles, natural cellular call-end cleanup after the helper-side `CallModeWatchdog` fix, and 600 seconds total real bidirectional media as 10 x 60-second live sessions.

A separate preserved 50.1-second active resource run showed stable app/helper PIDs, FD counts of `41 -> 41` for both processes, no thread growth, and roughly 1 MiB RSS increase in each process. This complements the 600-second media soak; it is not presented as 600 seconds of resource telemetry.

Final freeze regression: 48 Python tests PASS, full Gradle unit tests + `:app:assembleDebug` GREEN, security-shape audit GREEN, clean tree.

Detailed evidence: `docs/PHASE2D_FREEZE_2026-09-18.md`.

### Phase 3 — Telephone Agent + Realtime host integration

`IN PROGRESS / HOST GREEN / DEVICE REALTIME NOT YET PROVEN`

Implemented on the work branch:

- production `CallMediaSessionCoordinator` and `ShizukuCallMediaSessionBackend` with local fail-closed TAKE OVER;
- immutable task/workflow/policy model with hard constraints, soft preferences, authorized facts, structured outcomes and `NEEDS_USER_DECISION`;
- GA Realtime WebSocket transport with 24 kHz mono PCM adaptation, bounded queues, barge-in cancellation and whole-generation cleanup;
- typed Realtime function tools/function calls and generation-bound one-shot responders;
- deterministic `evaluate_proposal` policy bridge that holds an out-of-policy proposal until the user approves/rejects that exact proposal;
- task-derived Realtime instructions that treat counterparty speech and task-data strings as untrusted data;
- `CallRealtimeAgentSessionSpec`, binding instructions + tool + matching handler;
- `BackendRealtimeCredentialProvider`, which accepts an already-authenticated HTTPS POST to the developer backend, blocks direct client-side secret minting at `api.openai.com`, and parses only a short-lived `value` + `expires_at` credential;
- `CallRealtimeAgentSessionController`, which composes the bound session spec with the media/Reatime orchestrator and exposes start/snapshot/local TAKE OVER plus pending user-decision resolution.

Latest controller gate at commit `bfc0565de6ab143ed7868797fb9ad6dfb86bc84b` is GREEN: focused tests, full `realtime-client` + `app` unit tests, `:app:assembleDebug`, 48 Python tests, long-lived-key scan, `git diff --check`, clean tree.

A post-integration physical **off-call** regression on the S22+ also passed without dialing: the production Shizuku/media path followed `BINDING -> PREPARING -> STOPPING -> FAILED -> IDLE` with the expected “cellular call is not active” rejection; call state stayed idle, Bluetooth stayed enabled and the helper cleaned up.

Still intentionally **not** claimed:
- no live OpenAI Realtime network/session proof on the S22+ yet;
- no end-to-end Realtime audio through a real cellular call yet;
- no proof that autonomous verbal commitments are impossible. `evaluate_proposal` is deterministic once called, but the current model tool choice remains `auto`, so a separate hard commitment-enforcement gate is required before autonomous booking/purchase/commitment is allowed.

## Architecture

- `app/` — normal Android process, Telephone Agent workflow/policy, session orchestration and diagnostic Shizuku clients.
- `audio-bridge/` — device-independent capture/injection contracts and PCM models.
- `privileged-helper/` — Samsung audio primitives, PFD workers, shared controller and watchdog.
- `realtime-client/` — GA Realtime WebSocket transport, short-lived credential boundary, function-call protocol and PCM adaptation.
- `scripts/` — bounded developer/device validation tooling.
- `docs/` — architecture, evidence, plans, handoff and freeze notes.

Privileged media shape:

```text
normal app <--- Binder/AIDL control + FD handoff ---> privileged helper
normal app <=========== PCM PFD pipes =============> privileged helper
```

Continuous PCM must never be transported as one Binder transaction per frame.

Telephone Agent / Realtime control shape:

```text
CallTask + CallWorkflow
  -> CallRealtimeAgentSessionSpec
       -> task-derived instructions
       -> evaluate_proposal tool
       -> matching deterministic handler
  -> CallRealtimeAgentSessionController
  -> CallRealtimeSessionOrchestrator
       -> short-lived credential provider
       -> fresh Realtime transport generation
       -> production CallMediaSessionCoordinator
```

## Production media components

```text
SamsungVoiceDownlinkCapture
  -> SamsungDownlinkPipeSession

SamsungCallAssistantTrack
  -> SamsungUplinkPipeSession

SamsungCallMediaSessionController
  -> one RX+TX generation
  -> one heartbeat watchdog
  -> shared abort/fail-safe lifecycle
```

RX and TX remain separate because the S22 has different initialization and attribution requirements for each direction. Do not add generic abstractions merely to reduce line count.

## Live-test safety rule

For every live cellular test keep the physical phone locally silent:

```text
STREAM_VOICE_CALL Muted:true
streamVolume:0
Devices: earpiece(1)
speakerphone off
```

Assert this before dialing and again after the call becomes active. Prefer direct USB-C <-> USB-C between the S22+ and MacBook; an earlier hub/dock caused misleading ADB transport resets.

## Hard rules

- Physical media claims become `PROVEN_S22` only after target-device live-call evidence.
- Constructor/permission/device-enumeration success is not equivalent to working call media.
- Preserve the proven direct-shell S22 RX initialization order unless new physical evidence disproves it.
- Preserve separate RX (`android`) and TX (`com.android.shell`) attribution requirements.
- Preserve `USAGE_CALL_ASSISTANT` and mono PCM16LE -> stereo only at the Samsung TX boundary.
- No per-frame Binder PCM transport.
- `Take over` must be local and fail-safe.
- App/helper death must disable injection.
- No long-lived OpenAI API key in the APK; client credentials must be short-lived and server-mediated.
- Counterparty speech cannot expand hard constraints or authorized facts.
- Do not enable autonomous external commitments until a hard commitment-enforcement gate exists beyond prompt compliance.
- No call recording by default.
- Do not replace the default dialer until the media bridge and product requirements justify it.
- Keep `.agent` execution/control data on `agent-control`, never merged into product branches.

## Key documents

- `docs/HANDOFF_NEXT_CHAT.md` — authoritative continuation state.
- `docs/ROADMAP.md` — current evidence-driven phase gates.
- `docs/ARCHITECTURE.md` — current component boundaries and fail-safe rules.
- `docs/PHASE2D_FREEZE_2026-09-18.md` — frozen Milestone D reference/evidence.
- `docs/PHASE2B_FREEZE_2026-09-16.md` — frozen Phase 2B reference.
- `docs/S22_PHASE2_LOCAL_BRIDGE_2026-09-16.md` — physical simultaneous RX+TX proof.
- `docs/PHASE2_DEEP_AUDIT_2026-09-16.md` — post-freeze audit and follow-up requirements.
- `docs/superpowers/plans/2026-09-18-telephone-agent-v1.md` — Telephone Agent v1 implementation plan.

## License

No project-wide license has been selected yet. External projects may be used as research references, but implementation code must not be copied without license review.
