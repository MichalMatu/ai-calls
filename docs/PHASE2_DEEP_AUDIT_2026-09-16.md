# Phase 2 deep audit — 2026-09-16

## Scope

This audit was performed before continuing Milestone D and before any Realtime AI integration.

Audited work branch:

```text
work/phase1-live-call-probes
HEAD at audit start: 91d6fc115956ec1e221e406fc9fca64527e2c916
last code commit before handoff: c42377681f6b5025ada79c6e01fe7f675453b3ad
```

Frozen comparison point:

```text
milestone/phase2c-shizuku-live-proven-20260916
9c136fc05c5b33f383d72b0b7080ad5b9a754bb4
```

The current branch is seven commits ahead of the Phase 2C frozen point. The diff is limited to the later watchdog/abort/endurance diagnostics, `MainActivity` probe triggering and the handoff document. The proven low-level Samsung media implementation, `ShizukuCallMediaUserService` and `PrivilegedCallContexts` are unchanged from the frozen Phase 2C reference.

No production/media behavior was changed during the first audit pass.

## Fresh baseline

Local Agent was idle before the audit.

Exact SHA/tree verification passed:

```text
actual=91d6fc115956ec1e221e406fc9fca64527e2c916
remote=91d6fc115956ec1e221e406fc9fca64527e2c916
phase2c_snapshot=9c136fc05c5b33f383d72b0b7080ad5b9a754bb4
clean tree=true
```

Fresh build/test command passed from a clean build:

```text
:privileged-helper:testDebugUnitTest
:app:testDebugUnitTest
:audio-bridge:testDebugUnitTest
:realtime-client:testDebugUnitTest
:app:assembleDebug
git diff --check
```

Result:

```text
BUILD SUCCESSFUL
109 actionable tasks: 109 executed
```

Known warnings reproduced:

```text
CapabilityProbe.kt: AudioManager.isSpeakerphoneOn is deprecated
javac: unknown enum constant Scope.LIBRARY_GROUP_PREFIX (7 occurrences)
```

`audio-bridge` and `realtime-client` currently report `NO-SOURCE` for unit tests.

## Overall architecture verdict

The core Phase 2 media architecture is substantially better separated than the probe layer around it and should not be broadly refactored before endurance.

The useful existing separation is:

```text
SamsungVoiceDownlinkCapture
  owns AudioRecord only

SamsungDownlinkPipeSession
  owns RX PFD write side + RX worker

SamsungCallAssistantTrack
  owns Samsung AudioTrack only

SamsungUplinkPipeSession
  owns TX PFD read side + TX worker

SamsungCallMediaSessionController
  owns one shared RX+TX generation + watchdog policy

ShizukuCallMediaUserService
  owns Binder-facing lifecycle and endpoint handoff
```

This separation reflects real Samsung asymmetry. A generic RX/TX base class would currently reduce clarity and increase regression risk.

The weak area is the normal-app diagnostic/orchestration layer. The probes now duplicate substantial binding/thread/PFD/result logic and `MainActivity` is becoming both UI and execution dispatcher. That is acceptable for temporary diagnostics, but it should not become the Phase 3 production architecture.

## Module boundary review

### `privileged-helper/`

Verdict: **good boundary; preserve**.

It contains the protected Samsung audio resources, pipe workers and shared fail-safe controller. It contains no OpenAI/realtime networking and exposes no arbitrary shell execution.

The normal APK currently has an implementation dependency on this module because the Shizuku UserService class is packaged from the same APK. This is a packaging dependency, not a privilege grant: ordinary app code does not gain shell capability by being able to reference these classes. Splitting this into another APK/module would add complexity without a current safety benefit.

### `app/`

Verdict: **diagnostic responsibilities are now too concentrated**.

`MainActivity` owns permission flow, Shizuku readiness checks, probe dispatch, automation intent extras and UI result rendering. Separate probe classes then each reimplement their own UserService bind/timeout/cleanup plumbing.

Before Phase 3, production call-media lifetime should move into a dedicated coordinator independent of `MainActivity` and independent of diagnostic probes.

### `audio-bridge/`

Verdict: **clean but currently mostly architectural scaffold**.

`CallAudioCapture`, `CallAudioInjector`, `PcmFrame` and `AudioBridgeMetrics` are not the active Phase 2 production path. Do not delete them merely because reference counts are currently low: they are the intended device-independent boundary for the future app/realtime integration.

### `realtime-client/`

Verdict: **correctly isolated and intentionally unused**.

`RealtimeTransport` is not connected to telephony and should remain that way until Phase 2 is frozen.

## Actual lifecycle/state model

The helper does not currently use an explicit enum. Its real state is encoded by owned resources.

### Controller state

```text
EMPTY
  preparedDownlink == null
  activeDownlink == null
  activeUplink == null
  watchdog == null

PREPARED
  preparedDownlink != null
  preparedDownlinkReader != null
  preparedSampleRate != 0

ACTIVE
  activeDownlink != null
  activeUplink != null
  watchdog != null
```

`start()` transactionally removes PREPARED ownership before constructing/starting TX. Failure closes both endpoints and aborts both children.

`abortNow()` uses the strongest pattern in this codebase: detach all controller-owned references while holding the controller lock, increment generation, then close/abort outside the lock.

The watchdog captures both expected child object identities and a generation token, so a stale timeout cannot abort a newer generation.

### UserService state

Additional state exists above the controller:

```text
contexts != null
  prepare plumbing exists but media not started

endpoints != null
  controller endpoints object exists
```

These states are synchronized at the service level. They duplicate some controller truth but currently remain coherent in normal paths.

### Endpoint-close semantics

Transferred PFD closure is intentionally a session termination signal. A child worker terminates on EOF/EPIPE; the next `heartbeat()` / `hasActiveSession()` reaps the generation and aborts the sibling. Independently, the watchdog provides the upper bound if controller activity disappears entirely.

That invariant is sound and should not be redesigned before the explicit RX-close/TX-close device gates.

## Resource ownership

| Resource | Creator | Owner while active | Transfer/close rule |
| --- | --- | --- | --- |
| `AudioRecord` | `SamsungVoiceDownlinkCapture.open` | `SamsungVoiceDownlinkCapture` | released by stop/abort |
| RX pipe write PFD | `SamsungDownlinkPipeSession.open` | RX session/worker | AutoCloseOutputStream + abort close |
| RX pipe read PFD | RX session -> controller -> UserService -> app | transferred exactly once | app AutoCloseInputStream; close means session termination |
| `AudioTrack` | `SamsungCallAssistantTrack.open` | `SamsungCallAssistantTrack` | pause/flush/stop/release on abort |
| TX pipe read PFD | `SamsungUplinkPipeSession.open` | TX session/worker | AutoCloseInputStream + abort close |
| TX pipe write PFD | TX session -> controller -> UserService -> app | transferred exactly once | app AutoCloseOutputStream; close means session termination |
| RX worker | `SamsungDownlinkPipeSession` | RX session | daemon; abort closes PFD/capture and interrupts |
| TX worker | `SamsungUplinkPipeSession` | TX session | daemon; abort closes PFD/track and interrupts |
| watchdog executor | `HeartbeatWatchdog` | controller generation | close/shutdown on abort/reap; self-shutdown on fire |
| Shizuku binding | individual app probe | probe | explicit `unbindUserService(..., true)` |
| hidden attribution Contexts | `PrivilegedCallContexts` | UserService prepare/start path | currently recreated for each prepare |

The production helper ownership is mostly clear. The weaker ownership is in the diagnostic probe threads: the watchdog/abort/endurance probes do not put all client PFD/thread shutdown in a `finally`, so exceptional paths can depend on remote abort/process death to make local threads exit.

## Concurrency and lock review

### `SamsungCallAssistantTrack.writeMonoPcm16Le()`

The method still holds the track monitor across `AudioTrack.WRITE_BLOCKING`.

This is not currently a must-fix. Input chunks are approximately 20 ms and the physically measured explicit abort path was 89 ms while RX/TX were flowing. The 10-minute and call-end gates should continue to measure this rather than replacing a proven locking model speculatively.

### Controller locking

`abortNow()` is strong: references are detached under lock and expensive cleanup occurs outside the controller lock.

`reapTerminatedLocked()` and start rollback still perform some child/watchdog cleanup while the controller monitor is held. No concrete lock inversion was found: watchdog timeout releases its own monitor before invoking the controller callback, and child abort paths do not callback into the controller. This is therefore a bounded quality concern, not a current refactor target.

### UserService synchronization

`prepare()`, `startMedia()`, endpoint transfer and `abortNow()` are serialized on the Binder service instance.

This keeps state simple, but it means an abort RPC cannot enter while `prepare()`/`startMedia()` is still holding the service monitor. Today those operations contain hidden framework setup, route confirmation sleeps and a bounded warmup write. The successful device evidence shows this is acceptable for the proven path, but production telemetry should measure start/abort latency rather than assume these calls are always short.

## MUST FIX BEFORE ENDURANCE

### M1 — live Shizuku probe bodies execute on the app main thread

This is a concrete correctness defect in the diagnostic layer.

Shizuku 13.x `ShizukuServiceConnection` owns a static `Handler(Looper.getMainLooper())` and posts both `onServiceConnected()` and `onServiceDisconnected()` to that handler.

Current probe implementations call the synchronous probe body directly from `onServiceConnected()`:

- `ShizukuUserServiceProbe` — up to 5 seconds of live blocking reads/RPCs;
- `ShizukuWatchdogProbe` — roughly 2 seconds plus cleanup;
- `ShizukuAbortLatencyProbe` — blocking media setup/RPCs;
- `ShizukuEnduranceProbe` — 30 seconds of heartbeat polling/sleeps.

The new 30-second endurance probe therefore blocks the app main Looper for almost its entire run and can trigger ANR/unresponsive UI behavior. It also makes timeout/UI callback behavior harder to reason about.

Required fix: keep Shizuku connection callbacks on main, but hand the actual probe body to a dedicated bounded worker thread/executor and post only the final result back to main.

This fix touches diagnostic orchestration only, not the frozen media path.

### M2 — exported `MainActivity` accepts privileged live-probe automation extras

`MainActivity` is exported because it is the launcher activity. Its `onCreate()` also accepts extras such as:

```text
run_shizuku_live_probe
run_shizuku_watchdog_probe
run_shizuku_abort_probe
run_shizuku_endurance_probe
```

Once this app already has Shizuku permission, another ordinary app can explicitly launch the exported activity with those extras. That can cause the bridge diagnostics to bind a shell UserService and start privileged live-call media without the intended ADB/operator path.

This is inconsistent with `SECURITY_PRIVACY.md`'s requirement to minimize privileged control surface.

Required fix before further live endurance work: move automation-only triggers out of the exported launcher surface. The deterministic ADB entrypoint should be a separate debug/diagnostic component protected so shell/ADB can invoke it but arbitrary third-party apps cannot. Manual in-app buttons may remain user-driven.

Do not solve this with a secret extra or an unverified caller-package heuristic.

### M3 — `PrivilegedCallContexts.create()` constructs a second `ActivityThread` on a Binder pool thread

Current implementation:

```text
if Binder thread has no Looper -> Looper.prepare()
ActivityThread.systemMain()
getSystemContext()
create shell Context
```

This was sufficient to pass the physical Phase 2C path, but the implementation is structurally unsafe for repeated sessions.

Relevant Android 16 facts from AOSP:

- `ActivityThread` captures `Looper.myLooper()` into `mLooper` during construction;
- it constructs its internal `H extends Handler` immediately;
- `systemMain()` creates a new `ActivityThread` and calls `attach(true, 0)`;
- `attach()` updates static/current ActivityThread state and registers process callbacks.

A prepared Binder-thread Looper is never run with `Looper.loop()`, so framework work posted to that ActivityThread's Handler cannot be serviced normally. Repeating `systemMain()` for later prepares also creates additional framework objects/callback registrations.

More importantly, Shizuku already performs the correct process bootstrap before constructing our UserService: its `ServiceStarter.main()` prepares the process main Looper and `UserService.create()` calls `ActivityThread.systemMain()` before loading/instantiating the service class.

Therefore our second `systemMain()` is unnecessary.

Required fix: reuse Shizuku's already-created `ActivityThread` via `ActivityThread.currentActivityThread()` / current system context and derive the shell Context from that existing instance. Remove Binder-thread `Looper.prepare()` and do not create another ActivityThread.

Because this is part of the proven Shizuku attribution path, the code change must be followed by off-call prepare/abort validation and a narrow silent live Phase 2C regression before endurance.

### M4 — diagnostic media threads lack deterministic exceptional cleanup

`ShizukuWatchdogProbe`, `ShizukuAbortLatencyProbe` and `ShizukuEnduranceProbe` create client RX/TX PFDs plus two local worker threads.

Their normal path stops/closes/interrupts/joins these workers. If a Binder call or observation step throws after the workers start, control escapes to the outer `onServiceConnected()` catch; the outer `finally` calls `service.abortNow()`, but local PFD/thread cleanup is not guaranteed in a local `finally`.

Usually helper abort/process death will force EOF/EPIPE and the daemon threads will exit, but endurance/resource measurements must not rely on that side effect.

Required fix: put local stop/close/interrupt/join ownership behind one deterministic `finally`/AutoCloseable probe-media owner. This is also the right narrow place to remove duplicated probe worker code.

## SHOULD FIX BEFORE PHASE 3

### S1 — add a production `CallMediaSessionCoordinator`

Yes. A separate production coordinator is warranted before Realtime AI.

It should own:

```text
Shizuku availability/permission precondition
UserService bind/unbind
Binder instance
prepare/start/endpoints
heartbeat scheduling
local PFD streams/workers
explicit abort/takeover
helper-death transition
session telemetry
```

`MainActivity` should render/control coordinator state, not become the lifecycle owner. Diagnostic probes should remain separate regression tools.

Do not build this coordinator before the current MUST findings and Milestone D regression gates are complete.

### S2 — app-side Binder `DeathRecipient`

Yes, for the future production coordinator.

Shizuku itself links to the UserService binder and converts binder death into `onServiceDisconnected()`, so the current diagnostic path already receives a death signal. A coordinator-owned direct `IBinder.DeathRecipient` is still useful because it makes helper death an explicit immediate state-machine input instead of relying only on a higher-level callback/EPIPE observation.

Important distinction: an app-side DeathRecipient helps the app react to helper death. It does **not** make the helper react faster to app death. The helper-side 2-second heartbeat remains the proven app/control-loss fail-safe unless a future requirement justifies adding a controller Binder token to AIDL.

Do not expand AIDL solely to replace the working heartbeat.

### S3 — explicit app/session state model

The helper's implicit resource state is currently small and proven; do not rewrite it into a new enum merely for style.

The future app coordinator should have an explicit state model, for example:

```text
IDLE
BINDING
PREPARING
ACTIVE
STOPPING
FAILED
```

with a separate terminal/failure reason and generation/session id.

This removes the current tendency for UI/probe booleans to become accidental state.

### S4 — structured latency/resource telemetry

Add structured telemetry before Realtime AI, but do not expand the privileged Binder surface prematurely.

For Milestone D device validation, Local Agent/ADB should measure process RSS, FD count and thread count externally so instrumentation does not perturb the helper.

For production/runtime telemetry, define an app-side structured snapshot with monotonic timestamps and counters such as:

```text
session_generation
started_at_ns
last_heartbeat_at_ns
rx_bytes
rx_frames_or_samples
tx_bytes
tx_frames_or_samples
last_abort_latency_ms
helper_disconnect_count
endpoint_close_reason
```

Future Realtime transport latency can extend the app-level telemetry model without requiring log-string parsing.

### S5 — lifecycle regression coverage

Current host-side tests do not protect most of the helper lifecycle.

Existing useful tests:

- `HeartbeatWatchdogTest`;
- `Pcm16PipeFramerTest`;
- app pure utility tests (`PcmMetrics`, `ProbeArguments`, `ToneGenerator`).

Missing automated coverage includes:

- controller prepared -> abort/idempotent abort;
- stale watchdog generation against a newer session;
- child termination -> sibling abort;
- endpoint transfer exactly once;
- start rollback after one child starts/fails;
- repeated lifecycle cycles;
- UserService state transitions/fault injection;
- app coordinator state once it exists.

The current concrete controller classes are tightly bound to final Android media objects/static factories, so unit-testing them by mocking would require invasive seams. Prefer either a small pure lifecycle policy extraction when production coordinator work starts, or targeted instrumentation/fake-child seams only when they buy specific regression coverage.

Do not redesign the frozen media classes just to increase mockability.

### S6 — synchronize stale documentation

`README.md`, `docs/ARCHITECTURE.md`, `docs/ROADMAP.md`, `docs/DEVELOPMENT_WORKFLOW.md`, `docs/S22_PHASE2_LOCAL_BRIDGE_2026-09-16.md` and `privileged-helper/README.md` contain stale statements that Shizuku/Milestone C is blocked or unproven.

They must be synchronized to the actual evidence before Milestone D freeze.

## SAFE CLEANUP

### C1 — share narrow probe utilities

The four Shizuku probes duplicate:

- identical `UserServiceArgs` construction;
- connection timeout plumbing;
- exception-chain sanitization;
- PFD close helpers;
- RX drain/TX silence workers in three classes.

A small `shizuku` diagnostic support layer is justified because duplication is now causing correctness problems (M1/M4). Keep it diagnostic-only and do not create generic abstractions around Samsung media classes.

### C2 — investigate/fix the annotation compile warning

The app compile classpath contains Shizuku 13.1.5 but no AndroidX annotation artifact. Shizuku classes carry AndroidX annotation metadata, leading javac to warn that `RestrictTo.Scope.LIBRARY_GROUP_PREFIX` is unavailable.

This appears to be compile-time metadata rather than a runtime defect. A narrow compile-only annotation dependency is a reasonable cleanup if it removes the warning without altering packaged/runtime behavior. Confirm with `dependencies`/APK diff rather than adding dependencies blindly.

### C3 — deprecated speakerphone diagnostic

`AudioManager.isSpeakerphoneOn` is deprecated on the current SDK, but the capability report also records the modern communication-device API on API 31+.

Either retain the legacy diagnostic with a localized deprecation suppression/comment or remove it only if historical reports no longer need that field. This warning is not a Phase 2 blocker.

### C4 — preserve root causes in cleanup paths

Production code generally preserves checked/root causes well. Some diagnostic helpers catch broad `Throwable`, which is acceptable at the top-level probe boundary but should not spread into reusable production control code.

`SamsungDownlinkPipeSession.openWithCapture()` currently catches `Throwable` and wraps it as `IllegalStateException`; a later safe cleanup could distinguish checked pipe-creation failure from fatal `Error` so VM errors are not converted. This is not required before endurance.

## DEFER / YAGNI

### D1 — low-level Samsung media refactor

Do not merge RX/TX classes, change attribution, route usages, PCM boundary, PFD ownership or `prepare()` ordering.

### D2 — blocking AudioTrack lock redesign

Measured explicit abort was 89 ms. Continue measuring under endurance/call-end rather than replacing the proven track synchronization now.

### D3 — helper-side controller Binder token solely for app death

The current heartbeat provides a physically measured ~2-second fail-safe. Complete the required app-kill test first. Add an app/controller Binder token only if product requirements demand faster helper-side detection than the watchdog provides.

### D4 — remove `audio-bridge` / `realtime-client` scaffolding

Reference counts are low because Phase 3 has deliberately not started. Their current lack of runtime use is not proof of dead code.

### D5 — remove failed generic TX research classes wholesale

Only remove classes after repository-wide source references, build outputs and evidence/history needs are checked individually. Preserve the documented failed generic-path evidence even if executable probes are eventually removed.

## Security review

Positive findings:

- no arbitrary shell-command method exists in AIDL;
- Binder control surface is small and media is PFD-based;
- no per-frame Binder transport;
- no OpenAI networking in the privileged helper;
- no long-lived AI credential exists in this Phase 2 code;
- no normal path persists raw PCM;
- normal logs contain metrics/state/errors, not raw audio/transcripts;
- helper failure/timeout cleanup is local and does not depend on network connectivity.

Blocking security finding is M2: privileged diagnostic automation must not remain reachable via untrusted extras on the exported launcher activity.

## Java/Kotlin/API design

Java/Kotlin interop is straightforward and not currently a source of defects. Most privileged/media code is Java, UI is Kotlin, and AIDL produces the expected Java Binder surface.

Ownership is primarily documented by method names (`take...End`) and synchronization rather than by a richer type system. That is acceptable for the current small surface. The future production coordinator should use immutable result/state snapshots rather than returning more loosely related booleans.

The AIDL surface is appropriately control-only. `getProcessUid()` / `getProcessPid()` and state probes are diagnostic-oriented and can remain during Phase 2; they should be reviewed before a production surface is frozen.

## Answer to the specific architecture questions

### Should the app have `Binder.DeathRecipient`?

**Yes in the production coordinator, but not as a replacement for the heartbeat.** It improves immediate app state after helper death. It does not solve helper cleanup after app death.

### Do we need a separate production `CallMediaSessionCoordinator`?

**Yes before Phase 3.** `MainActivity` and probe classes should not become the long-lived call-session owner.

### Is a shared probe utility worth it?

**Yes, narrowly.** M1 and M4 show that duplicated connection/thread/PFD handling is now a correctness risk. Share only diagnostic infrastructure, not Samsung media abstractions.

### Is `Looper.prepare()` on a Binder pool thread sufficiently safe?

**No.** It only makes Handler construction possible; it does not run that Looper. AOSP shows `ActivityThread` binds its internal Handler to the current Looper, while Shizuku already created the process ActivityThread correctly before our service exists. The second Binder-thread `systemMain()` should be removed.

### Should the state model be more explicit?

**App/production coordinator: yes. Helper/media core: not yet.** The current helper resource state is small, coherent and proven. Refactor only when a concrete testability/correctness gain exists.

### How should structured latency/resource telemetry be added?

**Externally for Milestone D resource proof, structurally in the app for production.** Use ADB `/proc` measurements for RSS/FD/thread counts during endurance, and add app-level monotonic/counter snapshots for session/realtime latency. Avoid adding privileged telemetry RPCs until a production need is clear.

## Required implementation order after this audit

1. Fix M1 main-thread probe execution with a small tested probe-worker abstraction.
2. Fix M4 local probe thread/PFD exceptional cleanup and share only the necessary diagnostic utility.
3. Fix M2 by moving ADB automation triggers off the exported launcher surface.
4. Fix M3 by reusing Shizuku's existing `ActivityThread`; remove Binder-thread `Looper.prepare()` / second `systemMain()`.
5. Run full host build/tests and warning audit after each coherent batch.
6. Run off-call Shizuku prepare/abort after the context fix.
7. Run a narrow physically silent Shizuku live-parity regression because M3 touches the attribution/context path.
8. Synchronize stale docs.
9. Validate the 30-second endurance probe.
10. Complete app-kill, call-end, RX/TX endpoint-close and 10–20 repeated-cycle gates with resource measurement.
11. Run the 10-minute bidirectional endurance test with RSS/FD/thread telemetry.
12. Final regression/audit and freeze Milestone D.
13. Only then design/connect Realtime AI.

## Frozen invariants that remain unchanged

The audit does not recommend changing:

- direct-shell RX construction/prepare ordering;
- RX system attribution;
- TX `com.android.shell` attribution;
- `USAGE_CALL_ASSISTANT` / `AUDIO_STREAM_CALL_ASSISTANT`;
- internal mono PCM16LE;
- stereo duplication only at Samsung TX boundary;
- PFD transfer/AutoClose ownership;
- no per-frame Binder transport;
- one shared RX+TX fail-safe generation;
- local `abortNow()` takeover;
- silent live-call guard.
