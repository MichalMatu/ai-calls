# Cellular Audio PoC Test Plan

## Purpose

Prove or disprove digital access to both directions of a normal cellular call on the target Samsung Galaxy S22+ before adding AI or networking complexity.

This plan is the physical proof layer for the capability probe and backend experiments. API initialization alone never counts as proof.

The 2026-09-14 off-call baseline is already saved in `S22_BASELINE_2026-09-14.md`. Do not repeat that inventory unless firmware/build changes. Resume from the live-call steps below when the dedicated SIM is available.

## Evidence states

For every tested backend use one of:

- `UNAVAILABLE` — cannot initialize/use on this build;
- `AVAILABLE_UNPROVEN` — API/path appears available but media direction is not physically verified;
- `PROVEN_S22` — physical two-phone test passes;
- `FAILED_S22` — repeatable physical test fails on the recorded firmware build.

## Test equipment

- target Samsung Galaxy S22+;
- dedicated SIM/number for the target phone;
- second consenting test phone with a different number;
- quiet room;
- USB cable / ADB-capable computer when logs are needed;
- Shizuku when testing the eventual app-integrated shell-backed path;
- generated deterministic reference audio containing spoken markers and short low-amplitude tones.

## Baseline metadata

Before each live run record:
- model/product;
- Android version / API;
- One UI version;
- build fingerprint and security patch;
- app version / git SHA;
- privilege mode: normal / direct shell / Shizuku-shell / root / system;
- active audio route;
- call direction;
- carrier path if known: cellular/VoLTE/Wi-Fi Calling;
- microphone mute state;
- screen state;
- relevant capability-probe result.

If firmware changes, treat previous `PROVEN_S22` results as requiring revalidation.

Never record or commit SIM numbers, IMEI, serial numbers, subscriber identifiers, or ADB credentials.

## Test 0 — Capability report

The initial off-call capability report is complete for the current build.

Current minimum observations already established:

```text
shell effective UID=2000                         PASS
shell CAPTURE_AUDIO_OUTPUT                       granted
shell MODIFY_AUDIO_ROUTING                       granted
shell MODIFY_PHONE_STATE                         granted
VOICE_DOWNLINK constructor                       initialized off-call
TYPE_TELEPHONY source                            present
TYPE_TELEPHONY sink                              present
USAGE_MEDIA AudioTrack to telephony off-call     cannot create
USAGE_VOICE_COMMUNICATION AudioTrack off-call    cannot create
```

The `AudioTrack` results above are not media failures because there was no active cellular call.

This test does not prove either media direction.

## Test A — Remote-only downlink capture

Preferred source: `VOICE_DOWNLINK` / scrcpy-style `voice-call-downlink` from the privileged helper/shell process.

1. Start a normal cellular call between S22+ and second phone.
2. Use earpiece route first.
3. Keep the S22+ user silent.
4. On second phone speak/play `REMOTE ALPHA 1 2 3` repeatedly.
5. Start the experimental capture for a short bounded interval (1–3 s initially).
6. Save a local diagnostic PCM/WAV only with explicit development intent; do not commit it to Git.
7. Stop capture and close all resources.
8. Inspect objective metrics first, then intelligibility/source direction.

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

Primary candidate: privileged `AudioTrack` with `AudioAttributes.USAGE_MEDIA` directed to `AudioDeviceInfo.TYPE_TELEPHONY`.

Rationale: the external AgentCall project physically qualified that route on another privileged Android device. This is only a candidate for our S22+, not transferable proof.

Comparison candidate if needed: `USAGE_VOICE_COMMUNICATION`, based on BCP-style precedent.

1. Start a normal cellular call.
2. Keep the S22+ user silent.
3. Record whether `TYPE_TELEPHONY` still exists and its current device ID.
4. Create the test `AudioTrack` using `USAGE_MEDIA` first.
5. Require `STATE_INITIALIZED`.
6. Call `setPreferredDevice()` for telephony output and record its result.
7. Start playback.
8. Inject only a deterministic low-amplitude fixture, initially a 1 kHz tone for about 400 ms at about 5% full-scale.
9. Require full write completion and record playback-head progress.
10. Listen only on the second phone for the result.
11. Stop/flush/release the injector immediately.
12. Verify normal human speech still works without redialing.

If `USAGE_MEDIA` fails, preserve the exact result before separately trying `USAGE_VOICE_COMMUNICATION`. Do not hide the first result behind automatic fallback.

Pass criteria:
- second phone hears the injected sample clearly;
- sample did not come from acoustic playback through the S22+ speaker;
- actual route is telephony when Android exposes routed-device state;
- playback progressed;
- injection can be stopped cleanly;
- call remains active;
- repeated start/stop works.

Important: `TYPE_TELEPHONY` existing, `AudioTrack` initializing, `setPreferredDevice()` returning true, or the playback head advancing are still only `AVAILABLE_UNPROVEN` without remote audible confirmation. The second phone hearing the sample is required for `PROVEN_S22`.

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

Run only if generic Test D fails **during an active call** and a Samsung-specific Phase 1C backend is implemented.

Use exactly the same second-phone criteria. A private Binder call returning success is not proof; the remote endpoint must receive the injected sample.

Record exact:
- firmware/build;
- service/interface used;
- privilege/UID;
- required permissions;
- route/call type;
- cleanup behavior.

Do not enter this test because the off-call `AudioTrack` constructor failed.

## Evidence required to mark any media backend `PROVEN_S22`

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

A blog post, Android constant, Samsung feature, constructor result, permission result, or third-party recorder/player is useful research evidence but never substitutes for this physical test.

## Execution handoff

The detailed implementation/checklist for Tests A and D is:

`docs/superpowers/plans/2026-09-14-phase1-live-call-validation.md`
