# Handoff — Gate C / CallPlan v1 product wiring

Date: 2026-09-21

Repository: `MichalMatu/android-ai-call-bridge`

Durable branch: `main`

Local Agent control/evidence branch: `agent-control`

## Start here in the next chat

1. Read fresh `AGENTS.md`, this file, `README.md` and `docs/ROADMAP.md`.
2. Read `docs/ARCHITECTURE.md` / `docs/SECURITY_PRIVACY.md` before changing ownership or safety boundaries.
3. Read `docs/PHRASE_MATRIX_ENGINE_RESEARCH.md` before starting the planned Phrase/Intent Matrix engine-selection spike.
4. Fetch fresh `main` and fresh `agent-control:.agent/status/daemon.json`.
5. Trust only the current Bridge envelope plus fresh daemon binding for repo/binding identity.
6. Inspect the latest terminal Local Agent result before creating a successor task.
7. Keep `privileged-helper/` and the frozen Samsung media path untouched.

Live Orange calls require explicit current-session operator authorization and an exact operator-defined allowlisted destination.

## Frozen foundation

Target: Samsung Galaxy S22+ `SM-S906B`.

- cellular RX/TX and fail-safe media lifecycle: `DONE / PROVEN_S22 / FROZEN`;
- local Polish STT/TTS: `DONE / PROVEN_S22`;
- provider-neutral text pipeline + application-owned output approval: `DONE / PROVEN_S22`;
- Gate A readiness/prepared-call boundary: `DONE`;
- general-purpose phone-local llama.cpp route: frozen;
- Edge Gallery / Gemma 4 E2B / official Agent Skills checkpoint: `FROZEN / PROVEN_S22 PARTIAL / NOT PRODUCT_READY`;
- interactive ChatGPT relay: developer benchmark only.

## Gate C checkpoint

The deterministic `CallPlan v1` policy core and final-STT product wiring are `DONE / HOST_GREEN`.

Authority remains unchanged:

- `CallTask` owns immutable task/constraints/preferences/authorized facts;
- `CallResolvedTarget` does not grant or widen dial authority;
- `CallWorkflow` owns progress/proposals/user-decision/terminal outcome;
- `CallConfirmationPolicy` evaluates typed proposals;
- `CallCommitmentGate` owns exact one-shot commitment authorization;
- application-owned output approval remains mandatory before TTS/TX.

Models/helpers/skills/matchers are proposal- or classification-only.

### Completed final-STT wiring

1. `CallPlanTurnCoordinator` remains the sole CallPlan/workflow mutation boundary.
2. Prepared-call CallPlan binding keeps task/target/workflow identity fixed.
3. `TextCallTurnController.submitCandidateText(...)` routes exact deterministic text through the existing approval path without backend generation.
4. Session-owned consecutive-unknown state remains the only product fallback counter.
5. `TextCallFinalTurnDispatcher` provides `Generate`, exact `Candidate(text)` and `Consumed`; `Consumed` invalidates stale backend/controller work.
6. `CallPlanFinalTurnRouteMapper` maps:
   - `SAY` -> exact `Candidate(text)`;
   - `ASK_REPEAT`, `PROPOSAL`, `COMPLETE`, `TAKE_OVER` -> `Consumed` and preserves the exact structured `CallPlanTurnResult`.
7. `LocalSpeechTextPipeline` now has one optional neutral final-turn selector at final STT.
8. `LocalTextCallSession` owns plan selection and structured-result delivery; `localspeech` does not own CallPlan/workflow policy.
9. No-plan/default behavior remains `Generate`.
10. There is still exactly one `TextCallTurnController`, approval path and generation-cancellation lifecycle.

Evidence:

```text
.agent/results/chatgpt-gate-c-final-turn-mapper-red-v48c-20260921.json
.agent/results/chatgpt-gate-c-final-turn-mapper-green-v49-20260921.json
.agent/results/chatgpt-gate-c-final-stt-selector-red-v50-20260921.json
.agent/results/chatgpt-gate-c-final-stt-selector-green-v51-20260921.json
```

`v51` passed targeted regressions plus full `bash scripts/verify_host.sh`.

## Exact current product gap

Final STT no longer falls through unconditionally to backend generation. The next open product item is the Phrase / Intent Matrix engine-selection slice: common safe conversational turns need a tiny deterministic local classifier before bounded LLM supervision.

A current S22 probe uses `LocalTextCallSession`, but it is an off-call no-CallPlan/generative probe. A live Orange call with that harness would not prove the new plan-bound `Candidate` / `Consumed` path. Do not create physical evidence with a runner that does not exercise the changed boundary.

## Next exact engineering step

Start with one host-only TDD slice for the tiny native CallBridge `PhraseMatrix` baseline described in `docs/PHRASE_MATRIX_ENGINE_RESEARCH.md`.

Required first contract:

```text
normalized final transcript
 -> deterministic match against a bounded set of existing rule ids
 -> PhraseMatch(existingRuleId, confidence, matcherKind, optionalVariantClass)
```

Rules:

- classification only; no arbitrary generated/reply text;
- output rule id must already exist in the bound product data before it can influence CallPlan;
- fail closed on collisions, negation ambiguity and unknown text;
- deterministic replay for identical state + transcript;
- start with exact/alias matching and only then add bounded fuzzy logic if tests justify it;
- keep previous-turn/stage context non-authoritative and explicit;
- do not add third-party matcher dependencies in the baseline slice;
- after the baseline is green, run an isolated RiveScript Java compatibility/Polish/footprint comparison on the same corpus;
- keep ChatScript as a design/reference audit first and KStateMachine deferred unless stage state demonstrably needs it.

Live Orange calls are allowed only when the current operator explicitly authorizes them, an exact allowlisted target is bound, and the selected runner exercises the behavior under test. Host matcher work does not require a call by itself.

## Important follow-on idea — Phrase / Intent Matrix + LLM supervisor

This is now the active Gate C follow-on after final-STT integration.

Detailed engine/repository research, integration constraints and benchmark checklist are now preserved in:

```text
docs/PHRASE_MATRIX_ENGINE_RESEARCH.md
```

The dedicated note records the exact repositories to inspect and how to evaluate them:

- `aichaos/rivescript-java` — preferred first lightweight Java spike; MIT, simple trigger/topic/previous-turn machinery, but stale upstream/UTF-8/Android compatibility must be measured rather than assumed;
- `ChatScript/ChatScript` — mature source of pattern/topic/rejoinder ideas and possibly a native engine only after a minimal Android/NDK + path-by-path license/footprint audit;
- `KStateMachine/kstatemachine` — modern Kotlin state-machine candidate only if non-authority dialogue-stage state becomes genuinely complex;
- a tiny native CallBridge PhraseMatrix baseline used as the control implementation.

Do **not** simply copy/vendor those repositories. The research file contains explicit build, Polish UTF-8, APK/RAM/startup, matcher-quality, thread/cancellation, license and authority-boundary checks that must be completed before adoption.

The core product idea is to put a tiny deterministic local phrase/intent matrix in front of LLM reasoning so trivial conversational turns are answered immediately, while the LLM gets time to build/refresh context and only enters when a turn is ambiguous or important.

Target shape:

```text
final STT
 -> normalize
 -> exact phrase/intent matrix
 -> deterministic fuzzy match
 -> high confidence: exact CallPlan rule / reviewed response variant
 -> otherwise: bounded LLM supervisor suggests existing intent/ruleId
 -> CallPlan/workflow/output approval
 -> TTS
```

Examples worth covering first: `GREETING`, `ACK`, `CONFIRM`, `REJECT`, `ASK_REPEAT`, `WAIT`, common identity/purpose questions backed by authorized facts, and task-specific known rules.

Naturalness should come from a small reviewed variant bank, not free-form generation. A temperature-like setting may widen/narrow the eligible variant set, but the actual choice should remain deterministic/testable (for example stable seed from call id + turn index + intent).

The strategic point is important: **the matrix gives the LLM breathing room**. The phone can answer `Dzień dobry`, acknowledgement, repeat/wait and similar turns locally in effectively lookup time while the model observes bounded context, warms up or performs a shadow classification. Model capacity is then reserved for key moments rather than racing every turn.

Safety/ownership rules:

- matrix output still goes through CallPlan/workflow/output approval;
- LLM remains a supervisor/proposal layer and may only suggest existing intent/rule/ruleId;
- it cannot invent facts, targets, commitments or authority;
- speculative/shadow LLM output is quarantined and invalidated by newer transcript, resumed speech, cancellation or workflow change;
- it cannot retroactively replace a deterministic response already approved/released;
- sensitive/committing actions never become generic matrix shortcuts.

Useful metrics: matrix hit rate, fuzzy false-match rate, LLM invocation rate, deterministic vs supervised p50/p95 latency, takeover rate, and how often important turns required supervisor help.

Implement it only after the clean final-STT/CallPlan selector wiring, so it reuses the same authority and cancellation path instead of becoming a parallel dialogue system.

## Do not restart these paths by default

- Edge Gallery live-call experiments;
- old llama.cpp model sweep;
- paid OpenAI gates;
- Samsung media refactors;
- `privileged-helper/` work;
- new diagnostic probe growth.

Historical experiment detail belongs in Git history and `.agent/results`, not in new handoff documents.
