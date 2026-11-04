# Local Codex takeover — 2026-11-04

## Owner decision and source

The owner explicitly requested continuing code development and tests in local
Codex. Current execution instructions in AGENTS, README, autonomous mode,
roadmap, handoff and USB bridge now reflect that choice. Codex uses direct
local tools exclusively; Local Agent execution is reserved for ChatGPT.
No Local Agent task was dispatched. Its status was read only to check for
competing work; competing repository/device mutations are forbidden. The previous
Gemma instrumentation refusal remains NOT RUN and was not retried.

Checkout: `/Users/michal/agent-workspace/repos/ai-calls/work`, `main`.
Fetched fresh `origin/main` and fast-forwarded local main by 688 commits to
`721073f6c9b0873028c19093a7c1951f09763ebc`. Product sources were not changed.
Installed S22 APK remains the previously accepted `418c84e2` build.

The supplied conversation already includes the owner's confirmation that the
USB panel was open and the previous executor's ADB confirmation. No repeated
manual panel-opening request is needed; this is not a new visual QA result.

## Local environment acceptance

- Direct USB/ADB discovers the exact S22+ (`SM-S906B`).
- Existing `Device.safe_preflight()` and `run_bridge(..., "status")`: PASS.
  Twice IDLE, correct app dialer, inactive campaign service, no pending retry,
  working `run-as`, valid Android STT → Gemma E2B → Android TTS profile.
- Private grant/skills/audio fingerprints unchanged across the status request;
  phone remains IDLE. Only sanitized status was printed.
- Private status receipt, mode 0600:
  `~/Documents/AI-Calls/local-codex/2026-11-04-takeover-status.json`.
- `python3 scripts/aicall_tools/diagnostics/audit_product_friction.py`: PASS
  (static scope only).
- Local JVM tests: 766 executed, zero failures/errors/skips (app 705,
  realtime-client 51, privileged-helper 10; audio-bridge has no unit sources).
- `bash scripts/verify_host.sh`: **PASS, exit 0**,
  `host_quality_gate_green=true`: JVM tests, lint for all four modules, debug
  APK and AndroidTest APK build, 262 Python tests, audio quality audit and
  static secret/identity transport checks. This initial full run qualifies
  the new direct local build environment; it does not repeat S22
  instrumentations. Java 22 and the existing Android SDK were available
  without setup or a new runner.
- Changed Markdown links and execution-mode wording: PASS; `git diff --check`:
  PASS.

No install, call, grant mutation, profile write, model download, real audio
export or previously denied instrumentation was performed.

## Git preservation

Local branches are now `main` and `agent-control`. Before removing 19 stale
local branches, their unmerged tips were preserved in a private incremental
Git bundle, checked with `git bundle verify` and exact bundle-head comparison;
merged tips remain reachable from main. Remote branches were not deleted.

Private recovery files, mode 0600:

- `~/Documents/AI-Calls/local-codex/2026-11-04-pre-takeover-branches.bundle`
- `~/Documents/AI-Calls/local-codex/2026-11-04-pre-takeover-branches.json`

The manifest records original refs/SHAs, prerequisite source and bundle SHA-256.
Keep these files private because old relay history can contain call data.
Generated `.openvisio/` navigation metadata is ignored by Git.

## Continuation

Use the existing host verifier and USB console directly. Continue the open
quality/thermal work and F-045/F-047/F-048 from the canonical roadmap and
BLOCKERS; do not reclassify historical passes or create another debug channel.
External CLIR success remains unverified. Application-owned call authority,
FactDisclosurePolicy, STOP and the frozen Samsung/Shizuku media boundary are
unchanged.
