# Roadmap

This is the authoritative execution plan. Historical experiments belong in Git history and `.agent/results`, not in new status documents.

Evidence levels:

- `HOST_GREEN` — deterministic host tests/build/lint pass;
- `PROVEN_S22` — physically reproduced on the target Samsung S22+;
- `PRODUCT_READY` — proven, fail-safe and acceptable for normal use.

## Foundation — complete and frozen where noted

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

### Local speech and text boundary

Status: `DONE / PROVEN_S22`

Proven:

```text
telephony RX
 -> local S22 STT
 -> TextCallAgentBackend
 -> application-owned approval
 -> local S22 TTS
 -> telephony TX
```

`LocalSpeechTextPipeline` and `TextCallTurnController` already provide the conservative complete-turn boundary. New work should reuse them rather than duplicate STT/TTS or approval logic.

### Local phone LLM

Status: `DONE / ONE-TURN PROVEN_S22`

Current baseline:

```text
Qwen2.5-1.5B-Instruct Q4_K_M
alias qwen-phone-1.5b
/data/local/tmp/aicall-phone-llm/model-1.5b.gguf
SHA256 6a1a2eb6d15622bf3c96857206351ba97e1af16c30d7a74ee38970e434e9407e
```

The app owns server start/stop/recovery and verifies exact model identity before inference. A stale server must never be accepted from `/health` alone.

### End-of-utterance detection

Status: `DONE / PROVEN_S22`

Normal live turns no longer wait a fixed eight seconds. Current defaults are 20 ms frames, RMS threshold 600, minimum detected speech 200 ms, trailing silence 700 ms and an 8000 ms hard safety cap.

Physical Orange evidence:

```text
endpoint_capture_ms=1460
endpoint_reason=trailing_silence
end_of_speech_to_first_tx_ms=13412
live_endpointing_orange_proven_s22=true
```

The remaining latency baseline is downstream of speech capture and is useful for model comparisons.

---

# Active local-first plan

Paid OpenAI API work is intentionally deferred. Do not make it the next task unless the user explicitly resumes it.

## Gate A — clean product orchestration + READY_TO_DIAL

Status: `DONE / HOST_GREEN / PROVEN_S22` (off-call readiness)

Goal: before any model-quality live comparison, make preparation an explicit product state rather than a side effect of the first turn.

Required readiness sequence:

```text
load task / benchmark scenario
 -> validate authority and dial target
 -> prepare local STT
 -> prepare local TTS
 -> start selected local LLM runtime
 -> verify exact model identity
 -> warm model with bounded prompt
 -> verify resources are usable
 -> READY_TO_DIAL
 -> only then permit dial
```

Requirements:

1. introduce a dedicated product-owned local text-call orchestration boundary; do not extend `LocalPhoneLlmLiveCallProbe` into the product engine;
2. keep the frozen media coordinator unchanged unless a concrete regression requires otherwise;
3. expose explicit readiness failure reasons and fail closed;
4. keep model runtime/readiness separate from dialogue policy;
5. add deterministic host tests first, then an off-call S22 readiness proof;
6. only after readiness is proven should an automated allowlisted live benchmark dial.

Architecture cleanup attached to this gate:

- `DiagnosticProbeActivity` stays diagnostic; if new probes would add more routing branches, extract a dedicated diagnostic dispatcher rather than growing the Activity;
- `MainActivity` remains UI/configuration and must not become the call-session orchestrator;
- `CallMediaSessionCoordinator` and the preserved Realtime state machines are not split merely because they are large;
- new product classes should be cohesive around readiness, session ownership, planning or dialogue policy.

Exit: selected local backend, STT, TTS and scenario are proven ready before dialing.

Implemented boundary:

- `LocalTextCallReadinessCoordinator` validates workflow/target authority, local speech readiness and bounded backend warm-up;
- `AndroidLocalTextCallSpeechPreflight` proves on-device STT plus a non-network TTS voice before dial;
- the existing `IdentityVerifiedLocalPhoneLlmBackend` remains the runtime + exact-identity authority before warm-up inference;
- successful preparation returns one-shot `PreparedLocalTextCall`;
- `LocalTextCallSession` consumes that prepared backend and delegates dialogue turns to the existing `LocalSpeechTextPipeline`;
- telephony media and endpointing remain outside this new layer, so the frozen Samsung path was not changed.

Evidence:

```text
TDD RED:    .agent/results/gate-a-readiness-red-20260919-1325.json
TDD GREEN:  .agent/results/gate-a-readiness-green-20260919-1329.json
HOST_GREEN: .agent/results/gate-a-full-host-20260919-1332.json
PROVEN_S22: .agent/results/gate-a-offcall-ready-s22-20260919-1335.json
```

## Gate B — text-brain benchmark

Status: `DONE / PROVEN_S22 / PHONE-LOCAL LLM PATH FROZEN`

Goal: isolate model quality and latency while holding telephony, endpointing, STT, TTS and task constant.

Compare:

1. current Qwen2.5 1.5B baseline;
2. one larger feasible phone-local text model, selected only after checking current llama.cpp/Android compatibility;
3. GPT-5.6 Sol through the current ChatGPT conversation using Local Agent/ADB as a developer benchmark relay.

The ChatGPT relay is test infrastructure, not a production autonomous backend. It requires an active interactive chat and must not be described as a background service. Raw call audio need not leave the phone; the relay can operate on bounded STT text and return bounded response text for local TTS.

Current Gate B evidence (2026-09-19):

- frozen suite `benchmarks/text_model_suite_v1.json` contains 8 identical Polish phone-call transcript scenarios;
- deterministic host harness `scripts/text_model_benchmark.py` fixes generation to `temperature=0`, `seed=42`, `max_tokens=96`, verifies local model identity through `/props`, records complete responses and wall/llama.cpp timings, and applies conservative deterministic safety checks;
- harness TDD + final host verification: `.agent/results/gate-b-benchmark-red-20260919-1405.json`, `.agent/results/gate-b-benchmark-green-20260919-1410.json`, `.agent/results/gate-b-benchmark-final-host-20260919-1440.json`;
- Qwen2.5 1.5B S22 baseline: 24 samples (8 scenarios x 3), only 6/24 deterministic-safe (`25%`), median model-request wall time `1465.535 ms`, p95 `2690.038 ms`, warm-up `3588.515 ms`, and measured server `VmHWM=2087376 kB`; it incorrectly accepted purchase/appointment commitments, so it is not acceptable as an authority/reasoning brain. Evidence: `.agent/results/gate-b-qwen15b-baseline-s22-retry-20260919-1424.json`;
- GPT-5.6 Sol interactive reference: 8/8 deterministic-safe on one reference pass using the same frozen transcripts. This is quality/reference evidence only; interactive ChatGPT serving latency and RAM are deliberately not compared with phone-local inference. Durable result: `benchmarks/results/gpt56_sol_interactive_reference_v1.json`; verification: `.agent/results/gate-b-gpt56-reference-verify-20260919-1443.json`;
- Qwen3-4B-Instruct-2507 Q4_K_M was physically proven capable of loading and completing requests on the S22, so the earlier disconnect was not simple proof of incompatibility. The full 24-sample retry produced only 3/24 deterministic-safe (`12.5%`), median wall time `5427.353 ms`, p95 `180717.272 ms`, maximum `258447.910 ms`, and warm-up `13936.281 ms`; sustained execution caused severe memory/swap pressure and user-visible phone instability/hanging. Evidence: `.agent/results/gate-b-qwen3-4b-crash-diagnostic-s22-connected-20260919-1452.json`, `.agent/results/gate-b-qwen3-4b-full-benchmark-s22-retry-20260919-1500.json`;
- cleanup confirmed no remaining `llama-server`, removed the ADB forward, preserved call state 0 and measured battery temperature `39.7 C`. Evidence: `.agent/results/gate-b-stop-qwen3-4b-s22-20260919-1505.json`.

Gate B decision: freeze the general-purpose phone-local LLM path on the current Samsung S22+. The 1.5B model is too weak to be the call brain; the 4B model is both weaker on this benchmark and operationally unacceptable on this device. Do not spend the current phase trying additional 2B/3B/4B phone-local models. Preserve the proven runtime and harness as experimental infrastructure only. Reopen this path only after materially better hardware/runtime/model capability or an explicit user decision.

Use identical benchmark scenarios and record at least:

```text
task success / failure
transcript
model response
unsafe or invented facts
fallback / escalation count
model inference latency
end-of-speech -> first TX
model load + warm-up time
RAM high-water mark
thermal/resource observations for larger local models
```

Run off-call model benchmarks before controlled live calls. Do not assume a 3B/7B-class model is useful merely because it fits RAM.

Exit: complete. We know the quality/latency/resource gap well enough to stop the current S22 local-LLM route.

## Gate C — CallPlan v1: deterministic call brain with optional bounded language helper

Status: `NEXT / NOT STARTED`

Goal: move research, task interpretation and authority into a structured plan prepared before the call, so useful calls do not depend on a general-purpose local LLM.

Conceptual flow:

```text
user goal
 -> pre-call research/planning
 -> CallPlan v1
 -> deterministic conversation state
 -> presets / authorized facts / allowed actions
 -> local LLM only where language understanding or phrasing is useful
```

Do not duplicate the existing domain model blindly. `CallPlan v1` should extend/reuse `CallTask`, `CallConstraints`, `CallPreferences`, `authorizedFacts`, `CallWorkflow`, confirmation and commitment semantics.

A plan should be able to carry:

- resolved target metadata;
- explicit user goal;
- authorized facts needed during the call;
- preferences and hard constraints;
- preset answers;
- allowed conversational actions;
- decisions requiring user confirmation;
- fallback/escalation rules;
- completion criteria.

Important authority rule: research may propose a phone number, but it may not silently widen the automated dialing allowlist. The operator/user must explicitly authorize the actual live target under `AGENTS.md`.

Dialogue policy should prefer deterministic handling:

```text
known question + authorized fact -> preset/deterministic answer
known choice + rule -> deterministic action
language variation -> deterministic patterns/classification first; optional bounded helper only if later proven useful
low confidence / unknown request -> ask to repeat or escalate
new commitment -> application-owned confirmation/commitment gate
```

Exit: the local model cannot invent missing user facts or grant itself authority, and common task turns do not require general-purpose reasoning.

## Gate D — multi-turn real tasks

Status: `AFTER C`

Prove multi-turn behavior on bounded, explicitly authorized scenarios such as appointment inquiry/registration, opening-hours inquiry or controlled IVR.

Add:

- conversation history/state ownership;
- repeated endpointing across turns;
- interruption/cancellation rules;
- no-speech and low-confidence recovery;
- unknown-intent escalation;
- explicit user-decision surfaces;
- structured completion/outcome;
- latency accounting per turn.

Start with non-committing tasks, then carefully add real commitments behind existing one-shot authorization.

Exit: repeated turns finish or escalate deterministically without leaving AI audio active.

## Gate E — local audio-model experiments

Status: `LATER`

Only compare audio models after the text pipeline is a strong measured baseline.

Two separate experiments:

1. audio-capable local model that consumes caller audio and returns text/intent, compared against Android STT + text model;
2. true local speech-to-speech/full-duplex model that consumes audio and produces audio.

Candidate families change quickly; select them at execution time based on current mobile/runtime support. Do not assume desktop/GPU demos fit the S22.

Reuse the same authority, TAKE OVER, telephony generation and output-safety boundaries. A speech-to-speech model does not get extra dialing or commitment authority merely because it owns audio.

Exit: keep an audio model only if measured quality/latency/resource behavior beats or materially simplifies the established text pipeline.

---

## Preserved but deferred paths

### `LOCAL_MAC_LLM`

Retained as a provider option and useful fallback/experiment. It is not the current comparison priority unless needed for tooling.

### `OPENAI_TEXT`

Implementation work exists, but paid OpenAI API proof is deferred because there is currently no API-token budget. Preserve the code and credential boundary; do not put a standard OpenAI key on Android.

### `OPENAI_REALTIME_AUDIO`

Preserved/frozen. Do not resume API-dependent physical gates unless explicitly requested.

### `LOCAL_REALTIME_AUDIO`

Future integration point for a local audio-capable/speech-to-speech engine after Gate E feasibility work.

## Completion discipline

Every behavior change:

1. starts from a fresh `main`;
2. uses TDD where deterministic behavior is testable;
3. runs `bash scripts/verify_host.sh`;
4. runs only the physical gate required by the changed hardware/OEM behavior;
5. updates the existing authoritative docs rather than creating another status file;
6. leaves `main` clean and Local Agent traffic on `agent-control`.
