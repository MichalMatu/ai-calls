# Local Agent entrypoints

These repository-owned wrappers run on a Mac/host that has ADB. They do not bypass application authority or Android restrictions.

- `repo_state.sh` — refresh remote refs and report the checkout.
- `host_verify.sh` — canonical Gradle, lint, Android build and Python host gate.
- `device_readiness.sh` — **no-call** physical S22 check. Discovers USB or a paired Wireless Debugging transport via `s22_adb_discovery.py`; verifies actual model and hardware identity, then `IDLE` and existing Shizuku/audio readiness. Supports an optional `AICALL_SERIAL` override after verification.
- `clir_iteration.sh --allow-clir-enable` — **disabled (exit 3)** while no safe call-specific Android dial/hangup controller exists. Never resume the former `KEYCODE_ENDCALL`/global telecom fallback.
- `scripts/bin/aicalls relay-live --attach-existing-call --allow-clir-enable` — manual developer attach to an already operator-controlled call. May change CLIR only with separately valid authorization; **never dials or hangs up**. Default CLI discovers the verified ADB transport; Bluetooth must already be OFF; do not force audio settings of unrelated live calls.

Latest details: `docs/SPRINT_WIRELESS_ADB_CALL_SAFETY_2026-11-01.md`. The repository's `agent-control` branch is historical transport; a previous `daemon.json` is not proof that a current Local Agent task is running.
