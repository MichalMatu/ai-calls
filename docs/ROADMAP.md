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
- `PRODUCT_READY` — proven, stable, fail-safe, and acceptable for normal use.

For media-direction claims, constructor success, permissions, routing requests, and playback-head progress are supporting evidence only. `PROVEN_S22` requires physical live-call evidence.

## Frozen known-good baseline

Phase 2B is preserved independently from ongoing development:

```text
branch: milestone/phase2b-proven-s22-20260916
commit: c10f8dde29f245f8f98fb008a3572c21fe73fe35
```

This branch is the rollback/comparison point for the physically proven cellular RX+TX path. Do not move or rewrite it during normal development.

Freeze policy and post-freeze audit notes: `PHASE2B_FREEZE_2026-09-16.md`.

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

The original “blocked on test SIM” state is obsolete. Live cellular tests were completed on 2026-09-16.

## Phase 1A — Digital cellular downlink capture

Status: `DONE / PROVEN_S22`

Production path:

```text
VOICE_DOWNLINK
  -> privileged shell/helper AudioRecord
  -> SamsungDownlinkPipeSession
  -> ParcelFileDescriptor PCM pipe
  -> controller/app
```

Physical proof on the target S22 shows remote-call-correlated PCM while the physical phone output remains muted. The production pipe wrapper is proven, not only the raw legacy probe.

Important target constraint:

```text
construct VOICE_DOWNLINK / controller.prepare()
BEFORE explicit Context/AudioManager initialization
```

The helper preserves that order.

See:
- `S22_PHASE2_LOCAL_BRIDGE_2026-09-16.md`
- `S22_PHASE1C_PROOF_2026-09-16.md`

## Phase 1B — Generic cellular uplink injection

Status: `FAILED_S22 FOR TESTED GENERIC PATHS`

The tested generic `USAGE_MEDIA` / `USAGE_VOICE_COMMUNICATION` approaches did not provide the required S22 cellular-uplink behavior.

They are no longer the primary architecture.

## Phase 1C — Samsung-specific uplink injection

Status: `DONE / PROVEN_S22`

Proven Samsung path:

```text
mono PCM16LE
  -> SamsungCallAssistantTrack
  -> USAGE_CALL_ASSISTANT / AUDIO_STREAM_CALL_ASSISTANT
  -> AUDIO_DEVICE_OUT_TELEPHONY_TX
  -> cellular uplink
```

Target-specific requirements:
- shell attribution `com.android.shell`;
- protected audio-routing/phone-state privilege class;
- live cellular call;
- Samsung CALL_ASSISTANT route;
- mono duplicated to stereo only at the Samsung TX boundary.

Physical remote-receipt proof and route diagnostics are recorded in `S22_PHASE1C_PROOF_2026-09-16.md`.

## Phase 2 — Stable local bridge

Status: `MILESTONE B PROVEN / MILESTONE C BLOCKED ON SHIZUKU RUNTIME`

### Milestone A — reusable Samsung media primitives

Status: `DONE`

Implemented:
- reusable CALL_ASSISTANT TX primitive;
- reusable VOICE_DOWNLINK capture primitive;
- bounded 20 ms media chunks;
- explicit PCM16LE format boundaries;
- immediate abort paths.

### Milestone B — shared bidirectional local controller

Status: `DONE / PROVEN_S22 / FROZEN`

Implemented and physically proven:
- production downlink PFD pipe;
- production uplink PFD pipe;
- one `SamsungCallMediaSessionController` owning RX + TX;
- separate required attribution contexts (`android` for RX, `com.android.shell` for TX);
- one heartbeat watchdog for the whole bidirectional generation;
- sibling abort if either media path terminates;
- immediate `abortNow()` cleanup;
- no per-frame Binder transport.

Successful silent live test on work-branch SHA:

```text
6b8d9f619031e0ca8352e55b624bdc9bc1edf633
```

Key live evidence:

```text
active_after_start=true
heartbeat_after_start=true

pre_dtmf_bytes_read=80000
pre_dtmf_non_zero_samples=25451
pre_dtmf_peak=21327
pre_dtmf_rms=2420.7548160336273

uplink_bytes_written=9600

post_dtmf_bytes_read=80000
post_dtmf_non_zero_samples=38737
post_dtmf_peak=19584
post_dtmf_rms=2566.8176657877357

active_with_endpoints_open=true
heartbeat_with_endpoints_open=true
media_ok_before_endpoint_close=true

prepared_after_abort=false
active_after_abort=false
heartbeat_after_abort=false
```

The phone remained physically silent throughout: `STREAM_VOICE_CALL Muted:true`, `streamVolume:0`, route `earpiece(1)`.

Full proof: `S22_PHASE2_LOCAL_BRIDGE_2026-09-16.md`.

Frozen rollback/reference branch:

```text
milestone/phase2b-proven-s22-20260916
```

The direct-shell `BidirectionalMediaProbe` remains the physical regression harness and should not be casually rewritten while Milestone C is still being established.

### Phase 2B post-freeze cleanup

Status: `SMALL SAFE CLEANUP ONLY`

Allowed before Milestone C:
- documentation synchronization;
- stricter probes that fail instead of false-pass;
- app-facing responsibility cleanup;
- tests and diagnostics;
- removal of code only after repository-wide proof that it is unused.

Current cleanup:
- privileged system/shell Context reflection moved out of `ShizukuCallMediaUserService` into `PrivilegedCallContexts`;
- UserService now focuses on Binder/media lifecycle;
- direct-shell proven probe was intentionally left unchanged;
- Shizuku off-call parity gate now requires the exact expected `prepare -> abort` state transition.

Deliberately deferred:
- generic RX/TX base classes;
- low-level Samsung media lifecycle rewrites;
- route/attribute changes;
- PFD ownership changes.

Reason: RX and TX have real asymmetric Samsung requirements; reducing line count is not worth weakening the proven boundary.

Known test item for Milestone D: `SamsungCallAssistantTrack.writeMonoPcm16Le()` uses a blocking `AudioTrack.write()` under its object lock. Writes are bounded to roughly 20 ms, but takeover latency must be measured rather than assumed.

### Milestone C — Shizuku UserService parity

Status: `CODE BUILDS / DEVICE RUNTIME BLOCKED`

Implemented:
- Shizuku API/provider integration;
- AIDL control plane;
- shell-side `ShizukuCallMediaUserService`;
- PFD endpoint handoff API;
- preserved `prepare()`-before-Context ordering;
- app-side permission/bind plumbing;
- bounded off-call `prepare -> abort` path;
- dedicated `PrivilegedCallContexts` boundary for hidden Android Context construction;
- strict off-call parity state assertions.

The first UserService slice was build-validated, and the subsequent responsibility extraction also passed helper/app Gradle tests plus the existing 19 Python call-control tests.

Current blocker:
- active Android user is user `0`;
- Shizuku is not installed or running for user `0` at the latest device check;
- user `151` is Samsung Secure Folder and is intentionally ignored;
- no trusted Shizuku APK already exists in the checked Mac/phone download locations.

This is an external runtime prerequisite, not a failure of the local bridge implementation.

Required Milestone C exit gate:
- real UserService runs as the expected privileged/shell UID;
- off-call `prepare -> abort` succeeds through Binder with exact expected states;
- live RX and TX match direct-shell physical behavior;
- PFD handoff works continuously;
- controller death/Binder death disables both directions;
- watchdog behavior remains fail-safe.

### Milestone D — endurance

Status: `PENDING MILESTONE C`

Required final Phase 2 exit gate:
- 10-minute local bidirectional bridge;
- stable memory;
- bounded queues;
- clean start/stop;
- immediate takeover/abort;
- measured takeover latency during active TX writes;
- no orphaned injection after controller/helper failure.

## Phase 3 — Realtime AI integration

Status: `NOT STARTED BY DESIGN`

Start only after Phase 2 Milestones C and D pass.

Initial architecture:

```text
Android app
  -> short-lived session credential backend
  -> OpenAI Realtime
```

The long-lived API key must never live in the APK.

First transport candidate remains WebSocket because the app already owns PCM frames and needs explicit stream control. Re-evaluate WebRTC only if measurements justify it.

Implement later:
- session lifecycle;
- audio send/receive;
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

## Phase 5 — Robustness matrix

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

## Fallback order

If the final app-facing privileged boundary cannot be made reliable on stock Samsung firmware:

1. compare against the frozen direct-shell Phase 2B baseline;
2. retain the proven direct-shell backend as a development/reference implementation;
3. evaluate another trusted privileged boundary only with explicit evidence;
4. SIP/VoIP transport where the app owns both media directions;
5. root/system-app research for development only;
6. external hardware only if the product requirement still justifies it.

## Current decision

Do not connect realtime AI yet.

The cellular media problem itself is no longer the blocker: bidirectional RX+TX is physically proven on the S22 and the production PFD controller works.

The proven state is frozen on `milestone/phase2b-proven-s22-20260916`.

The current next gate is **Shizuku UserService parity**. Code exists and builds, but the target user `0` has no Shizuku runtime installed at the latest check. Resume physical Milestone C validation only when a trusted Shizuku installation is available; do not silently fetch or install an unverified APK.

Current detailed evidence:

- `docs/PHASE2B_FREEZE_2026-09-16.md`
- `docs/S22_PHASE2_LOCAL_BRIDGE_2026-09-16.md`
