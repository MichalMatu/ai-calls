# Architecture

## Objective

Build the smallest reliable path from a normal cellular call to a realtime AI session and back on one Android phone, while isolating every privileged or Samsung-specific mechanism behind replaceable backends.

The architecture must remain useful even if one telephony experiment fails. Capture, injection, call control and realtime transport are separate capabilities.

## High-level data flow

```text
                         normal Android app
                    +---------------------------+
                    | UI / session controller   |
                    | capability results        |
                    | realtime client           |
                    +-------------+-------------+
                                  |
                       Binder/AIDL control only
                                  |
                    +-------------v-------------+
                    | privileged helper         |
                    | Shizuku/shell backend     |
                    | Samsung backend (later)   |
                    +------+------+-------------+
                           |      |
                   PCM pipe|      |PCM pipe
                           |      |
              +------------v-+  +-v----------------+
cell downlink -> capture backend  injection backend -> cell uplink
              +--------------+  +------------------+
                       |
                       v
                realtime model
```

PCM should move through a pipe/local socket/shared buffer. Binder/AIDL is for control, state and file-descriptor handoff, not one transaction per audio frame.

## Evidence-driven backend selection

No backend is assumed to work merely because its API exists.

Capability states:
- `UNAVAILABLE` — capability cannot be initialized on the target device;
- `AVAILABLE_UNPROVEN` — API/device path exists but media behavior is not physically validated;
- `PROVEN_S22` — passed the two-phone test on the target build when the claim is a media direction;
- `FAILED_S22` — reproducibly failed on the target build under the documented test conditions.

Narrow device/permission/source facts can be proven separately without promoting a media direction. For example, `VOICE_DOWNLINK` construction on the S22+ is proven as a capability fact while actual remote-party PCM is still unproven.

A `DeviceCapabilityProbe` runs before implementation assumptions are promoted into backend selection.

## Modules

### `app/`

Normal Android process.

Responsibilities:
- show capability/test state;
- orchestrate helper sessions;
- manage realtime transport after the cellular media gates pass;
- expose a one-tap `Take over` control;
- display routing, privilege and latency diagnostics;
- hold no long-lived server API credential.

The app must not contain Samsung/private API calls directly.

### `audio-bridge/`

Device-independent audio contracts and models.

Capture and injection stay independently testable.

Conceptual contracts:

```kotlin
interface CallAudioCapture {
    suspend fun probe(): Result<ProbeResult>
    suspend fun start(...): Result<Unit>
    suspend fun stop()
}

interface CallAudioInjector {
    suspend fun probe(): Result<ProbeResult>
    suspend fun start(): Result<Unit>
    suspend fun write(...): Result<Unit>
    suspend fun stop()
    suspend fun abortNow(): Result<Unit>
}
```

`abortNow()` is different from a graceful `stop()`: it must discard queued output and prioritize restoring normal human call behavior.

Candidate backends:
- shell/scrcpy-style downlink capture;
- generic `TYPE_TELEPHONY` injection;
- Samsung-specific injection if generic routing fails during an active call;
- later SIP/VoIP media backend if cellular injection is blocked.

### `privileged-helper/`

The primary research boundary for stock-Android cellular media access.

Initial preferred mechanism: Shizuku UserService / shell privileges.

Responsibilities:
- run capability probes requiring shell context;
- own privileged `AudioRecord`/scrcpy-style capture resources;
- own privileged `AudioTrack` injection resources;
- create and return PCM pipes/file descriptors;
- expose minimal control methods over Binder/AIDL;
- enforce cleanup and watchdog behavior independently of the UI process.

Any backend must document exactly which UID, permission or hidden capability it relies on.

The 2026-09-14 target baseline proved the relevant direct-shell UID 2000 capability class before Shizuku integration. Shizuku remains the intended app-facing privilege boundary, but its UserService parity must still be tested rather than assumed.

### `realtime-client/`

Independent from telephony.

Responsibilities:
- connect using a short-lived/session-scoped credential;
- stream input PCM;
- receive output PCM;
- cancel active responses;
- surface remote speech events and errors;
- collect transport latency metrics.

Do not start this layer until local cellular capture/injection is stable.

## Device capability probe

Before Phase 1, collect target-device facts rather than guessing:

```text
DeviceCapabilityReport
  device/build metadata
  normal app UID
  helper UID / privilege mode
  audio device inventory
  candidate input source results
  TYPE_TELEPHONY presence
  route-request result
  current call route/state
  Samsung package observations
```

The first off-call baseline has been collected and is stored in `S22_BASELINE_2026-09-14.md`.

Current target facts:

```text
SM-S906B / Android 16 / API 36 / One UI 8.0
shell UID 2000
shell call-audio permissions granted
TYPE_TELEPHONY sink + source visible
VOICE_CALL / VOICE_DOWNLINK / VOICE_UPLINK initialize under shell
```

The probe does not prove actual media direction. It determines what experiments are worth running next.

See `DEVICE_CAPABILITY_PROBE.md`.

## Capture architecture

Preferred first target:

```text
VOICE_DOWNLINK / scrcpy voice-call-downlink
  -> privileged process
  -> raw PCM
  -> ParcelFileDescriptor pipe
  -> app-side reader
```

Remote-only downlink is preferred over a mixed `VOICE_CALL` source because it reduces echo and prevents local/AI audio from being re-fed to the model.

If only mixed call audio is available, mark that explicitly and design echo/source-separation work as a separate risk rather than hiding it in the transport layer.

Current state: shell can initialize `VOICE_DOWNLINK` on the exact S22+ build, but active-call start/read/useful remote PCM still requires the dedicated SIM test.

## Injection architecture

First generic active-call experiment:

```text
test PCM
  -> privileged AudioTrack
     AudioAttributes.USAGE_MEDIA
  -> preferred TYPE_TELEPHONY sink
  -> cellular uplink
```

Why `USAGE_MEDIA` first: the external AgentCall project reports a physically-qualified telephony TX path using that usage on another privileged Android device. It is research evidence, not transferable proof. `USAGE_VOICE_COMMUNICATION` remains a separate comparison candidate based on BCP-style precedent.

The S22+ exposes a `TYPE_TELEPHONY` sink and shell has the relevant protected permissions. However, `AudioTrack` construction failed while no call was active for both tested usages. That result is deliberately left `AVAILABLE_UNPROVEN`/unresolved because the telephony route may only open in-call.

Only the second phone hearing the deterministic digital sample proves injection. Constructor success, `setPreferredDevice()`, routed-device state, and playback-head progress are supporting evidence but not the final gate.

If generic injection fails reproducibly **during an active call**, a Samsung-specific backend may investigate the class of mechanisms used by Samsung's own call features. That code must remain isolated and version-gated.

## PCM boundary

Use an explicit format instead of opaque byte arrays. The model should eventually distinguish encoding details, not just bit depth.

Target internal representation for the first bridge:

```text
PCM signed 16-bit little-endian
mono
explicit sample rate
monotonic timestamp
```

Resampling belongs at module boundaries.

`ByteArray` frames are acceptable for the initial PoC but should not be frozen as the final high-frequency transport because repeated allocations may create avoidable GC pressure. The pipe/socket is the continuous transport primitive.

## State machine

```text
IDLE
  -> PROBING
  -> READY
  -> CAPTURING_TEST
  -> INJECTING_TEST
  -> LOCAL_BRIDGE
  -> AI_BRIDGING
  -> USER_TAKEOVER
  -> STOPPING
  -> IDLE

Any state -> ERROR -> fail-safe cleanup -> IDLE/HUMAN_CALL
```

## Takeover invariant

`Take over` is a safety path, not a UI feature.

Required order:

```text
reject new AI audio
flush queued injection
abort injector locally
restore/unmute human microphone path
keep cellular call alive
cancel remote model response
```

The local audio stop must not wait for the network.

## Watchdog and process death

The privileged helper must fail toward a normal human call.

If the normal application process dies, disconnects, or stops its heartbeat while injection is active, the helper should:
- stop accepting PCM;
- flush/abort injection;
- close audio resources and pipes;
- release any temporary route state;
- restore the human path where possible.

Use Binder death notification and/or a bounded heartbeat timeout. Never allow an orphaned helper to continue injecting indefinitely.

## Barge-in

Remote speech interruption is latency-sensitive.

Preferred behavior:

```text
remote speech detected locally
  -> immediately flush/abort current injected AI output
  -> then send response-cancel to realtime service
```

Do not wait for a network round trip before silencing AI audio to the caller.

## Development process boundary

The engineering workflow is described in `DEVELOPMENT_WORKFLOW.md` and `AGENTS.md`.

Substantial behavior changes should have a Superpowers-style plan under `docs/superpowers/plans/`, follow TDD where the behavior is under our control, and preserve hardware evidence separately from automated test success.

The current handoff plan is `docs/superpowers/plans/2026-09-14-phase1-live-call-validation.md`.

## Hard architectural rules

1. No hidden/private Android or Samsung API outside a dedicated backend/helper.
2. No media backend is marked working before a physical S22+ test.
3. Capture and injection must be independently testable.
4. PCM streaming does not use per-frame Binder calls.
5. `Take over` is local, immediate and fail-safe.
6. App/helper death must disable injection.
7. No long-lived OpenAI credential in the APK.
8. No call recording by default.
9. Do not replace the default dialer until media transport is proven.
10. Keep SIP/VoIP available as a clean fallback rather than contaminating the cellular experiments.
11. Off-call telephony output failure must not be treated as in-call failure without a live call test.
12. Keep `agent-control` execution metadata separate from the canonical source tree on `main`.
