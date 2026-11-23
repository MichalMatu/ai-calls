# Mac Local Agent

`agent-control` stores tasks and results for `macbook-michal`. `main` stores the code. Confirm the latest heartbeat or acknowledgement, report and source SHA before relying on a task result.

Run `scripts/local_agent/repo_state.sh` before working. Preserve local changes. Use `scripts/bin/test-profile.sh preflight`, `quick` or `full`. Run `lab-ui` on an emulator only.

For S22+, run `scripts/local_agent/device_readiness.sh`, check IDLE and the exact Call/SIM, and prepare rollback. Get consent before GSM calls. Record PASS or HOLD with scope and SHA. Keep personal data and WAV out of reports.
