# Handoff — continue Phase 2 after deep audit

Date: 2026-09-16

Repository: `MichalMatu/android-ai-call-bridge`

Work branch: `work/phase1-live-call-probes`

Local Agent hard binding for the current workflow:

```text
agent_binding: c25f88c0-4682-414c-8062-c47fa4034cb0
repository_id: android-ai-call-bridge
repository: MichalMatu/android-ai-call-bridge
control_branch: agent-control
```

This handoff supersedes the older status in this file that said the deep audit still had to be started. The deep audit has now been completed, the first three MUST fixes have been implemented, and the next engineering task is M3 followed by narrow physical regression and Milestone D robustness gates.

## Continuation update — 2026-09-17

Authoritative newer state:
- M3 is complete at `60cbe81af2e02ba2e4100691c8afb76332735548` and passed host, off-call, silent live parity and 30-second endurance validation.
- Milestone D is active. The app-death gate is still an evidence problem, not a confirmed product defect.
- Harness diagnosis: this S22+ has no `toybox nohup` (`exit 125`); a background `sh ... &` process survives ADB-shell return and can be used for the phone-side observer.
- The event-driven Local Chat Bridge experiment was withdrawn. Do not use `LAB:WAIT_TASK` or rely on `task_result_ready`; terminal `.agent/results/<task-id>.json` remains authoritative.
- Hard binding and repository isolation remain unchanged.

## New-chat start rule

Do not restart discovery from zero and do not begin Realtime AI work.

Read, in this order:

1. `docs/HANDOFF_NEXT_CHAT.md`
2. `AGENTS.md`
3. `docs/PHASE2_DEEP_AUDIT_2026-09-16.md`
4. `docs/superpowers/plans/2026-09-16-phase2-deep-audit-fixes.md`
5. `docs/ARCHITECTURE.md`
6. `docs/ROADMAP.md`
7. `docs/SECURITY_PRIVACY.md`
8. `docs/DEVELOPMENT_WORKFLOW.md`
9. `docs/S22_PHASE2_LOCAL_BRIDGE_2026-09-16.md`
10. `docs/PHASE2B_FREEZE_2026-09-16.md`

The exact next goal is:

```text
Finish M3 safely -> full host regression -> off-call Shizuku parity -> narrow silent live Shizuku regression -> Milestone D physical robustness/endurance gates.
```

Do not redesign the proven Samsung audio path before those gates.

## Current branch / commit state

Latest behavior-changing commit:

```text
de99da7a5c3774655a1a862488d984701cfa8878  Protect privileged diagnostic probe entrypoint
```

Deep-audit documentation commits:

```text
dc3ec47c34439731da72ac9ec071fca5f2e95ef6  Document Phase 2 deep audit findings
9efe90c0049e51648e19b06ce56eac7334a7c808  Plan Phase 2 deep-audit fixes
```

Audit/fix commits already completed:

```text
2ff26e95b8eb989753fbaecbbdcca93f11326e72  Test Shizuku probe worker threading       [M1 RED]
812bac193c04c73ab0c51b7448aa9d90b517fce5  Run Shizuku probes off main thread        [M1 GREEN]
a613264e2878b12d832681afbd7ae6c8919f089c  Test diagnostic media cleanup ownership   [M4 RED]
de1bffec6ab712992b5a8658a1efe8da1ab947fb  Own Shizuku probe media cleanup            [M4 GREEN]
de99da7a5c3774655a1a862488d984701cfa8878  Protect privileged diagnostic probe entrypoint [M2 GREEN]
```

Two later repository-API housekeeping commits temporarily added and then removed `.tmp-placeholder`:

```text
ea920b7622adac6d6851c342f1cfb3b577a546ae
f85e2290d484aa8a11871296ccd82ad6e7a2aa74
```

They have no net tree effect and must not be interpreted as code changes. The behavior baseline remains `de99da7...`; the newest handoff commit is documentation-only.

Original pre-audit baseline was:

```text
91d6fc115956ec1e221e406fc9fca64527e2c916
```

Last code-changing commit before that original handoff:

```text
c42377681f6b5025ada79c6e01fe7f675453b3ad
```

## Frozen proven checkpoints — do not move

### Phase 2B physical RX+TX baseline

```text
branch: milestone/phase2b-proven-s22-20260916
commit: c10f8dde29f245f8f98fb008a3572c21fe73fe35
```

### Phase 2C Shizuku live-parity baseline

```text
branch: milestone/phase2c-shizuku-live-proven-20260916
commit: 9c136fc05c5b33f383d72b0b7080ad5b9a754bb4
```

The comparison from frozen Phase 2C to the audit starting point showed that the proven low-level media/controller/UserService implementation had not been changed by the later diagnostic-probe additions. Preserve these snapshots as rollback/reference points.

## What is already physically proven on the S22+

Target device:

```text
Samsung Galaxy S22+ SM-S906B
serial RFCT70L7E8J
Android 16 / API 36 / One UI 8
Orange PL SIM1
Google Phone default dialer
Shizuku adb mode / shell UID 2000
```

Already proven:

- digital cellular downlink RX;
- digital cellular uplink TX;
- simultaneous production RX+TX under one controller lifetime;
- normal app -> Shizuku UserService -> privileged controller live parity;
- shared watchdog lifetime for both directions;
- heartbeat timeout `2000 ms` -> inactive in approximately `2003 ms`;
- explicit `abortNow()` / TAKE OVER -> inactive in approximately `89 ms`;
- UserService process death -> helper disappears, client gets EPIPE, main app and Shizuku server survive;
- no per-frame Binder media transport;
- PFD streaming and AutoClose ownership model works on the proven path.

Important evidence files retained in `agent-control` include:

```text
.agent/results/phase2c-postmortem-live-logcat-20260916-1245.json
.agent/results/phase2c-freeze-shizuku-live-proven-20260916-1249.json
.agent/results/phase2d-live-watchdog-silent-20260916-1333.json
.agent/results/phase2d-live-abort-silent-20260916-1343.json
.agent/results/phase2d-live-userservice-death-silent-20260916-1352.json
```

## Proven invariants that must not be casually changed

### RX

The direct-shell S22 RX path proved a real ordering dependency:

```text
construct/prepare VOICE_DOWNLINK
BEFORE explicit Context/AudioManager initialization
```

Do not generalize that rule incorrectly to the Shizuku UserService path. The frozen Phase 2C Shizuku path intentionally creates attributed privileged contexts before `controller.prepare(sampleRate, candidate.shell())` because that exact route was physically proven.

### TX

Required proven boundary:

```text
internal mono PCM16LE
 -> duplicate to stereo only at Samsung TX boundary
 -> USAGE_CALL_ASSISTANT / AUDIO_STREAM_CALL_ASSISTANT
 -> AUDIO_DEVICE_OUT_TELEPHONY_TX
```

Required TX attribution: `com.android.shell`.

Do not replace it with generic media/voice-communication usage; those generic experiments failed on the tested S22.

### Lifetime / ownership

Do not regress:

- one shared RX+TX fail-safe lifetime;
- endpoint loss causing whole-generation cleanup;
- PFD ownership/AutoClose semantics;
- no per-frame Binder transport;
- local TAKE OVER independent from future network/model shutdown;
- silent-phone guard for every live test.

## Deep audit — completed

The durable audit is:

```text
docs/PHASE2_DEEP_AUDIT_2026-09-16.md
```

Implementation plan:

```text
docs/superpowers/plans/2026-09-16-phase2-deep-audit-fixes.md
```

Audit classification:

```text
MUST FIX BEFORE ENDURANCE
SHOULD FIX BEFORE PHASE 3
SAFE CLEANUP
DEFER / YAGNI
```

The four MUST findings were:

1. M1 — synchronous Shizuku probe bodies were running inside main-thread ServiceConnection callbacks.
2. M2 — exported `MainActivity` accepted privileged `run_shizuku_*` automation extras.
3. M3 — `PrivilegedCallContexts.create()` creates another `ActivityThread.systemMain()` and prepares a Looper on a Binder pool thread even though Shizuku already bootstrapped an ActivityThread.
4. M4 — local probe media worker/PFD cleanup depended too much on normal exit/remote EPIPE instead of one deterministic local owner/finally path.

M1, M2 and M4 are now fixed. M3 remains.

## M1 — completed and GREEN

Commit:

```text
812bac193c04c73ab0c51b7448aa9d90b517fce5
```

Added `ShizukuProbeWorker` and moved synchronous work out of `onServiceConnected()` for:

- `ShizukuUserServiceProbe`;
- `ShizukuWatchdogProbe`;
- `ShizukuAbortLatencyProbe`;
- `ShizukuEnduranceProbe`.

The worker is a named daemon thread. Results are still posted back through the existing main `Handler`.

Focused test and full build/test passed.

Local Agent evidence:

```text
.agent/results/phase2-m1-probe-worker-red-20260916-1749.json
.agent/results/phase2-m1-probe-worker-green-20260916-1755.json
```

## M4 — completed and GREEN

Commit:

```text
de1bffec6ab712992b5a8658a1efe8da1ab947fb
```

Added diagnostic-only `ShizukuBidirectionalProbeMedia` owning:

- RX `InputStream`;
- TX `OutputStream`;
- both daemon worker threads;
- counters/terminal states;
- idempotent shutdown.

`close()` performs deterministic local cleanup:

```text
stop -> close streams -> interrupt workers -> join workers
```

Watchdog/abort/endurance probes now use this owner and still preserve their result keys/measurement intent.

Focused RED/GREEN test verifies that closing the owner terminates both blocking workers and closes each stream exactly once; repeated `close()` is idempotent.

Evidence:

```text
.agent/results/phase2-m4-probe-media-red-20260916-1802.json
.agent/results/phase2-m4-probe-media-green-20260916-1808.json
```

Full project tests/build passed after the change.

## M2 — completed and device-verified GREEN

Commit:

```text
de99da7a5c3774655a1a862488d984701cfa8878
```

Security issue:

`MainActivity` is an exported launcher. It previously accepted privileged automation extras such as `run_shizuku_watchdog_probe`, `run_shizuku_abort_probe`, `run_shizuku_endurance_probe`, etc. After the app had Shizuku permission, another ordinary application could attempt to drive those diagnostic paths through the exported launcher.

Fix:

- removed privileged automation dispatch from `MainActivity`;
- retained only the ordinary capability probe extra there;
- kept the manual Shizuku UserService button/permission flow;
- added `DiagnosticProbeActivity` for deterministic shell/ADB diagnostics;
- protected the new diagnostic Activity with `android.permission.DUMP`;
- kept existing diagnostic extra names on the protected component.

Full host build/tests passed.

Most recent physical off-call device validation:

```text
.agent/results/phase2-m2-device-green-v3-20260916-1836.json
```

Result:

```text
call_state=0
shizuku_server user=shell
DiagnosticProbeActivity launch Status: ok
shizuku_probe_start=true
service_uid=2000
prepared_after_prepare=true
prepared_after_abort=false
active_after_abort=false
heartbeat_after_abort=false
privileged_uid=true
offcall_parity_ok=true
phase2_m2_shell_entrypoint_green=true
```

This proves the protected shell/ADB diagnostic entrypoint still works on the physical phone after M2.

Do not treat the earlier failed `pm check-permission` command as an app failure; that Android build simply did not expose that exact `pm` subcommand. The direct ActivityManager launch is the useful positive shell-path evidence.

## M3 — NEXT engineering change

Current `PrivilegedCallContexts.create()` still does approximately:

```java
if (Looper.myLooper() == null) {
    Looper.prepare();
}
Class<?> activityThreadClass = Class.forName("android.app.ActivityThread");
Method systemMain = activityThreadClass.getDeclaredMethod("systemMain");
Object thread = systemMain.invoke(null);
Context system = (Context) getSystemContext.invoke(thread);
...
```

The audit and AOSP/Shizuku source review established:

- Shizuku UserService bootstrap already creates/owns the process ActivityThread on the prepared process main Looper;
- calling a second `ActivityThread.systemMain()` from a Binder pool thread is structurally redundant/unsafe;
- `Looper.prepare()` on that reusable Binder thread is also unnecessary and leaves surprising thread state.

### Minimal intended M3 fix

Do not redesign the context attribution mechanism. Make the smallest change:

1. remove the `Looper` import/use from `PrivilegedCallContexts`;
2. reflect `ActivityThread.currentActivityThread()` instead of `systemMain()`;
3. use the existing ActivityThread returned by Shizuku bootstrap;
4. if it is unexpectedly null, fail explicitly with `IllegalStateException("Shizuku ActivityThread is unavailable")`;
5. leave `getSystemContext()`, `getPackageInfoNoCheck(shellInfo)` and shell `ContextImpl.createAppContext(...)` construction otherwise unchanged.

This touches the proven Shizuku attribution/context path, therefore validation must be stronger than compile-only.

### M3 validation sequence

After the minimal code change:

1. focused/static review + full host unit tests/build;
2. install on S22;
3. with `call_state=0`, run protected `DiagnosticProbeActivity` off-call prepare/abort and require `offcall_parity_ok=true`;
4. preferably repeat off-call prepare/abort several times to exercise context creation;
5. then run one narrow physically silent live Shizuku parity regression before any endurance test;
6. if RX/TX attribution or parity changes, stop and compare directly with frozen Phase 2C before proceeding.

Do not start the 10-minute test before M3 has this narrow live proof.

## Host build/test baseline

Fresh baseline after the audit and again after M1/M4/M2 passed:

```text
:privileged-helper:testDebugUnitTest
:app:testDebugUnitTest
:audio-bridge:testDebugUnitTest
:realtime-client:testDebugUnitTest
:app:assembleDebug
```

Observed full baseline: `BUILD SUCCESSFUL`, 109 tasks.

Known warnings still open but not blockers for M3:

- deprecated `AudioManager.isSpeakerphoneOn` in `CapabilityProbe.kt`;
- 7 javac warnings around missing `androidx.annotation.RestrictTo$Scope` / `Scope.LIBRARY_GROUP_PREFIX` metadata;
- `audio-bridge` and `realtime-client` unit-test tasks are currently `NO-SOURCE`.

Do not add AndroidX annotation dependencies blindly. Confirm the dependency source/packaging first; treat this as SAFE CLEANUP after the critical gates unless it becomes a build correctness issue.

Historical handoff text mentioned 19 Python tests, but the current worktree did not contain a root `tests/` directory during this audit. Do not claim those tests are currently runnable until their historical location/commit is explicitly found.

## Milestone D — remaining physical gates

After M3 host + off-call + narrow live regression is GREEN, prioritize physical robustness evidence rather than new features.

Recommended order:

1. validate the existing 30-second bidirectional endurance probe on one silent live call;
2. kill the normal app while media is active and measure app-death -> watchdog/media stop;
3. end the cellular call while bridge is active and prove complete child/sibling cleanup;
4. intentionally close one transferred RX or TX PFD and prove sibling abort / whole-generation stop;
5. run 10–20 start/abort cycles and compare FD/thread/process/resource counts;
6. run final 10-minute bidirectional endurance with telemetry/resource counts;
7. final full regression/audit;
8. freeze Milestone D on a dedicated milestone branch;
9. only then move to Realtime AI integration.

For the 10-minute run record at least:

- RX bytes progressing;
- TX bytes progressing;
- heartbeat count;
- active state throughout;
- helper and app RSS start/end/peak where practical;
- FD count start/end;
- thread count start/end;
- AudioTrack/AudioRecord active during the run;
- final abort latency;
- no orphaned session/UserService after stop.

## Production architecture work — important, but after Milestone D evidence

The deep audit concluded these are worthwhile before Phase 3, but they should not delay the immediate M3 + Milestone D physical gates:

- introduce production `CallMediaSessionCoordinator` instead of letting `MainActivity`/diagnostic probes become lifecycle architecture;
- add an explicit app-side session state model such as `IDLE/BINDING/PREPARING/ACTIVE/STOPPING/FAILED` with generation/failure reason;
- add app-side Binder `DeathRecipient` / explicit helper-death transition in the production coordinator;
- keep the helper heartbeat watchdog even after adding `DeathRecipient` — it covers a different app/control-loss failure mode;
- add structured app telemetry rather than relying on human log parsing;
- use external ADB `/proc` process/FD/thread telemetry for Milestone D where that is simpler than growing the Binder API.

Do not introduce abstractions merely to make the proven Samsung helper code look generic. Samsung RX and TX are asymmetric for real device reasons.

## Silent-phone rule for every live cellular test

Never allow audible local phone call output.

Before dialing and again after the call becomes ACTIVE, assert:

```text
STREAM_VOICE_CALL Muted:true
streamVolume:0
Devices: earpiece(1)
```

Keep speakerphone off.

Use direct USB-C <-> USB-C between S22+ and MacBook. A previous hub/dock caused misleading host-side ADB server resets even when the phone-side test had completed correctly.

Digital capture/injection may run; local audible speaker output may not.

## Local Agent / Local Chat Bridge — current workflow

Keep the hard binding authoritative:
`c25f88c0-4682-414c-8062-c47fa4034cb0` / `android-ai-call-bridge` / `MichalMatu/android-ai-call-bridge`.

- every Local Agent task must use that exact `agent_binding`;
- never infer or switch repository identity;
- check active-task state before editing the same branch;
- ChatGPT owns planning; Local Agent runs deterministic Mac/Gradle/ADB/device commands;
- queued/ACK state is not success; read the exact terminal result JSON;
- the withdrawn event-driven `LAB:WAIT_TASK` / `task_result_ready` mechanism must not be used;
- avoid rapid polling of healthy long-running tasks; recheck manually at a reasonable cadence or when the user asks.

## Suggested exact next-chat execution sequence

1. Read this handoff and the audit/plan docs.
2. Confirm hard binding and work branch.
3. Check Local Agent active-task state before any branch edit.
4. Verify the newest branch HEAD and distinguish documentation-only handoff commits from behavior baseline `de99da7...`.
5. Inspect current `PrivilegedCallContexts.java` and implement only the minimal M3 change described above.
6. Run full host validation through Local Agent and inspect the exact terminal result before continuing.
7. If GREEN, run off-call physical Shizuku parity through the protected `DiagnosticProbeActivity`.
8. If GREEN, perform one narrow physically silent live Shizuku parity regression.
9. Only then validate the 30-second endurance probe and continue Milestone D failure gates.
10. Keep commits small and evidence-linked.
11. Synchronize stale general docs after behavior is stable; do not spend the critical physical-test window on broad prose cleanup first.
12. Freeze Milestone D only after all required robustness evidence is green.
13. Do not begin Realtime AI before that freeze.

## Bottom line

The difficult S22/Samsung media route is already solved and physically proven. This chat completed the deep audit and fixed three of four MUST issues without changing the proven Samsung media primitives.

Current status:

```text
M1 main-thread Shizuku probes       FIXED / TESTED
M4 diagnostic local media ownership FIXED / TESTED
M2 exported privileged diagnostics  FIXED / HOST+DEVICE VERIFIED
M3 duplicate ActivityThread/Looper   FIXED / HOST+DEVICE VERIFIED
Milestone D robustness/endurance     OPEN — AFTER M3 REGRESSION
Realtime AI                         NOT STARTED BY DESIGN
```

The next chat should preserve the proven media path, continue Milestone D from the completed M3 baseline, use the current non-event-driven Local Agent workflow, and avoid speculative refactoring.
