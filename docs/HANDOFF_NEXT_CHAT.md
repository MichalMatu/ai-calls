# Handoff — Phase 2 Milestone D continuation

Date: 2026-09-17

Repository: `MichalMatu/android-ai-call-bridge`

Work branch: `work/phase1-live-call-probes`

Local Agent binding:

```text
agent_binding: c25f88c0-4682-414c-8062-c47fa4034cb0
repository_id: android-ai-call-bridge
repository: MichalMatu/android-ai-call-bridge
control_branch: agent-control
```

## Start rule

Do not restart discovery from zero and do not start Realtime AI yet.

Read:

1. `AGENTS.md`
2. this handoff
3. `docs/ROADMAP.md`
4. `docs/ARCHITECTURE.md`
5. `docs/PHASE2_DEEP_AUDIT_2026-09-16.md`
6. `docs/SECURITY_PRIVACY.md`

The active goal is **Milestone D robustness**, not M3 and not Phase 3.

## Frozen proven checkpoints — do not move

### Phase 2B local RX+TX baseline

```text
branch: milestone/phase2b-proven-s22-20260916
commit: c10f8dde29f245f8f98fb008a3572c21fe73fe35
```

### Phase 2C Shizuku live-parity baseline

```text
branch: milestone/phase2c-shizuku-live-proven-20260916
commit: 9c136fc05c5b33f383d72b0b7080ad5b9a754bb4
```

These are rollback/reference points. Do not rewrite them.

## Target device

```text
Samsung Galaxy S22+ SM-S906B
serial: RFCT70L7E8J
Android 16 / API 36 / One UI 8
Orange PL SIM1
Google Phone default dialer
Shizuku adb mode / shell UID 2000
```

## Already physically proven

- digital cellular downlink RX;
- digital cellular uplink TX;
- simultaneous production RX+TX under one controller lifetime;
- normal app -> Shizuku UserService -> privileged controller live parity;
- PFD media transfer without per-frame Binder calls;
- shared RX+TX heartbeat/watchdog lifetime;
- heartbeat timeout around 2 s -> inactive;
- explicit `abortNow()` / TAKE OVER -> inactive with bounded latency;
- UserService/helper process death during active media -> helper disappears, client gets EPIPE, normal app and Shizuku server survive;
- deterministic PFD/worker cleanup in the diagnostic client owner.

## Deep-audit status

All four MUST findings are complete:

```text
M1 probe work off app main Looper          FIXED / TESTED
M2 privileged diagnostics protected       FIXED / HOST+DEVICE VERIFIED
M3 duplicate ActivityThread/Looper         FIXED / HOST+DEVICE VERIFIED
M4 diagnostic PFD/worker ownership         FIXED / TESTED
```

M3 behavior commit:

```text
60cbe81af2e02ba2e4100691c8afb76332735548
```

M3 changed `PrivilegedCallContexts` to reuse Shizuku's existing `ActivityThread.currentActivityThread()` and removed Binder-thread `Looper.prepare()` / second `ActivityThread.systemMain()` construction.

After M3 the following passed:
- full host tests/build;
- off-call Shizuku parity;
- silent live Shizuku parity;
- 30-second bidirectional endurance.

## Proven invariants — do not casually change

### RX

The direct-shell S22 path has a real ordering requirement:

```text
construct/prepare VOICE_DOWNLINK
BEFORE explicit Context/AudioManager initialization
```

Do not incorrectly generalize this into a rewrite of the already-proven Shizuku sequence.

### TX

```text
internal mono PCM16LE
 -> duplicate to stereo only at Samsung TX boundary
 -> USAGE_CALL_ASSISTANT / AUDIO_STREAM_CALL_ASSISTANT
 -> AUDIO_DEVICE_OUT_TELEPHONY_TX
```

Required TX attribution: `com.android.shell`.

Generic media/voice-communication TX experiments failed on this S22 and are not the production path.

### Lifetime/ownership

Preserve:
- one shared RX+TX fail-safe lifetime;
- endpoint loss -> whole-generation cleanup;
- PFD AutoClose ownership semantics;
- no per-frame Binder transport;
- local TAKE OVER independent of future model/network shutdown.

## Live-test silent-phone rule

Every live cellular test must keep the local phone inaudible.

Before dialing and again after ACTIVE assert:

```text
STREAM_VOICE_CALL Muted:true
streamVolume:0
Devices: earpiece(1)
```

Keep speakerphone off.

Prefer direct USB-C <-> USB-C between the S22+ and MacBook. A prior hub/dock caused misleading ADB transport resets.

## Current Milestone D state

Already GREEN:
- 30-second bidirectional endurance;
- explicit abort/takeover path;
- helper/UserService process-death behavior;
- current host regression after app-death tooling.

Latest host-only validation after adding the new harness:

```text
focused app-death harness tests: 11/11 PASS
all Python tests: 30/30 PASS
full Gradle host build/test: GREEN
git diff/check/clean tree: GREEN
```

## Current unresolved gate — normal app death while media is active

The first attempt is **inconclusive**, not a confirmed product bug.

Observed before termination:

```text
call_assistant_active_before_app_death=1
```

Then the host ran:

```text
adb shell am force-stop pl.michalmatu.aicallbridge
```

During the timing-critical observation ADB printed:

```text
- waiting for device -
```

After transport recovered:

```text
app_process_gone=1
userservice_process_gone=1
call_assistant_stop_seen=0
app_death_to_media_cleanup_ms=105571
```

Do **not** interpret `105571 ms` as actual media cleanup latency. The host-side loop was blocked by ADB transport loss, so the timing is invalid. The missing stop log may also be an observation gap.

## New resilient app-death harness

Use:

```text
scripts/s22_app_death_gate.py
scripts/test_s22_app_death_gate.py
```

Purpose: keep timing-critical observation on the phone so a host ADB interruption cannot corrupt the measurement.

The phone-side observer:
- uses `/proc/uptime` for monotonic timing;
- can terminate by `kill-pid` or `force-stop`;
- watches `/proc/<app_pid>` and `/proc/<helper_pid>` disappearance;
- watches CALL_ASSISTANT `state:stopped` evidence in device logcat;
- records call state after cleanup;
- records Shizuku-server survival;
- records boot-id continuity;
- atomically publishes the result file for later host collection.

Important target fact:

```text
toybox nohup --help -> exit 125
```

So the harness intentionally launches a detached background shell process without `nohup`.

The harness is host-tested but the actual destructive app-death gate is still **NOT TESTED on the currently disconnected phone**.

## Remaining Milestone D gates

After reliable app-death evidence:

1. end the cellular call while bridge media is active and prove complete cleanup;
2. intentionally close one transferred RX/TX PFD and prove sibling abort / whole-generation stop;
3. run 10–20 start/abort cycles and compare FD/thread/process/resource counts;
4. run final 10-minute bidirectional endurance with at least:
   - RX bytes;
   - TX bytes;
   - heartbeat count;
   - active state;
   - app/helper RSS start/end/peak;
   - FD count;
   - thread count;
   - AudioTrack/AudioRecord active state;
   - final abort latency;
   - no orphan session/UserService;
5. final regression/security/evidence audit;
6. freeze Milestone D;
7. only then begin Realtime AI integration.

## Production architecture after Milestone D

Before Phase 3, introduce a production `CallMediaSessionCoordinator` instead of allowing `MainActivity` or diagnostic probes to become lifecycle architecture.

Expected app-side state shape:

```text
IDLE
BINDING
PREPARING
ACTIVE
STOPPING
FAILED
```

Include generation/session id, failure reason, helper-death transition and structured telemetry.

Add app-side Binder death handling in the production coordinator, but keep the helper heartbeat watchdog: they cover different failure modes.

Do not expand the privileged Binder surface merely to collect Milestone D metrics that ADB `/proc` telemetry can measure externally.

## Local Agent workflow

Every task JSON must contain exactly:

```json
"agent_binding": "c25f88c0-4682-414c-8062-c47fa4034cb0"
```

and explicit `resources`.

ChatGPT owns planning and code decisions. Local Agent executes deterministic Mac/Gradle/ADB/device commands.

Before writing the same branch, check whether a task is active.

Queued/ACK state is not success; terminal `.agent/results/<task-id>.json` is authoritative.

The experimental event-driven Local Chat Bridge workflow was withdrawn. Do not use `LAB:WAIT_TASK` or rely on `task_result_ready`. For healthy long tasks, avoid rapid 30-second polling; inspect results at a reasonable cadence or when the user asks.

Never launch local Codex from a Local Agent task.

## Immediate next action when the phone returns

1. confirm branch HEAD and clean worktree;
2. confirm direct USB target `RFCT70L7E8J`, call state idle and Shizuku shell server alive;
3. build/install the current debug APK if needed;
4. enforce silent-phone guard;
5. start a live bidirectional session;
6. arm `scripts/s22_app_death_gate.py` with exact app/helper PIDs;
7. perform one bounded normal-app-death run;
8. collect the phone-side result after ADB recovers if necessary;
9. classify the gate only from that result;
10. do not change production audio code unless the physical evidence proves a real defect.
