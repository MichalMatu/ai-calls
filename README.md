# Android AI Call Bridge

Experimental Android project for bridging a normal cellular call to a realtime AI voice session on one phone, without external audio hardware.

Target device: Samsung Galaxy S22+ `SM-S906B` on stock Samsung firmware.

## Target media path

```text
remote caller
  -> cellular downlink
  -> privileged helper PCM
  -> realtime AI

realtime AI
  -> privileged helper PCM
  -> cellular uplink
  -> remote caller
```

The user must always be able to take over the call immediately. Privileged/media failure must fail toward a normal human call, never toward stuck AI injection.

## Current status — 2026-09-17

The difficult stock-Samsung cellular media path is physically proven on the target S22+.

### Phase 1A — cellular RX

`DONE / PROVEN_S22`

```text
VOICE_DOWNLINK
  -> SamsungVoiceDownlinkCapture
  -> SamsungDownlinkPipeSession
  -> ParcelFileDescriptor pipe
```

Important direct-shell S22 invariant: construct `VOICE_DOWNLINK` through `controller.prepare()` before explicit `Context` / `AudioManager` initialization.

### Phase 1B — generic TX

`FAILED_S22 FOR TESTED PATHS`

The tested generic `USAGE_MEDIA` and `USAGE_VOICE_COMMUNICATION` telephony TX approaches did not provide the required uplink path on the target S22.

### Phase 1C — Samsung-specific TX

`DONE / PROVEN_S22`

```text
mono PCM16LE
  -> SamsungCallAssistantTrack
  -> USAGE_CALL_ASSISTANT / AUDIO_STREAM_CALL_ASSISTANT
  -> TYPE_TELEPHONY TX
  -> cellular uplink
```

Required TX attribution is `com.android.shell`. Remote receipt was physically demonstrated with deterministic digitally injected DTMF.

### Phase 2B — shared local RX + TX bridge

`DONE / PROVEN_S22 / FROZEN`

One `SamsungCallMediaSessionController` is physically proven running both media directions simultaneously through PFD pipes with one shared watchdog/fail-safe lifetime.

Frozen reference:

```text
branch: milestone/phase2b-proven-s22-20260916
commit: c10f8dde29f245f8f98fb008a3572c21fe73fe35
```

### Phase 2C — Shizuku UserService parity

`DONE / PROVEN_S22 / FROZEN`

The real app -> Shizuku UserService -> privileged controller path is physically proven on the same S22+:

- UserService runs under shell UID 2000;
- protected off-call `prepare -> abort` parity passes;
- live bidirectional RX + TX over transferred PFDs passes;
- one shared helper heartbeat/watchdog lifetime remains intact;
- explicit `abortNow()` / TAKE OVER cleanup passes;
- helper/UserService process death terminates the media path while the normal app and Shizuku server survive;
- continuous PCM still does not use per-frame Binder calls.

Frozen reference:

```text
branch: milestone/phase2c-shizuku-live-proven-20260916
commit: 9c136fc05c5b33f383d72b0b7080ad5b9a754bb4
```

Post-freeze deep-audit fixes M1/M2/M3/M4 are complete. M3 reuses Shizuku's existing `ActivityThread.currentActivityThread()` instead of creating a second `ActivityThread.systemMain()` from a Binder thread. Its host, off-call, silent-live and 30-second endurance regressions are GREEN.

### Current gate — Milestone D robustness

`ACTIVE`

Already GREEN:

- 30-second bidirectional endurance;
- explicit abort/takeover latency;
- helper/UserService process-death behavior;
- host regression after the latest tooling work: 30 Python tests PASS and full Gradle build/test PASS.

The nearest unresolved physical gate is **normal app death while media is active**. An earlier attempt was inconclusive because host ADB entered `waiting for device`; the resulting ~105 s host-side timing is therefore not a valid cleanup-latency measurement, even though both the app and UserService were later confirmed gone.

To remove that measurement dependency, the repository now contains:

```text
scripts/s22_app_death_gate.py
scripts/test_s22_app_death_gate.py
```

The observer runs timing-critical checks on the phone using `/proc/uptime`, watches app/helper process death and CALL_ASSISTANT stop evidence, publishes its result atomically, and does not depend on uninterrupted host ADB. It intentionally does not use `nohup` because this S22+ does not provide `toybox nohup`.

Remaining Milestone D work after the app-death gate:

1. end the cellular call while the bridge is active and prove full cleanup;
2. close one transferred RX/TX PFD and prove sibling abort / whole-generation stop;
3. run 10–20 start/abort cycles and compare FD/thread/process/resource counts;
4. run a final 10-minute bidirectional endurance test with telemetry/resource counts;
5. perform final regression/audit and freeze Milestone D;
6. only then connect realtime AI.

## Architecture

- `app/` — normal Android process, UI/orchestration and diagnostic Shizuku clients.
- `audio-bridge/` — device-independent capture/injection contracts and PCM models.
- `privileged-helper/` — Samsung audio primitives, PFD workers, shared controller and watchdog.
- `realtime-client/` — realtime model transport abstraction; intentionally not connected yet.
- `scripts/` — bounded developer/device validation tooling.
- `docs/` — architecture, evidence, plans, handoff and freeze notes.

Privileged media shape:

```text
normal app <--- Binder/AIDL control + FD handoff ---> privileged helper
normal app <=========== PCM PFD pipes =============> privileged helper
```

Continuous PCM must never be transported as one Binder transaction per frame.

## Production media components

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

RX and TX remain separate because the S22 has different initialization and attribution requirements for each direction. Do not add generic abstractions merely to reduce line count.

## Live-test safety rule

For every live cellular test keep the physical phone locally silent:

```text
STREAM_VOICE_CALL Muted:true
streamVolume:0
Devices: earpiece(1)
speakerphone off
```

Assert this before dialing and again after the call becomes active. Prefer direct USB-C <-> USB-C between the S22+ and MacBook; an earlier hub/dock caused misleading ADB transport resets.

## Hard rules

- Physical media claims become `PROVEN_S22` only after target-device live-call evidence.
- Constructor/permission/device-enumeration success is not equivalent to working call media.
- Preserve the proven direct-shell S22 RX initialization order unless new physical evidence disproves it.
- Preserve separate RX (`android`) and TX (`com.android.shell`) attribution requirements.
- Preserve `USAGE_CALL_ASSISTANT` and mono PCM16LE -> stereo only at the Samsung TX boundary.
- No per-frame Binder PCM transport.
- `Take over` must be local and fail-safe.
- App/helper death must disable injection.
- No long-lived OpenAI API key in the APK.
- No call recording by default.
- Do not replace the default dialer until the media bridge and product requirements justify it.
- Keep `.agent` execution/control data on `agent-control`, never merged into product branches.

## Key documents

- `docs/HANDOFF_NEXT_CHAT.md` — authoritative continuation state.
- `docs/ROADMAP.md` — current evidence-driven phase gates.
- `docs/ARCHITECTURE.md` — current component boundaries and fail-safe rules.
- `docs/PHASE2B_FREEZE_2026-09-16.md` — frozen Phase 2B reference.
- `docs/S22_PHASE2_LOCAL_BRIDGE_2026-09-16.md` — physical simultaneous RX+TX proof.
- `docs/PHASE2_DEEP_AUDIT_2026-09-16.md` — post-freeze audit and follow-up requirements.

## License

No project-wide license has been selected yet. External projects may be used as research references, but implementation code must not be copied without license review.
