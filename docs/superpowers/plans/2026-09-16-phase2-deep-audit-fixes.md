# Phase 2 deep-audit fixes plan — 2026-09-16

## Goal

Resolve the concrete `MUST FIX BEFORE ENDURANCE` findings from `docs/PHASE2_DEEP_AUDIT_2026-09-16.md` without changing the physically proven Samsung media semantics.

Do not touch:

- direct-shell RX prepare/Context ordering;
- RX system attribution;
- TX `com.android.shell` attribution;
- CALL_ASSISTANT attributes/route;
- mono PCM16LE -> Samsung stereo boundary;
- PFD production ownership semantics;
- one shared RX+TX watchdog lifetime;
- no per-frame Binder transport.

## Task 1 — move live probe work off the app main Looper

Files:

```text
app/src/main/java/pl/michalmatu/aicallbridge/shizuku/ShizukuProbeWorker.java
app/src/test/java/pl/michalmatu/aicallbridge/shizuku/ShizukuProbeWorkerTest.java
app/src/main/java/pl/michalmatu/aicallbridge/shizuku/ShizukuUserServiceProbe.java
app/src/main/java/pl/michalmatu/aicallbridge/shizuku/ShizukuWatchdogProbe.java
app/src/main/java/pl/michalmatu/aicallbridge/shizuku/ShizukuAbortLatencyProbe.java
app/src/main/java/pl/michalmatu/aicallbridge/shizuku/ShizukuEnduranceProbe.java
```

TDD:

1. add host test proving a probe task runs on a dedicated daemon worker rather than the caller thread;
2. run test and verify RED before implementation;
3. add the minimal worker utility;
4. move synchronous probe bodies out of `ServiceConnection.onServiceConnected()`;
5. post final `finish()` back through the existing main `Handler`;
6. run app unit tests/build.

## Task 2 — deterministic client probe-media cleanup

Files:

```text
app/src/main/java/pl/michalmatu/aicallbridge/shizuku/ShizukuBidirectionalProbeMedia.java
ShizukuWatchdogProbe.java
ShizukuAbortLatencyProbe.java
ShizukuEnduranceProbe.java
```

Approach:

- one diagnostic-only owner for RX drain thread, TX digital-silence thread, their PFDs, stop flag, counters and terminal reason;
- idempotent stop/close/interrupt/join;
- every probe wraps the owner in a `finally` path;
- preserve the current 640-byte/20-ms chunk size and current digital-silence behavior;
- do not share production Samsung classes through this utility.

Verification:

```text
:app:testDebugUnitTest
:app:assembleDebug
git diff --check
```

## Task 3 — remove privileged automation triggers from exported launcher

Files:

```text
app/src/main/AndroidManifest.xml
app/src/main/kotlin/pl/michalmatu/aicallbridge/MainActivity.kt
app/src/main/java/pl/michalmatu/aicallbridge/DiagnosticProbeActivity.java (or Kotlin equivalent)
```

Approach:

- launcher `MainActivity` remains user-facing and exported;
- remove privileged Shizuku auto-run extras from `MainActivity.onCreate()`;
- add a separate automation-only component protected by `android.permission.DUMP` so ADB shell can invoke it but ordinary app UIDs cannot;
- keep manual in-app probe buttons user-driven;
- keep capability-probe automation separate because it does not cross the privileged UserService boundary.

Device verification, off-call only:

1. confirm `com.android.shell` has `android.permission.DUMP`;
2. install debug APK;
3. invoke diagnostic component from ADB shell and confirm it reaches the off-call guard/probe path;
4. attempt the same explicit component launch under the app UID with `run-as` and confirm Android rejects it before probe execution.

No cellular call is needed for this security gate.

## Task 4 — remove Binder-thread `Looper.prepare()` / second `ActivityThread.systemMain()`

File:

```text
app/src/main/java/pl/michalmatu/aicallbridge/shizuku/PrivilegedCallContexts.java
```

Approach:

- use Shizuku's already-created process `ActivityThread` through hidden `ActivityThread.currentActivityThread()`;
- fail explicitly if it is unavailable;
- reuse its system context and create the same shell attribution context as before;
- do not create a second ActivityThread;
- do not prepare a Looper on Binder pool threads.

Reason:

Shizuku's UserService bootstrap already calls `ActivityThread.systemMain()` on the process main Looper before constructing the service. Repeating it on Binder threads creates a Handler bound to a Looper that never runs and duplicates framework process state/callbacks.

Verification:

1. full host build/tests;
2. off-call Shizuku `prepare -> abort` parity on S22;
3. narrow physically silent live Shizuku parity regression before endurance because this touches the proven context/attribution path.

Live-call safety guard remains mandatory:

```text
STREAM_VOICE_CALL Muted:true
streamVolume:0
Devices: earpiece(1)
```

Mute before call and again after ACTIVE. Never use phone speaker/audible local audio.

## Task 5 — synchronize docs and warnings

After behavior fixes are green:

- update `README.md`, `ARCHITECTURE.md`, `ROADMAP.md`, `DEVELOPMENT_WORKFLOW.md`, `S22_PHASE2_LOCAL_BRIDGE_2026-09-16.md`, `SECURITY_PRIVACY.md` as needed, and `privileged-helper/README.md`;
- record exact result files for the new regressions;
- investigate the AndroidX annotation warning with dependency evidence;
- keep deprecated speakerphone diagnostic only if intentionally retained.

## Exit gate before Milestone D resumes

- all MUST findings resolved or explicitly blocked by evidence;
- fresh full Gradle baseline green;
- off-call Shizuku parity green after context fix;
- narrow silent live Shizuku regression green after context fix;
- diagnostic automation surface protected;
- no new unowned PFD/worker thread paths;
- documentation truthful.
