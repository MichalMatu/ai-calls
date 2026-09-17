# Architecture

## Objective

Build the smallest reliable path from a normal cellular call to a realtime AI session and back on one Android phone, while isolating privileged and Samsung-specific mechanisms behind replaceable boundaries.

Capture, injection, call control and realtime transport remain separate capabilities. A failure in one layer must not leave cellular injection running or prevent immediate human takeover.

## Current proven data flow

The target S22+ has a physically proven local bidirectional cellular bridge through the real Shizuku UserService boundary:

```text
                         normal Android app
                    +---------------------------+
                    | UI / orchestration        |
                    | diagnostic probe clients  |
                    | realtime client (later)   |
                    +-------------+-------------+
                                  |
                       Binder/AIDL control only
                                  |
                    +-------------v-------------+
                    | Shizuku UserService       |
                    | shell UID 2000            |
                    +------+------+-------------+
                           |      |
                   PCM PFD |      | PCM PFD
                           |      |
              +------------v-+  +-v----------------+
cell downlink -> VOICE_DOWNLINK  CALL_ASSISTANT TX -> cell uplink
              +--------------+  +------------------+
```

PCM moves through `ParcelFileDescriptor` pipes. Binder/AIDL is for control, state and FD handoff only; continuous audio never uses one Binder transaction per frame.

## Evidence-driven backend selection

Capability states:
- `UNAVAILABLE` — capability cannot be initialized on the target device;
- `AVAILABLE_UNPROVEN` — API/device path exists but media behavior is not physically validated;
- `PROVEN_S22` — passed a physical live-call test on the target build;
- `FAILED_S22` — reproducibly failed on the target build under documented conditions.

Current target verdicts:

```text
VOICE_DOWNLINK production RX pipe                    PROVEN_S22
Samsung CALL_ASSISTANT / TYPE_TELEPHONY TX          PROVEN_S22
shared bidirectional RX+TX controller               PROVEN_S22
Shizuku UserService live parity                     PROVEN_S22
generic USAGE_MEDIA TX path                         FAILED_S22
generic USAGE_VOICE_COMMUNICATION TX path           FAILED_S22
normal-app-death cleanup timing                      OPEN MILESTONE D GATE
```

Constructor success, permissions and route enumeration are supporting evidence only; media directions are promoted only after physical live-call evidence.

## Modules

### `app/`

Normal Android process.

Responsibilities:
- show capability/test state;
- orchestrate helper sessions;
- own future realtime transport;
- expose immediate `Take over` control;
- display routing, privilege and latency diagnostics;
- hold no long-lived server API credential.

The app must not own Samsung/private call-audio primitives directly.

Current diagnostic code includes the Shizuku clients and protected `DiagnosticProbeActivity`. These are regression/probe surfaces, not the desired long-term Phase 3 lifecycle architecture.

Before Realtime AI, production orchestration should move into a dedicated app-side `CallMediaSessionCoordinator` with explicit states such as `IDLE/BINDING/PREPARING/ACTIVE/STOPPING/FAILED`, generation/failure reason, helper-death handling and structured telemetry.

### `audio-bridge/`

Device-independent audio contracts and models.

Target internal media representation:

```text
signed PCM16 little-endian
mono
explicit sample rate
monotonic timestamp where frame objects are used
```

Capture and injection remain independently testable. `abortNow()` is distinct from graceful stop and prioritizes immediate restoration of normal human call behavior.

### `privileged-helper/`

Primary boundary for stock-Samsung cellular media access.

Responsibilities:
- own protected `AudioRecord` and Samsung `AudioTrack` objects;
- create/own continuous PCM PFD pipes;
- enforce Samsung-specific initialization and attribution requirements;
- expose minimal control methods through Binder/AIDL;
- own heartbeat/watchdog and fail-safe cleanup;
- contain no OpenAI networking.

Reusable production components:

```text
SamsungVoiceDownlinkCapture
  -> SamsungDownlinkPipeSession

SamsungCallAssistantTrack
  -> SamsungUplinkPipeSession

SamsungCallMediaSessionController
  -> one RX+TX generation
  -> one heartbeat watchdog
  -> shared abort/fail-safe lifecycle
```

### `realtime-client/`

Independent from telephony and intentionally not connected to the call media path yet.

Responsibilities later:
- connect with a short-lived/session-scoped credential;
- stream input PCM;
- receive output PCM;
- cancel responses;
- surface remote speech events/errors;
- collect transport latency metrics.

Do not connect this layer until Milestone D is frozen.

### `scripts/`

Developer/device validation tooling only.

Important tools:
- `s22_call_control.py` — bounded call control and target selection;
- `s22_app_death_gate.py` — resilient phone-side Milestone D normal-app-death observer;
- unit tests for both tools.

These scripts are not product runtime code.

## Target-device facts

Current exact target:

```text
Samsung Galaxy S22+ SM-S906B
Android 16 / API 36 / One UI 8
shell UID 2000
Orange PL cellular calls used for proof
TYPE_TELEPHONY sink + source visible
Shizuku UserService path proven under shell UID 2000
```

For every live validation the phone must remain locally silent:

```text
STREAM_VOICE_CALL Muted:true
streamVolume:0
Devices: earpiece(1)
speakerphone off
```

Assert this before dialing and again after ACTIVE.

## Capture architecture — proven

Production RX path:

```text
VOICE_DOWNLINK AudioRecord
  -> SamsungVoiceDownlinkCapture
  -> SamsungDownlinkPipeSession
  -> PFD pipe
  -> controller/app reader
```

### S22 initialization-order invariant

On the target firmware, the direct-shell path requires `VOICE_DOWNLINK` construction before explicit `Context` / `AudioManager` initialization.

The shared controller therefore keeps two phases:

```text
controller.prepare(sampleRate)
create required contexts
controller.start(systemContext, shellContext)
```

Do not generalize this into a different ordering without physical evidence. The frozen Shizuku path intentionally uses its proven attributed-context sequence and must not be rewritten merely to resemble the direct-shell probe.

### RX attribution

The proven downlink route uses system attribution (`android`) during start/route confirmation.

## Injection architecture — proven Samsung path

Generic media/voice-communication usages are not the production path on this S22.

```text
internal mono PCM16LE
  -> SamsungUplinkPipeSession
  -> SamsungCallAssistantTrack
  -> duplicate mono to stereo at Samsung boundary
  -> USAGE_CALL_ASSISTANT / AUDIO_STREAM_CALL_ASSISTANT
  -> AUDIO_DEVICE_OUT_TELEPHONY_TX
  -> cellular uplink
```

Required TX attribution is `com.android.shell`.

## Shared bidirectional controller

`SamsungCallMediaSessionController` owns one media generation at a time.

Lifecycle:

```text
prepare(sampleRate)
  -> reserve downlink capture + RX read PFD

start(systemContext, shellContext)
  -> open uplink path
  -> start RX
  -> start TX
  -> start one heartbeat watchdog
  -> return controller-facing RX read + TX write PFDs

heartbeat()
  -> verify/reap child state
  -> refresh watchdog

abortNow()
  -> detach generation synchronously
  -> stop watchdog
  -> close prepared/active endpoints
  -> abort RX + TX
```

If either active child terminates, the controller aborts its sibling rather than allowing a half-live bridge.

Closing a transferred controller endpoint is intentionally a media-session termination signal.

## PFD ownership

Transferred pipe endpoints use `ParcelFileDescriptor.AutoCloseInputStream` / `AutoCloseOutputStream`. Raw `FileInputStream/FileOutputStream` over `getFileDescriptor()` is not the production ownership pattern.

No per-frame Binder calls are allowed.

Diagnostic RX/TX client threads are owned by `ShizukuBidirectionalProbeMedia`, whose cleanup is idempotent and deterministic.

## Shizuku UserService boundary — proven

The app-facing privilege boundary is physically validated.

Proven properties:
- UserService runs as shell UID 2000;
- off-call `prepare -> abort` has the expected exact states;
- live RX + TX PFD parity matches the proven media behavior;
- helper heartbeat covers the shared generation;
- explicit `abortNow()` removes prepared/active/heartbeat state;
- helper process death breaks the media streams and leaves the normal app + Shizuku server alive;
- no PCM frames are sent through Binder transactions.

Frozen reference:

```text
milestone/phase2c-shizuku-live-proven-20260916
9c136fc05c5b33f383d72b0b7080ad5b9a754bb4
```

## Hidden Android context boundary

`PrivilegedCallContexts` encapsulates reflection-heavy system/shell Context creation.

Post-freeze M3 removed the previous Binder-thread `Looper.prepare()` and second `ActivityThread.systemMain()` construction. The code now reuses Shizuku's process `ActivityThread.currentActivityThread()` and fails explicitly if it is unexpectedly unavailable.

This change passed host, off-call, silent-live and 30-second endurance validation.

## Watchdog and process death

The helper fails toward a normal human call.

Current behavior:
- one heartbeat watchdog covers both media directions;
- stale watchdog generations cannot abort a newer session;
- timeout/abort closes both media directions;
- either child ending causes sibling cleanup;
- helper/UserService process death is physically proven to terminate the bridge path.

Normal-app death is covered architecturally by heartbeat loss, but its end-to-end cleanup latency still requires a clean physical Milestone D measurement.

### App-death observer design

The first host-only timing attempt was invalidated by a transient ADB transport interruption. Therefore the timing-critical observer now runs on the phone:

```text
scripts/s22_app_death_gate.py
```

It:
- records monotonic device time from `/proc/uptime`;
- terminates the selected app process (`kill-pid` or `force-stop` mode);
- observes app and UserService process disappearance via `/proc/<pid>`;
- observes CALL_ASSISTANT `state:stopped` from device logcat;
- records call state, Shizuku-server survival and boot-id continuity;
- atomically renames a temporary result into the final result file;
- can be collected after ADB transport recovers.

It does not use `nohup`; the target S22's `toybox nohup` check returns exit 125.

## PCM boundary

Internal bridge format remains:

```text
PCM signed 16-bit little-endian
mono
explicit sample rate
```

Samsung TX stereo duplication happens only at the final device boundary.

PFD workers operate in bounded chunks, avoiding unbounded queued audio and large application-level buffers.

## Takeover invariant

`Take over` is a safety path, not merely a UI feature.

Required order:

```text
reject new AI audio
flush/discard queued injection
abort injector locally
stop privileged bridge media
restore human microphone path where required
keep cellular call alive
cancel remote model response
```

The local audio stop must never wait for a network round trip.

## Current Milestone D gates

Completed:
- 30-second bidirectional endurance;
- explicit abort/takeover path;
- helper/UserService process death;
- post-tooling host regression.

Pending physical gates:
1. reliable normal-app-death cleanup measurement with the phone-side observer;
2. cellular call end while bridge active -> complete cleanup;
3. transferred RX/TX PFD close -> sibling abort / whole-generation stop;
4. 10–20 start/abort resource-drift cycles;
5. final 10-minute bidirectional endurance with telemetry/resource counts;
6. final regression/security/evidence audit and Milestone D freeze.

Only after that should Realtime AI integration begin.

## Hard architectural rules

1. No hidden/private Android or Samsung API outside a dedicated backend/helper boundary.
2. No media backend is marked working before a physical S22+ test.
3. Capture and injection remain independently testable.
4. PCM streaming never uses per-frame Binder calls.
5. `Take over` is local, immediate and fail-safe.
6. App/helper death must disable injection.
7. No long-lived OpenAI credential in the APK.
8. No call recording by default.
9. Do not replace the default dialer until media transport and product UX justify it.
10. Preserve the proven S22 RX construction requirements unless new hardware evidence disproves them.
11. Preserve separate RX (`android`) and TX (`com.android.shell`) attribution contexts.
12. Preserve `USAGE_CALL_ASSISTANT` and mono-to-stereo conversion only at the Samsung TX boundary.
13. Keep `agent-control` execution metadata separate from canonical source branches.
