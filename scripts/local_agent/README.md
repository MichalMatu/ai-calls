# Local Agent entrypoints

These wrappers keep routine Local Agent task payloads small and repository-owned. They do **not** bypass application authority, runner guards, or external platform/tool controls.

- `repo_state.sh` — refresh remote refs and report the current checkout/branch state.
- `host_verify.sh` — run the canonical host quality gate.
- `device_readiness.sh` — require the exact S22 over ADB, require cellular `IDLE`, then run the existing no-call readiness probe.

The physical live-call implementation remains behind the existing guarded CLI entrypoint in `scripts/bin/aicalls`. A wrapper here must never be used to weaken or route around an external safety decision.
