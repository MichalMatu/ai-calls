# Architecture

## Objective

Build the smallest possible Android path from cellular call audio to a realtime AI session and back, while keeping telephony-specific experiments isolated from the rest of the application.

## High-level data flow

```text
Cellular call
   |
   | remote party audio (downlink)
   v
AudioCaptureBackend
   |
   v
AudioNormalizer / jitter buffer
   |
   v
RealtimeTransport  ---> realtime model session
   ^                         |
   |                         v
AudioInjectorBackend <--- returned AI audio
   |
   v
Cellular call uplink
```

The UI controls the bridge but does not own the audio pipeline.

## Modules

### `app/`

Android application shell.

Responsibilities:
- start/stop a bridge session;
- show current routing/backend state;
- expose a one-tap `Take over` control;
- display errors and test measurements;
- never contain a long-lived OpenAI API key.

### `audio-bridge/`

Telephony/audio abstraction layer.

The public app code talks only to interfaces such as:

```kotlin
interface CallAudioCapture {
    suspend fun start(onPcm: (PcmFrame) -> Unit): Result<Unit>
    suspend fun stop()
}

interface CallAudioInjector {
    suspend fun start(): Result<Unit>
    suspend fun write(frame: PcmFrame): Result<Unit>
    suspend fun stop()
}
```

Backends are intentionally swappable:
- public Android API experiment;
- Samsung/device-specific experiment;
- shell/privileged experiment;
- later, a VoIP/SIP backend if cellular injection is impossible.

### `realtime-client/`

Transport-independent realtime AI client.

Responsibilities:
- obtain a short-lived session credential from a backend;
- connect via the selected realtime transport;
- stream input audio;
- receive output audio;
- expose interruption/cancel events;
- collect latency metrics.

The telephony layer must work without this module before integration begins.

### `privileged-helper/`

Experimental code only. This directory exists so shell/system-level experiments do not contaminate the normal Android application architecture.

Any helper must state exactly which permission or system capability it relies on and whether it requires ADB, Shizuku, root, a system signature, or device-specific behavior.

## Audio format boundary

Internally the bridge uses an explicit frame model instead of passing opaque byte arrays:

```kotlin
data class PcmFormat(
    val sampleRateHz: Int,
    val channels: Int,
    val bitsPerSample: Int,
)

data class PcmFrame(
    val format: PcmFormat,
    val data: ByteArray,
    val monotonicTimestampNs: Long,
)
```

Resampling happens at module boundaries. The current OpenAI Realtime API supports PCM output at 24 kHz and also telephony-oriented G.711 formats, so the bridge should not assume that the cellular side and AI side share a native format.

## State machine

```text
IDLE
  -> PROBING
  -> READY
  -> BRIDGING
  -> USER_TAKEOVER
  -> STOPPING
  -> IDLE

Any state -> ERROR
```

`USER_TAKEOVER` must immediately stop AI injection before doing anything else.

## Hard architectural rules

1. Do not depend on hidden/private Android APIs outside a dedicated backend.
2. Do not claim a backend works until it passes the physical two-phone test.
3. Never place a long-lived API key in the APK.
4. Do not record calls by default.
5. Keep capture and injection independently testable.
6. Every experimental backend must emit measurable latency and failure diagnostics.
