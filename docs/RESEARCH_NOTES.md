# Research Notes

_Last reviewed: 2026-09-14_

This document separates platform facts, external precedents and project hypotheses. Nothing is considered proven for the Samsung Galaxy S22+ media direction until it passes the physical tests in `POC_AUDIO_TEST_PLAN.md`.

The target-device capability facts collected on 2026-09-14 are preserved separately in `S22_BASELINE_2026-09-14.md`.

## Evidence labels

- `CONFIRMED_PLATFORM` — supported by official Android/project documentation.
- `EXTERNAL_PRECEDENT` — another project demonstrates the behavior on some devices/configurations.
- `TARGET_CAPABILITY_PROVEN` — reproduced on the exact S22+ but does not yet prove live media direction.
- `TARGET_HYPOTHESIS` — plausible for our S22+ but not yet reproduced.
- `PROVEN_S22` — reproduced on the target S22+ with the physical two-phone media proof required by `POC_AUDIO_TEST_PLAN.md`.

## Public Android constraints

### Voice-call capture sources

`MediaRecorder.AudioSource` exposes `VOICE_CALL`, `VOICE_UPLINK` and `VOICE_DOWNLINK`. Capturing these sources requires privileged capability such as `CAPTURE_AUDIO_OUTPUT`; an ordinary third-party application cannot simply request that permission at runtime.

Status: `CONFIRMED_PLATFORM`.

Target-device update: the normal S22+ app cannot construct these call sources, while direct shell UID 2000 can construct all three. This is `TARGET_CAPABILITY_PROVEN`, not yet live-media proof.

Official reference:
- https://developer.android.com/reference/android/media/MediaRecorder.AudioSource

### Playback capture is not the cellular-call solution

`AudioPlaybackCapture` / MediaProjection captures eligible application playback and obeys usage/capture policy constraints. It is not a general path to cellular voice-call downlink.

Status: `CONFIRMED_PLATFORM`.

References:
- https://developer.android.com/media/platform/av-capture
- https://developer.android.com/reference/android/media/AudioPlaybackCaptureConfiguration

## scrcpy direct audio capture

Current scrcpy supports direct audio sources including:
- `voice-call`;
- `voice-call-uplink`;
- `voice-call-downlink`;
- microphone sources;
- raw PCM codec output.

It executes device-side server code with shell/ADB capability and is therefore relevant to our privileged-helper design rather than to a normal Play-style process.

Status: `EXTERNAL_PRECEDENT` for the mechanism.

Target-device update: shell UID 2000 on the S22+ can construct `VOICE_DOWNLINK`, which materially strengthens this path, but actual remote-party PCM is still pending the live call.

Reference:
- https://github.com/Genymobile/scrcpy/blob/master/doc/audio.md

License note: scrcpy is Apache-2.0. Any code reuse must preserve applicable license/notice obligations.

## ShizuCallRecorder

ShizuCallRecorder is an open-source call recorder built around Shizuku/ADB plus a scrcpy-style audio pipeline. Its project documentation states that it records both sides of carrier calls without root and supports Android 12 through 16 in its tested-version matrix.

Architecturally important observations from the source:
- shell-side process owns the privileged capture operation;
- audio bytes are relayed through a pipe/file descriptor to the app process;
- Binder/AIDL is used as control/FD boundary rather than transporting every audio frame;
- it exposes call-specific audio sources including downlink-only.

Status: `EXTERNAL_PRECEDENT`.

This substantially increases confidence in the capture half, but it does not prove our exact S22+ live-call behavior.

License note: ShizuCallRecorder is GPL-3.0-or-later with additional terms. Do not copy its implementation into this repository unless the entire licensing consequence is intentionally accepted. Use it as a research/reference implementation.

Reference:
- https://github.com/kitsumed/ShizuCallRecorder

## Basic Call Player (BCP) and telephony output injection

BCP is a system/privileged Android tech demo that plays an audio file to the remote party of an active phone call.

Its implementation:
- obtains `AudioDeviceInfo.TYPE_TELEPHONY`;
- creates an `AudioTrack` with `USAGE_VOICE_COMMUNICATION`;
- routes the track with `setPreferredDevice()`;
- writes decoded PCM into that track.

The project requires privileged `MODIFY_PHONE_STATE` and system-app style installation.

Status: `EXTERNAL_PRECEDENT` for digital call injection.

Critical limitation: BCP documents that the telephony output device is device-dependent. A Samsung A52 user reported the path did not work; the BCP author noted that most devices may not implement it and that modem/vendor support is involved.

Implication: `USAGE_VOICE_COMMUNICATION -> TYPE_TELEPHONY` remains a comparison experiment, not an assumed solution for S22+.

Reference:
- https://github.com/chenxiaolong/BCP

## AgentCall external precedent

`sidinsearch/AgentCall` is a cellular-call gateway project that reports independently qualified digital telephony RX and TX on a privileged/rooted Xiaomi POCO M2 Pro reference device.

Relevant published evidence:

- downlink uses `VOICE_DOWNLINK` and an actual `TYPE_TELEPHONY` RX route;
- uplink uses `AudioTrack` with `AudioAttributes.USAGE_MEDIA` explicitly routed to `TYPE_TELEPHONY` TX;
- physical remote-party confirmation is required for TX qualification;
- their published probe records playback-head progress and route state instead of treating `setPreferredDevice()` alone as success;
- the production privileged path requires protected permissions including `CAPTURE_AUDIO_OUTPUT`, `MODIFY_AUDIO_ROUTING`, and `MODIFY_PHONE_STATE`.

Status: `EXTERNAL_PRECEDENT` only.

Important differences from our target:

- reference phone is Xiaomi/Qualcomm, not Samsung/Exynos;
- their qualified path uses root/Magisk/system privilege;
- our primary target remains stock Samsung without root;
- their project is AGPL-3.0, so it is a research/design reference, not implementation code to copy into this repository.

Project impact: `USAGE_MEDIA -> TYPE_TELEPHONY` is now our first Phase 1B active-call candidate. `USAGE_VOICE_COMMUNICATION` remains the second candidate.

References:
- https://github.com/sidinsearch/AgentCall
- https://github.com/sidinsearch/AgentCall/blob/main/docs/phase0-direct-agent-gateway.md

## Samsung evidence

Samsung firmware/components use privileged permissions around InCallUI, including capabilities such as call/audio routing and output capture in vendor builds. Samsung also ships call features such as Bixby/Samsung Text Call that can convert the caller's speech to text and synthesize typed text back into the call.

This is strong evidence that Samsung's own telephony stack contains a software-accessible media path on supported devices.

Status: `EXTERNAL_PRECEDENT` / vendor capability evidence.

It is **not** proof that:
- the path is exposed to shell UID;
- Shizuku can call the same private interface;
- the interface is stable across One UI releases;
- the S22+ build in hand exposes the same mechanism to third-party code.

If generic `TYPE_TELEPHONY` injection fails during a real call, Samsung-specific research becomes Phase 1C rather than the first implementation.

## Shizuku as privilege boundary

Shizuku can start a UserService in a privileged service context backed by ADB/shell (or root when configured that way). For our intended no-root experiment the relevant case is shell/ADB capability.

Status: `CONFIRMED_PLATFORM` for the execution model.

Target-device update: direct ADB shell probing proved the relevant UID 2000 capability class on the S22+, but Shizuku itself has not yet been integrated/authorized/tested end-to-end in this project.

Important rule: do not translate "running through Shizuku" into "all signature/system permissions are available". Each API call can still have UID, SELinux, app-op, call-state or service-specific checks.

## S22+ target capability findings — 2026-09-14

The physical target is `SM-S906B`, Android 16 / API 36 / One UI 8.0, build `S906BXXSOGZH3`.

### Confirmed target capability facts

Status: `TARGET_CAPABILITY_PROVEN`.

- normal app has user-granted `RECORD_AUDIO`;
- normal app cannot construct `VOICE_CALL`, `VOICE_DOWNLINK`, or `VOICE_UPLINK`;
- stock firmware exposes `TYPE_TELEPHONY` sink and source devices;
- direct ADB shell runs as UID 2000;
- shell permission checks return granted for `CAPTURE_AUDIO_OUTPUT`, `MODIFY_AUDIO_ROUTING`, and `MODIFY_PHONE_STATE`;
- shell can construct initialized `VOICE_CALL`, `VOICE_DOWNLINK`, and `VOICE_UPLINK` records off-call.

### Off-call injection observation

With no cellular call active, shell `AudioTrack` construction failed for both:

- `USAGE_MEDIA`;
- `USAGE_VOICE_COMMUNICATION`.

Error class: `UnsupportedOperationException: Cannot create AudioTrack`.

Status: unresolved, not `FAILED_S22`.

Reason: no active modem/call route was present. A live-call test is required before deciding that Samsung blocks generic telephony TX.

Detailed snapshot: `S22_BASELINE_2026-09-14.md`.

## Current risk split

### Downlink capture

Confidence: high at capability level; live media still unproven.

Evidence:
- Android defines the needed source;
- scrcpy implements it under shell-style privilege;
- ShizuCallRecorder demonstrates a modern non-root Shizuku integration;
- our exact S22+ shell process can initialize `VOICE_DOWNLINK`.

Remaining proof: start/read during a real call and physically verify remote-only audio.

### Uplink injection

Confidence: uncertain / highest project risk, but now better constrained.

Evidence:
- BCP proves one generic style on some devices;
- AgentCall proves a `USAGE_MEDIA -> TYPE_TELEPHONY` style on another privileged Android device;
- our exact S22+ exposes a telephony TX sink;
- our shell identity has the relevant protected permissions;
- off-call track construction fails, but active-call behavior has not been tested.

Remaining proof: active-call S22+ telephony TX test followed by remote audible verification.

## Realtime integration

OpenAI Realtime supports native realtime audio sessions and multiple transport styles. This layer is not currently the blocking problem.

Project decision:
- keep `realtime-client` transport-independent;
- initially favor a WebSocket implementation once the local PCM bridge works because the app already owns explicit PCM frames and cancellation semantics;
- evaluate WebRTC later using measured latency/reliability, not assumptions;
- keep the long-lived API key off-device.

Official reference:
- https://platform.openai.com/docs/guides/realtime

## Unresolved hypotheses

These must become isolated experiments:

1. During an active carrier call, does S22+ shell `VOICE_DOWNLINK` start and produce remote-party PCM?
2. Does downlink capture remain remote-only across earpiece/speaker and screen-off states?
3. During an active call, can a shell `AudioTrack` using `USAGE_MEDIA` be created and routed to `TYPE_TELEPHONY`?
4. If `USAGE_MEDIA` fails, does `USAGE_VOICE_COMMUNICATION` behave differently on the same call?
5. Does the second phone actually hear injected PCM without acoustic playback?
6. Can the human microphone be muted/restored independently while software injection continues?
7. If generic injection fails, which Samsung service/audio-policy path powers Text Call on this exact firmware?
8. Does Wi-Fi Calling use materially different capture/injection routing?
9. What is the lowest reliable local abort latency for queued injection audio?
10. Does a Shizuku UserService reproduce the direct shell capability without new restrictions?

## Decision rules

- Do not start AI integration after capture alone passes.
- Do not mark injection as working from `setPreferredDevice(true)` alone.
- Do not mark a media direction as working from constructor initialization alone.
- Do not treat the off-call `AudioTrack` failure as an in-call failure.
- Do not infer Samsung private API accessibility from firmware package permissions.
- Do not root the primary target merely to keep the cellular design alive; first make an explicit fallback decision.
- Keep the final product architecture replaceable enough to use SIP/VoIP if stock cellular injection proves inaccessible.

## Current pause point

The next physical experiment is intentionally deferred until a dedicated SIM is available. Resume from:

`docs/superpowers/plans/2026-09-14-phase1-live-call-validation.md`
