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
 -> TextCallAgentBackend
 -> application-owned approval
 -> local S22 TTS
 -> telephony TX
```

`LocalSpeechTextPipeline` and `TextCallTurnController` are the established text-turn boundary. New work must reuse them rather than duplicate speech or approval logic.

### End-of-utterance detection

Status: `DONE / PROVEN_S22`

Current defaults: 20 ms frames, RMS threshold 600, minimum detected speech 200 ms, trailing silence 700 ms and 8000 ms hard safety cap.

Representative Orange evidence:

```text
.agent/results/live-endpointing-orange-s22-20260919-1214.json
```

The old normal fixed eight-second capture wait is gone.

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

Decision: do not spend the current phase testing nearby-size 2B/3B/4B general-purpose models on this S22. Preserve the local runtime/harness only as experimental infrastructure and reopen that path only with materially better hardware/runtime/model capability or an explicit user decision.

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

```text
live S22 call
 -> local STT
 -> transient GitHub relay request
 -> interactive ChatGPT response
 -> ADB delivery
 -> local S22 TTS
 -> cellular TX
```

Proven:

- repeated 3-turn Orange flow completed end-to-end;
- cleanup deletes transient relay branches and restores call/media state;
- response delivery to Android is fast once a response exists;
- the dominant delay in the interactive run was waiting for the chat-side response, not local STT/TTS/media;
- TX pacing accounts for time already spent inside a blocking pipe write while retaining a minimum 250 ms playback guard;
- final one-turn physical confirmation completed without TTS truncation.

Evidence:

```text
.agent/results/chatgpt-relay-orange-active-v4b.json
.agent/results/chatgpt-relay-full-host-tx-pacing-v2.json
.agent/results/chatgpt-relay-orange-pacing-confirm-v1.json
```

This relay is a closed benchmark checkpoint. Further relay optimization is not the default next task.

---

# Active gate

## Gate C — CallPlan v1: deterministic call brain with optional bounded language helper

Status: `NEXT / NOT STARTED`

Goal: prepare structured task context and authority before the call so useful calls do not depend on a general-purpose local LLM.

Do not create a second authority model. Reuse/extend the existing:

- `CallTask`;
- `CallConstraints`;
- `CallPreferences`;
- `authorizedFacts`;
- `CallWorkflow`;
- confirmation and commitment semantics.

A future plan should carry only what is needed for the call:

- resolved target metadata;
- explicit user goal;
- authorized facts;
- preferences/hard constraints;
- preset answers/actions;
- decisions requiring confirmation;
- fallback/escalation rules;
- completion criteria.

Authority rule: research may propose a phone number, but it may not silently widen the dialing allowlist. The actual live target remains explicitly operator-authorized under `AGENTS.md`.

Dialogue policy:

```text
known question + authorized fact -> deterministic answer
known choice + rule -> deterministic action
language variation -> deterministic patterns/classification first
unknown / low confidence -> ask to repeat or escalate
new commitment / sensitive disclosure -> existing application-owned authority gate
optional future model -> language/reasoning helper only, never authority
```

### Gate C entry task

Perform a **preimplementation audit first**:

1. read the current authority/workflow classes and tests;
2. define the narrowest responsibility split;
3. identify which existing types are reused and what new immutable plan/state data is actually required;
4. define the RED/GREEN test matrix;
5. do not implement until that split is clear.

Exit: common bounded turns do not require general-purpose reasoning, and no model/helper can invent missing user facts or grant itself authority.

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
- `EDGE_GALLERY` — explicit experimental Gemma 4 E2B/LiteRT phone-local provider; this is a materially different runtime path from the frozen llama.cpp model sweep;
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
