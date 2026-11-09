# Developer tooling layout

The developer tooling is grouped by responsibility instead of keeping implementation and tests in one flat directory. The repository-wide placement contract lives in `docs/REPOSITORY_LAYOUT.md`.

- `aicall_tools/` — importable Python implementation grouped by calls, device, realtime, text, benchmarks, relay and Orange historical evidence.
- `bin/aicalls` — stable developer command dispatcher.
- `local_agent/` — small repository-owned wrappers for routine Local Agent repo/build/readiness work; they do not bypass authority or platform controls.
- `tests/` — host tests grouped by the same domains. Versioned Orange tests remain under `tests/orange/history/` because they preserve physical evidence and regression contracts.
- `aicall_tools/diagnostics/audit_audio_engine_quality.py` — static M1–M4 host-only audio contracts, offline without device/model/network, automatically part of `verify_host.sh`.
- `tests/test_audit_audio_engine_quality.py` — mutation regressions proving that dangerous changes fail the audio quality gate.
- `verify_host.sh` — canonical host quality gate. It validates repository layout, Android build/unit/lint, Python tests and the audio gate; no ADB or real calls.

## Fast iteration: selective tests, incremental caches, no APK until device

- `bash scripts/bin/test-profile.sh preflight` — static diff + anti-friction, no Gradle.
- `bash scripts/bin/test-profile.sh auto <pre-sprint-sha>` — select **only changed module JVM unit tests** and paired Python tests; includes staged/unstaged/untracked edits. Without SHA uses `HEAD^`, but for multi-commit sprint pass exact pre-sprint SHA.
- `bash scripts/bin/test-profile.sh changed app` — only `:app:testDebugUnitTest`; `focus app '*SpecificTest'` — single selected class, if the impact is known.
- `bash scripts/bin/test-profile.sh full` — intentional integration gate for unit/lint/Python/static/security, **no APK assembly**.
- `bash scripts/bin/test-profile.sh device-build` — `:app:assembleDebug` and `:app:assembleDebugAndroidTest` **only immediately before a separately authorized exact S22 installation**; this never installs or places calls.
- `bash scripts/bin/test-profile.sh hooks-enable` — optional repository-local `core.hooksPath` to install a **static-only pre-commit** (no compilation, network, ADB). Connector commits do not invoke local Mac hooks; Local Agent must still explicitly run the fast profile.

The `auto` router fails with `DEV_AUTO_SCOPE_ESCALATION` when Gradle config changes, a Python source has no paired test, or a path is unknown: choose an explicit target rather than pretend the change was tested. Android instrumentation is deferred until the physical device gate. `full` remains useful at integration checkpoints but is **not** a requirement for every small edit.

Existing `gradle.properties` already enables **build cache**, **configuration cache** and **parallel build**. The forced `--no-daemon` was removed so the warm Gradle daemon and its caches can persist between Mac tasks. Do not delete `.gradle` / `~/.gradle` or overwrite cached artifacts. Gradle may still compile JVM/Android classes for unit tests; only APK **packaging** is postponed. Measure cold vs warm runs on the same Mac before making speed claims.

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
