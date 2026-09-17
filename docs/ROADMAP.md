# Project Roadmap

This roadmap is evidence-driven. A phase advances only when its exit gate is proven on the target Samsung Galaxy S22+ running stock Samsung firmware.

## Core product goal

Use one Android phone to bridge a normal cellular call to a realtime AI voice session without external hardware:

```text
remote caller -> cellular downlink -> app/helper PCM -> realtime AI
realtime AI -> app/helper PCM -> cellular uplink -> remote caller
```

The user must be able to take over the call immediately at any time.

## Evidence levels

- `HYPOTHESIS` — plausible but not demonstrated on the target device.
- `SUPPORTED_EXTERNALLY` — demonstrated elsewhere, but not yet on the target S22+.
- `PROVEN_S22` — reproduced on the target S22+ with logged metadata and a repeatable physical test.
- `FAILED_S22` — reproducibly failed on the target S22+ under the documented conditions.
- `PRODUCT_READY` — proven, stable, fail-safe and acceptable for normal use.

For media-direction claims, constructor success, permissions, routing requests and playback-head progress are supporting evidence only. `PROVEN_S22` requires physical live-call evidence.

## Frozen references

Phase 2B local media baseline:

```text
branch: milestone/phase2b-proven-s22-20260916
commit: c10f8dde29f245f8f98fb008a3572c21fe73fe35
```

Phase 2C Shizuku live parity baseline:

```text
branch: milestone/phase2c-shizuku-live-proven-20260916
commit: 9c136fc05c5b33f383d72b0b7080ad5b9a754bb4
```

Do not move or rewrite these branches during normal development.

## Phase 0 — Repository and test discipline

Status: `DONE`

Delivered:
- modular Android project;
- CI/build discipline;
- explicit capture/injection interfaces;
- physical device-test protocol;
- separation of normal app code from privileged experiments;
- durable implementation plans and hardware-evidence notes.

## Phase 0.5 — Device capability baseline

Status: `DONE`

Baseline evidence: `S22_BASELINE_2026-09-14.md`.

Proven target facts include:
- SM-S906B / Android 16 / API 36 / One UI 8;
- shell UID 2000 execution;
- shell grants for protected call-audio capabilities;
- `TYPE_TELEPHONY` sink and source presence;
- shell construction of protected call sources;
- normal-app inability to create those protected sources.

## Phase 1A — Digital cellular downlink capture

Status: `DONE / PROVEN_S22`

```text
VOICE_DOWNLINK
  -> privileged shell/helper AudioRecord
  -> SamsungDownlinkPipeSession
  -> ParcelFileDescriptor PCM pipe
  -> controller/app
```

The production pipe path produces remote-call-correlated PCM while the physical phone remains locally muted.

Direct-shell target constraint:

```text
construct VOICE_DOWNLINK / controller.prepare()
BEFORE explicit Context/AudioManager initialization
```

## Phase 1B — Generic cellular uplink injection

Status: `FAILED_S22 FOR TESTED GENERIC PATHS`

The tested generic `USAGE_MEDIA` / `USAGE_VOICE_COMMUNICATION` approaches did not provide the required S22 cellular-uplink behavior.

## Phase 1C — Samsung-specific uplink injection

Status: `DONE / PROVEN_S22`

```text
mono PCM16LE
  -> SamsungCallAssistantTrack
  -> USAGE_CALL_ASSISTANT / AUDIO_STREAM_CALL_ASSISTANT
  -> AUDIO_DEVICE_OUT_TELEPHONY_TX
  -> cellular uplink
```

Target requirements:
- shell attribution `com.android.shell`;
- protected routing/phone-state privilege class;
- live cellular call;
- Samsung CALL_ASSISTANT route;
- mono duplicated to stereo only at the Samsung TX boundary.

## Phase 2 — Stable local bridge

Status: `MILESTONES B+C PROVEN / MILESTONE D ACTIVE`

### Milestone A — reusable Samsung media primitives

Status: `DONE`

Delivered:
- reusable VOICE_DOWNLINK capture primitive;
- reusable CALL_ASSISTANT TX primitive;
- bounded media chunks;
- explicit PCM16LE boundary;
- immediate abort paths.

### Milestone B — shared bidirectional local controller

Status: `DONE / PROVEN_S22 / FROZEN`

Implemented and physically proven:
- production downlink and uplink PFD pipes;
- one `SamsungCallMediaSessionController` owning RX + TX;
- separate required attribution contexts (`android` RX, `com.android.shell` TX);
- one heartbeat watchdog for the whole bidirectional generation;
- sibling abort if either media path terminates;
- immediate `abortNow()` cleanup;
- no per-frame Binder transport.

Reference branch: `milestone/phase2b-proven-s22-20260916`.

### Milestone C — Shizuku UserService parity

Status: `DONE / PROVEN_S22 / FROZEN`

Physically proven through the real normal-app -> Shizuku UserService -> privileged controller boundary:
- UserService effective UID 2000;
- protected off-call `prepare -> abort` parity;
- live bidirectional RX + TX over transferred PFDs;
- shared heartbeat/watchdog semantics;
- explicit `abortNow()` / TAKE OVER cleanup;
- PFD ownership and client AutoClose behavior;
- UserService/helper process death while media is active, with app-side EPIPE and survival of the normal app + Shizuku server.

Reference branch: `milestone/phase2c-shizuku-live-proven-20260916`.

### Post-freeze deep-audit fixes

Status: `M1/M2/M3/M4 COMPLETE`

- M1: long-running Shizuku probes moved off the app main Looper.
- M2: privileged automation removed from exported `MainActivity`; shell diagnostics moved to `DiagnosticProbeActivity` protected by `android.permission.DUMP`.
- M3: `PrivilegedCallContexts` reuses Shizuku's existing `ActivityThread.currentActivityThread()` instead of preparing a Binder-thread Looper and constructing a second `ActivityThread.systemMain()`.
- M4: bidirectional diagnostic probe media has deterministic owner/cleanup semantics.

M3 behavior commit:

```text
60cbe81af2e02ba2e4100691c8afb76332735548
```

After M3 the following are GREEN:
- full host build/tests;
- off-call Shizuku parity;
- silent live Shizuku parity;
- 30-second bidirectional endurance.

### Milestone D — robustness and endurance

Status: `ACTIVE`

Already GREEN:
- 30-second bidirectional endurance with continuous heartbeat and active-state checks;
- explicit abort/takeover cleanup and bounded latency;
- helper/UserService process-death behavior;
- latest host-only regression after app-death tooling: 30 Python tests PASS and full Gradle build/test PASS.

#### Current unresolved gate — normal app death

The first normal-app-death attempt is **inconclusive**, not a confirmed product defect.

Observed:
- CALL_ASSISTANT was active before app termination;
- `am force-stop pl.michalmatu.aicallbridge` was issued;
- host ADB entered `waiting for device` during the timing-critical loop;
- after transport recovery both normal app and UserService processes were gone;
- the expected CALL_ASSISTANT stop log was not observed by the disrupted host-side collector;
- therefore the reported ~105 s host-side cleanup duration is invalid as a media cleanup measurement.

A resilient phone-side harness now exists:

```text
scripts/s22_app_death_gate.py
scripts/test_s22_app_death_gate.py
```

It measures process cleanup with `/proc/uptime`, observes CALL_ASSISTANT stop on-device, checks call/Shizuku/boot continuity and atomically publishes a result for later host collection. This avoids dependence on uninterrupted host ADB. The S22 does not provide `toybox nohup`, so the observer is launched with a background shell process instead.

Focused harness tests: `11/11 PASS`.
Repository Python tests after adding it: `30/30 PASS`.
Full Gradle host build/test: `GREEN`.

#### Remaining Milestone D gates

After normal-app-death is measured reliably:

1. end the cellular call while bridge media is active and prove complete cleanup;
2. intentionally close one transferred RX/TX PFD and prove sibling abort / whole-generation stop;
3. run 10–20 start/abort cycles and compare FD/thread/process/resource counts;
4. run final 10-minute bidirectional endurance with telemetry including RX/TX bytes, heartbeat count, active state, app/helper RSS, FD count, thread count, AudioRecord/AudioTrack state and final abort latency;
5. run final regression/security/evidence audit;
6. freeze Milestone D.

## Phase 3 — Realtime AI integration

Status: `NOT STARTED BY DESIGN`

Start only after Milestone D is frozen.

Initial architecture:

```text
Android app
  -> short-lived session credential backend
  -> OpenAI Realtime
```

The long-lived API key must never live in the APK.

First transport candidate remains WebSocket because the app already owns PCM frames and needs explicit stream control. Re-evaluate WebRTC only if measurements justify it.

Planned later:
- session lifecycle;
- input/output audio streaming;
- local barge-in detection;
- immediate local injection flush on remote speech;
- model response cancellation;
- reconnect/failure behavior;
- end-to-end latency measurement.

## Phase 4 — Product UX

After Phase 3 media works:
- call session screen;
- AI on/off;
- `Take over now`;
- mute AI;
- disclosure state;
- optional transcript;
- diagnostics;
- call-state integration.

Do not replace the default dialer unless a concrete UX requirement demands it.

## Phase 5 — robustness matrix

Validate:
- 30+ minute calls;
- background/foreground transitions;
- screen off;
- app/helper death;
- network loss;
- route changes;
- Bluetooth;
- Wi-Fi Calling;
- incoming/outgoing calls;
- hold/resume;
- repeated sessions without reboot.

Exit gate: defined failure behavior for every tested transition and no condition that leaves AI injection stuck active.

## Current decision

Do not connect realtime AI yet.

The cellular RX/TX problem and the Shizuku privilege boundary are no longer blockers. The active engineering work is **Milestone D robustness**, starting with a reliable physical normal-app-death measurement using the phone-side observer harness.

Current authoritative continuation state: `docs/HANDOFF_NEXT_CHAT.md`.
