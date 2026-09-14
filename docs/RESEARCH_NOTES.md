# Research Notes

_Last reviewed: 2026-09-14_

This file separates confirmed public-platform facts from hypotheses that must be validated on the Samsung Galaxy S22+.

## Confirmed Android public API constraints

### `VOICE_DOWNLINK` / `VOICE_UPLINK`

Android exposes `MediaRecorder.AudioSource.VOICE_DOWNLINK` and `VOICE_UPLINK`, but the official API reference states that capturing either source requires `android.permission.CAPTURE_AUDIO_OUTPUT`.

`CAPTURE_AUDIO_OUTPUT` is reserved for system components and is not available to ordinary third-party applications.

Source:
- https://developer.android.com/reference/android/media/MediaRecorder.AudioSource

Implication: a normal Play-style application cannot simply request these sources and expect cellular call audio.

### MediaProjection / AudioPlaybackCapture

Android's AudioPlaybackCapture API is useful for capturing eligible playback from other apps, but the official `AudioPlaybackCaptureConfiguration` documentation states that capturable playback is limited to usages such as `USAGE_UNKNOWN`, `USAGE_MEDIA`, and `USAGE_GAME` and is also subject to the source app's capture policy.

Voice communication audio is therefore not a general public solution for cellular call downlink capture.

Sources:
- https://developer.android.com/media/platform/av-capture
- https://developer.android.com/reference/android/media/AudioPlaybackCaptureConfiguration

### Consequence

The cellular path is currently an **experimental backend problem**. Candidate directions may include:

- Samsung/device-specific behavior;
- shell or privileged process access;
- Shizuku-assisted experiments;
- root/system-app research;
- switching the media transport to SIP/VoIP.

None of those is marked proven until it passes `docs/POC_AUDIO_TEST_PLAN.md`.

## Samsung call recording observation

Samsung firmware on some devices/regions can provide system-level call recording. This demonstrates that the platform/vendor stack can access call audio internally, but it does **not** imply that a third-party APK receives the same audio path.

Research task: determine which Samsung service/process owns that path on the exact S22+ firmware and whether any supported or privilege-bounded integration is available.

Do not design the production architecture around private Samsung internals before a reproducible proof exists.

## Realtime AI integration

The current OpenAI Realtime API supports low-latency realtime sessions over WebRTC, WebSocket, and SIP, including native audio input/output.

Relevant official reference:
- https://platform.openai.com/docs/api-reference/realtime

Current API details relevant to this project:
- realtime sessions can exchange audio natively;
- PCM output uses 24 kHz when PCM is selected;
- telephony-oriented G.711 PCMU/PCMA formats are also supported;
- response interruption/cancellation is supported and should be connected to user/remote-party barge-in;
- the long-lived API credential should stay off the Android client.

## Preferred integration order

1. prove downlink capture locally;
2. prove uplink injection locally;
3. stabilize a local bridge and measure latency;
4. add the realtime transport;
5. add conversational behavior;
6. only then build a polished dialer-like UX.

## Hypotheses to investigate

These are intentionally unresolved:

- Can a shell-privileged process on current Samsung firmware open `VOICE_DOWNLINK` / `VOICE_UPLINK` successfully?
- Can Shizuku provide a useful privilege boundary for the required audio operation, or is a stronger/system-only capability required?
- Does Samsung's recording implementation expose a binder/service path that is accessible without modifying the OS?
- Is digital uplink injection possible independently of capture on the stock cellular stack?
- Does routing through `AudioManager` communication-device APIs help only with app-owned VoIP, or can it affect the cellular call path enough to create a usable bridge?

Each hypothesis should become a small isolated experiment, not a speculative feature branch.
