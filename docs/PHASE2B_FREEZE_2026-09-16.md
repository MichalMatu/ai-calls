# Phase 2B frozen baseline — 2026-09-16

## Purpose

This file freezes the known-good Samsung Galaxy S22+ local cellular bridge before Shizuku UserService parity work continues.

The goal is to preserve a simple rollback/reference point for the media path that has already been physically proven. Later Shizuku, AI, UX, or cleanup work must not silently redefine that proof.

## Frozen reference

Git branch:

```text
milestone/phase2b-proven-s22-20260916
```

Exact commit:

```text
c10f8dde29f245f8f98fb008a3572c21fe73fe35
```

This branch points at the documented Phase 2B state after the successful physical bidirectional S22 proof and documentation synchronization.

Do not move or rewrite this branch as part of normal development. Use it as the comparison/rollback reference if later privilege-boundary or product work regresses cellular media behavior.

## What the frozen baseline proves

On the target Samsung Galaxy S22+ `SM-S906B`, Android 16 / API 36 / One UI 8:

- production `VOICE_DOWNLINK` capture produces remote-call-correlated PCM;
- production Samsung `CALL_ASSISTANT` uplink reaches cellular TX;
- `SamsungCallMediaSessionController` owns both directions simultaneously;
- RX and TX use independent PFD media pipes;
- Binder is not used per audio frame;
- one heartbeat watchdog owns the bidirectional generation;
- either child ending causes sibling fail-safe cleanup;
- `abortNow()` clears prepared/active state and stops both media paths;
- RX uses the required `android` attribution context;
- TX uses the required `com.android.shell` attribution context;
- the S22 RX initialization order (`prepare()` before explicit Context/AudioManager creation) is preserved;
- the successful live proof ran with physical call audio muted (`STREAM_VOICE_CALL Muted:true`, volume `0`, earpiece route).

Detailed metrics and Local Agent evidence are in:

```text
docs/S22_PHASE2_LOCAL_BRIDGE_2026-09-16.md
```

## Deliberately frozen implementation pieces

The following are treated as the reference implementation until Shizuku parity and endurance are proven:

```text
SamsungVoiceDownlinkCapture
SamsungDownlinkPipeSession
SamsungCallAssistantTrack
SamsungUplinkPipeSession
SamsungCallMediaSessionController
BidirectionalMediaProbe
```

`BidirectionalMediaProbe` is intentionally kept as the direct-shell physical regression harness. Cleanup/refactoring should prefer changing app-facing orchestration first rather than rewriting the proven hardware path.

## Post-freeze cleanup policy

Safe cleanup after the frozen commit may:

- improve documentation;
- strengthen probes so they fail rather than false-pass;
- move Shizuku/context plumbing out of service classes;
- remove code proven unused by repository-wide reference checks;
- add tests and diagnostics.

Do not, without a new physical regression run:

- reorder `prepare()` and Context creation;
- merge RX/TX low-level classes into a new abstraction;
- change CALL_ASSISTANT attributes/routing;
- change PFD EOF ownership semantics;
- increase queued TX audio substantially;
- weaken `abortNow()` or watchdog behavior.

## Immediate post-freeze audit findings

### Keep the media classes separate

The current separation is useful, not accidental:

- low-level Android/Samsung resources are isolated from pipe workers;
- RX and TX have different attribution and initialization requirements;
- the shared controller owns only cross-direction lifecycle/watchdog policy.

A generic base class for both directions would currently hide important asymmetry and increase regression risk.

### Extract app-facing privileged Context creation

The Shizuku UserService should own Binder/media lifecycle, not reflection-heavy Context construction. Post-freeze work therefore extracts that responsibility into `PrivilegedCallContexts` while preserving the exact ordering requirement.

The direct-shell `BidirectionalMediaProbe` remains unchanged so the physically proven regression harness stays independent from the new UserService helper.

### Strengthen Shizuku parity success criteria

The first off-call Shizuku probe could report success without explicitly requiring `prepared_after_prepare=true`. Post-freeze work tightens the gate so success requires the complete expected state transition:

```text
before: prepared=false, active=false
prepare: prepared=true, active=false, heartbeat=false
abort: prepared=false, active=false, heartbeat=false
```

This changes only the diagnostic gate, not media behavior.

### Remove unused legacy uplink controller

Repository-wide source reference checks found no user of `SamsungUplinkSessionController`; matches existed only in previously compiled `.class`, `.dex`, and APK outputs. The class was the older single-direction owner superseded by `SamsungCallMediaSessionController`.

It is removed on the working branch after the freeze. The frozen branch still contains it, so the historical baseline remains fully reproducible.

### Takeover latency remains a test item

`SamsungCallAssistantTrack.writeMonoPcm16Le()` performs a blocking `AudioTrack.write()` while holding its object lock. Input writes are deliberately bounded to roughly 20 ms, limiting the expected abort delay, but this must be measured in Milestone D rather than redesigned speculatively.

## Next gate

Phase 2B is frozen and complete.

Continue with Milestone C only through the app-facing privileged boundary:

1. trusted Shizuku runtime on Android user `0`;
2. off-call UserService bind and exact `prepare -> abort` parity;
3. live UserService RX/TX PFD parity under the existing silent-audio guard;
4. Binder/controller-death cleanup proof;
5. Milestone D endurance/takeover latency measurement;
6. only then connect realtime AI.
