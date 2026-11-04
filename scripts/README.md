# Developer tooling layout

The developer tooling is grouped by responsibility instead of keeping implementation and tests in one flat directory. The repository-wide placement contract lives in `docs/REPOSITORY_LAYOUT.md`.

- `aicall_tools/` — importable Python implementation grouped by calls, device, realtime, text, benchmarks, relay and Orange historical evidence.
- `bin/aicalls` — stable developer command dispatcher.
- `local_agent/` — small repository-owned wrappers for routine Local Agent repo/build/readiness work; they do not bypass authority or platform controls.
- `tests/` — host tests grouped by the same domains. Versioned Orange tests remain under `tests/orange/history/` because they preserve physical evidence and regression contracts.
- `aicall_tools/diagnostics/audit_audio_engine_quality.py` — static M1–M4 host-only audio contracts, offline without device/model/network, automatically part of `verify_host.sh`.
- `tests/test_audit_audio_engine_quality.py` — mutation regressions proving that dangerous changes fail the audio quality gate.
- `verify_host.sh` — canonical host quality gate. It validates repository layout, Android build/unit/lint, Python tests and the audio gate; no ADB or real calls.

Python-only verification:

```bash
PYTHONPATH=scripts python3 -m unittest discover -s scripts/tests -t scripts -p 'test_*.py'
```

Do not add new top-level Python files to `scripts/`; place implementation and tests in the matching domain directory. Do not add a new immediate child of `scripts/` only to bypass organization: introduce a new tooling category only when the existing areas cannot own it, and update `docs/REPOSITORY_LAYOUT.md` plus the layout allowlist in `verify_host.sh` in the same change.

Audio-engine structural regression checks (host-only):

```bash
python3 scripts/aicall_tools/diagnostics/audit_audio_engine_quality.py
PYTHONPATH=scripts python3 -m unittest scripts.tests.test_audit_audio_engine_quality -v
```

These are structural checks; they do not certify the sound quality or device availability of Parakeet/Piper/Gemma Audio. See [checkpoint](../docs/checkpoints/2026-11-04-audio-models-hostonly-quality-gates-checkpoint.md).
