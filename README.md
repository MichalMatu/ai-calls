# Android AI Call Bridge

Experimental Android project for bridging a normal cellular call to a realtime AI voice session on one phone, without external audio hardware.

Initial target: Samsung Galaxy S22+ `SM-S906B` on stock Samsung firmware.

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

## Current status — 2026-09-16

The core stock-Samsung cellular media problem is no longer hypothetical.

### Phase 1A — cellular RX

`DONE / PROVEN_S22`

The production path captures remote-call-correlated PCM digitally through:

```text
VOICE_DOWNLINK
  -> SamsungVoiceDownlinkCapture
  -> SamsungDownlinkPipeSession
  -> ParcelFileDescriptor pipe
```

Important S22 invariant: `VOICE_DOWNLINK` must be constructed through `controller.prepare()` before explicit `Context` / `AudioManager` initialization in the direct-shell process.

### Phase 1B — generic TX

`FAILED_S22 FOR TESTED PATHS`

The tested generic `USAGE_MEDIA` and `USAGE_VOICE_COMMUNICATION` telephony TX approaches did not provide the required uplink path on the target S22.

### Phase 1C — Samsung-specific TX

`DONE / PROVEN_S22`

The working uplink path is:

```text
mono PCM16LE
  -> SamsungCallAssistantTrack
  -> USAGE_CALL_ASSISTANT / AUDIO_STREAM_CALL_ASSISTANT
  -> TYPE_TELEPHONY TX
  -> cellular uplink
```

Remote receipt was physically demonstrated with deterministic digitally injected DTMF against the Orange IVR.

### Phase 2B — shared local RX + TX bridge

`DONE / PROVEN_S22`

One `SamsungCallMediaSessionController` has been physically proven running both directions simultaneously through the production PFD paths while its watchdog and abort path remained healthy.

Successful live proof included:

```text
active_after_start=true
heartbeat_after_start=true
pre_dtmf_non_zero_samples=25451
uplink_bytes_written=9600
post_dtmf_non_zero_samples=38737
active_with_endpoints_open=true
heartbeat_with_endpoints_open=true
media_ok_before_endpoint_close=true
prepared_after_abort=false
active_after_abort=false
heartbeat_after_abort=false
```

The phone remained physically silent during the successful validation:

```text
STREAM_VOICE_CALL Muted:true
streamVolume:0
route=earpiece
```

Full evidence: [`docs/S22_PHASE2_LOCAL_BRIDGE_2026-09-16.md`](docs/S22_PHASE2_LOCAL_BRIDGE_2026-09-16.md).

## Frozen known-good baseline

The proven Phase 2B state is preserved on:

```text
branch: milestone/phase2b-proven-s22-20260916
commit: c10f8dde29f245f8f98fb008a3572c21fe73fe35
```

Do not move/rewrite that branch during normal development. It is the rollback/comparison point for the cellular media path.

Freeze rationale and post-freeze refactor rules are documented in [`docs/PHASE2B_FREEZE_2026-09-16.md`](docs/PHASE2B_FREEZE_2026-09-16.md).

## Current gate — Phase 2C / Shizuku UserService parity

The first app-facing Shizuku slice is implemented:

- Shizuku API/provider dependency;
- AIDL control plane;
- `ShizukuCallMediaUserService`;
- PFD endpoint handoff;
- app-side bind/permission plumbing;
- bounded off-call parity probe;
- preserved `prepare()`-before-Context ordering.

Post-freeze cleanup also separates privileged Context construction into `PrivilegedCallContexts`, keeping reflection-heavy Android plumbing out of the UserService lifecycle class.

The off-call parity probe is intentionally strict: it requires the exact expected `prepare -> abort` state transition rather than merely checking that no session remains afterward.

### Current device prerequisite

At the last device check, Android user `0` had no Shizuku manager/server installed or running. Samsung Secure Folder user `151` is intentionally out of scope.

No unverified APK should be fetched or installed merely to satisfy this gate.

When a trusted Shizuku runtime is available, the next validations are:

1. UserService effective UID and bind;
2. off-call `prepare -> abort` parity;
3. live RX + TX PFD parity under the silent-audio guard;
4. Binder/controller-death fail-safe cleanup;
5. 10-minute local bridge endurance and takeover-latency test.

Only after those pass should realtime AI transport be connected.

## Architecture

- `app/` — normal Android process, UI/orchestration, Shizuku client/probes.
- `audio-bridge/` — device-independent capture/injection contracts and PCM models.
- `privileged-helper/` — protected Android/Samsung audio primitives, PFD workers, shared controller and watchdog.
- `realtime-client/` — realtime model transport abstraction; intentionally not connected yet.
- `docs/` — physical evidence, architecture, roadmap, plans and freeze notes.

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

RX and TX remain separate low-level components because the S22 has different initialization and attribution requirements for each direction. Do not introduce a generic common base merely to reduce line count.

The direct-shell `BidirectionalMediaProbe` remains a hardware regression/reference harness and should not be casually rewritten while Shizuku parity is still being established.

## Development order

1. preserve the proven Phase 2B baseline;
2. prove Shizuku UserService parity;
3. prove controller/Binder-death fail-safe behavior;
4. pass local endurance and takeover-latency gates;
5. connect realtime AI;
6. add product UX and broader route/device robustness.

See [`docs/ROADMAP.md`](docs/ROADMAP.md) for the current gates.

## Hard rules

- Physical media claims become `PROVEN_S22` only after target-device live-call evidence.
- Constructor/permission/device-enumeration success is not equivalent to working call media.
- Preserve the proven S22 RX initialization order unless new physical evidence disproves it.
- Preserve separate RX (`android`) and TX (`com.android.shell`) attribution requirements.
- No per-frame Binder PCM transport.
- `Take over` must be local and fail-safe.
- App/helper death must disable injection.
- No long-lived OpenAI API key in the APK.
- No call recording by default.
- Do not replace the default dialer until the media bridge and product requirements justify it.
- Keep `.agent` execution/control data off canonical product branches such as `main`.

## Key documents

- [`docs/PHASE2B_FREEZE_2026-09-16.md`](docs/PHASE2B_FREEZE_2026-09-16.md) — frozen known-good Phase 2B reference and refactor policy.
- [`docs/S22_PHASE2_LOCAL_BRIDGE_2026-09-16.md`](docs/S22_PHASE2_LOCAL_BRIDGE_2026-09-16.md) — physical simultaneous RX+TX proof.
- [`docs/S22_PHASE1C_PROOF_2026-09-16.md`](docs/S22_PHASE1C_PROOF_2026-09-16.md) — Samsung-specific TX proof.
- [`docs/S22_BASELINE_2026-09-14.md`](docs/S22_BASELINE_2026-09-14.md) — target capability baseline.
- [`docs/ROADMAP.md`](docs/ROADMAP.md) — evidence-driven phase gates.
- [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) — current component boundaries and fail-safe rules.
- [`docs/superpowers/plans/2026-09-16-phase2-local-bridge.md`](docs/superpowers/plans/2026-09-16-phase2-local-bridge.md) — current implementation plan.

## License

No project-wide license has been selected yet. External projects may be used as research references, but implementation code must not be copied without license review.
