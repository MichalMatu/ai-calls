# Cellular Audio PoC Test Plan

## Purpose

Prove or disprove digital access to both directions of a normal cellular call on the target Samsung Galaxy S22+ before adding any AI or networking complexity.

## Test equipment

- target Samsung Galaxy S22+;
- second phone with a different number;
- quiet room;
- USB cable / ADB-capable computer for logs when needed;
- generated reference audio file with spoken markers and test tones.

## Baseline metadata to record

Before each test run capture:

- device model;
- Android version;
- One UI version;
- build number;
- app version / git SHA;
- active audio route;
- SIM / cellular call type if known (VoLTE, Wi-Fi Calling, etc.);
- granted permissions;
- whether ADB/Shizuku/root/system privileges are involved.

## Test A — downlink capture

1. Start a normal cellular call between the S22+ and second phone.
2. On the second phone, play/read a deterministic phrase such as `REMOTE ALPHA 1 2 3`.
3. Keep the S22+ user silent for the first sample.
4. Start the experimental capture backend.
5. Save only a short local PCM/WAV diagnostic artifact.
6. Stop capture.
7. Inspect the artifact offline.

Pass criteria:

- remote phrase is clearly present;
- capture is digital rather than speaker-to-microphone leakage;
- format is stable and documented;
- no app crash or call teardown.

Repeat with:
- earpiece;
- speakerphone;
- screen off;
- call held/resumed if supported;
- Wi-Fi Calling enabled/disabled if relevant.

## Test B — local microphone isolation

Repeat Test A while the S22+ user says `LOCAL BRAVO 4 5 6`.

Determine whether capture contains:
- remote only;
- local only;
- mixed remote + local;
- silence.

This distinction matters for echo cancellation later.

## Test C — uplink injection

1. Place the cellular call.
2. Keep the S22+ user silent.
3. Start the injection backend.
4. Inject a deterministic short PCM sample, e.g. spoken `INJECT CHARLIE 7 8 9` followed by a short tone.
5. Listen on the second phone.
6. Stop injection immediately after the sample.

Pass criteria:

- second phone hears the injected sample clearly;
- sample does not depend on acoustic playback through the S22+ speaker;
- stopping injection restores normal call behavior;
- repeated start/stop works.

## Test D — takeover

While injection is active:

1. trigger `Take over`;
2. injection must cease immediately;
3. user speaks normally;
4. second phone must hear the user without restarting the call.

Target: no perceptible delay beyond normal UI reaction time.

## Test E — stability

If A–D pass, run a 10-minute test:

- capture continuously;
- inject short synthetic responses at intervals;
- switch screen on/off;
- briefly background/foreground the app;
- monitor buffer depth, underruns, overruns and exceptions.

## Evidence required for a backend to be marked `PROVEN`

Every proven backend needs:

- exact source path / implementation SHA;
- exact privilege requirements;
- test metadata listed above;
- short description of observed routing behavior;
- latency estimate;
- known failure modes.

A blog post, API constant, Samsung feature or third-party recorder is evidence that a path may exist; it is **not** proof that our app can use that path bidirectionally.
