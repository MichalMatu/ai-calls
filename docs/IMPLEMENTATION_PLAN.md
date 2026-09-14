# Implementation Plan

## Phase 0 — Repository bootstrap

Goal: create a clean architecture and define the proof gates before writing device-specific code.

Deliverables:
- repository structure;
- architecture document;
- Android/audio research notes;
- repeatable S22+ physical test protocol;
- minimal Kotlin interfaces for capture, injection and realtime transport.

Exit condition: repository documents make it impossible to confuse a hypothesis with a proven capability.

## Phase 1 — Cellular audio proof of concept

This phase deliberately contains **no AI**.

### 1A. Downlink capture

Build the smallest APK that can be launched during a normal cellular call and attempt each viable backend independently.

For every backend record:
- Android version / One UI version;
- phone model and build number;
- required permissions;
- whether the remote party is present in captured PCM;
- whether local microphone audio is also mixed in;
- sample rate/channel count;
- whether Bluetooth/speaker/earpiece routing changes the result;
- whether the approach survives screen-off and a 10-minute call.

A backend passes only when a generated recording can be inspected and the remote party is clearly present without relying on acoustic speaker-to-microphone pickup.

### 1B. Uplink injection

Do not connect a microphone or model yet. Feed a known generated test tone / spoken PCM sample into the proposed injection path.

Use a second phone as the remote endpoint and verify that the remote phone hears the injected signal through the cellular call.

Test:
- earpiece route;
- speaker route;
- wired/USB route if available;
- Bluetooth route only if relevant;
- repeated start/stop;
- interruption by the user's real microphone.

### Phase 1 gate

Proceed only when both are proven independently:

- `remote caller -> app PCM`
- `app PCM -> remote caller`

If only capture works, keep the result but do **not** start realtime integration.

## Phase 2 — Stable local bridge

Connect capture directly to injection through a controlled processing pipeline, still without network AI.

Implement:
- bounded ring buffers;
- monotonic timestamps;
- resampling abstraction;
- underrun/overrun counters;
- configurable frame size;
- latency measurement;
- hard stop / user takeover.

Use synthetic transforms so routing is obvious, for example a short delay or deterministic gain change. Avoid echo loops.

Exit condition: 10-minute bridge test without unbounded buffer growth, deadlock, runaway feedback or unrecoverable routing state.

## Phase 3 — Realtime AI integration

Connect `realtime-client` only after Phase 2 is stable.

Recommended shape:

```text
Android app -> small credential/session backend -> OpenAI Realtime
```

The backend owns the long-lived API credential. The Android client receives only a short-lived/session-scoped credential or uses a server-mediated connection.

Implement:
- session creation;
- audio streaming;
- output audio playback into the injector;
- server/semantic VAD experiment;
- barge-in / response cancellation;
- end-to-end latency metrics;
- reconnect behavior.

First prompt should be intentionally narrow, e.g. a cooperative test agent that introduces itself and repeats simple information.

### Phase 3 gate

A remote caller can speak, receive an AI answer, interrupt the answer and continue, with user takeover always available.

## Phase 4 — Product UX

Only after the transport works:
- call-session screen;
- `AI on/off`;
- `Take over now`;
- mute AI output;
- visible disclosure state;
- transcript toggle if enabled;
- per-call consent/retention controls;
- diagnostics screen.

Do not replace the default Android dialer until there is a concrete UX reason to do so.

## Phase 5 — Fallback paths

If normal cellular uplink injection is not achievable on the target device without unacceptable privileges:

1. evaluate Shizuku/shell-backed helper;
2. evaluate Samsung-specific/system integration only if reproducible;
3. evaluate root as a development/research-only option;
4. move the call transport itself to SIP/VoIP, where the application owns both audio directions.

VoIP is the clean fallback because the app controls the media stream directly; it is not the first path because the primary goal is normal cellular calling from the phone.

## Non-goals for the first implementation

- replacing Samsung Phone;
- contact management;
- call history replication;
- automatic unattended calling;
- cloud call recording;
- multiple AI personalities;
- production backend infrastructure.
