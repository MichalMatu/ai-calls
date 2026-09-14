# Cellular Audio PoC Test Plan

## Purpose

Prove or disprove digital access to both directions of a normal cellular call on the target Samsung Galaxy S22+ before adding AI or networking complexity.

This plan is the physical proof layer for the capability probe and backend experiments. API initialization alone never counts as proof.

## Evidence states

For every tested backend use one of:

- `UNAVAILABLE` — cannot initialize/use on this build;
- `AVAILABLE_UNPROVEN` — API/path appears available but media direction is not physically verified;
- `PROVEN_S22` — physical two-phone test passes;
- `FAILED_S22` — repeatable physical test fails on the recorded firmware build.

## Test equipment

- target Samsung Galaxy S22+;
- second phone with a different number;
- quiet room;
- USB cable / ADB-capable computer when logs are needed;
- Shizuku when testing shell-backed paths;
- generated deterministic reference audio containing spoken markers and short tones.

## Baseline metadata

Before each run record:
- model/product;
- Android version / API;
- One UI version;
- build fingerprint and security patch;
- app version / git SHA;
- privilege mode: normal / Shizuku-shell / root / system;
- active audio route;
- call direction;
- carrier path if known: cellular/VoLTE/Wi-Fi Calling;
- microphone mute state;
- screen state;
- relevant capability-probe result.

If firmware changes, treat previous `PROVEN_S22` results as requiring revalidation.

## Test 0 — Capability report

Run `DEVICE_CAPABILITY_PROBE.md` before the first media test on a firmware build.

Minimum required observations:
- Shizuku helper effective UID;
- `VOICE_DOWNLINK` initialization result;
- `TYPE_TELEPHONY` presence;
- generic telephony `AudioTrack` route-request result if applicable.

This test does not prove media access.

## Test A — Remote-only downlink capture

Preferred source: `VOICE_DOWNLINK` / scrcpy-style `voice-call-downlink` from the privileged helper.

1. Start a normal cellular call between S22+ and second phone.
2. Use earpiece route first.
3. Keep the S22+ user silent.
4. On second phone speak/play `REMOTE ALPHA 1 2 3` repeatedly.
5. Start the experimental capture for a short bounded interval.
6. Save a local diagnostic PCM/WAV only with explicit development intent.
7. Stop capture and close all resources.
8. Inspect the artifact offline.

Pass criteria:
- remote phrase is clearly present;
- signal is digital, not acoustic leakage from the S22+ speaker;
- format is stable/documented;
- no call teardown;
- no unexpected route switch.

Mark `PROVEN_S22` only when those criteria pass.

## Test B — Local-side isolation

Repeat Test A while the S22+ user says `LOCAL BRAVO 4 5 6`.

Classify captured data as:
- remote only — preferred;
- local only;
- remote + local mixed;
- silence/noise.

If the chosen backend is mixed, document that as a separate echo/source-separation risk before Phase 2.

## Test C — Capture routing matrix

After Test A passes on earpiece, repeat capture with:
- speakerphone;
- screen off;
- backgrounded app;
- Wi-Fi Calling enabled/disabled if available;
- Bluetooth only after basic routes work.

Do not declare all routes supported from the earpiece result alone.

## Test D — Generic uplink injection

Primary candidate: privileged `AudioTrack` directed to `AudioDeviceInfo.TYPE_TELEPHONY`.

1. Start a normal cellular call.
2. Keep the S22+ user silent.
3. Record whether `TYPE_TELEPHONY` exists.
4. Create the test `AudioTrack`.
5. Call `setPreferredDevice()` for telephony output and record its result.
6. Inject deterministic audio: `INJECT CHARLIE 7 8 9` plus a short tone.
7. Listen only on the second phone for the result.
8. Stop/flush the injector immediately.
9. Verify normal human speech still works without redialing.

Pass criteria:
- second phone hears the injected sample clearly;
- sample did not come from acoustic playback through the S22+ speaker;
- injection can be stopped cleanly;
- call remains active;
- repeated start/stop works.

Important: `TYPE_TELEPHONY` existing or `setPreferredDevice()` returning true is only `AVAILABLE_UNPROVEN`. The second phone hearing the sample is required for `PROVEN_S22`.

## Test E — Microphone interaction

After injection is proven, determine interaction with the physical microphone.

Run four short conditions where supported:
- injection active + mic unmuted + user silent;
- injection active + mic unmuted + user speaks;
- injection active + mic muted;
- injection stopped + mic restored.

Document whether the remote party receives:
- injected audio only;
- human voice only;
- both mixed;
- silence.

The result defines the Phase 2 microphone/takeover policy.

## Test F — Immediate takeover

While a deterministic injection stream is active:

1. queue enough audio to expose buffering behavior;
2. trigger `Take over` / local abort;
3. reject new injected frames;
4. flush queued output;
5. stop the injector;
6. restore/unmute the human microphone if controlled by the backend;
7. speak `HUMAN DELTA` immediately.

Pass criteria:
- injected audio stops with no long audible tail;
- `HUMAN DELTA` reaches the second phone without redialing;
- the call is not dropped;
- local abort does not require network access.

Measure approximate abort-to-silence latency.

## Test G — App/process death fail-safe

Only after injection works.

1. start a deterministic repeating injection;
2. kill the normal app process while keeping helper context alive if possible;
3. observe helper watchdog/death handling;
4. verify injection stops and does not restart;
5. verify human call path remains usable.

Repeat with:
- realtime/network connection removed later in Phase 3;
- helper/service disconnect;
- UI activity destroyed/backgrounded.

A stuck injection is a blocking failure.

## Test H — Local bridge stability

After independent capture and injection pass, connect them through the local processing pipeline without AI.

Run 10 minutes with:
- bounded buffers;
- timestamps;
- resampling if needed;
- queue-depth logging;
- underrun/overrun counters;
- occasional deterministic injected markers;
- screen on/off and foreground/background transition.

Avoid direct capture-to-injection echo loops. Use a controlled synthetic transform/test source until routing behavior is understood.

Pass criteria:
- no unbounded queue growth;
- no deadlock;
- no unrecoverable route change;
- stable memory;
- takeover still passes.

## Samsung-specific Test I

Run only if generic Test D fails and a Samsung-specific Phase 1C backend is implemented.

Use exactly the same second-phone criteria. A private Binder call returning success is not proof; the remote endpoint must receive the injected sample.

Record exact:
- firmware/build;
- service/interface used;
- privilege/UID;
- required permissions;
- route/call type;
- cleanup behavior.

## Evidence required to mark any backend `PROVEN_S22`

Store in the development notes:
- implementation git SHA;
- exact privilege requirements;
- full baseline metadata;
- route and call-type tested;
- actual audio format;
- observed remote/local channel behavior;
- latency estimate;
- stop/abort behavior;
- known failure modes.

A blog post, Android constant, Samsung feature or third-party recorder/player is useful research evidence but never substitutes for this physical test.