# Handoff — Gate C / deterministic PhraseMatrix fast path

Date: 2026-09-21

Repository: `MichalMatu/android-ai-call-bridge`

Durable branch: `main`

Local Agent control/evidence branch: `agent-control`

## Start here in the next chat

1. Read fresh `AGENTS.md`, this file, `README.md` and `docs/ROADMAP.md`.
2. Read `docs/ARCHITECTURE.md` / `docs/SECURITY_PRIVACY.md` before changing ownership or safety boundaries.
3. Read `docs/PHRASE_MATRIX_ENGINE_RESEARCH.md` before changing matcher-engine direction.
4. Fetch fresh `main` and fresh `agent-control:.agent/status/daemon.json`.
5. Trust only the fresh daemon binding.
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
- Edge Gallery / Gemma 4 E2B / official Agent Skills: `FROZEN / PROVEN_S22 PARTIAL / NOT PRODUCT_READY`;
- interactive ChatGPT relay: developer benchmark only.

## Gate C checkpoint

The deterministic `CallPlan v1` final-STT path plus the native PhraseMatrix fast-path baseline are `HOST_GREEN`.

Authority remains unchanged: `CallTask`, `CallResolvedTarget`, `CallWorkflow`, `CallConfirmationPolicy`, `CallCommitmentGate` and application-owned output approval remain the only authority owners. Models/helpers/skills/matchers are proposal- or classification-only.

### Completed product wiring

- CallPlan policy core, coordinator and prepared-call binding;
- exact deterministic candidates through the existing output approval;
- neutral `Generate` / `Candidate` / `Consumed` final-turn dispatcher;
- final-STT selector before backend generation;
- session-owned consecutive-unknown fallback state;
- native `PhraseMatrix` exact/alias classification with deterministic normalization and collision fail-closed behavior;
- matcher `ruleId` validation through the existing CallPlan coordinator;
- explicit optional previous-rule constraints;
- session-owned `previousValidatedRuleId`, sourced only from a validated `CallPlanDecision.ruleId()`;
- PhraseMatrix transport through readiness and `PreparedLocalTextCall`;
- Android readiness factory binding for optional `CallPlan + PhraseMatrix`;
- no-plan/default path remains ordinary `Generate`.

Key GREEN evidence:

```text
.agent/results/chatgpt-gate-c-phrase-matrix-baseline-green-v55-20260921.json
.agent/results/chatgpt-gate-c-suggested-rule-coordinator-green-v57-20260921.json
.agent/results/chatgpt-gate-c-phrase-router-green-v59-20260921.json
.agent/results/chatgpt-phrase-matrix-previous-context-green-v64-20260921.json
.agent/results/chatgpt-phrase-router-previous-context-green-v66-20260921.json
.agent/results/chatgpt-session-phrase-context-green-v68-20260921.json
.agent/results/chatgpt-readiness-phrase-matrix-green-v70-20260921.json
.agent/results/chatgpt-android-readiness-binding-green-v72-20260921.json
```

`v72` passed targeted regressions plus full `bash scripts/verify_host.sh`.

## Engine-selection decision

Use the native Kotlin `PhraseMatrix` as the production direction.

Measured host spikes:

```text
native PhraseMatrix:
  init ~11.85 ms
  average match ~0.815 us

RiveScript Java:
  init/sort ~46.56 ms
  average reply ~88.85 us
  debug APK delta +134,132 bytes
  adds slf4j-api
```

RiveScript proved Polish UTF-8 and `%Previous` behavior but also exposes arbitrary reply-text scripting and a wider capability surface. Do not add it to production now. ChatScript is reference material for pattern/topic/rejoinder design; its C++/JNI/data footprint is not justified for the current matcher. KStateMachine remains deferred.

These are host measurements, not `PROVEN_S22` performance evidence.

## Exact current product gap

The fast path is now wired end-to-end at the host/product boundary, but it is intentionally conservative: exact normalized phrases, explicit aliases and explicit previous-rule context only.

Open Gate C work:

1. add a small **bounded deterministic fuzzy/pattern layer** only where corpus tests justify it;
2. preserve fail-closed behavior for ambiguous, negated or multi-intent text;
3. measure matcher hit/false-positive/no-match rates and p50/p95 latency on a larger Polish ASR-like corpus;
4. then define a bounded LLM supervisor that may suggest only an existing ruleId and cannot release speech or mutate authority itself;
5. invalidate stale supervisor work on newer transcript/resumed speech/cancel/workflow change.

Do not turn fuzzy matching into free semantic guessing. Sensitive/committing actions never become generic shortcuts.

## Next exact engineering step

Start one host-only TDD slice for the bounded native matcher extension. Prefer the smallest rule data/algorithm that covers a concrete failing corpus case. RED must prove the intended safe match and at least one neighboring false-positive/negation case; GREEN must remain deterministic and classification-only.

After every behavior slice run targeted regressions and `bash scripts/verify_host.sh`. A host matcher slice does not require a physical call.

## Do not restart these paths by default

- Edge Gallery live-call experiments;
- old llama.cpp model sweep;
- paid OpenAI gates;
- Samsung media refactors;
- `privileged-helper/` work;
- new diagnostic probe growth.

Historical experiment detail belongs in Git history and `.agent/results`, not in new status documents.
