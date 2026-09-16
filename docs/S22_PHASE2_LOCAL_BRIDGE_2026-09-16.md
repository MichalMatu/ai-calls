# S22 Phase 2 local bridge proof — 2026-09-16

## Scope

This note records the physical Samsung Galaxy S22+ evidence for the local bidirectional cellular media bridge before any realtime AI transport is connected.

Target device:

```text
Samsung Galaxy S22+ SM-S906B
Android 16 / API 36 / One UI 8
serial RFCT70L7E8J
shell UID 2000
Orange PL cellular call
```

The phone remained physically silent during all live validation. `STREAM_VOICE_CALL` was forced muted with `streamVolume:0` and the active route remained `earpiece(1)`. No speakerphone test was used.

## Result

Phase 2 Milestone B is `PROVEN_S22`.

One `SamsungCallMediaSessionController` successfully owned both directions of a live cellular call at the same time:

```text
remote cellular downlink
  -> VOICE_DOWNLINK AudioRecord
  -> SamsungDownlinkPipeSession
  -> ParcelFileDescriptor PCM pipe
  -> controller

controller mono PCM16LE
  -> ParcelFileDescriptor PCM pipe
  -> SamsungUplinkPipeSession
  -> SamsungCallAssistantTrack
  -> Samsung AUDIO_STREAM_CALL_ASSISTANT / TYPE_TELEPHONY TX
  -> remote cellular uplink
```

Control remained local to the helper. Continuous PCM did not use per-frame Binder calls.

## Exact successful live-smoke evidence

Validated work-branch SHA:

```text
6b8d9f619031e0ca8352e55b624bdc9bc1edf633
```

Local Agent result:

```text
.agent/results/phase2-validate-bidirectional-live-delayed-v4-20260916-0352.json
```

Live-call safety guard:

```text
STREAM_VOICE_CALL:
  Muted: true
  streamVolume:0
  Devices: earpiece(1)

STREAM_DTMF (aliased to: STREAM_VOICE_CALL)
```

The test waited three seconds after `mCallState=2`, re-checked the mute guard, then ran a five-second bidirectional media session.

Controller state at start:

```text
active_after_start=true
heartbeat_after_start=true
```

Downlink before uplink DTMF injection:

```text
pre_dtmf_bytes_read=80000
pre_dtmf_samples_read=40000
pre_dtmf_non_zero_samples=25451
pre_dtmf_peak=21327
pre_dtmf_rms=2420.7548160336273
pre_dtmf_half_sample_carry=false
```

Uplink pipe accepted a generated mono PCM16LE DTMF digit `1`:

```text
uplink_dtmf_duration_ms=300
uplink_bytes_written=9600
```

Downlink after the uplink write:

```text
post_dtmf_bytes_read=80000
post_dtmf_samples_read=40000
post_dtmf_non_zero_samples=38737
post_dtmf_peak=19584
post_dtmf_rms=2566.8176657877357
post_dtmf_half_sample_carry=false
```

Controller state while both PFD endpoints were still open:

```text
active_with_endpoints_open=true
heartbeat_with_endpoints_open=true
media_ok_before_endpoint_close=true
```

Fail-safe cleanup:

```text
prepared_after_abort=false
active_after_abort=false
heartbeat_after_abort=false
post_call_state=0
```

## What is physically proven

### RX — `PROVEN_S22`

`VOICE_DOWNLINK` produces non-zero remote-call-correlated PCM through the production `SamsungDownlinkPipeSession` while the physical phone output remains muted.

The S22 initialization-order constraint is real and preserved:

1. construct `VOICE_DOWNLINK` / call `controller.prepare()` before explicit `Context` or `AudioManager` initialization in the direct-shell process;
2. create the required contexts only afterwards;
3. start the route while `AudioManager.MODE_IN_CALL` is active.

### TX — `PROVEN_S22`

The Samsung-specific `USAGE_CALL_ASSISTANT` / `AUDIO_STREAM_CALL_ASSISTANT` path reaches `AUDIO_DEVICE_OUT_TELEPHONY_TX` under shell attribution and is physically proven from Phase 1C.

The production uplink pipe now also accepts the same PCM through `SamsungUplinkPipeSession` while the shared bidirectional controller remains active and healthy.

Generic `USAGE_MEDIA` and `USAGE_VOICE_COMMUNICATION` TX are `FAILED_S22` for the tested target path; they are no longer the primary architecture.

## Shared-controller invariants now validated

- one active bidirectional generation at a time;
- one heartbeat watchdog owns RX and TX lifetime;
- either child termination causes sibling fail-safe cleanup;
- `abortNow()` synchronously detaches prepared/active state and aborts both media paths;
- downlink and uplink retain separate required attribution contexts (`android` and `com.android.shell` respectively);
- PCM transport uses kernel PFD pipes;
- Samsung TX duplicates internal mono PCM to stereo only at the device boundary;
- closing controller endpoints is intentionally treated as media-session termination.

## Important probe correction

An earlier live-smoke incorrectly checked `hasActiveSession()` only after leaving the try-with-resources block. At that point both controller PFD endpoints had already been closed, so the child sessions correctly terminated and the controller correctly reaped the generation.

Probe v4 moved the active/heartbeat assertion inside the open-endpoint scope. The successful test above therefore measures the controller while both directions are genuinely live.

The earlier all-zero short captures were also not a backend failure. Orange can have a silent IVR interval immediately after the call becomes active. Repeating the same code after a three-second delay produced strong non-zero PCM in both capture windows.

## Milestone C — Shizuku UserService parity

Code-side first slice exists on the work branch and builds successfully:

- Shizuku API/provider dependency;
- AIDL control plane;
- shell-side `ShizukuCallMediaUserService`;
- PFD endpoint handoff API;
- context-free `prepare()` preserved before Context/AudioManager creation;
- app-side bind/permission plumbing;
- bounded off-call `prepare -> abort` path.

AIDL was validated with explicit method IDs and the Shizuku-reserved destroy transaction retained:

```text
destroy() = 16777114
```

Current code SHA for the first UserService slice:

```text
6cceb9c9b139c4ac5ba683f13525821f66d325ed
```

Build validation is green, including helper/app unit tests and the existing 19 Python call-control tests.

### Current blocker

The target phone's active Android user is user `0`. Shizuku is not installed or running for that user. User `151` is Samsung Secure Folder and is intentionally ignored.

A bounded search found no existing Shizuku APK in:

```text
$HOME/Downloads
$HOME/Desktop
/sdcard/Download
/storage/emulated/0/Download
```

Therefore physical UserService parity cannot be tested until a trusted Shizuku manager/server installation is available on user `0`.

This is an external runtime prerequisite, not a failure of the local bridge or of the current UserService code.

## Remaining Phase 2 gates

Milestone B is complete. Phase 2 is not yet fully complete because these remain:

1. Shizuku UserService parity on the same S22 for RX, TX, PFD handoff, heartbeat and abort semantics;
2. controller-death/Binder-death cleanup proof through the real UserService boundary;
3. ten-minute local bidirectional endurance test with bounded memory/queues and clean takeover;
4. only after those pass, connect realtime AI transport.

Do not start OpenAI/realtime audio integration while Milestone C/D are still open.
