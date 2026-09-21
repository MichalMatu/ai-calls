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

Live Orange calls require explicit current-session operator authorization and the exact operator-defined allowlisted destination.

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

The deterministic `CallPlan v1` final-STT path, native PhraseMatrix fast path, bounded fuzzy extension and controlled Gate C live-call wiring are `HOST_GREEN`.

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
- bounded opt-in fuzzy matching: max one edit, same token count, minimum source length, exact/alias priority and ambiguity fail-closed;
- context-specific fuzzy ambiguity cannot fall back to a generic fuzzy rule;
- controlled Orange Gate C diagnostic scenario bound to exact allowlist target `510100100`;
- controlled scenario authorizes only the reviewed `Dzień dobry.` response and otherwise takes over/fails closed;
- Gate C sentinel backend forbids model generation and records `backend_generate_calls`;
- `LocalPhoneLlmLiveCallProbe` uses `LocalTextCallSession + CallPlan + PhraseMatrix` for Gate C;
- `DiagnosticProbeActivity` accepts explicit `gate_c_fast_path` and exact `live_call_target` extras;
- Python live runner validates exact allowlist, requires Gate C report markers and requires `backend_generate_calls=0`;
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
.agent/results/chatgpt-phrase-matrix-fuzzy-green-v75-20260921.json
.agent/results/chatgpt-phrase-matrix-fuzzy-context-green-v77-20260921.json
.agent/results/chatgpt-gate-c-live-safety-green-v82-20260921.json
.agent/results/chatgpt-gate-c-live-fastpath-green-v84-20260921.json
.agent/results/chatgpt-gate-c-live-report-green-v86-20260921.json
.agent/results/chatgpt-gate-c-host-final-v96-20260921.json
```

`v96` is the current host-final checkpoint: targeted Gate C live tests, Python runner tests, full `bash scripts/verify_host.sh` and `:app:assembleDebug` all pass. It emits `GATE_C_HOST_FINAL_GREEN=true` and `APK_READY_FOR_S22_INSTALL=true`.

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

The host/product path is ready for the first controlled physical Gate C proof. What is **not** yet proven on the current wiring is the complete physical chain:

```text
Orange cellular RX
 -> S22 telephony downlink
 -> local final STT
 -> PhraseMatrix
 -> existing CallPlan ruleId validation
 -> application-owned output approval
 -> local TTS
 -> S22 telephony uplink
```

The physical success report must prove all of the following at once:

- exact allowlisted target `510100100`;
- `gate_c_fast_path=true`;
- `gate_c_call_plan_bound=true`;
- reviewed response only: `approved_text=Dzień dobry.`;
- `backend_generate_calls=0`;
- nonblank STT transcript;
- nonzero TTS / telephony TX PCM;
- bounded trailing-silence endpointing;
- cleanup/hangup returns the phone to idle.

A no-match or changed Orange prompt must fail closed / take over. Do not broaden the matcher just to make a live test pass.

## Next exact engineering step — requires S22 connected

No more host-only implementation is required before the first controlled physical Gate C attempt.

When the S22 is available again:

1. fetch fresh `main` and daemon binding;
2. build `:app:assembleDebug` from fresh `main`;
3. require exact direct USB S22 `RFCT70L7E8J`, model `SM-S906B`, API 36 and cellular state `IDLE`;
4. install the fresh debug APK;
5. prove the Shizuku diagnostic probe is healthy;
6. confirm the Gate C probe intent contains `gate_c_fast_path=true` and `live_call_target=510100100` and contains no dial/tel action;
7. only with explicit current-session operator authorization, perform one bounded call to the exact allowlisted Orange target;
8. inspect the terminal report against the success conditions above and hang up/restore state in `finally`.

Important: `.agent/results/chatgpt-gate-c-s22-predial-v93-20260921.json` is **not** valid physical preflight evidence. The S22 was absent and the shell command did not use `set -e`, so later commands masked the failed assertion. Future physical tasks must use `set -euo pipefail` or otherwise preserve the first failure.

## After the first physical Gate C proof

Then continue Gate C quality work:

1. measure matcher hit/false-positive/no-match rates and p50/p95 latency on a larger Polish ASR-like corpus;
2. add reviewed phrases/aliases/fuzzy rules only from concrete corpus failures;
3. define a bounded LLM supervisor that may suggest only an existing ruleId and cannot release speech or mutate authority itself;
4. invalidate stale supervisor work on newer transcript/resumed speech/cancel/workflow change;
5. later expand to bounded multi-turn non-committing tasks.

Do not turn fuzzy matching into free semantic guessing. Sensitive/committing actions never become generic shortcuts.

## Do not restart these paths by default

- Edge Gallery live-call experiments;
- old llama.cpp model sweep;
- paid OpenAI gates;
- Samsung media refactors;
- `privileged-helper/` work;
- broad new diagnostic probe growth.

Historical experiment detail belongs in Git history and `.agent/results`, not in new status documents.
