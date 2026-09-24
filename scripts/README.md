# Developer tooling layout

The developer tooling is grouped by responsibility instead of keeping implementation and tests in one flat directory.

- `aicall_tools/` — importable Python implementation grouped by calls, device, realtime, text, benchmarks, relay and Orange historical evidence.
- `bin/aicalls` — stable developer command dispatcher.
- `local_agent/` — small repository-owned wrappers for routine Local Agent repo/build/readiness work; they do not bypass authority or platform controls.
- `tests/` — host tests grouped by the same domains. Versioned Orange tests remain under `tests/orange/history/` because they preserve physical evidence and regression contracts.
- `verify_host.sh` — canonical host quality gate used by CI.

Python-only verification:

```bash
PYTHONPATH=scripts python3 -m unittest discover -s scripts/tests -t scripts -p 'test_*.py'
```

Do not add new top-level Python files to `scripts/`; place implementation and tests in the matching domain directory.
