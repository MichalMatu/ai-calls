# Development workflow — Superpowers adaptation

This project uses the methodology from `obra/superpowers` for agentic development.

Reference reviewed on 2026-09-14:

- upstream repository: `obra/superpowers`;
- upstream `main` commit reviewed: `b36e0829c6d0140e93cfef2ca599b1b07d4a7797`;
- upstream release line at that commit: v6.3.0;
- license: MIT.

Superpowers is a coding-agent workflow/plugin, not an Android runtime dependency. Nothing from it is shipped in the APK.

## How we use it here

ChatGPT owns planning, code/evidence review and decisions. Local Agent is a deterministic execution worker for Mac, Gradle, ADB and physical-device commands.

The repository mirrors the project-specific workflow contract through:
- `AGENTS.md`;
- durable design/planning documents;
- TDD requirements;
- explicit physical-evidence gates;
- terminal Local Agent result files;
- current handoff and roadmap state.

Do not vendor a stale copy of the Superpowers skill library into this repository.

## Workflow

### 1. Brainstorm/design before implementation

For new behavior or architecture changes:
- clarify the real goal;
- explore alternatives;
- identify the smallest experiment that can falsify the risky assumption;
- prefer YAGNI;
- save durable design decisions when they affect future work.

Physical Android audio behavior is a design constraint. A design is incomplete if it assumes an OEM route without a device proof gate.

### 2. Isolate substantial implementation work

For significant code changes, use a branch/worktree and start from a clean test baseline.

The current development branch is:

```text
work/phase1-live-call-probes
```

Frozen milestone branches are evidence/rollback references and must not be moved casually.

The Local Agent `agent-control` branch is control/result transport only. `.agent` task/run/result traffic must not be merged into product branches.

### 3. Write an implementation plan

Plans live under:

```text
docs/superpowers/plans/YYYY-MM-DD-<feature>.md
```

A useful plan contains:
- exact goal;
- architecture/approach;
- exact files to create/modify;
- interfaces produced/consumed;
- one test cycle per behavior-changing task;
- exact verification commands;
- physical-device gates where automation cannot prove the outcome;
- explicit handoff/blocker state.

For the current project state, `docs/HANDOFF_NEXT_CHAT.md`, `docs/ROADMAP.md` and `docs/ARCHITECTURE.md` are the authoritative continuation documents. Historical phase plans remain useful evidence but are not automatically the current task list.

### 4. TDD for behavior we control

Default cycle:

```text
RED -> verify expected failure -> minimal GREEN -> verify -> REFACTOR
```

Use unit tests for pure logic/tooling where possible and device tests where Android framework or OEM behavior is essential.

A physical call test does not replace automated tests for code we control. Conversely, automated tests cannot replace physical live-call proof for cellular media and destructive failure gates.

### 5. Systematic debugging

When a hardware/API experiment fails:

1. capture the exact failure and target build;
2. separate product failure from harness/transport failure;
3. classify the failure layer: constructor, permission, routing, call state, data, process lifetime, observation or host transport;
4. change one variable at a time;
5. rerun the narrow probe;
6. document the observed result before changing architecture.

Example from Milestone D: a host ADB `waiting for device` interruption invalidated an app-death timing measurement. That result must not be reclassified as a 105-second product cleanup defect.

### 6. Code review and evidence review

Review has two dimensions:

- **spec/code quality:** implementation matches the plan, remains minimal and has tests;
- **evidence quality:** claims match what was actually measured.

For this repository, incorrect evidence classification is a blocking defect even if the code is clean.

### 7. Verification before completion

Do not say `done`, `working`, `GREEN`, or `PROVEN_S22` until fresh evidence supports that exact claim.

Examples:
- Gradle success proves buildability, not telephony routing;
- `AudioRecord.STATE_INITIALIZED` proves construction, not remote audio capture;
- a granted permission proves a privilege fact, not working media;
- `TYPE_TELEPHONY` presence proves inventory, not uplink injection;
- deterministic remote receipt proves actual cellular uplink behavior;
- a process disappearing after an ADB outage does not by itself prove bounded cleanup latency.

## Local Agent contract

Hard binding for this repository:

```text
agent_binding: c25f88c0-4682-414c-8062-c47fa4034cb0
repository_id: android-ai-call-bridge
repository: MichalMatu/android-ai-call-bridge
control_branch: agent-control
```

Every task must include exactly that `agent_binding` and explicit `resources`.

Rules:
- never infer or switch repository identity;
- check active task state before writing the same branch;
- queued/ACK state is not success;
- terminal `.agent/results/<task-id>.json` is authoritative;
- never launch local Codex from a Local Agent task;
- use Local Agent for deterministic Mac/Gradle/ADB/device execution;
- direct GitHub edits are appropriate when the exact diff is known and no local/device evidence is required.

The experimental event-driven Local Chat Bridge workflow was withdrawn. Do not use `LAB:WAIT_TASK` or rely on `task_result_ready`. Avoid rapid polling of healthy long-running tasks; inspect at a reasonable cadence or when the user asks.

## Physical-device discipline

For every live cellular test on the S22+:

```text
STREAM_VOICE_CALL Muted:true
streamVolume:0
Devices: earpiece(1)
speakerphone off
```

Assert before dial and again after ACTIVE.

Prefer direct USB-C <-> USB-C between S22+ and MacBook. Treat ADB transport instability as a separate test-harness variable.

## Current project state — 2026-09-17

Completed/proven:
- Phase 1 cellular RX;
- Samsung-specific cellular TX;
- Phase 2B shared bidirectional local controller;
- Phase 2C real Shizuku UserService live parity;
- deep-audit M1/M2/M3/M4 fixes;
- 30-second bidirectional endurance;
- explicit abort/takeover path;
- helper/UserService process-death behavior.

Active work:

```text
Milestone D robustness
```

Nearest unresolved physical gate: normal app death while bridge media is active, measured with the phone-side observer in `scripts/s22_app_death_gate.py`.

After that remain call-end cleanup, transferred-PFD close/sibling abort, repeated start/abort resource drift, final 10-minute endurance, final audit and Milestone D freeze.

Do not begin Realtime AI before that freeze.
