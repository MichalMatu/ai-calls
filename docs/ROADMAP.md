# Project Roadmap

This roadmap is evidence-driven. A phase advances only when its exit gate is proven on the target device. The initial target is the Samsung Galaxy S22+ running stock Samsung firmware.

## Core product goal

Use one Android phone to bridge a normal cellular call to a realtime AI voice session without external hardware.

Required media path:

```text
remote caller -> cellular downlink -> app PCM -> realtime AI
realtime AI -> app PCM -> cellular uplink -> remote caller
```

The user must be able to take over the call immediately at any time.

## Evidence levels

- `HYPOTHESIS` — plausible but not demonstrated on the target device.
- `SUPPORTED_EXTERNALLY` — demonstrated by Android documentation or another project, but not yet on the target S22+.
- `PROVEN_S22` — reproduced on the target S22+ with logged metadata and a repeatable test.
- `PRODUCT_READY` — proven, stable, fail-safe, and acceptable for normal use.

For media-direction claims, `PROVEN_S22` requires the physical two-phone test. Constructor success, permissions, device enumeration, and routing requests are capability evidence only.

Do not promote an item because a similar Samsung feature exists. Vendor functionality is evidence that a path exists internally, not proof that a third-party process can access it.

## Phase 0 — Repository and test discipline

Status: `DONE`

Deliverables:
- modular Android project;
- CI build;
- explicit capture/injection interfaces;
- physical two-phone test protocol;
- separation of ordinary app code from privileged experiments;
- Superpowers-based development workflow and durable implementation-plan convention.

Exit gate: repository builds and hypotheses are clearly separated from proven behavior.

## Phase 0.5 — Device capability probe

Status: `BASELINE COMPLETE / LIVE-CALL ITEMS PENDING`

The first capability probe was run on the physical target S22+ on 2026-09-14. Durable evidence is stored in `S22_BASELINE_2026-09-14.md`.

Already reproduced on this target build:

- model/build/Android/One UI identity;
- shell UID 2000 execution;
- shell permission grants for `CAPTURE_AUDIO_OUTPUT`, `MODIFY_AUDIO_ROUTING`, and `MODIFY_PHONE_STATE`;
- `TYPE_TELEPHONY` sink and source device presence;
- shell initialization of `VOICE_CALL`, `VOICE_DOWNLINK`, and `VOICE_UPLINK`;
- normal-app inability to create those protected call sources.

Observed while **no cellular call was active**:

- `AudioTrack` construction targeting telephony failed for both `USAGE_MEDIA` and `USAGE_VOICE_COMMUNICATION` attempts.

This is not a negative Phase 1B verdict because the in-call telephony route may only be openable while a real cellular call is active.

Still pending from the Phase 0.5/Phase 1 boundary:

- active-call downlink start/read/data proof;
- active-call telephony TX construction/routing;
- Shizuku UserService onboarding/end-to-end reproduction after raw shell behavior is known.

Pause reason: a dedicated test SIM/number is not yet available.

Exit gate for baseline capability inventory: **met**.

Live-media gates remain Phase 1A/1B.

## Phase 1A — Digital cellular downlink capture

Status: `READY FOR LIVE TEST / BLOCKED ON TEST SIM`

Primary path:

```text
Shizuku UserService / shell
  -> VOICE_DOWNLINK / scrcpy-style direct capture
  -> raw PCM pipe
  -> app process
```

Prefer remote-only downlink over a mixed `VOICE_CALL` stream. Remote-only PCM avoids feeding local microphone audio and AI output back into the model.

Current target-device evidence:

- shell can initialize `VOICE_DOWNLINK` off-call;
- `TYPE_TELEPHONY` source is exposed;
- actual in-call remote PCM has not yet been read/verified.

Test routes after base pass:
- earpiece;
- speakerphone;
- screen on/off;
- Wi-Fi Calling if available;
- Bluetooth only after the base route works.

Exit gate: remote speech is present digitally in PCM on the S22+, with no acoustic speaker-to-microphone dependency.

## Phase 1B — Generic cellular uplink injection

Status: `READY FOR LIVE TEST / BLOCKED ON TEST SIM`

First live-call experiment:

```text
shell/privileged process
  -> AudioTrack (USAGE_MEDIA first)
  -> TYPE_TELEPHONY preferred output
  -> cellular uplink
```

`USAGE_MEDIA` is now the primary candidate because the external AgentCall project reports a physically qualified Telephony TX route with that usage on another privileged Android device. `USAGE_VOICE_COMMUNICATION` remains a comparison candidate based on BCP-style precedent.

Current target-device evidence:

- `TYPE_TELEPHONY` sink is exposed;
- shell has the relevant protected permission grants;
- off-call `AudioTrack` creation failed for both tested usages;
- no active-call construction/routing attempt has yet been made;
- no remote audible injection has yet been tested.

Inject only a deterministic local PCM sample. Do not connect OpenAI yet.

Measure:
- whether track construction succeeds in-call;
- whether `setPreferredDevice()` succeeds;
- actual routed device if available;
- full write count and playback-head progress;
- whether the remote phone actually receives the injected audio;
- interaction with microphone mute/unmute;
- stop and immediate abort behavior.

Exit gate: second phone clearly hears injected PCM without acoustic playback.

## Phase 1C — Samsung-specific injection research

Run only if Phase 1B fails **during an active call** with reproducible evidence.

Research the class of mechanisms used by Samsung call features such as Text Call and InCallUI. Treat firmware services, Binder interfaces, hidden APIs, vendor audio policy, and privileged permissions as implementation-specific research targets, not stable APIs.

Rules:
- isolate Samsung-specific code behind one backend;
- do not patch the OS for the primary path;
- document exact privilege requirements;
- do not infer accessibility from the existence of a Samsung feature;
- do not start Phase 1C merely because the off-call `AudioTrack` constructor failed.

Exit gate: either a repeatable stock-firmware injection path is proven, or the cellular path is declared blocked under the current no-root constraints.

## Phase 2 — Stable local bridge

Status: `BLOCKED ON PHASE 1A + 1B/1C`

Connect proven capture and injection backends without AI.

Implement:
- raw PCM transport through pipe/local socket rather than per-frame Binder transactions;
- bounded buffers;
- explicit PCM encoding metadata;
- timestamps and latency metrics;
- resampling boundaries;
- underrun/overrun counters;
- call-state handling;
- local microphone mute policy;
- immediate `abortNow()` path;
- helper watchdog and process-death fail-safe.

`Take over` semantics:

```text
1. stop accepting AI output
2. flush queued injection audio
3. stop/abort injector
4. restore human microphone path
5. keep the cellular call alive
```

Exit gate: 10-minute local bridge test with stable memory, bounded queues, clean stop/start and immediate human takeover.

## Phase 3 — Realtime AI integration

Only after Phase 2 passes.

Initial architecture:

```text
Android app
  -> short-lived session credential backend
  -> OpenAI Realtime
```

The long-lived API key never lives in the APK.

First transport candidate: WebSocket because the app already owns PCM frames and needs explicit stream control. Re-evaluate WebRTC later if measurements justify it.

Implement:
- session lifecycle;
- audio send/receive;
- local barge-in detection;
- immediate local injection flush on remote speech;
- model response cancellation;
- reconnect/failure behavior;
- end-to-end latency measurement.

Exit gate: remote caller can converse with the AI, interrupt it naturally, and the user can take over instantly.

## Phase 4 — Product UX

Only now add product polish:
- call session screen;
- AI on/off;
- `Take over now`;
- mute AI;
- clear disclosure state;
- optional transcript view;
- diagnostics page;
- call-state integration.

Do not replace the default dialer unless a concrete UX requirement demands it.

## Phase 5 — Robustness matrix

Validate:
- 30+ minute calls;
- background/foreground transitions;
- screen off;
- process death;
- network loss;
- route changes;
- Bluetooth;
- Wi-Fi Calling;
- incoming/outgoing calls;
- hold/resume;
- repeated sessions without reboot.

Exit gate: defined failure behavior for every tested transition and no condition that leaves AI injection stuck active.

## Fallback order

If stock-Samsung cellular injection cannot be achieved within acceptable privileges:

1. SIP/VoIP transport where the app owns both media directions;
2. root/system-app research for development only;
3. external Bluetooth/hardware bridge only if the product requirement still justifies it.

Termux is not a primary architecture. It may be useful as an experimental environment, but the product path remains a native Android app.

## Current decision

Do not implement additional AI/product layers now.

The repository is intentionally paused at the physical live-call gate until a dedicated SIM is available. Resume from:

`docs/superpowers/plans/2026-09-14-phase1-live-call-validation.md`

The first live action is bounded `VOICE_DOWNLINK` capture during a real call. The second is deterministic `USAGE_MEDIA -> TYPE_TELEPHONY` injection during the same class of call. Only physical remote/local evidence can advance Phase 1.