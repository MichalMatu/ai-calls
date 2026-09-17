# Handoff — Milestone D closeout / overnight continuation

Date: 2026-09-18

Repository: `MichalMatu/android-ai-call-bridge`

Work branch: `work/phase1-live-call-probes`

Current product HEAD at handoff:

```text
72cc290a261432588005f0300ace382aa252deb1
fix: stop downlink when cellular call mode ends
```

Local Agent binding:

```text
agent_binding: c25f88c0-4682-414c-8062-c47fa4034cb0
repository_id: android-ai-call-bridge
repository: MichalMatu/android-ai-call-bridge
control_branch: agent-control
```

## Start rule

Do not restart discovery from zero. Read this file first, then `AGENTS.md`, `docs/ROADMAP.md`, `docs/ARCHITECTURE.md` and `docs/PHASE2_DEEP_AUDIT_2026-09-16.md`.

The immediate goal is to **close Milestone D cleanly**, then move directly into Telephone Agent v1 / Realtime AI architecture and implementation. Do not spend the night inventing more test infrastructure unless exact evidence proves it is necessary.

## User product goal

Target experience:

```text
user in chat:
"Zadzwoń do przychodni w Sky Tower i umów mnie do dermatologa,
najlepiej po 16:00 w przyszłym tygodniu."

system:
intent -> public business/number lookup -> goal/constraints -> cellular dial
-> AI talks to receptionist -> negotiates within constraints -> detects result
-> optional Calendar/result persistence -> reports back to user
```

Ask the user only for a genuinely material choice that cannot be safely inferred, for example a large price/availability tradeoff outside supplied constraints.

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

Never rewrite frozen branches.

## Target device

```text
Samsung Galaxy S22+ SM-S906B
serial: RFCT70L7E8J
Android 16 / API 36 / One UI 8
Orange PL SIM1
Google Phone default dialer
Shizuku adb mode / shell UID 2000
```

Prefer direct USB-C. Hubs/docks previously caused misleading ADB resets.

Safe established test number: `510100100`. User already authorized safe live calls to this number; do not re-ask unless circumstances materially change.

## Proven Samsung media invariants — do not casually change

### RX

Direct-shell proof requires:

```text
construct VOICE_DOWNLINK / controller.prepare()
BEFORE explicit Context/AudioManager initialization
```

Shizuku has its own already-proven attributed-context ordering. Do not unify these paths by refactoring unless device evidence requires it.

### TX

```text
mono PCM16LE internally
-> duplicate to stereo only at Samsung TX boundary
-> USAGE_CALL_ASSISTANT / AUDIO_STREAM_CALL_ASSISTANT
-> AUDIO_DEVICE_OUT_TELEPHONY_TX
```

Required TX attribution: `com.android.shell`.

### Lifetime

Preserve:
- one shared RX+TX fail-safe lifetime;
- endpoint loss -> whole-generation cleanup;
- PFD AutoClose ownership;
- no per-frame Binder PCM;
- local TAKE OVER independent of future model/network shutdown;
- helper heartbeat watchdog upper bound.

## Deep-audit findings

All original MUST findings are fixed:

```text
M1 probe work off main callbacks       FIXED / TESTED
M2 diagnostics privilege surface       FIXED / VERIFIED
M3 duplicate ActivityThread/Looper      FIXED / VERIFIED
M4 deterministic media/PFD ownership    FIXED / TESTED
```

M3 behavior commit: `60cbe81af2e02ba2e4100691c8afb76332735548`.

## Milestone D physical evidence — completed

### Normal app death while media active — GREEN

Physical task:

```text
phase2-milestone-d-app-death-physical-v5-20260917-2301
```

Evidence:
- app process disappeared ~180 ms after force-stop;
- helper disappeared ~340 ms;
- media stopped ~530 ms;
- cellular call remained active;
- Shizuku server survived;
- same device boot;
- silent guard preserved;
- `app_death_gate_ok=true`.

### Transferred PFD close RX/TX — GREEN

Physical task:

```text
phase2-milestone-d-endpoint-close-physical-v2-20260917-2330
```

Evidence:
- DOWNLINK close -> controller inactive ~39 ms;
- UPLINK close -> controller inactive ~13 ms;
- final prepared/active/heartbeat false;
- whole-generation cleanup worked before heartbeat timeout;
- `endpoint_close_fail_safe_ok=true`.

### 20 start/abort cycles — logic GREEN

Physical cycle probe completed all 20 cycles:

```text
completed_cycles=20
first_failed_cycle=0
service_pid_stable=true
final_prepared=false
final_active=false
final_heartbeat=false
cycle_probe_ok=true
```

The first external resource telemetry harness was too slow for the short helper lifetime. Later PS-based telemetry was fixed, but do not repeat 20 cycles solely because of the old sampler artifact unless needed to close the final resource-evidence gap.

### Natural cellular call end — real product defect found and fixed, then GREEN

Initial physical natural call-end observation proved a real issue: the network ended the call but the media session stayed active because heartbeat kept the helper alive and the low-level PCM paths did not terminate by themselves.

Fix at current HEAD `72cc290a...`:
- new helper-side `CallModeWatchdog`;
- polls the same public `AudioManager.MODE_IN_CALL` condition already required at RX start;
- when call mode leaves `MODE_IN_CALL`, downlink terminates locally;
- existing controller sees a terminated direction and aborts the sibling TX;
- no new telephony permission;
- no change to proven Samsung PCM primitives or attribution.

TDD:
- RED commit `855fd6be7727b9962e88ffe459c44262cbd61536`;
- implementation commits `1161471b118d302effa86feb22ff912a75e05e95` and `72cc290a261432588005f0300ace382aa252deb1`;
- focused watchdog test GREEN;
- full helper tests + app tests + APK build GREEN.

Physical validation:

```text
phase2-natural-call-end-watchdog-physical-20260918-0108
milestone_d_natural_call_end_watchdog_green=true
```

The call ended naturally; app-side probe required media ended, workers stopped, prepared/active/heartbeat false and `call_end_fail_safe_ok=true`.

### 10-minute media soak — 600 seconds media-plane GREEN, resource summary not preserved

The safe Orange IVR ends idle calls at roughly 1.5 minutes, so one uninterrupted 600 s call is not practical with this endpoint. The test was therefore executed honestly as **10 independent 60-second live bidirectional sessions**.

Task:

```text
phase2-milestone-d-10min-segmented-live-soak-v2-20260918-0125
```

All ten media segments completed GREEN before the task wrapper failed:

```text
segment 1  DL 1923840  UL 1866240
segment 2  DL 1925120  UL 1859840
segment 3  DL 1925120  UL 1861760
segment 4  DL 1923840  UL 1861760
segment 5  DL 1923840  UL 1863040
segment 6  DL 1923840  UL 1859840
segment 7  DL 1925120  UL 1861120
segment 8  DL 1925120  UL 1861760
segment 9  DL 1925120  UL 1861120
segment 10 DL 1923840  UL 1862400
```

For every segment the harness required:
- `duration_ms=60000`;
- all heartbeats OK;
- session active throughout;
- duration OK;
- active + heartbeat immediately before explicit abort;
- prepared/active/heartbeat false after abort;
- both app-side media threads stopped;
- positive RX and TX byte counts;
- `endurance_ok=true`.

The task wrapper finally failed with `command_background_process_leak` because the external telemetry watcher was still a child process. Local Agent cleaned it up before the result-summary step. A later no-call postmortem could not find the `/tmp` telemetry file, so **do not claim RSS/FD/thread soak metrics as proven**.

Important interpretation:
- the 600 s media-plane soak is GREEN;
- the remaining gap is only preserved external resource-trend evidence, not media stability.

## Current remaining Milestone D work

Keep this short and decisive.

1. **Close resource-trend evidence** with the smallest reliable method. Prefer a foreground sampler or another method that cannot be killed as a leaked child. Do not repeat ten live calls unless genuinely required; use existing cycle/endurance capabilities intelligently.
2. Run final full regression after `CallModeWatchdog`:
   - all Python tests;
   - full Gradle unit tests;
   - `:app:assembleDebug`;
   - `git diff --check` / clean tree.
3. Final security/architecture audit:
   - `.agent` absent from product branch;
   - `DiagnosticProbeActivity` remains protected by DUMP permission;
   - `MainActivity` has no privileged automation extras;
   - no long-lived OpenAI API key embedded;
   - recording remains off by default;
   - no per-frame Binder transport;
   - TAKE OVER / abort fail-safe remains local;
   - helper/app death and natural call end fail closed.
4. Update `ROADMAP`, `ARCHITECTURE`, security docs and this handoff with final evidence.
5. Freeze Milestone D, suggested branch:

```text
milestone/phase2d-failsafe-proven-s22-20260918
```

Do not modify the frozen branch after creation.

6. Immediately move to Telephone Agent v1; do not start another robustness phase.

## Telephone Agent v1 — next architecture

Do not turn `MainActivity` or diagnostic probes into the production lifecycle owner.

Introduce production app-side orchestration such as `CallMediaSessionCoordinator` with explicit states:

```text
IDLE
BINDING
PREPARING
ACTIVE
STOPPING
FAILED
```

Include generation/session id, failure reason, Binder death handling and structured telemetry while keeping the helper heartbeat watchdog.

Then implement the user-level call task model:

```text
target description
requested action/service
constraints (date/time/price/insurance/etc.)
authorized user facts
```

High-level call flow:

```text
RESEARCHING
READY_TO_DIAL
DIALING
ACTIVE_NEGOTIATION
NEEDS_USER_DECISION?  (only material deviation)
COMPLETED / FAILED
```

Outcome should be structured: success/failure, appointment time, location/provider, cost if relevant, booking reference and follow-up.

Realtime AI is the conversational engine inside this orchestrator, not the whole product architecture.

Before implementing OpenAI Realtime integration, verify current OpenAI API documentation on the web. Never embed a long-lived OpenAI key in the APK; use an ephemeral/server-mediated credential design.

## Overnight autonomy policy

The user is going to sleep and explicitly wants autonomous progress for roughly 8 hours.

Within this repository and branch:
- take initiative;
- do not ask for confirmation for ordinary engineering choices;
- prefer TDD and small focused commits;
- use Local Agent for Mac/Gradle/ADB/device work only;
- use direct GitHub edits when diff/CI evidence is enough;
- do not launch local Codex;
- do not touch another repository;
- do not rewrite frozen checkpoints;
- safe calls to the established test number are already authorized if truly needed;
- avoid gratuitous physical calls and avoid endless harness work;
- if a physical/user-action gate is genuinely impossible while the user sleeps, document it and continue with all host-only work instead of blocking.

After Milestone D is frozen, continue autonomously into Telephone Agent v1 with preimplementation audit, architecture/tests, then implementation in small validated slices.

## Local Agent workflow

Every task JSON must contain exactly:

```json
"agent_binding": "c25f88c0-4682-414c-8062-c47fa4034cb0"
```

Before writing the same branch, check active task evidence. Terminal `.agent/results/<task-id>.json` is authoritative. Healthy multi-minute tasks should not be polled more often than needed. Never launch local Codex from a Local Agent task.
