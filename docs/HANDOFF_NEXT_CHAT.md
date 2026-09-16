# Handoff — next chat / Phase 2 deep audit

Date: 2026-09-16

Repository: `MichalMatu/android-ai-call-bridge`

Work branch: `work/phase1-live-call-probes`

Current work-branch HEAD at handoff creation:

```text
c42377681f6b5025ada79c6e01fe7f675453b3ad
```

This HEAD includes the newly added 30-second `ShizukuEnduranceProbe` trigger. The endurance probe itself has not yet been promoted to a proven result and must not be treated as verified merely because it exists.

## Start here

The next chat should **not immediately continue feature work**. First perform a deep repository audit and bring code quality, object/responsibility separation, tests and documentation to a coherent baseline before the remaining Phase 2 endurance work and any Realtime AI integration.

Read first:

- `AGENTS.md`
- `docs/HANDOFF_NEXT_CHAT.md`
- `docs/ARCHITECTURE.md`
- `docs/ROADMAP.md`
- `docs/SECURITY_PRIVACY.md`
- `docs/DEVELOPMENT_WORKFLOW.md`
- `docs/S22_PHASE2_LOCAL_BRIDGE_2026-09-16.md`
- `docs/PHASE2B_FREEZE_2026-09-16.md`

Important: several older status sections in `ARCHITECTURE.md`, `ROADMAP.md`, `DEVELOPMENT_WORKFLOW.md` and `S22_PHASE2_LOCAL_BRIDGE_2026-09-16.md` are now stale. In particular, they may still describe Shizuku/Milestone C as blocked or unproven. The evidence below supersedes those stale status paragraphs until the audit synchronizes the docs.

## Hard safety rule for all live-call work

Never use the phone loudspeaker or any audible local call output.

For every live cellular test:

```text
STREAM_VOICE_CALL Muted:true
streamVolume:0
Devices: earpiece(1)
```

Mute before dialing and re-assert mute after call state becomes ACTIVE. Keep speakerphone off. Digital RX/TX capture/injection is allowed; local audible playback is not.

Target device:

```text
Samsung Galaxy S22+ SM-S906B
serial RFCT70L7E8J
Android 16 / API 36 / One UI 8
build BP2A.250605.031.A3.S906BXXSOGZH3
Orange PL SIM1
Google Phone default dialer
Shizuku 13.5 / adb mode
shell UID 2000
```

Keep the phone directly connected USB-C <-> USB-C to the MacBook. A prior hub/dock setup caused misleading host-side ADB server resets while device-side tests completed correctly.

## Frozen known-good checkpoints

### Phase 2B physical RX+TX baseline

```text
branch: milestone/phase2b-proven-s22-20260916
commit: c10f8dde29f245f8f98fb008a3572c21fe73fe35
```

Do not rewrite or move this branch.

### Phase 2C Shizuku live-parity baseline

```text
branch: milestone/phase2c-shizuku-live-proven-20260916
commit: 9c136fc05c5b33f383d72b0b7080ad5b9a754bb4
```

This is the rollback/reference point for the normal-app -> Shizuku UserService -> privileged production controller path.

## What is already proven

### Digital downlink RX — PROVEN_S22

Production path:

```text
VOICE_DOWNLINK
 -> SamsungVoiceDownlinkCapture
 -> SamsungDownlinkPipeSession
 -> PFD PCM pipe
 -> app/controller reader
```

The S22 initialization-order invariant is real:

```text
construct VOICE_DOWNLINK / controller.prepare()
BEFORE explicit Context/AudioManager initialization
```

Do not collapse or reorder this without new physical evidence.

### Digital uplink TX — PROVEN_S22

Production path:

```text
mono PCM16LE
 -> SamsungUplinkPipeSession
 -> SamsungCallAssistantTrack
 -> duplicate mono to stereo only at Samsung boundary
 -> USAGE_CALL_ASSISTANT / AUDIO_STREAM_CALL_ASSISTANT
 -> AUDIO_DEVICE_OUT_TELEPHONY_TX
 -> cellular uplink
```

Required attribution: `com.android.shell`.

Generic `USAGE_MEDIA` and `USAGE_VOICE_COMMUNICATION` uplink paths are `FAILED_S22` for the tested target.

### Shared bidirectional controller — PROVEN_S22

`SamsungCallMediaSessionController` owns one RX+TX generation with one watchdog. Either child ending causes sibling cleanup. PCM transport is through PFD pipes; no per-frame Binder transport.

Do not casually refactor the proven Samsung media primitives merely to reduce line count.

### Shizuku UserService parity — PROVEN_S22 / FROZEN

Exact successful live evidence is retained in:

```text
.agent/results/phase2c-postmortem-live-logcat-20260916-1245.json
```

Key result:

```text
probe=shizuku-user-service-live-v1
shizuku_uid=2000
service_uid=2000
active_after_start=true
heartbeat_after_start=true
uplink_bytes_written=9600
post_dtmf_non_zero_samples=22733
post_dtmf_peak=21994
post_dtmf_rms=2326.3491799867447
active_with_endpoints_open=true
heartbeat_with_endpoints_open=true
media_ok_before_endpoint_close=true
prepared_after_abort=false
active_after_abort=false
heartbeat_after_abort=false
live_parity_ok=true
```

Freeze verification:

```text
.agent/results/phase2c-freeze-shizuku-live-proven-20260916-1249.json
```

### Heartbeat watchdog fail-safe — PROVEN live

Result:

```text
.agent/results/phase2d-live-watchdog-silent-20260916-1333.json
```

Measured while RX and TX were actively flowing:

```text
heartbeat_timeout_ms=2000
watchdog_observed_ms=2003
watchdog_overrun_ms=3
watchdog_became_inactive=true
prepared_after_timeout=false
active_after_timeout=false
heartbeat_after_timeout=false
threads_stopped=true
watchdog_fail_safe_ok=true
```

The 2-second watchdog fired with only ~3 ms observed overrun.

### Explicit TAKE OVER / abortNow latency — PROVEN live

Result:

```text
.agent/results/phase2d-live-abort-silent-20260916-1343.json
```

Measured while RX and TX were actively flowing:

```text
downlink_bytes_read=7680
uplink_bytes_written=73600
abort_rpc_ms=89
abort_inactive_observed_ms=89
abort_became_inactive=true
prepared_after_abort=false
active_after_abort=false
heartbeat_after_abort=false
threads_stopped=true
explicit_abort_fail_safe_ok=true
```

The earlier concern that blocking `AudioTrack.write()` under synchronization could make takeover slow did not reproduce as a large latency problem in this live test. Still audit the locking structure; do not refactor it without a concrete benefit and regression proof.

### UserService process death — PROVEN fail-safe

Result:

```text
.agent/results/phase2d-live-userservice-death-silent-20260916-1352.json
```

The shell UserService process was killed while CALL_ASSISTANT media was active:

```text
userservice_pid=10271
call_assistant_active_before_kill=true
userservice_process_gone=1
userservice_kill_to_gone_ms=167
error=IOException:write failed: EPIPE (Broken pipe)
shizuku_server still alive
main app still alive
silent_audio_guard_after_userservice_death=true
phase2d_userservice_death_silent_ok=true
```

This proves the client does not continue writing into an orphaned helper path after UserService death.

## Current unverified work at HEAD

After the proven watchdog/abort/death tests, two commits added a short endurance diagnostic:

```text
c55946e59be14ead95016ecf187c31acbea46265  Add Shizuku bidirectional endurance probe
c42377681f6b5025ada79c6e01fe7f675453b3ad  Wire Shizuku endurance probe trigger
```

`ShizukuEnduranceProbe` currently targets 30 seconds with:

- RX drain;
- TX digital silence;
- heartbeat every 250 ms;
- repeated `hasActiveSession()` checks;
- byte counters;
- explicit abort and thread cleanup at the end.

Do **not** assume this new HEAD is fully validated. The first action of the next audit should verify exact HEAD, clean tree and a fresh full build/test baseline before changing code.

## Mandatory next step: deep re-audit before continuation

The next chat should perform a systematic pre-continuation audit with **no behavior change in the first pass**.

Create a durable audit document, preferably:

```text
docs/PHASE2_DEEP_AUDIT_2026-09-16.md
```

Classify findings by severity/risk and distinguish:

```text
MUST FIX BEFORE ENDURANCE
SHOULD FIX BEFORE PHASE 3
SAFE CLEANUP
DEFER / YAGNI
```

### Audit area 1 — module and responsibility separation

Review whether responsibilities are correctly separated across:

```text
app/
audio-bridge/
privileged-helper/
realtime-client/
```

Check specifically:

- normal app does not own Samsung/private audio primitives;
- UserService remains only a narrow privileged lifecycle/control owner;
- Samsung-specific code stays behind the privileged boundary;
- diagnostic probes do not become accidental production architecture;
- AIDL surface is minimal and has a clear ownership contract;
- no Realtime/OpenAI networking leaks into privileged code;
- no arbitrary shell-command capability is exposed to app/model code.

Look for duplicated orchestration among `ShizukuUserServiceProbe`, `ShizukuWatchdogProbe`, `ShizukuAbortLatencyProbe`, `ShizukuEnduranceProbe` and `MainActivity`. Prefer extracting safe test/probe utilities only when it improves correctness/readability without touching proven media semantics.

### Audit area 2 — lifecycle/state machine correctness

Build the actual lifecycle/state model from code, not from comments.

Audit:

- prepare/start/endpoint-transfer/heartbeat/abort/destroy;
- repeated start/stop;
- stale generation protection;
- endpoint close semantics;
- child death -> sibling abort;
- UserService disconnect/death;
- app process death;
- call ending while bridge is active;
- Shizuku server disappearance;
- service restart/rebind behavior;
- whether every path ends with no prepared/active/heartbeat state left behind.

Look for impossible states, double-close hazards, state duplication and races between Binder calls, watchdog callbacks and worker termination.

### Audit area 3 — concurrency, locks and blocking I/O

Review all `synchronized`, thread ownership and blocking calls.

Pay special attention to:

- `SamsungCallAssistantTrack.writeMonoPcm16Le()` holding a lock around `AudioTrack.WRITE_BLOCKING`;
- `SamsungUplinkPipeSession` worker shutdown;
- downlink blocking reads;
- `abortNow()` lock ordering;
- watchdog callback versus explicit abort;
- `ShizukuCallMediaUserService` synchronized methods;
- Binder pool thread behavior;
- potential deadlocks or lock inversion;
- whether 20 ms chunking is a sufficient bound everywhere.

Do not rewrite the locking model merely because a pattern looks inelegant; require a concrete failure/risk and preserve the measured 89 ms takeover behavior or improve it with proof.

### Audit area 4 — resource ownership and leaks

For every resource, identify exactly who creates, transfers, closes and aborts it:

- `AudioRecord`;
- `AudioTrack`;
- every `ParcelFileDescriptor` end;
- AutoClose streams;
- worker threads;
- watchdog scheduler/timer;
- Handler callbacks;
- Shizuku binding;
- privileged Context objects;
- Binder references.

Check FD/thread/resource leak behavior across repeated sessions.

### Audit area 5 — Looper / hidden-context construction

`PrivilegedCallContexts.create()` currently calls `Looper.prepare()` on a Binder pool thread when needed before hidden Context creation.

That solved a real API36 failure, but the prepared Looper is not run as a conventional message loop.

Audit this carefully:

- whether any constructed framework object may post work to that Looper;
- whether the Binder thread can later be reused in a surprising state;
- whether a dedicated helper thread/Looper would be safer;
- whether changing this would touch the frozen proven behavior enough that it should be deferred.

Do not refactor preemptively without evidence.

### Audit area 6 — API/object quality

Review:

- class responsibilities;
- method naming and contracts;
- immutable state where practical;
- constructor/factory boundaries;
- exception taxonomy and preservation of root cause;
- nullability/ownership clarity;
- Java/Kotlin interop;
- package placement;
- whether diagnostics are structured enough for future automation;
- unnecessary reflection or duplicated magic constants;
- dead code from failed generic TX experiments.

Avoid generic abstractions that hide real Samsung asymmetry.

### Audit area 7 — automated tests

Inventory what is and is not covered.

At minimum consider tests for:

- watchdog generation/stale callback protection;
- sibling abort when either child terminates;
- prepared -> abort state transition;
- repeated abort idempotence;
- endpoint transfer only once;
- PFD close termination semantics;
- framing/half-sample handling;
- PCM mono/stereo boundary conversion;
- DTMF generation utilities if retained;
- explicit state transitions under fake/fault-injected children;
- UserService lifecycle logic that can be tested without real Android audio;
- repeated start/stop leak regression where practical.

Do not mock away the physical proof gates: automated tests complement, but do not replace, S22 live-call evidence.

### Audit area 8 — documentation truthfulness

Synchronize at least:

- `docs/ARCHITECTURE.md`
- `docs/ROADMAP.md`
- `docs/DEVELOPMENT_WORKFLOW.md`
- `docs/S22_PHASE2_LOCAL_BRIDGE_2026-09-16.md`
- `docs/SECURITY_PRIVACY.md` if lifecycle facts changed

Current docs still contain stale claims that Milestone C/Shizuku runtime is blocked. Replace them with exact proven states and result-file references.

Keep evidence classification strict: build success is not physical media proof.

### Audit area 9 — build quality and warnings

Freshly run all relevant builds/tests and investigate warnings rather than ignoring them indefinitely.

Known current javac warning during app build:

```text
unknown enum constant Scope.LIBRARY_GROUP_PREFIX
reason: class file for androidx.annotation.RestrictTo$Scope not found
```

Determine whether this is harmless dependency metadata, a missing compile dependency, or something worth fixing. Do not add dependencies blindly.

### Audit area 10 — security and product boundary

Re-check `docs/SECURITY_PRIVACY.md` against actual code:

- helper cannot execute arbitrary shell commands for app/model;
- no persistent raw PCM recording;
- no long-lived API secret in APK;
- fail-safe is local and independent from network/OpenAI;
- binder surface cannot leave injection active after app/control loss;
- logging contains no private audio/transcript payload.

## Missing physical/device gates after the audit

Do not jump directly to Realtime AI yet. After audit/fixes and fresh regression tests, complete Phase 2 robustness.

### 1. Validate the new 30-second endurance probe

First build/test it off-call, then run one physically silent live call.

Use it as a smoke gate, not the final endurance requirement.

### 2. Full 10-minute bidirectional endurance

Measure at least:

- RX bytes progressing;
- TX bytes progressing;
- heartbeat count;
- active state throughout;
- helper RSS start/end/peak if practical;
- app RSS start/end/peak;
- FD count start/end;
- thread count start/end;
- AudioTrack/AudioRecord still alive during run;
- final abort latency;
- no orphaned helper process/session after stop.

### 3. Kill the normal app process while media is active

This is still important even though UserService death is proven.

Expected behavior:

- heartbeats stop;
- watchdog expires in about 2 seconds;
- privileged media shuts down locally;
- no orphaned injection remains;
- cellular call itself remains under human control.

Measure app-death -> media-stop latency.

### 4. End the cellular call while bridge is active

Verify media children terminate and sibling cleanup leaves no active/prepared state.

### 5. Close one transferred PFD intentionally

Prove the designed invariant that one endpoint loss terminates the whole bidirectional generation and sibling media cannot remain live.

Test both RX-read-end close and TX-write-end close if feasible.

### 6. Repeated lifecycle cycles

Run multiple start/abort cycles (for example 10-20) and compare:

- process RSS;
- FD count;
- thread count;
- stale UserService processes;
- AudioFlinger tracks/records.

This is likely more useful than a single very long run for detecting ownership bugs.

### 7. Optional robustness before Phase 3

If cheap after the audit:

- Shizuku server death during active media;
- app background/foreground transition;
- screen off;
- ADB disconnect during active media while phone-side Shizuku remains alive.

These may also be deferred into later robustness phases if the core Phase 2 exit gate is already strong.

## Architecture improvements worth considering during audit

These are **questions**, not pre-approved refactors:

1. Add explicit Binder `DeathRecipient` handling in the app/session coordinator so UserService death becomes an immediate explicit state transition rather than only an EPIPE/disconnect side effect.
2. Separate diagnostic probe plumbing from the eventual production session coordinator so `MainActivity` does not become the lifecycle owner.
3. Introduce a small production session state enum/model only if it removes duplicated implicit state without hiding helper truth.
4. Share safe probe utilities for Shizuku binding, PFD drain/write workers, timing and result formatting if duplication is now causing maintenance risk.
5. Add structured latency/resource counters that the future Realtime layer can consume without parsing human log strings.
6. Consider a dedicated privileged Context/Looper owner if the deep audit proves Binder-thread `Looper.prepare()` is fragile.
7. Remove failed experimental generic TX code only after repository-wide proof that nothing still depends on it and after preserving research/evidence documentation.
8. Keep `Take over` local-first: local injection abort must complete before any future network/model cancellation is awaited.

## Things that must not regress

Do not change without explicit evidence and a live regression plan:

- `prepare()` before explicit Context/AudioManager initialization for S22 RX;
- RX system attribution requirement;
- TX `com.android.shell` attribution;
- Samsung `USAGE_CALL_ASSISTANT` / `AUDIO_STREAM_CALL_ASSISTANT` path;
- internal mono PCM16LE with stereo duplication only at Samsung TX boundary;
- PFD ownership/AutoClose semantics;
- no per-frame Binder transport;
- one fail-safe lifetime for both directions;
- local silent-test guard;
- immediate human takeover semantics.

## Suggested next-chat execution sequence

1. Verify Local Agent is idle before editing the work branch.
2. Fetch and verify exact remote `work/phase1-live-call-probes` HEAD.
3. Run fresh baseline build/tests on that exact SHA before any edit.
4. Perform read-only deep code audit and write `docs/PHASE2_DEEP_AUDIT_2026-09-16.md`.
5. Review findings against the frozen Phase 2C snapshot and classify by risk.
6. Fix only clear issues, in small commits, with tests before/with behavior changes.
7. Re-run full automated suite and `git diff --check` after cleanup.
8. If any proven media/lifecycle path changed, run narrow physically silent regression(s), not a broad blind test.
9. Synchronize stale architecture/roadmap/evidence docs.
10. Validate 30-second endurance probe.
11. Complete the missing app-death/call-end/PFD-close/repeated-cycle gates.
12. Run final 10-minute endurance/resource test.
13. Freeze Milestone D on a dedicated milestone branch only after all required evidence is green.
14. Only then design/connect Realtime AI.

## Local tooling

Mac build environment used successfully:

```text
ANDROID_HOME=$HOME/Library/Android/sdk
GRADLE=$HOME/.gradle/local-agent/gradle-9.6.0/bin/gradle
```

Typical full validation set:

```text
:privileged-helper:testDebugUnitTest
:privileged-helper:assembleDebug
:app:testDebugUnitTest
:app:assembleDebug
python tests (currently 19 historical call-control tests in prior freeze runs)
git diff --check
```

Use Local Agent for Mac/device execution. Do not delegate to local Codex; ChatGPT owns planning/code decisions and Local Agent executes deterministic commands.

## Final state at handoff

The hard Android/Samsung media problem is solved:

- digital cellular RX proven;
- digital cellular TX proven;
- simultaneous production RX+TX proven;
- normal app -> Shizuku privileged boundary proven;
- watchdog fail-safe measured;
- explicit takeover latency measured at 89 ms;
- UserService death fail-safe proven.

What remains before Realtime AI is primarily engineering quality and robustness:

- deep audit + targeted cleanup;
- documentation synchronization;
- complete automated test coverage around lifecycle/fail-safe code;
- endurance/resource/leak validation;
- normal-app-death, call-end, endpoint-loss and repeated-cycle tests;
- final Milestone D freeze.

The next chat should preserve this evidence, improve the implementation around it, and avoid rediscovering or destabilizing the already proven Samsung media path.
