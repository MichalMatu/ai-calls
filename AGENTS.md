# Agent workflow for android-ai-call-bridge

This repository adopts the development methodology from [`obra/superpowers`](https://github.com/obra/superpowers) as the default process for agentic engineering work.

User instructions always take precedence. The project-specific evidence gates in `docs/ROADMAP.md` and safety rules in `docs/SECURITY_PRIVACY.md` also override generic workflow advice where they are stricter.

## Before changing code

1. Read `README.md`, `docs/ROADMAP.md`, `docs/ARCHITECTURE.md`, and the most relevant current evidence document.
2. Check whether the requested work changes behavior, architecture, test strategy, or only documentation.
3. For new designs or unclear requirements, use the Superpowers `brainstorming` workflow before implementation.
4. For multi-step work, create a concrete implementation plan under:
   `docs/superpowers/plans/YYYY-MM-DD-<topic>.md`
5. For substantial implementation work, use an isolated branch/worktree unless the user explicitly requests direct work on `main`.

## Implementation discipline

Follow the Superpowers principles:

- **TDD for behavior changes:** RED -> verify expected failure -> GREEN -> verify -> REFACTOR.
- **Systematic debugging:** establish root cause before changing implementation.
- **Small independently testable tasks:** each task should produce one reviewable deliverable.
- **Frequent verification:** do not claim success from code inspection alone.
- **Evidence over claims:** hardware behavior must be measured on the target device.
- **YAGNI:** do not build Realtime AI, dialer UX, or generalized abstractions before the cellular media gates pass.

Diagnostic/throwaway hardware probes may use a narrower test strategy when the user explicitly accepts that exception, but production behavior must not bypass the repository's test gates.

## Project-specific proof rules

Do not promote a cellular media capability to `PROVEN_S22` from any of the following alone:

- an API constructor succeeding;
- a permission being granted;
- `TYPE_TELEPHONY` being present;
- `setPreferredDevice()` returning true;
- another Samsung feature doing something similar;
- another GitHub project proving the path on different hardware.

For downlink and uplink, the physical two-phone criteria in `docs/POC_AUDIO_TEST_PLAN.md` are authoritative.

## Local Agent

`MichalMatu/local-agent` is an execution mechanism, not the source of truth.

- Source code and durable project documentation live on `main`.
- `.agent` task/result traffic lives on the dedicated `agent-control` branch and must not be merged into `main`.
- A queued task or ACK is not success; inspect the final result/log.
- For phone work, record the exact target model/build and distinguish `NOT TESTED` from `PASS`.

## External research code

External projects may be used as evidence and design references. Do not copy implementation code without checking its license.

In particular:

- scrcpy: Apache-2.0 — reuse may be possible with attribution/licensing compliance;
- ShizuCallRecorder: GPL-family licensing — research reference unless licensing is intentionally adopted;
- AgentCall: AGPL-3.0 — research reference; do not copy implementation into this repository by default;
- Superpowers: MIT — methodology/plugin reference.

## Completion gate

Before declaring a task complete:

1. run the relevant automated tests/builds;
2. run any required Local Agent/device checks;
3. verify the result rather than inferring it;
4. update evidence/status docs when a hardware assumption changes;
5. leave a clear handoff when a physical prerequisite (for example a SIM) blocks the next gate.

See `docs/DEVELOPMENT_WORKFLOW.md` for the detailed Superpowers adaptation used by this repository.
