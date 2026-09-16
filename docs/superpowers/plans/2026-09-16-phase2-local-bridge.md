# Phase 2 Local Cellular Bridge Plan

**Goal:** Convert the physically proven direct-shell cellular capture and injection mechanisms into a fail-safe local bidirectional PCM bridge, without AI networking.

## Current status — 2026-09-16

- Milestone A: `DONE`.
- Milestone B: `DONE / PROVEN_S22 / FROZEN`.
- Milestone C: code slice `BUILDS`, physical parity `BLOCKED ON MISSING SHIZUKU RUNTIME FOR USER 0`.
- Milestone D: `PENDING MILESTONE C`.

Primary physical proof: `docs/S22_PHASE2_LOCAL_BRIDGE_2026-09-16.md`.

Frozen known-good reference:

```text
branch: milestone/phase2b-proven-s22-20260916
commit: c10f8dde29f245f8f98fb008a3572c21fe73fe35
```

Freeze/refactor policy: `docs/PHASE2B_FREEZE_2026-09-16.md`.

## Proven inputs

- S22+ `VOICE_DOWNLINK` remote cellular PCM: `PROVEN_S22`.
- Generic `USAGE_MEDIA` / `USAGE_VOICE_COMMUNICATION` telephony TX: `FAILED_S22` for the tested target path.
- Samsung `USAGE_CALL_ASSISTANT -> incall_music_uplink -> TELEPHONY_TX`: `PROVEN_S22`.
- Direct shell UID 2000 privilege boundary: proven.
- Shared production RX+TX PFD controller: `PROVEN_S22`.
- Shizuku UserService code path: builds; physical privilege/media parity not yet proven.

## Invariants

1. Hidden/system Samsung audio behavior stays inside `privileged-helper`.
2. Binder/AIDL is control-plane only.
3. Continuous PCM uses `ParcelFileDescriptor` pipes.
4. No per-frame Binder transactions.
5. Internal PCM is signed PCM16 little-endian, mono, with explicit sample rate.
6. Samsung TX duplicates mono to stereo only at the backend boundary.
7. `abortNow()` discards queued output immediately and never waits on network activity.
8. Controller death/watchdog failure stops capture/injection and releases resources.
9. The helper never places calls and contains no OpenAI networking.
10. Existing bounded CLI probes remain physical-regression tools.
11. On the target S22, `VOICE_DOWNLINK` construction / `controller.prepare()` must occur before explicit Context/AudioManager initialization in the direct-shell/UserService process.
12. RX and TX retain separate proven attribution contexts: `android` for downlink start and `com.android.shell` for CALL_ASSISTANT TX.
13. Do not rewrite the frozen low-level media path merely to reduce class count while Milestone C/D are still unproven.

## Milestone A — reusable Samsung media primitives

Status: `DONE`.

Implemented:
- reusable `SamsungCallAssistantTrack` TX lifecycle;
- reusable `SamsungVoiceDownlinkCapture` RX lifecycle;
- 16 kHz / 48 kHz CALL_ASSISTANT support;
- route guard requiring `TYPE_TELEPHONY` before controller PCM is accepted;
- mono PCM16LE -> Samsung stereo conversion at the TX boundary;
- graceful stop and immediate abort paths;
- existing probes retained as regression harnesses rather than duplicate production implementations.

## Milestone B — privileged media pipes + shared controller

Status: `DONE / PROVEN_S22 / FROZEN`.

Implemented:
- downlink helper writes mono PCM16LE to a PFD pipe and controller reads;
- uplink controller writes mono PCM16LE to a PFD pipe and helper injects;
- `AutoCloseInputStream` / `AutoCloseOutputStream` ownership semantics;
- bounded roughly-20-ms worker chunks;
- deterministic EOF/close behavior;
- one `SamsungCallMediaSessionController` owning both directions;
- one watchdog generation covering RX + TX;
- sibling abort when either media path terminates;
- synchronous `abortNow()` cleanup.

Physical silent live-smoke passed on work-branch SHA:

```text
6b8d9f619031e0ca8352e55b624bdc9bc1edf633
```

Evidence included:

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

The phone remained physically muted (`STREAM_VOICE_CALL Muted:true`, `streamVolume:0`, route `earpiece(1)`).

### Post-freeze cleanup rule

Keep the proven media primitives and `BidirectionalMediaProbe` stable while Milestone C is established.

Safe cleanup is limited to:
- app-facing orchestration boundaries;
- documentation;
- stricter probes/tests;
- removal of code only after repository-wide evidence that it is unused.

Current post-freeze cleanup:
- reflection-heavy system/shell Context creation extracted from `ShizukuCallMediaUserService` into `PrivilegedCallContexts`;
- direct-shell `BidirectionalMediaProbe` intentionally unchanged;
- off-call Shizuku success criteria tightened to require exact `prepare -> abort` states.

Known Milestone D measurement item: `SamsungCallAssistantTrack.writeMonoPcm16Le()` can block in `AudioTrack.write()` while holding its object lock. Current pipe chunks bound writes to roughly 20 ms, but takeover latency must be measured physically rather than assumed.

## Milestone C — Shizuku UserService parity

Status: `CODE BUILDS / PHYSICAL PARITY BLOCKED ON RUNTIME`.

Implemented:
- Shizuku API/provider dependency;
- AIDL control interface;
- shell-side `ShizukuCallMediaUserService`;
- controller PFD handoff methods;
- app-side permission/bind plumbing;
- bounded off-call `prepare -> abort` path;
- `PrivilegedCallContexts` boundary preserving context-free service construction;
- required Shizuku destroy transaction retained as `destroy() = 16777114`;
- strict parity gate requiring:

```text
before: prepared=false, active=false
prepare: prepared=true, active=false, heartbeat=false
abort: prepared=false, active=false, heartbeat=false
```

The UserService responsibility extraction passed helper/app Gradle tests plus the existing 19 Python call-control tests. Physical Shizuku execution is still pending.

Physical parity requirements still to prove:
- UserService effective UID/attribution;
- required protected permission class;
- off-call `prepare -> abort` through real Binder;
- live `VOICE_DOWNLINK` start/read through returned PFD;
- live CALL_ASSISTANT TX through returned PFD;
- shared heartbeat/abort behavior;
- Binder/controller-death cleanup.

Current blocker:
- active Android user is `0`;
- Shizuku is not installed or running for user `0` at the latest device check;
- user `151` is Samsung Secure Folder and is intentionally out of scope;
- no trusted Shizuku APK already exists in the checked Mac/phone download locations.

Do not fetch or install an unverified external APK just to clear this gate.

## Milestone D — local bridge endurance

Status: `PENDING MILESTONE C`.

Before any realtime model:
- start both media directions through the app-facing privileged boundary;
- move deterministic PCM through the same production PFD boundaries;
- exercise repeated stop/start;
- exercise `abortNow()` while TX data is queued;
- kill/disconnect the controller and verify helper cleanup;
- measure underrun/overrun, memory and latency behavior;
- measure actual takeover latency while an uplink write is active.

Exit gate: 10-minute stable local bridge with bounded resources and measured immediate human-takeover semantics.

## Next action when runtime prerequisite is satisfied

1. Install/start a trusted Shizuku runtime for Android user `0`.
2. Install the current app build.
3. Run bounded off-call UserService bind + strict `prepare -> abort` parity and verify UID 2000.
4. Only after that passes, run the same silent-guarded Orange live RX+TX proof through UserService.
5. Prove Binder/controller-death fail-safe cleanup.
6. Execute Milestone D endurance and takeover-latency measurements.
7. Do not start realtime/OpenAI integration before C and D pass.
