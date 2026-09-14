# Implementation Plan

This implementation plan follows the evidence gates in `ROADMAP.md`. The project must not advance because an API exists or another device supports a feature; it advances only after the target Samsung Galaxy S22+ passes the relevant physical test.

For active engineering work, follow the repository's Superpowers adaptation in `DEVELOPMENT_WORKFLOW.md` and save detailed task plans under `docs/superpowers/plans/`.

## Phase 0 — Bootstrap

Status: complete.

Existing deliverables:
- modular Android project;
- CI build on Android 16 API 36;
- capture and injection interfaces;
- realtime transport abstraction;
- physical two-phone PoC protocol;
- isolated privileged-helper area;
- Local Agent execution path from GitHub to the physical S22+;
- Superpowers-based development workflow and planning convention.

## Phase 0.5 — Device Capability Probe

Status: baseline complete; live-call-dependent observations remain pending.

Durable result: `S22_BASELINE_2026-09-14.md`.

### 0.5A — Normal-process probe

Completed observations on the target S22+:
- device/build metadata recorded;
- Android 16 / API 36 / One UI 8.0 baseline recorded;
- `RECORD_AUDIO` granted by the user;
- communication/audio device inventory collected;
- `TYPE_TELEPHONY` sink and source are visible;
- protected call sources fail to construct in the ordinary app process, as expected.

### 0.5B — Shell-process probe

Completed observations through direct ADB shell / `app_process`:
- effective UID is `2000(shell)`;
- `CAPTURE_AUDIO_OUTPUT`, `MODIFY_AUDIO_ROUTING`, and `MODIFY_PHONE_STATE` are granted to shell on this build;
- `VOICE_CALL`, `VOICE_DOWNLINK`, and `VOICE_UPLINK` all create initialized `AudioRecord` instances off-call;
- telephony sink/source devices remain visible from shell;
- off-call `AudioTrack` construction targeting telephony failed for both `USAGE_MEDIA` and `USAGE_VOICE_COMMUNICATION` attempts.

The `AudioTrack` result is not a live-call failure classification. The in-call route may only exist while AudioPolicy is in an active cellular call state.

### Still pending

- actual `startRecording()` + PCM read from `VOICE_DOWNLINK` during a carrier call;
- actual in-call `AudioTrack` creation/routing to the telephony sink;
- Shizuku UserService onboarding/end-to-end reproduction after raw shell capability is settled.

### Exit condition

The baseline capability inventory is complete enough to proceed to Phase 1 physical tests. The project is paused until a dedicated SIM/number is available.

## Phase 1A — Prove cellular downlink capture

Status: ready for live physical test; blocked only on the dedicated SIM/test call prerequisite.

Primary implementation candidate:

```text
Shizuku UserService / shell
  -> VOICE_DOWNLINK / scrcpy-style direct capture
  -> RAW PCM
  -> ParcelFileDescriptor pipe
  -> app-side diagnostic consumer
```

Prefer `VOICE_DOWNLINK` rather than mixed `VOICE_CALL` whenever the target firmware supports it.

### Current evidence

- shell privilege class can initialize `VOICE_DOWNLINK` on the exact S22+ build;
- telephony RX device exists;
- useful remote-party PCM has not yet been physically observed.

### Next implementation/test tasks

Use the detailed Superpowers plan:

`docs/superpowers/plans/2026-09-14-phase1-live-call-validation.md`

That plan adds only the bounded diagnostic behavior needed to:
- start/read `VOICE_DOWNLINK` during an active call;
- report objective PCM metrics;
- prove remote/local channel behavior with a second phone;
- store only redacted evidence, never call audio in Git.

Do not copy GPL implementation code from ShizuCallRecorder. It is a research reference. If scrcpy server code is reused/derived, preserve its Apache 2.0 license obligations and attribution.

### Test gate

Use the second phone and deterministic phrase from `POC_AUDIO_TEST_PLAN.md`.

Pass only if remote speech is digitally present without acoustic speaker pickup.

## Phase 1B — Prove generic cellular uplink injection

Status: ready for active-call experiment after/alongside Phase 1A; blocked on test SIM.

This remains the highest-risk generic Android gate.

### Primary experiment

In the privileged helper/shell context during an active cellular call:

1. enumerate output devices;
2. locate `AudioDeviceInfo.TYPE_TELEPHONY`;
3. create `AudioTrack` using `AudioAttributes.USAGE_MEDIA` first;
4. request the telephony device with `setPreferredDevice()`;
5. feed a deterministic locally generated low-amplitude PCM tone;
6. verify write count and playback-head progress;
7. verify on the second phone that the tone is heard through the cellular call;
8. stop immediately and restore the normal call path.

Why `USAGE_MEDIA` first: the external AgentCall project reports a physically qualified Telephony TX path using that usage on another privileged Android device. This is architectural evidence only. `USAGE_VOICE_COMMUNICATION` remains a comparison path based on BCP-style precedent.

Do not connect a microphone or model.

### Required observations

Record:
- device presence;
- track-construction result while in-call;
- route-request result;
- actual routed-device information if available;
- audio write count/errors;
- playback-head progress;
- remote audible result;
- microphone mute/unmute interaction;
- cleanup behavior after repeated start/stop.

### Exit condition

The second phone receives the injected digital sample. API success without remote audio is a failure. Off-call constructor failure is not sufficient to reject this path.

## Phase 1C — Samsung-specific injection research

Run only if Phase 1B fails reproducibly **during an active call**.

The objective is not to clone Samsung Phone. The objective is to determine whether stock firmware exposes a privilege-bounded software-to-call route that our helper can access.

Research areas:
- Samsung InCallUI and telephony package metadata;
- vendor audio policy and available audio devices;
- relevant Binder/system services;
- hidden Android APIs;
- permissions used by Samsung call features;
- behavior differences between cellular, VoLTE and Wi-Fi Calling paths.

Samsung Text Call/Bixby Text Call is evidence that Samsung's own stack has a software media bridge. It is not an accessible API assumption.

### Rules

- all Samsung-specific code stays behind a dedicated backend;
- version/build checks are mandatory;
- no firmware modification for the primary product path;
- document every required permission/UID;
- stop if the only practical route becomes root/system-image modification and move to the documented fallback decision.

### Exit condition

Either:
- stock-firmware injection is proven, or
- cellular injection is marked blocked under current no-root constraints.

## Phase 2 — Stable local bridge

Start only when capture and injection are both independently proven.

### Transport

Control plane:
- Binder/AIDL for start/stop/probe/status;
- pass file descriptors across Binder.

Media plane:
- PCM pipe or local socket;
- bounded app-side buffers;
- no per-frame Binder transactions.

### Audio processing

Implement:
- explicit signed PCM format metadata;
- mono path first;
- resampling abstraction;
- monotonic timestamps;
- bounded ring/jitter buffers;
- underrun/overrun counters;
- queue-depth metrics;
- one-way and round-trip latency measurements where possible.

### Human microphone policy

Determine whether injection mixes with or replaces the physical microphone.

For autonomous AI mode, define a deterministic microphone policy. `Take over` must restore the human microphone without redialing the call.

### Immediate abort

Extend injector semantics with a hard local abort/flush operation. Graceful `stop()` is insufficient for takeover and barge-in.

### Watchdog

Implement helper fail-safe:
- Binder death recipient and/or heartbeat;
- if app disappears during injection, flush and stop locally;
- close all tracks/records/pipes;
- release temporary routing state.

### Exit condition

10-minute local bridge test with:
- bounded memory;
- no stuck audio;
- repeatable start/stop;
- screen background/foreground transition;
- instant takeover;
- no need to redial after takeover.

## Phase 3 — Realtime AI

Only after the local bridge passes.

### Credential model

```text
Android app -> project backend -> short-lived/session credential
                                 -> OpenAI Realtime
```

Never embed a long-lived API key in the APK.

### Initial transport

Start with WebSocket because the application already owns PCM and explicit stream timing. Keep the transport abstraction so WebRTC can be evaluated later using measurements rather than assumptions.

### Implementation tasks

- session creation;
- input audio append/stream;
- response audio receive;
- output resampling into injector format;
- connection health and reconnect policy;
- latency metrics;
- narrow initial model instructions.

### Barge-in

Remote speech detection must first silence local AI injection, then cancel model generation.

```text
remote speech start
  -> local injector flush/abort
  -> realtime response cancel
```

Do not wait for the server before silencing the caller-facing output.

### Exit condition

Remote caller can:
- speak to the AI;
- hear a response;
- interrupt the response;
- continue naturally;
while the device owner can take over immediately.

## Phase 4 — Product UX

Only after functional voice bridging:
- session screen;
- AI on/off;
- `Take over now`;
- AI mute;
- visible helper/privilege state;
- visible disclosure/consent state;
- optional transcript;
- diagnostics page;
- meaningful errors and recovery actions.

Do not build a replacement dialer unless call-state/user-flow requirements make it necessary.

## Phase 5 — Robustness

Test a matrix of:
- incoming and outgoing calls;
- 30+ minute duration;
- repeated calls;
- screen off;
- app backgrounded;
- app process killed;
- Shizuku restarted/lost;
- Internet lost/recovered;
- route changes;
- speaker/earpiece;
- Bluetooth;
- Wi-Fi Calling;
- hold/resume;
- remote hang-up during AI output.

Every transition needs a deterministic failure result. No test may leave injection active after the owner believes it is stopped.

## Fallback decision

If cellular injection on stock Samsung firmware cannot be achieved without unacceptable privilege or OS modification:

1. move transport to SIP/VoIP so the app owns both media directions;
2. keep root/system-app work as research-only;
3. consider a Bluetooth/external bridge only if the single-device requirement is relaxed.

Termux may be used for experiments, but it is not the target application architecture.

## Explicit non-goals until the media gate passes

- replacing Samsung Phone;
- full contact management;
- call history replication;
- autonomous unattended outbound calling;
- cloud call recording;
- multiple AI personas;
- tool execution by the voice model;
- production backend scaling;
- polished consumer onboarding.

## Current handoff

Do not add more platform architecture while waiting for the SIM. The next engineering session should start by reading:

1. `docs/S22_BASELINE_2026-09-14.md`;
2. `docs/POC_AUDIO_TEST_PLAN.md`;
3. `docs/superpowers/plans/2026-09-14-phase1-live-call-validation.md`.

Then resume the bounded active-call tests rather than repeating the off-call capability inventory.