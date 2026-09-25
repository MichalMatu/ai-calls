# Local Agent entrypoints

These wrappers keep routine Local Agent task payloads small and repository-owned. They do **not** bypass application authority, runner guards, or external platform/tool controls.

- `repo_state.sh` — refresh remote refs and report the current checkout/branch state.
- `host_verify.sh` — run the canonical host quality gate.
- `device_readiness.sh` — require the exact S22 over ADB, require cellular `IDLE`, then run the existing no-call readiness probe.
- `bash scripts/local_agent/clir_iteration.sh --allow-clir-enable` — run the current guarded physical CLIR enable iteration through the existing `relay-live` runner, then require the phone to return to `IDLE`. It preserves the runner's explicit CLIR-effect guard and does not duplicate call/media/dialogue logic.

The physical live-call implementation remains behind the existing guarded CLI entrypoint in `scripts/bin/aicalls`. Wrappers here must never weaken application authority, remove runner guards, conceal the requested external effect, or route around an external safety decision.
