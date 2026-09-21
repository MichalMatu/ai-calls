# Roadmap

This is the authoritative execution plan. Historical experiment detail belongs in Git history and `.agent/results`, not in new status documents.

Evidence levels:

- `HOST_GREEN` — deterministic host tests/build/lint pass;
- `PROVEN_S22` — physically reproduced on the target Samsung S22+;
- `PRODUCT_READY` — proven, fail-safe and acceptable for normal use.

## Foundation

### Cellular media

Status: `DONE / PROVEN_S22 / FROZEN`

```text
RX: VOICE_DOWNLINK -> privileged helper -> transferred PFD -> app
TX: app -> transferred PFD -> CALL_ASSISTANT / TELEPHONY_TX -> cellular uplink
```

Frozen checkpoint:

```text
59b0505537a53306acdab6a2a66ca6eed2b3f1c0
```

Do not redesign this path while working on models, planning or dialogue.

### Local speech/text boundary

Status: `DONE / PROVEN_S22`

```text
telephony RX
 -> end-of-utterance detector
 -> local S22 STT
 -> TextCallAgentBackend / deterministic text decision source
 -> application-owned approval
 -> local S22 TTS
 -> telephony TX
```

`LocalSpeechTextPipeline` and `TextCallTurnController` are the established speech/text and complete-text approval boundaries. New planning work must reuse those boundaries rather than duplicate speech or approval logic.

### End-of-utterance / IVR turn boundary

Status: `SIGNALS PROVEN_S22 / PRODUCTION STATE MACHINE OPEN`

Real Orange testing proved that fixed capture duration and short trailing-silence thresholds are not correct IVR semantics:

- 700 ms and 1500 ms trailing silence cut Max mid-prompt;
- one complete greeting contained multiple `onEndOfSpeech` callbacks and multi-second internal pauses;
- a recognizer-end candidate with about 4.5 s hangover captured the complete prompt;
- partial STT exposed the greeting progressively and reached the complete semantic prompt before final endpoint.

Target state model:

```text
speech/begin -> cancel pending END
recognizer END -> candidate end
resumed speech/partial growth -> cancel candidate
stable later END + bounded hangover -> final turn
long watchdog -> safety only
```

Do not promote diagnostic timeout values into product semantics without physical evidence.

Representative evidence:

```text
.agent/results/chatgpt-orange-agent-skills-endpoint-v9b-20260921.json
.agent/results/chatgpt-orange-partial-stt-v12d-20260921.json
```

---

# Completed gates

## Gate A — clean product orchestration + READY_TO_DIAL

Status: `DONE / HOST_GREEN / PROVEN_S22` (off-call readiness)

Product ownership is separate from diagnostics and frozen Samsung media:

- `LocalTextCallReadinessCoordinator` owns fail-closed technical readiness;
- `AndroidLocalTextCallSpeechPreflight` proves local STT/TTS capability;
- `IdentityVerifiedLocalPhoneLlmBackend` owns local runtime + exact identity verification for the preserved local backend;
- successful preparation returns one-shot `PreparedLocalTextCall`;
- `LocalTextCallSession` consumes the prepared backend and reuses `LocalSpeechTextPipeline`;
- telephony media/endpointing remain outside that session boundary.

Evidence:

```text
.agent/results/gate-a-readiness-red-20260919-1325.json
.agent/results/gate-a-readiness-green-20260919-1329.json
.agent/results/gate-a-full-host-20260919-1332.json
.agent/results/gate-a-offcall-ready-s22-20260919-1335.json
```

## Gate B — text-brain benchmark

Status: `DONE / PROVEN_S22 / PHONE-LOCAL LLM PATH FROZEN`

Frozen benchmark infrastructure:

```text
benchmarks/text_model_suite_v1.json
scripts/text_model_benchmark.py
scripts/test_text_model_benchmark.py
```

Measured decision:

- Qwen2.5-1.5B Q4_K_M: `6/24` deterministic-safe, median request `1465.535 ms`, p95 `2690.038 ms`, warm-up `3588.515 ms`; too weak as the authority/reasoning brain;
- GPT-5.6 Sol interactive reference: `8/8` deterministic-safe on one reference pass; quality reference only, not a production backend and not latency/RAM-comparable to local llama.cpp;
- Qwen3-4B-Instruct-2507 Q4_K_M: `3/24` deterministic-safe, median `5427.353 ms`, p95 `180717.272 ms`, max `258447.910 ms`, warm-up `13936.281 ms`; severe memory/swap pressure and user-visible S22 instability/hanging.

Decision: do not spend the current phase testing nearby-size 2B/3B/4B general-purpose models on this S22. Preserve the runtime/harness only as experimental infrastructure.

Representative evidence:

```text
.agent/results/gate-b-benchmark-final-host-20260919-1440.json
.agent/results/gate-b-qwen15b-baseline-s22-retry-20260919-1424.json
.agent/results/gate-b-gpt56-reference-verify-20260919-1443.json
.agent/results/gate-b-qwen3-4b-full-benchmark-s22-retry-20260919-1500.json
.agent/results/gate-b-stop-qwen3-4b-s22-20260919-1505.json
```

### Interactive ChatGPT developer relay

Status: `DONE / HOST_GREEN / PROVEN_S22 / DEVELOPER-ONLY`

Purpose: compare strong interactive-model behavior against the same local S22 speech/media stack without turning ChatGPT into a production backend.

Proven:

- repeated 3-turn Orange flow completed end-to-end;
- cleanup deletes transient relay branches and restores call/media state;
- response delivery to Android is fast once a response exists;
- the dominant delay was waiting for the chat-side response;
- TX pacing accounts for time already spent inside a blocking pipe write while retaining a minimum 250 ms playback guard;
- final one-turn physical confirmation completed without TTS truncation.

Evidence:

```text
.agent/results/chatgpt-relay-orange-active-v4b.json
.agent/results/chatgpt-relay-full-host-tx-pacing-v2.json
.agent/results/chatgpt-relay-orange-pacing-confirm-v1.json
```

This relay is a closed benchmark checkpoint. Further relay optimization is not the default next task.

## Edge Gallery + Agent Skills feasibility checkpoint

Status: `FROZEN / PROVEN_S22 PARTIAL / NOT PRODUCT_READY`

This was deliberately separate from the frozen llama.cpp sweep. `Gemma-4-E2B-it` ran through a clean-upstream Google AI Edge Gallery/LiteRT development harness. Official Agent Skills were callable headlessly with proposal-only tools:

```text
say(text)
listenMore()
takeOver(reason)
```

No skill/tool owned dialing, DTMF, Samsung media, credentials, sensitive disclosure, purchase, activation, tariff change or commitment authority.

Positive findings:

- warm official Agent Skills `SAY` decisions repeated around `3.58-4.07 s` in v16;
- late-partial speculative inference on the real Orange greeting completed about `2.33 s` before the simulated final endpoint;
- cancellation was fast (`~8.7 ms` request, stale work released) and a clean post-cancel decision recovered;
- real Orange partial STT exposed the full semantic prompt before endpoint closure;
- the temporary v17 speculative integration built, unit-tested and installed cleanly while remaining diagnostic-only.

Final bounded live result:

- v18 made exactly one allowlisted information-only call to `510100100`;
- cellular RX and local STT succeeded and captured the complete Max prompt;
- Edge returned `backend_agent_network_IOException` before application approval, TTS or TX, so no generated reply was transmitted;
- cleanup returned the call/device state to idle.

Root cause / recovery evidence:

- v19 found the Edge process gone and port 8080 closed after v18;
- v20 historical logcat showed `com.google.aiedge.gallery` crashing while `LocalPhoneAgentRuntime.decide()` was under long monitor contention;
- after relaunch, loopback from the CallBridge UID worked and off-call inference succeeded, so basic transport was sound;
- the first post-relaunch decision took about `10.65 s`;
- Edge consumed about `2.58 GB` total PSS after inference, with swap pressure.

Decision: the current Edge Gallery / Gemma 4 E2B / Agent Skills runtime is not robust or lightweight enough for the live S22 product path. Do not add further probe hacks or repeat Orange calls by default. Reopen only after a materially improved runtime/model/hardware condition or an explicit user decision.

Evidence:

```text
.agent/results/chatgpt-edge-speculative-offcall-benchmark-v16-20260921.json
.agent/results/chatgpt-edge-speculative-live-build-v17-20260921.json
.agent/results/chatgpt-orange-speculative-live-v18-20260921.json
.agent/results/chatgpt-edge-live-transport-diagnosis-v19-20260921.json
.agent/results/chatgpt-edge-exit-reason-offcall-v20-20260921.json
```

---

# Active product gate

## Gate C — CallPlan v1: deterministic call brain with optional bounded language helper

Status: `ACTIVE / DETERMINISTIC HOST-POLICY CORE DONE / PRODUCT WIRING NEXT`

Goal: make common bounded turns deterministic and preserve all existing application-owned authority. A language helper may only propose a bounded match to already-authorized plan data.

### Authority ownership is frozen

- `CallTask` — immutable user/operator authority: task description/action/service plus `CallConstraints`, `CallPreferences` and `authorizedFacts`;
- `CallResolvedTarget` — one concrete resolved target. It does not widen the live dial allowlist;
- `CallWorkflow` — task progress, resolved-target state, concrete pending proposal, user-decision state and structured completion/failure;
- `CallConfirmationPolicy` — deterministic evaluation of one typed `CallProposal` against immutable task constraints/preferences;
- `CallCommitmentGate` — one-shot authorization bound to exactly one concrete proposal;
- application-owned output approval — final speech release remains fail-closed outside normal active negotiation and while commitment authority is pending.

`CallPlan` references these owners rather than copying or mutating authority data.

### Deterministic host-policy core — DONE / HOST_GREEN

Durable `CallPlan` behavior now includes:

```text
CallPlan
  -> existing CallTask reference
  -> existing CallResolvedTarget reference
  -> immutable known-fact rules
  -> immutable completion rules
  -> immutable typed proposal rules
  -> bounded repeat/escalation policy
```

`CallPlanEngine` consumes a **final** transcript plus an explicit prior-unknown count and produces only typed decisions:

```text
SAY
ASK_REPEAT
PROPOSAL
COMPLETE
TAKE_OVER
```

It does not dial, mutate the workflow, authorize commitments, approve proposals, access media/TTS/TX, or convert partial/model output into authority.

Host-green slices:

- v22/v23 — known authorized facts, missing-fact fail closed, ambiguity, immutability/redaction;
- v24/v25 — explicit stateless bounded repeat/escalation;
- v26/v27 — structured completion proposals with `CallWorkflow.complete` retained as terminal-state owner;
- v28/v29 — typed counterparty `CallProposal` routing with collisions fail closed;
- v30 — integrated regression proving plan decisions cannot bypass policy, exact commitment permit, or output approval;
- v31/v32 — bounded helper suggestion/validation by existing `ruleId` only.

Representative evidence:

```text
.agent/results/chatgpt-gate-c-callplan-green-v23-20260921.json
.agent/results/chatgpt-gate-c-callplan-bounded-fallback-green-v25-20260921.json
.agent/results/chatgpt-gate-c-callplan-completion-green-v27-20260921.json
.agent/results/chatgpt-gate-c-callplan-proposal-green-v29-20260921.json
.agent/results/chatgpt-gate-c-callplan-authority-regression-v30-20260921.json
.agent/results/chatgpt-gate-c-callplan-helper-green-v32-20260921.json
```

### Gate C matrix

| Case | Status |
| --- | --- |
| known question -> authorized fact | `GREEN` — exact value from existing `authorizedFacts` |
| referenced fact missing | `GREEN` — fail closed |
| unknown/ambiguous final transcript | `GREEN` — bounded `ASK_REPEAT` / `TAKE_OVER` |
| known counterparty offer | `GREEN` — typed predeclared `CallProposal` only |
| offer outside task policy | `GREEN` — existing policy returns `NEEDS_USER_DECISION` |
| commitment without exact permit | `GREEN` regression — blocked by existing one-shot gate |
| speech outside ACTIVE_NEGOTIATION or while permit pending | `GREEN` regression — dropped |
| target binding | preserved — plan has no dialing/allowlist authority |
| completion criterion | `GREEN` — structured outcome; workflow owns terminal mutation |
| optional helper unsupported item | `GREEN` — unknown/duplicate `ruleId` falls back/fails closed |
| mutable input collections | `GREEN` — defensive immutable copies |
| privacy rendering | `GREEN` — facts/target/outcome/proposal speech data redacted from ordinary rendering |

No S22/live-call gate was required for the deterministic core because it is pure host/data-policy behavior.

### Product wiring audit

Existing product path:

```text
LocalTextCallSession
  -> LocalSpeechTextPipeline
      -> final STT transcript
      -> TextCallTurnController
          -> TextCallAgentBackend.generate(...)
          -> TextOutputApprovalPolicy
      -> local TTS
```

`TextCallTurnController` is a deliberately narrow complete-backend-text -> application-approval contract. `LocalSpeechTextPipeline` owns speech lifecycle. Do not place CallPlan state/mutations inside either merely for convenience, and do not grow `MainActivity` or diagnostics into the product orchestrator.

### Next host-only wiring slice

Add a narrow product-owned `CallPlanTurnCoordinator` (or equivalently scoped name) outside media/speech ownership:

1. inputs: existing immutable `CallPlan`, existing `CallWorkflow`, final transcript, explicit prior-unknown count;
2. delegate classification to `CallPlanEngine`; do not duplicate matching;
3. `SAY`, `ASK_REPEAT`, `TAKE_OVER` remain structured results only — no direct TTS/TX;
4. `PROPOSAL` routes the exact typed proposal to `CallWorkflow.evaluateProposal(...)` and exposes the resulting `CallPolicyDecision`;
5. `COMPLETE` routes the exact outcome to `CallWorkflow.complete(...)`;
6. it must not authorize commitment, approve/reject pending proposals, dial, touch target allowlists, media or speech;
7. product routing must reject plan/workflow target mismatch before any workflow mutation;
8. invalid workflow state fails closed using the existing workflow state machine;
9. no model/backend fallback in this first wiring slice;
10. RED first, minimal GREEN, targeted tests + `bash scripts/verify_host.sh`;
11. no S22 gate and no live Orange call.

After this coordinator is host-green, a separate slice can connect its structured results to the existing text/session output boundary while retaining application-owned output approval before TTS/TX.

Gate C exit remains: common bounded turns work through the product session without general-purpose reasoning, missing user facts are never invented, and no helper/model can grant itself authority.

---

# Later gates

## Gate D — bounded multi-turn real tasks

Status: `AFTER C`

Prove repeated turns, IVR/recovery, unknown-intent escalation, interruption/cancellation rules, structured completion/outcome and explicit user-decision surfaces. Start with non-committing tasks; add real commitments only behind existing one-shot authorization.

## Gate E — local audio-model experiments

Status: `LATER`

Only after the text/product baseline is strong, compare current mobile-feasible audio-understanding and later true speech-to-speech/full-duplex candidates. Reuse the same authority and frozen media boundaries.

---

## Preserved but deferred paths

- `LOCAL_PHONE_LLM` — experimental infrastructure only on the current S22;
- `EDGE_GALLERY` — frozen experimental Gemma 4 E2B/LiteRT path; not current product work;
- `LOCAL_MAC_LLM` — retained provider option/experiment;
- `OPENAI_TEXT` — implementation preserved, paid API proof deferred;
- `OPENAI_REALTIME_AUDIO` — preserved/frozen;
- `LOCAL_REALTIME_AUDIO` — future integration point after Gate E feasibility work.

Do not put a standard OpenAI API key on Android.

## Completion discipline

Every behavior change:

1. starts from fresh `main` plus fresh Local Agent status/binding;
2. uses TDD where deterministic behavior is testable;
3. runs `bash scripts/verify_host.sh`;
4. runs only the physical gate required by changed hardware/OEM behavior;
5. updates the existing authoritative docs instead of creating new status files;
6. leaves `main` clean and Local Agent traffic on `agent-control`;
7. deletes temporary work branches after integration.
