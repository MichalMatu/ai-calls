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

The active goal is **Milestone D robustness**. All host-side diagnostics for the remaining gates are prepared; the next work is physical execution on the S22+ and evidence collection.

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

Prefer direct USB-C <-> USB-C. A prior hub/dock caused misleading ADB transport resets.

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
- deterministic PFD/worker cleanup in the diagnostic client owner;
- 30-second bidirectional endurance.

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

## Proven invariants — do not casually change

### RX

The direct-shell S22 path has a real ordering requirement:

```text
construct VOICE_DOWNLINK / controller.prepare()
BEFORE explicit Context/AudioManager initialization
```

Do not generalize this into a rewrite of the already-proven Shizuku sequence.

### TX

```text
internal mono PCM16LE
 -> duplicate to stereo only at Samsung TX boundary
 -> USAGE_CALL_ASSISTANT / AUDIO_STREAM_CALL_ASSISTANT
 -> AUDIO_DEVICE_OUT_TELEPHONY_TX
```

Required TX attribution: `com.android.shell`.

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

## Milestone D host preparation — COMPLETE

The remaining physical gates now have dedicated host/device tooling and host tests.

### Normal app death

Ready:

```text
scripts/s22_app_death_gate.py
scripts/test_s22_app_death_gate.py
```

The phone-side observer uses `/proc/uptime`, watches app/helper process disappearance and CALL_ASSISTANT stop, preserves call-state/Shizuku/boot evidence and publishes an atomic result file. It is resilient to temporary host ADB loss.

The earlier physical run remains **inconclusive**, not a product failure, because host ADB entered `waiting for device`; the reported ~105 s timing is invalid.

### Selective PFD close

Ready:
- `ShizukuEndpointCloseProbe`;
- deterministic RX endpoint close case;
- deterministic TX endpoint close case;
- controller inactivity required before the 2 s watchdog fallback;
- no production helper/controller changes.

Physical RX and TX cases are still pending.

### Final 10-minute endurance

Ready:
- default endurance remains 30 s;
- protected diagnostic override accepts 5 s through 600000 ms;
- existing media loop/heartbeat semantics reused.

Physical 600000 ms gate is pending.

### Repeated start/abort cycles

Ready:
- protected `ShizukuCycleProbe`;
- default 20 cycles, accepted range 1–20;
- exactly one UserService bind for the whole run;
- each iteration does prepare/start/PFD transfer/media warmup/heartbeat/abort/empty-state check/media close;
- reports cumulative RX/TX, maximum abort RPC latency, first failed cycle and stable service PID.

Physical 20-cycle gate is pending.

### External resource telemetry

Ready:

```text
scripts/s22_resource_telemetry.py
scripts/test_s22_resource_telemetry.py
```

JSONL samples include:
- device monotonic uptime;
- call state;
- CALL_ASSISTANT state;
- app/helper PID;
- VmRSS;
- FD count;
- thread count.

Summary mode computes deterministic start/end/peak/delta and does not fabricate zeroes for missing processes.

### Call-end gate

Ready:
- protected `ShizukuCallEndProbe`;
- default wait 120 s, accepted range 5–180 s;
- one UserService bind;
- establishes active bidirectional media and continuously refreshes heartbeat;
- waits for actual media termination and then requires controller prepared/active/heartbeat false and app-side workers stopped;
- therefore cleanup cannot be credited merely to the 2 s heartbeat watchdog.

Phone-side observer:

```text
scripts/s22_call_end_gate.py
scripts/test_s22_call_end_gate.py
```

It observes rather than triggers hangup. It records:
- initial OFFHOOK state;
- CALL_ASSISTANT started before hangup;
- `OFFHOOK -> IDLE` timing;
- CALL_ASSISTANT stopped timing after call end;
- Shizuku server survival;
- boot-id continuity.

It intentionally contains no `KEYCODE_ENDCALL` / `input keyevent` automation.

No call-end preparation changed `privileged-helper`; Samsung backend semantics are unchanged.

## Latest host validation

After all remaining-gate tooling, including call-end:

```text
focused call-end Java tests: PASS
focused call-end Python tests: 7 PASS
all Python tests: 44 PASS
full Gradle host tests + :app:assembleDebug: GREEN
security-shape checks: GREEN
git diff --check: GREEN
clean worktree: GREEN
```

A final documentation-sync audit is the only remaining host-only action before physical testing.

## Physical execution order

When the S22+ is available, execute in this order:

1. confirm exact branch HEAD and clean worktree;
2. confirm direct USB target `RFCT70L7E8J`, call state IDLE and Shizuku shell server alive;
3. install current debug APK if needed;
4. enforce silent-phone guard;
5. normal-app-death gate with phone-side observer;
6. RX transferred-PFD close gate;
7. TX transferred-PFD close gate;
8. call-end gate with app-side probe plus phone-side observer;
9. 20-cycle gate while collecting external resource telemetry;
10. final 10-minute endurance while collecting external resource telemetry;
11. final regression/security/evidence audit;
12. freeze Milestone D;
13. only then begin Realtime AI integration.

Do not change production Samsung audio code unless physical evidence proves a real defect.

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

Include generation/session id, failure reason, app-side Binder death handling and structured telemetry while retaining the helper heartbeat watchdog.

## Local Agent workflow

Every task JSON must contain exactly:

```json
"agent_binding": "c25f88c0-4682-414c-8062-c47fa4034cb0"
```

ChatGPT owns planning and code decisions. Local Agent executes deterministic Mac/Gradle/ADB/device commands.

Before writing the same branch, check whether a task is active. Terminal `.agent/results/<task-id>.json` is authoritative. Never launch local Codex from a Local Agent task. Do not use withdrawn `LAB:WAIT_TASK`.
