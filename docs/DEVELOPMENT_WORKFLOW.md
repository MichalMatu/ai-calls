# Development workflow — Superpowers adaptation

This project uses the methodology from [`obra/superpowers`](https://github.com/obra/superpowers) for agentic development.

Reference reviewed on 2026-09-14:

- upstream repository: `obra/superpowers`
- upstream `main` commit reviewed: `b36e0829c6d0140e93cfef2ca599b1b07d4a7797`
- upstream release line at that commit: v6.3.0
- license: MIT

Superpowers is a **coding-agent workflow/plugin**, not an Android runtime dependency. Nothing from it is shipped in the APK.

## How we use it here

Where the active coding harness supports Superpowers directly, install the upstream plugin for that harness and let its skills trigger normally.

For ChatGPT + this repository's Local Agent, Local Agent is only an execution worker. It does not itself provide the Superpowers skill runtime, so the repository mirrors the workflow contract through `AGENTS.md`, durable design/planning documents, TDD requirements, evidence gates, and verification steps.

Do not vendor a stale copy of the whole Superpowers skill library into this repository. Upstream explicitly treats proper harness integration and automatic skill triggering as important. Keep the framework external and keep our project-specific process here.

## Workflow

### 1. Brainstorm/design before implementation

For new behavior or architecture changes:

- clarify the real goal;
- explore alternatives;
- identify the smallest experiment that can falsify the risky assumption;
- prefer YAGNI;
- save durable design decisions when they affect future work.

For this project, physical Android audio behavior is a design constraint. A design is incomplete if it assumes an OEM route without a device proof gate.

### 2. Isolate substantial implementation work

For significant code changes, use a branch/worktree and start from a clean test baseline.

Exception: the user may explicitly ask for direct work on `main`. That instruction wins. Even then, keep commits small and independently understandable.

The Local Agent `agent-control` branch is **not** an implementation branch. It is control/result transport and stays separate from `main`.

### 3. Write an implementation plan

Plans live under:

```text
docs/superpowers/plans/YYYY-MM-DD-<feature>.md
```

A useful plan must contain:

- exact goal;
- architecture/approach;
- exact files to create/modify;
- interfaces produced/consumed;
- one test cycle per behavior-changing task;
- exact verification commands;
- physical-device gates where automation cannot prove the outcome;
- explicit handoff/blocker state.

The current next-step plan is:

- `docs/superpowers/plans/2026-09-14-phase1-live-call-validation.md`

### 4. TDD for implementation behavior

Default cycle:

```text
RED -> verify the expected failure -> minimal GREEN -> verify -> REFACTOR
```

For Android code, prefer unit tests for pure state/format/metrics/tone-generation logic and instrumentation/device tests where Android framework behavior is essential.

A physical call test does not replace automated tests for code we control. Conversely, an automated test cannot replace the physical two-phone proof required for cellular routing.

### 5. Systematic debugging

When a hardware/API experiment fails:

1. capture the exact failure and target build;
2. determine whether failure is constructor, permission, routing, call-state, data, or remote-observation failure;
3. change one variable at a time;
4. rerun the narrow probe;
5. document the observed result before changing architecture.

Do not jump directly to Samsung private APIs/root because a generic experiment failed once.

### 6. Code review and evidence review

Review has two dimensions:

- **spec/code quality:** implementation matches the plan, remains minimal, and has tests;
- **evidence quality:** claims match what was actually measured.

For this repository, an incorrect evidence classification is a blocking defect even if the code is clean.

### 7. Verification before completion

Do not say `done`, `working`, or `PROVEN_S22` until fresh verification supports that exact claim.

Examples:

- Gradle build success proves buildability, not telephony media routing.
- `AudioRecord.STATE_INITIALIZED` proves construction, not remote audio capture.
- a granted protected permission proves a privilege fact, not successful use of every guarded API.
- `TYPE_TELEPHONY` presence proves inventory, not TX injection.
- the remote test phone hearing deterministic injected audio proves the Phase 1B media direction.

## Superpowers concepts mapped to this repo

| Superpowers concept | Repository application |
| --- | --- |
| brainstorming | architecture/risk experiments before code |
| using-git-worktrees | isolated implementation branches for substantial work |
| writing-plans | `docs/superpowers/plans/` |
| test-driven-development | tests first for behavior we control |
| systematic-debugging | narrow Android/OEM probe before fallback changes |
| requesting-code-review | review plan/spec first, then implementation quality |
| verification-before-completion | build/device evidence before success claims |
| finishing-a-development-branch | verify, merge to `main`, preserve durable handoff |

## Current project handoff

As of 2026-09-14:

- the S22+ baseline capability probe is saved in `docs/S22_BASELINE_2026-09-14.md`;
- shell UID 2000 can initialize call-specific capture sources;
- telephony RX and TX devices are exposed;
- protected shell permissions needed for the experiment are present;
- uplink `AudioTrack` construction was attempted without an active call and failed, so it is not a negative live-call result;
- the next proof requires a dedicated SIM/live cellular call;
- implementation should resume from the live-call validation plan rather than rediscovering the baseline.
