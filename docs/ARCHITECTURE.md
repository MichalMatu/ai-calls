# Architecture

## Objective

Build the smallest reliable path from a normal cellular call to a realtime AI session and back on one Android phone, while isolating privileged and Samsung-specific mechanisms behind replaceable backends.

Capture, injection, call control and realtime transport remain separate capabilities. A failure in one layer must not leave cellular injection running or prevent immediate human takeover.

## Current proven data flow

As of 2026-09-16, the target S22 has a physically proven local bidirectional cellular bridge:

```text
                         normal Android app
                    +---------------------------+
                    | UI / session controller   |
                    | realtime client (later)   |
                    +-------------+-------------+
                                  |
                       Binder/AIDL control only
                                  |
                    +-------------v-------------+
                    | privileged helper         |
                    | shell / Shizuku boundary  |
                    +------+------+-------------+
                           |      |
                   PCM PFD |      | PCM PFD
                           |      |
              +------------v-+  +-v----------------+
cell downlink -> VOICE_DOWNLINK  CALL_ASSISTANT TX -> cell uplink
              +--------------+  +------------------+
```

PCM moves through `ParcelFileDescriptor` pipes. Binder/AIDL is for control, state and FD handoff only; continuous audio never uses one Binder transaction per frame.

Detailed physical evidence is stored in `S22_PHASE2_LOCAL_BRIDGE_2026-09-16.md`.

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
generic USAGE_MEDIA TX path                         FAILED_S22
generic USAGE_VOICE_COMMUNICATION TX path           FAILED_S22
Shizuku UserService parity                          AVAILABLE_UNPROVEN / runtime missing
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

The current app also contains the first Shizuku UserService client slice and AIDL definitions required to bind to the privileged helper.

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

The reusable production components are:

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

Do not connect this layer until UserService parity and Phase 2 endurance gates pass.

## Target-device facts

Current exact target:

```text
Samsung Galaxy S22+ SM-S906B
Android 16 / API 36 / One UI 8
shell UID 2000
Orange PL live cellular calls used for proof
TYPE_TELEPHONY sink + source visible
```

The phone was physically silent during the successful live bridge proof:

```text
STREAM_VOICE_CALL Muted:true
streamVolume:0
Devices: earpiece(1)
```

The active DTMF stream reports an alias to `STREAM_VOICE_CALL`; its own displayed mute flag is not used as the physical-audio safety criterion.

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

On the target firmware, `VOICE_DOWNLINK` construction must happen before explicit `Context` / `AudioManager` initialization in the direct-shell process.

Therefore the shared controller uses two phases:

```text
controller.prepare(sampleRate)   // context-free; constructs RX first
create required contexts
controller.start(systemContext, shellContext)
```

Do not collapse this ordering without new physical evidence.

### RX attribution

The proven downlink route uses the system-attribution context (`android`) during start/route confirmation.

Physical production-pipe evidence includes strong non-zero PCM before and after simultaneous uplink activity. See `S22_PHASE2_LOCAL_BRIDGE_2026-09-16.md`.

## Injection architecture — proven Samsung path

Generic media/voice-communication usages are not the production path on this S22.

Production TX path:

```text
internal mono PCM16LE
  -> SamsungUplinkPipeSession
  -> SamsungCallAssistantTrack
  -> duplicate mono to stereo at Samsung boundary
  -> USAGE_CALL_ASSISTANT / AUDIO_STREAM_CALL_ASSISTANT
  -> AUDIO_DEVICE_OUT_TELEPHONY_TX
  -> cellular uplink
```

Required attribution for TX is `com.android.shell`.

This path is physically proven on the target device. The raw Phase 1C proof established remote uplink behavior; the production shared-controller proof established the PFD wrapper/controller path while RX remained active and healthy.

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

Use `ParcelFileDescriptor.AutoCloseInputStream` / `AutoCloseOutputStream` for transferred pipe endpoints. Raw `FileInputStream/FileOutputStream` over `getFileDescriptor()` caused lifecycle ambiguity and is not the production pattern.

No per-frame Binder calls are allowed.

## Watchdog and process death

The helper fails toward a normal human call.

Current local controller behavior:
- one heartbeat watchdog covers both media directions;
- default timeout is bounded;
- stale watchdog generations cannot abort a newer session;
- helper abort closes media resources and interrupts blocked workers;
- either child ending causes sibling cleanup.

Milestone C must prove the same invariant through a real app <-> UserService Binder boundary, including controller/Binder death.

## Shizuku UserService boundary

Shizuku remains the intended app-facing privilege boundary because the physical media primitives require shell-class capabilities.

First code slice is implemented and builds successfully:

```text
IShizukuCallMediaService.aidl
ShizukuCallMediaUserService
app-side Shizuku bind/permission plumbing
```

AIDL is control-only and transfers PFDs for media.

The UserService preserves the S22 initialization invariant by keeping construction context-free and calling `controller.prepare()` before creating explicit Android/shell contexts.

Current blocker is environmental, not architectural:
- active device user is `0`;
- Shizuku manager/server is not installed or running for user `0`;
- Samsung Secure Folder user `151` is out of scope;
- no trusted existing Shizuku APK was found in the checked local/download locations.

Do not fetch or install an unverified APK merely to satisfy this gate.

## PCM boundary

Internal bridge format remains:

```text
PCM signed 16-bit little-endian
mono
explicit sample rate
```

Samsung TX stereo duplication happens only at the final device boundary.

PFD workers operate in bounded chunks (roughly 20 ms at the current sample rate), avoiding unbounded queued audio and large blocking writes.

## State machine

```text
IDLE
  -> PROBING
  -> READY
  -> LOCAL_BRIDGE
  -> AI_BRIDGING          // later
  -> USER_TAKEOVER
  -> STOPPING
  -> IDLE

Any state -> ERROR -> fail-safe cleanup -> IDLE/HUMAN_CALL
```

The direct-shell probes remain regression/debug tooling, not the final product control surface.

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

## Barge-in

Future preferred behavior:

```text
remote speech detected locally
  -> immediately stop/flush current injected AI output
  -> then send response-cancel to realtime service
```

Do not wait for server acknowledgement before silencing AI audio to the caller.

## Current implementation gates

### Completed

- direct-shell target capability baseline;
- production VOICE_DOWNLINK RX pipe;
- production Samsung CALL_ASSISTANT TX pipe;
- shared RX+TX controller;
- heartbeat watchdog and fail-safe local abort;
- physical simultaneous bidirectional live-smoke on S22;
- first Shizuku UserService code slice compiles and passes repository tests.

### Pending

1. install/start a trusted Shizuku runtime for device user `0`;
2. off-call UserService bind + `prepare -> abort` parity;
3. live UserService RX/TX PFD parity;
4. Binder/controller-death fail-safe proof;
5. 10-minute local bridge endurance test;
6. only then connect realtime AI.

## Development process boundary

The engineering workflow is described in `DEVELOPMENT_WORKFLOW.md` and `AGENTS.md`.

Current implementation plan:

`docs/superpowers/plans/2026-09-16-phase2-local-bridge.md`

Hardware evidence is durable and separate from automated test success.

## Hard architectural rules

1. No hidden/private Android or Samsung API outside a dedicated backend/helper.
2. No media backend is marked working before a physical S22+ test.
3. Capture and injection remain independently testable.
4. PCM streaming never uses per-frame Binder calls.
5. `Take over` is local, immediate and fail-safe.
6. App/helper death must disable injection.
7. No long-lived OpenAI credential in the APK.
8. No call recording by default.
9. Do not replace the default dialer until media transport and product UX justify it.
10. Keep SIP/VoIP available as a clean fallback.
11. Preserve the proven S22 RX construction order unless new hardware evidence disproves the requirement.
12. Preserve separate RX (`android`) and TX (`com.android.shell`) attribution contexts.
13. Keep `agent-control` execution metadata separate from canonical source files.
