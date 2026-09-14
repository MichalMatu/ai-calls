# Research Notes

_Last reviewed: 2026-09-14_

This document separates platform facts, external precedents and project hypotheses. Nothing is considered proven for the Samsung Galaxy S22+ until it passes the physical tests in `POC_AUDIO_TEST_PLAN.md`.

## Evidence labels

- `CONFIRMED_PLATFORM` — supported by official Android/project documentation.
- `EXTERNAL_PRECEDENT` — another project demonstrates the behavior on some devices/configurations.
- `TARGET_HYPOTHESIS` — plausible for our S22+ but not yet reproduced.
- `PROVEN_S22` — reproduced on the target S22+ build.

## Public Android constraints

### Voice-call capture sources

`MediaRecorder.AudioSource` exposes `VOICE_CALL`, `VOICE_UPLINK` and `VOICE_DOWNLINK`. Capturing these sources requires privileged capability such as `CAPTURE_AUDIO_OUTPUT`; an ordinary third-party application cannot simply request that permission at runtime.

Status: `CONFIRMED_PLATFORM`.

Implication: the normal APK is not the correct process for the cellular capture experiment. Privileged work belongs in `privileged-helper`.

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

Status: `EXTERNAL_PRECEDENT` for the mechanism, `TARGET_HYPOTHESIS` for actual S22+ cellular downlink behavior.

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

This substantially increases confidence in the capture half, but it does not prove our exact S22+ firmware behavior.

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

Implication: `TYPE_TELEPHONY` injection is our first generic experiment, not an assumed solution for S22+.

Reference:
- https://github.com/chenxiaolong/BCP

## Samsung evidence

Samsung firmware/components use privileged permissions around InCallUI, including capabilities such as call/audio routing and output capture in vendor builds. Samsung also ships call features such as Bixby/Samsung Text Call that can convert the caller's speech to text and synthesize typed text back into the call.

This is strong evidence that Samsung's own telephony stack contains a software-accessible media path on supported devices.

Status: `EXTERNAL_PRECEDENT` / vendor capability evidence.

It is **not** proof that:
- the path is exposed to shell UID;
- Shizuku can call the same interface;
- the interface is stable across One UI releases;
- the S22+ build in hand exposes the same mechanism to third-party code.

If generic `TYPE_TELEPHONY` injection fails, Samsung-specific research becomes Phase 1C rather than the first implementation.

## Shizuku as privilege boundary

Shizuku can start a UserService in a privileged service context backed by ADB/shell (or root when configured that way). For our intended no-root experiment the relevant case is shell/ADB capability.

Status: `CONFIRMED_PLATFORM` for the execution model, but individual Android operations must still be tested.

Important rule: do not translate "running through Shizuku" into "all signature/system permissions are available". Each API call can still have UID, SELinux, app-op or service-specific checks.

## Current risk split

### Downlink capture

Confidence: comparatively high.

Reason:
- Android defines the needed source;
- scrcpy implements it under shell-style privilege;
- ShizuCallRecorder demonstrates a modern non-root Shizuku integration.

Remaining proof: S22+ physical capture with remote-only audio.

### Uplink injection

Confidence: uncertain / highest project risk.

Reason:
- BCP proves the generic concept;
- OEM/modem support varies;
- Samsung's own call features prove vendor-internal functionality exists, but accessibility is unknown.

Remaining proof: S22+ `TYPE_TELEPHONY` test, followed by Samsung-specific research only if necessary.

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

1. Does Shizuku/shell on the target S22+ successfully start `VOICE_DOWNLINK` during a carrier call?
2. Does downlink capture remain remote-only across earpiece/speaker and screen-off states?
3. Does the S22+ expose `TYPE_TELEPHONY` as an output device?
4. If present, can a shell/privileged `AudioTrack` route to it and reach the remote party?
5. Can the human microphone be muted/restored independently while software injection continues?
6. If generic injection fails, which Samsung service/audio-policy path powers Text Call on this exact firmware?
7. Does Wi-Fi Calling use materially different capture/injection routing?
8. What is the lowest reliable local abort latency for queued injection audio?

## Decision rules

- Do not start AI integration after capture alone passes.
- Do not mark injection as working from `setPreferredDevice(true)` alone.
- Do not infer Samsung private API accessibility from firmware package permissions.
- Do not root the primary target merely to keep the cellular design alive; first make an explicit fallback decision.
- Keep the final product architecture replaceable enough to use SIP/VoIP if stock cellular injection proves inaccessible.