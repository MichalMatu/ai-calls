# AI Calls — LAB-DIALOG-01: implementation checkpoint (2026-11-08)

## Scope and baseline

Preflight GitHub `main@4eae72c` (plan approval), Mac Local Agent `aicalls-labdialog01-mac-preflight-20261108-v1` DONE/0 (clean main=origin/main, physical Samsung SM-S906B detected by ADB). The four rejected baked-in dialogue stories remain deleted; no real calls, external effects, GitHub Actions, Codex or Work.

## Source delivered

- A — `SkillLabRunner` extended (not copied): native backend reused across unrestricted manual turns, explicit START/STOP and reset, late-callback fencing, immutable snapshots; new manual input UI and offline model selection.
- B — `LabCaseLibrary`: create/edit/revise/duplicate/select/delete; caller role, opening utterance, state, facts, expectations, completion condition; private Keystore-encrypted `AndroidConversationPreparationStore` record; PL/EN editor in Lab.
- C — `LabDialogueReport`: private transcript evidence, latency, END_CALL proposal as observation, eight rubric dimensions with `UNASSESSED` until a user selects PASS/FAIL and cites a turn. A/B flags mismatched case revision; user test facts and goals kept out of model prompt.
- D — `LabAdaptiveInteractor`: single-use conditional rules `question/end/refusal/contains:/missing:/otherwise`, driven by the real previous model reply, with per-turn decision trace; same runner/model session.
- E — Android-only isolated Keystore and UI tests and opt-in real local two-turn S22 manual model/STOP/reset test. Audio STT/TTS, real polish PCM and inline human takeover during auto branch are not done.

Source commits from `894eaa3` through `6f77401` (fresh main required). All commits `[skip ci]`.

## Verification and limitations

- `aicalls-labdialog01-mac-preflight-20261108-v1`: DONE/0, Mac clean and S22 ADB detected.
- `aicalls-labdialog01-stage-a-build-20261108-v1`: FAILED due to Gradle `--offline` missing plugin artifact; not a source-code verdict.
- `aicalls-labdialog01-stage-abc-compile-20261108-v1`: FAILED at Android resource apostrophe in English string; subsequently fixed at `a5f521c`.
- `aicalls-labdialog01-stage-abcd-regression-20261108-v1`: DONE/0, `:app:testDebugUnitTest` and `:app:assembleDebug`, BUILD SUCCESSFUL at `a5f521c`.
- `aicalls-labdialog01-full-mac-gate-20261108-v1`: QA_PENDING for source `f9836fd` (all modules, lint, APK+AndroidTest, Python, audio audit); later reference-fields change at `6f77401` not yet in this gate.
- `aicalls-labdialog01-s22-readonly-20261108-v1`: READONLY_PENDING; do not install or take foreground if PocketHost/other app occupies S22.
- New `SkillLabManualDialogueTest`, `LabCaseLibraryTest`, `LabAdaptiveInteractorTest`, `LabDialogueReportTest`, `AndroidLabDialogStorageAcceptanceTest` and `AndroidLabManualNativeOfflineTest` must be explicitly judged by current Mac/device receipts, not inherited earlier green outcomes.
- Real semantic quality and Polish listening are **NOT_VERIFIED**. END_CALL correctness is not naturalness or factual accuracy.

## Next gate

1. Read new task-results, repair any actual compile/tests, refresh Mac complete quality gate for final code HEAD.
2. If S22 foreground is safe and Call IDLE, install verified matching APK/AndroidTest with preserve-data update only and run two isolated Android UI/store tests and opt-in native manual 2+ turns/STOP/reset; capture screenshots and private model transcript, do not publish.
3. Record PASS/FAIL/BLOCKED with exact task receipt, update matrix and this checkpoint; leave F2 audio and any unverified semantically graded A/B open.
