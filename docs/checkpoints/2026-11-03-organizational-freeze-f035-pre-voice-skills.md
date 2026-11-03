# AI Calls — organizational freeze before voice/skills redesign (2026-11-03)

## Why this checkpoint exists

Owner requested a **non-destructive organizational checkpoint** before continuing development: establish precisely what works, audit current documentation/blockers, finish source QA, retain a recoverable code revision, and *plan* (not implement during the freeze) stronger local speech and a separation of AI, skills and deterministic scripts.

**Baseline code/repository commit before documentation hygiene:** `6d3db9159b24d91aa28f2db6162d3881a0a428f0` (`main`). The Android code/configuration at this point is already built/tested and installed from source pin `7176f32bcb0e3f96dec99d790fd5159cc6427fa7`. Later commits in this freeze are documentation/checkpoint changes unless individually declared. Verify exact current `main` HEAD on restart.

Do not create a speculative `LIVE_TASK_PASS` and do not confuse a source QA pass with in-call/external success. **`BLOCKERS.md` is the only current F-xxx status register**, not this checkpoint. This document is the consolidated evidence and decision record.

## Physically proven, with scope

- S22+ `SM-S906B`, ADB serial `RFCT70L7E8J`; application installed and default dialer. Mac Local Agent `macbook-michal` is the development executor, not the phone task engine.
- Earlier authorized real S22 call on application source `eb2f505831ddca17a395f666c4e7f08529306c55`: `OFFHOOK` → `IDLE`, 5 completed dialogue turns; real technical pipeline counters RX `2,161,920 B`, TX `642,090 B`; 5 Gemma decisions and app-approved STT/TTS turns; max repetitive `STATE_TASK_SUBJECT` streak = **1**. This proves local technical paths, not independent far-end acoustic audibility.
- F-034 was **already implemented before this organizational checkpoint**: `CampaignGemmaPreflight.prepare` creates and warms the **same Gemma classifier before** `Telecom.placeCall`; `AppOwnedCampaignService.startAuthorizedAttempt` rechecks generation, grant/SIM and IDLE after prewarm before dialing, then transfers the prepared engine. First measured speech-end-to-TX was **47,567 ms before** vs **10,567 ms after** (max measured new **12,175 ms**). Remaining latency cannot honestly be assigned to cold-start alone without per-stage current-speech measurements.
- F-035 source at `7176f32b`: speech recognizer `NO_MATCH=7` or `SPEECH_TIMEOUT=6` requests continued listening within the **same** authorized call. `ChatRelayDownlinkPump.abandonActiveCapture()` detaches stale RX capture; concurrency/unit and metadata-only diagnostics tests added. Mac task `aicalls-20261103-f035-rx-capture-detach-source-qa-v1` **DONE/0**: Python regression, anti-friction, full Kotlin unit/assemble/lint PASS. Mac `aicalls-20261103-f035-rx-capture-detach-install-s22-offcall-v1` **DONE/0**: exact S22, APK install PASS, preflight READY, IDLE, no call.
- Read-only follow-up `aicalls-20261103-1904-s22-readonly-health-and-report-v1` **DONE/0**: 19:04 Poland, S22 attached and IDLE, AI Calls default dialer; app last updated `2026-11-03 11:46:09` local device display; no active campaign continuation/service. Last private report timestamp `2026-11-03T09:23:44Z` (before that installation), therefore **no live F-035 retry verification on installed APK**. In that historical call `failure_reason=stt_recognition_error_7`, `clir_route_verified=false`, `clir_commitment_consumed=false`, `chat_relay_live_call_success=false`. Numeric retries `0` on an older APK cannot be used to conclude new code fails.
- Local Gemma contextual off-call tests previously 7/7 PASS. This verifies a bounded action classifier, **not** arbitrary autonomous human-level dialogue.

## Known incomplete, must remain visibly OPEN

1. **F-035 in-call acceptance:** no fresh physical NO_MATCH/timeout followed by successful renewed STT on the newly installed APK; source and install are checked, hardware dialogue recovery is not.
2. **External outcome:** no independent audible far-end TX proof and **no verified CLIR state change**; technical RX/TX bytes alone cannot prove either.
3. **Performance:** 10–12 s measured response after speech end is still too slow for many IVRs. Profile separately VAD/speech-end, STT finalization, Gemma inference (including fallback/turn memory), TTS first PCM, RX/TX; prewarm is not the only possible bottleneck. Do not invent exact attribution.
4. **Generic task/skills coverage:** current `GateCHybridDialogueBackendFactory.actionContext/actionPolicy/actionExecutor` embeds CLIR-specific phrases in code. A generic skills catalog/capability registry is a future redesign, not yet installed.
5. Existing `BLOCKERS.md` rows for real unverified long calls, retry lifecycle, diagnostic boundaries, legacy documentation debt and tooling denials stay historically visible. **Do not delete or falsely mark VERIFIED** for organizational neatness.

## Decided architecture — implementation in later phase, not now

```text
APP / EXECUTOR (never delegated to a model)
  scoped authorization, target/SIM, phone ownership, STOP,
  disclosure policy, commitment and external-success verification
       |
  deterministic workflow / TaskGraph / PhraseMatrix / service-packs
       | concrete task state, approved facts, eligible next actions
       v
  AI REASONER (local Gemma or replaceable local model)
    hears finalized STT / optional future native audio input,
    interprets intent and chooses structured skill with confidence;
    cannot dial, authorize, disclose or claim success
       |
  SKILL CONTRACTS (versioned, task-scoped capability interfaces)
    id, intent, inputs, allowed facts/effects, parameters,
    confidence/clarification/fallback, preconditions, outputs,
    evidence requirements; typed validation before execution
       |
  DETERMINISTIC SKILL EXECUTORS / SCRIPTS
    dialogue wording, IVR navigation, facts from authorized vault,
    task-specific protocols, effect validation and output approval
       |
  TTS / Telecom TX (or future validated real-time voice adapter)
```

- **Separate roles, not two competing brains:** model suggests, app validates and executes, evidence decides completion. Models and scripts cannot become independent authority owners.
- **Preserve working `DialogueActionBackend`, `DialogueSkillTextBackend`, `CallCommitmentGate`, `FactDisclosurePolicy` and the frozen `CallMediaSessionCoordinator` / Shizuku media boundary.** Migrate CLIR hard-coded action phrases into reviewed task-scoped skill definitions incrementally with behaviour-equivalent regressions. Do not wipe or replace old action paths at once.
- `service-packs/` holds declarative IVR/domain knowledge only, not grants, phone numbers, plaintext secrets or new side-effect authority. `scripts/` is also a **host development tooling directory**; its CLI scripts are not the same as the eventual Android deterministic skill executors.
- Keep the existing **Gemma 4 prewarm** before dial. Test warm instance identity, STOP/cancellation and state revalidation in future refactors before changing execution order.
- A speech-to-speech native model is a possible future transport/backend **experiment** only. Gemma 4 E2B/E4B accept audio but generate text; full duplex voice systems (e.g. Moshi) and local VAD→STT→LLM→TTS cascades exist. S22 resource fit, Polish dialogue quality, telephony compatibility, first-audio latency and privacy must be measured before replacement. No unsupported voice-runtime deployment in this freeze.

## Organizational cleanup boundaries

- **Preserve all prior test receipts and historical checkpoints**. Replace ambiguous `latest` claims with this explicit summary at the top of README, ROADMAP, ARCHITECTURE and HANDOFF; mark older paragraphs as historical, not current. No bulk deletion of docs or the `BLOCKERS.md` register.
- Remove contradictory *active guidance* (e.g. `F-002` still requiring per-call arming, call controller missing, or mandatory live supervisor) via dated current-state notes, without erasing provenance.
- Do not introduce arbitrary attempt/turn/time caps, manual handoffs, whitelist targets or redundant consent screens. Exact target/grant, local permission, STOP, data and counterparty law are real safety boundaries.
- No device/audio/PII exports to Git; keep `record_call_evidence=false` unchanged. Do not dial in this organizational run. A previous live-call job submission was blocked by external tool controls; do not circumvent it via alternate commands.
- **No production code/behavior changes under the label of a checkpoint.** If the source inspection finds an actual behavioral bug, log it as a separate blocker and handle in another focused sprint with QA.

## Recoverable checkpoint and actual organization-audit receipts

- **Immutable source baseline:** `6d3db9159b24d91aa28f2db6162d3881a0a428f0`. A separate checkpoint branch was created at this exact SHA: `checkpoint/2026-11-03-f035-s22-baseline`. The branch preserves working F-035 source and installed-hardware acceptance *before* the documentation / quality-gate cleanup. No branch switch or Android runtime modification was made in `main`.
- The current README, ARCHITECTURE, ROADMAP, HANDOFF and GEMMA_ACTION_ROUTER have a single explicit top-level reference to this freeze or its responsibility contract. Older chronological statements are labeled historical; do not delete old receipts.
- **F-016:** historical instructions about per-call recording arm and missing call controller were replaced as *active guidance* by dated current sections; the original text remains as documented history. Mac `aicalls-20261103-organizational-freeze-full-host-qa-v1` confirmed `ANTI_FRICTION=PASS` and `DOC_LINK_AND_FREEZE_AUDIT=PASS`. `BLOCKERS.md` preserves F-016 with scoped documentation VERIFIED.
- **F-036:** that same v1 gate exposed a real script defect, not a runtime failure: `scripts/verify_host.sh` rejected required root `BLOCKERS.md` (`repository_layout_failed=unexpected_top_level_entries`) because it was absent from `allowed_root_entries`. The fix adds `BLOCKERS.md` to the allowlist (`f640f141`), a standalone Python regression `scripts/tests/test_verify_host_layout.py` (`34febd0a`) and a matching note in `docs/REPOSITORY_LAYOUT.md`. No allowlist exclusions or policy guardrails were weakened.
- **Full gate redo submitted:** `aicalls-20261103-organizational-freeze-full-host-qa-v2`, pinned `873bfac74d1b56e97ea2b79062f1e2f5d1ae22d5`. It covers the regression, doc links, anti-friction, all four Android module test/lint suites, Android build/test APK, Python discovery and source/privacy scans via `scripts/verify_host.sh`. **Outcome must be read from Mac task-results**; the queued task is not a PASS until it reports `done/0`.
- **Known code hygiene / deferred refactor:** the project currently has `DialogueSkillBackendFactory` and `LocalDialogueSkillBackendFactory` with overlapping local classifier construction, plus CLIR-specific responses in `GateCHybridDialogueBackendFactory`. These are candidates for carefully tested consolidation in the **next** architecture sprint, not reasons to change phone behavior during this checkpoint.

## QA and final sign-off

Target: `python3 scripts/aicall_tools/diagnostics/audit_product_friction.py`; Python regression suites; `./gradlew --no-daemon :app:testDebugUnitTest :app:assembleDebug :app:lintDebug` and module-specific tests as available; optionally `scripts/verify_host.sh` as a separate read-only quality gate after examining its side effects. The exact final test receipt, pinned SHA and pass/fail must be **filled from the Mac Local Agent task result**, not assumed.

Freeze success criteria: source prewarm verified, no functional/code change to the installed S22 APK, canonical docs linked, no new artificial blockers, quality gate finished with actual receipt, and open physical outcomes explicitly retained. Next phase is a separate design implementation/benchmark phase, **not resumed until this checkpoint's QA result is read**.
