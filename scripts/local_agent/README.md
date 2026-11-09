# Local Agent entrypoints — current Mac Host Control 4.20.16

**Transport is owned by `MichalMatu/local-agent`, NOT this application's `.agent/daemon`.** See `docs/LOCAL_AGENT_HOST_FLOW_2026-11-01.md` for the exact host/installation identifiers, immutable host JSON, `task-results`, status and recovery contract.

These scripts are repository-owned **commands to invoke within a Mac host task** after selecting a clean, fresh AI Calls worktree. They do not schedule Local Agent work themselves and do not bypass telephony permissions.

- `repo_state.sh` — repository refs and checkout inspection.
- `host_verify.sh` — explicit **full host** unit/lint/Python/static tests, now **without APK builds**. Prefer `bash scripts/bin/test-profile.sh auto <base-SHA>` or `focus <module> '<class-filter>'` during iteration; use `device-build` only just before authorized S22 installation. The opt-in lightweight pre-commit hook was enabled on selected Mac, but GitHub Connector commits bypass it.
- `device_readiness.sh` — paired Wireless ADB/USB discovery for exact S22, `IDLE`, Android/Shizuku no-call readiness. Optionally `AICALL_SERIAL` must still pass identity verification.
- `clir_iteration.sh` — fails closed while a real exact-call dial/controller is missing.
- `scripts/bin/aicalls relay-live --attach-existing-call --allow-clir-enable` — separate developer attach-only mode with explicit effect authority; never dials or hangs up.

Never infer current Mac status from `ai-calls:agent-control/.agent/status/daemon.json`. For Host Ops submit only to the selected installation's private `local-agent:agent-control` namespace and check its immutable result. No GitHub Actions, Codex or Work.

- `scripts/bin/aicalls call-capabilities` — read-only host-side S22 Telecom role/SDK/app/Shizuku/call-state inspection. Requires exact-device ADB identity verification, does not place or hang up calls, and **cannot authorize dialing**. See `docs/EXACT_CALL_CAPABILITY_SPRINT_2026-11-01.md`.
