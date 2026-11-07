# AI Calls — UX-04 model and audio presentation closeout (2026-11-07)

**Status: SOURCE_PASS / HOST_PASS / FRICTION_PASS / ANDROIDTEST_APK_COMPILE_PASS / PHYSICAL_NOT_RUN.**

**Exact source tested:** `main@7df2c33b282da868d06d5308c9e57d1dd0317435`. A later documentation-only commit does not certify a different APK. Previous UX Sprint 6, UX-01, UX-02 and UX-03 remain closed on their own tested commits.

## Scope: two small, sequential read-only extractions

1. `LanguageModelStatusPresenter` replaces the inline model row status/visibility/progress arithmetic in `LanguageModelActivity` with immutable `LanguageModelScreenState` and `LanguageModelRowState`. It distinguishes selected vs actually installed model: only **installed + selected** is `ACTIVE`; installed but not selected is `READY`; missing models are not ready/selectable. Transfer message is displayed only when changed and non-empty, global busy state controls action enabling, progress is clamped to 0–100 and belongs to the matching model. The Activity still resolves actual per-model readiness through `AndroidGemma4ModelReadiness.check` and renders PL/EN resource labels.
2. `AudioEngineActiveProfilePresenter` replaces the inline current-profile label lookup in `AudioEngineSettingsActivity` with sealed `AudioEngineActiveState.Invalid/Configured` immutable presentation. A missing or corrupt profile remains invalid (never silently becomes stable), existing registry titles are reused, missing catalog labels fall back to exact enum IDs, voice choice is unchanged and the Activity formats the localized auto-voice label.

**Unchanged owners:** `LanguageModelTransferService` alone owns active transfer/cancel; existing model readiness probe owns verified installation status, `AudioEngineProfileStore` alone persists selection, `AudioEngineReadinessProbe` owns readiness checks, `ParakeetModelDownloader` owns download, and Android `TextToSpeech` remains in the Activity for preview/voice probe. Both presenters are pure, have no Android `Context`, I/O, stores, service execution or effects. No duplicate transfer service or preferences were introduced.

**Safety:** zero changes to Samsung/Shizuku, PCM/PFD, RX/TX, Telecom, `Call` ownership, recording, authorization/grants, STOP authority or approved-full-TX-before-end_call order. No new dialogs, usage quotas, task restrictions, timeouts, manual dependencies or `FLAG_SECURE`. No private audio, transcripts, numbers or screenshots were copied to Git.

## RED → GREEN and Mac receipts

Chosen Host Ops installation: `macbook-michal` / `cfea4cc3-b54c-4b6d-b9a6-758d12733e98`. Tasks and immutable task-results are stored in `MichalMatu/local-agent:agent-control` under the exact installation namespace. Mac checkout `/Users/michal/agent-workspace/repos/ai-calls/work`.

| Stage | SHA / task receipt | Result |
|---|---|---|
| Fresh preflight | `aicalls-ux04-main-preflight-20261107-v1` | **DONE/0**, main `1d28de63`, clean Mac, origin synced, Android SDK present, phone untouched |
| Model presenter RED | `aicalls-ux04-model-presentation-red-20261107-v1` | **DONE/0**, isolated old-source JVM compiler RED on unresolved presenter/typed states |
| Model presenter source | `36286a4b10a47fe384459d4833cf118f2d7fb5ef` | Single read-only model-status responsibility and 7 JVM regression cases |
| Model GREEN | `aicalls-ux04-model-presentation-green-20261107-v1` | **DONE/0**, 7/7 JVM PASS, 16 focused Python PASS, original Mac clean |
| Audio summary RED | `aicalls-ux04-audio-summary-red-20261107-v1` | **DONE/0**, isolated old-source JVM compiler RED on missing typed active state/presenter |
| Audio summary source | `7df2c33b282da868d06d5308c9e57d1dd0317435` | One read-only current-profile responsibility and 6 JVM regression cases |
| Full green | `aicalls-ux04-final-source-green-host-20261107-v1` | **DONE/0**, 13/13 new JVM tests PASS, 18 focused Python PASS, **332/332 host Python PASS**, anti-friction PASS, `bash scripts/verify_host.sh` returns `host_quality_gate_green=true`, `compileDebugAndroidTestKotlin` + application and AndroidTest APK assembly PASS, clean Mac |

No rerun of the accepted Sprint 6/UX-01/UX-02/UX-03 physical tests was performed. `ERROR=explicit_real_call_opt_in_required` and `DEVICE_STATUS=NOT_CHECKED` in the host gate are expected safe refusals, not real-Call or phone evidence.

## Coverage and remaining gates

- New JVM regression matrix verifies missing vs ready vs selected models, transfer busy/other model progress, bounded negative/oversized byte counts, changed-only feedback, immutable snapshots, corrupt active profile, stable baseline, all engine catalog titles, unknown-label fallback, unmodified selected voice and unsupported choices without silent substitution.
- Original Android instrumentation is **compiled**, not executed on an attached phone for this source SHA. **S22: PHYSICAL_NOT_RUN**, no installation, ADB, actual model transfer, real local Gemma run or call. Do not equate this source gate with device acceptance.
- No new product blocker was proven. Existing `BLOCKERS.md` is unchanged. F-103 language/accessibility, F-080 continuous recording, F-091 real-call acceptance and semantic model quality remain separate.

## Next independent work

**UX-04 closed SOURCE_PASS.** Start independent **UX-05 / F-103**: audit current PL/EN and accessibility of visible strings without changing protected behavior, with targeted RED→GREEN JVM/source checks, full appropriate host gate, PL/EN parity and explicitly scoped physical screenshot acceptance if exact S22 and a safe IDLE state are available. The future selection/download logic remains the existing single owner; do not refactor it again without new evidence.
