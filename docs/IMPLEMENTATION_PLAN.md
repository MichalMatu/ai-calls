# Implementation Plan

This implementation plan follows the evidence gates in `ROADMAP.md`. The project must not advance because an API exists or another device supports a feature; it advances only after the target Samsung Galaxy S22+ passes the relevant physical test.

## Phase 0 — Bootstrap

Status: complete.

Existing deliverables:
- modular Android project;
- CI build on Android 16 API 36;
- capture and injection interfaces;
- realtime transport abstraction;
- physical two-phone PoC protocol;
- isolated privileged-helper area.

## Phase 0.5 — Device Capability Probe

This is the next implementation task.

Build a small diagnostic layer that can run checks in both the normal app process and a Shizuku UserService/shell process.

### 0.5A — Normal-process probe

Collect:
- device/build metadata;
- Android/One UI version where obtainable through supported properties/APIs;
- current audio devices;
- active communication route;
- call-state metadata available without becoming the default dialer;
- Shizuku installed/running/authorization state.

### 0.5B — Shell-process probe

Through Shizuku UserService record:
- effective UID;
- relevant permission checks;
- audio input/output device visibility;
- initialization/start result for `VOICE_DOWNLINK`, `VOICE_UPLINK`, `VOICE_CALL`, and a control source;
- whether `TYPE_TELEPHONY` output exists;
- whether a test `AudioTrack` can request that preferred device.

No AI, no long recording and no Samsung private API calls in this phase.

### Exit condition

Save one reproducible capability report with exact S22+ firmware metadata. Use its results to select Phase 1 experiments.

## Phase 1A — Prove cellular downlink capture

Primary implementation candidate:

```text
Shizuku UserService / shell
  -> scrcpy-derived/direct voice-call-downlink capture
  -> RAW PCM
  -> ParcelFileDescriptor pipe
  -> app-side diagnostic consumer
```

Prefer `VOICE_DOWNLINK` rather than mixed `VOICE_CALL` whenever the target firmware supports it.

### Implementation tasks

- add Shizuku dependency and permission/onboarding only as needed for the experiment;
- create minimal shell helper lifecycle;
- create capture pipe and return its read end to the app;
- start a direct capture source in the privileged process;
- use raw PCM first to avoid codec/muxer complexity;
- save at most a short opt-in diagnostic WAV/PCM sample for analysis;
- log format, route, source and privilege metadata.

Do not copy GPL implementation code from ShizuCallRecorder. It is a research reference. If scrcpy server code is reused/derived, preserve its Apache 2.0 license obligations and attribution.

### Test gate

Use the second phone and deterministic phrase from `POC_AUDIO_TEST_PLAN.md`.

Pass only if remote speech is digitally present without acoustic speaker pickup.

## Phase 1B — Prove generic cellular uplink injection

This is the highest-risk generic Android gate.

### Minimal experiment

In the privileged helper:

1. enumerate output devices;
2. locate `AudioDeviceInfo.TYPE_TELEPHONY`;
3. create `AudioTrack` using voice-communication usage;
4. request the telephony device with `setPreferredDevice()`;
5. feed a deterministic locally generated mono PCM sample;
6. verify on the second phone that the sample is heard through the cellular call;
7. stop immediately and restore the normal call path.

Do not connect a microphone or model.

### Required observations

Record:
- device presence;
- route-request result;
- actual routed-device information if available;
- audio write errors;
- remote audible result;
- microphone mute/unmute interaction;
- cleanup behavior after repeated start/stop.

### Exit condition

The second phone receives the injected digital sample. API success without remote audio is a failure.

## Phase 1C — Samsung-specific injection research

Run only if Phase 1B fails.

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

The next code change after this documentation rework should implement **Phase 0.5 Device Capability Probe**, not Realtime.