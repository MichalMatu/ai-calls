# Privileged Helper

This module is the isolation boundary for operations that a normal third-party Android process cannot perform.

The primary stock-device privilege boundary is now **Shizuku UserService / shell UID 2000**, and that path is physically proven on the target Galaxy S22+.

## Current target evidence

Target:

```text
Samsung Galaxy S22+ SM-S906B
Android 16 / API 36 / One UI 8
shell UID 2000
```

Physically proven:

- direct shell can access protected call-audio capabilities;
- `VOICE_DOWNLINK` produces real cellular downlink PCM;
- generic `USAGE_MEDIA` / `USAGE_VOICE_COMMUNICATION` TX paths failed for the tested cellular uplink use case;
- Samsung `USAGE_CALL_ASSISTANT / AUDIO_STREAM_CALL_ASSISTANT -> TYPE_TELEPHONY TX` works with `com.android.shell` attribution;
- simultaneous RX + TX works under one `SamsungCallMediaSessionController` lifetime;
- the real app -> Shizuku UserService -> helper path matches the proven media behavior;
- UserService runs as shell UID 2000;
- PFD endpoint handoff works continuously;
- explicit `abortNow()` and heartbeat timeout stop the shared session;
- killing the UserService/helper process terminates the media path while the normal app and Shizuku server survive.

Frozen references:

```text
Phase 2B: milestone/phase2b-proven-s22-20260916
          c10f8dde29f245f8f98fb008a3572c21fe73fe35

Phase 2C: milestone/phase2c-shizuku-live-proven-20260916
          9c136fc05c5b33f383d72b0b7080ad5b9a754bb4
```

## Responsibilities

The helper owns:

- protected Samsung/Android call-media primitives;
- cellular downlink capture;
- Samsung-specific cellular uplink injection;
- PCM `ParcelFileDescriptor` pipes;
- one shared bidirectional controller generation;
- heartbeat/watchdog lifetime;
- immediate local abort/takeover cleanup;
- sibling cleanup if either media direction terminates.

The normal application owns UI/orchestration and, later, realtime networking.

Do not implement OpenAI/realtime networking in this module.

## Control plane versus media plane

Use Binder/AIDL for:

- prepare/start/abort commands;
- heartbeat/state checks;
- passing `ParcelFileDescriptor` handles;
- small capability/diagnostic values.

Do **not** send PCM frames as repeated Binder transactions.

Continuous media uses kernel PFD pipes.

## Current production components

```text
SamsungVoiceDownlinkCapture
  -> SamsungDownlinkPipeSession

SamsungCallAssistantTrack
  -> SamsungUplinkPipeSession

SamsungCallMediaSessionController
  -> one RX+TX generation
  -> one heartbeat watchdog
  -> shared fail-safe cleanup
```

RX and TX intentionally remain asymmetric because the target Samsung firmware requires different ordering and attribution rules.

## Proven S22 invariants

### Direct-shell RX ordering

The direct-shell reference path requires:

```text
construct/prepare VOICE_DOWNLINK
BEFORE explicit Context/AudioManager initialization
```

Do not rewrite the proven Shizuku sequence merely to make it look identical to the direct-shell probe; preserve the physically validated behavior of each path.

### TX boundary

```text
internal mono PCM16LE
 -> duplicate to stereo only at Samsung TX boundary
 -> USAGE_CALL_ASSISTANT / AUDIO_STREAM_CALL_ASSISTANT
 -> AUDIO_DEVICE_OUT_TELEPHONY_TX
```

Required TX attribution: `com.android.shell`.

### Lifetime

Preserve:

- one shared RX+TX generation;
- one shared heartbeat watchdog;
- stale generation protection;
- endpoint loss -> whole-generation cleanup;
- idempotent `abortNow()` cleanup;
- no per-frame Binder transport.

## Hidden Android context construction

`PrivilegedCallContexts` contains the reflection-heavy system/shell Context setup used by the Shizuku UserService path.

Post-freeze M3 removed Binder-thread `Looper.prepare()` and the duplicate `ActivityThread.systemMain()` construction. It now reuses Shizuku's existing `ActivityThread.currentActivityThread()` and fails explicitly if it is unavailable.

That change passed host, off-call, silent-live and 30-second endurance regression.

## Current gate

Phase 2C is complete. The active work is Milestone D robustness.

Still pending physical proof includes:

- normal app death while media is active, with reliable cleanup timing;
- cellular call end while the bridge is active;
- deliberate transferred-PFD closure and sibling abort;
- repeated start/abort resource-drift cycles;
- final 10-minute bidirectional endurance and telemetry.

The normal-app-death measurement now uses the phone-side observer in `scripts/s22_app_death_gate.py` so a host ADB interruption cannot corrupt timing evidence.

## Safety requirements

Injection is the highest-risk resource.

The helper must fail toward a normal human call:

- heartbeat timeout stops both media directions;
- controller/helper failure stops injection;
- queued PCM is discarded during abort/takeover;
- resources are released when initialization fails halfway;
- no arbitrary shell-command API is exposed to normal UI or future model traffic;
- test playback is bounded and deterministic;
- no automatic call placement lives in this helper.

## Evidence discipline

A successful constructor, permission grant, route request or playback-head movement is not enough for a media claim.

`PROVEN_S22` requires physical live-call evidence on the target device.

See:

- `docs/HANDOFF_NEXT_CHAT.md`
- `docs/ROADMAP.md`
- `docs/ARCHITECTURE.md`
- `docs/S22_PHASE2_LOCAL_BRIDGE_2026-09-16.md`
- `docs/PHASE2_DEEP_AUDIT_2026-09-16.md`

## Licensing

External projects are research references, not code donors by default.

- scrcpy: Apache-2.0;
- ShizuCallRecorder: GPL-family, research only unless licensing is deliberately adopted;
- AgentCall: AGPL-3.0, research only by default;
- Superpowers: MIT, development methodology reference only.
