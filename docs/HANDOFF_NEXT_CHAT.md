# Handoff — Gate B text benchmark in progress

Date: 2026-09-19

Repository: `MichalMatu/android-ai-call-bridge`

Durable product branch: `main`

Local Agent control branch: `agent-control`

## Start here

In a new chat:

1. read fresh `AGENTS.md`;
2. read this file;
3. read `README.md`;
4. read `docs/ROADMAP.md`;
5. read `docs/ARCHITECTURE.md`;
6. read `docs/SECURITY_PRIVACY.md` for authority/privacy work;
7. read `docs/PHASE2D_FREEZE_2026-09-18.md` before any Samsung media change;
8. fetch fresh `main` and fresh `agent-control:.agent/status/daemon.json` before any write;
9. use the new chat's current Local Agent binding, never an old binding copied from history.

This handoff deliberately does not pin its own commit SHA. Always trust fresh `main`.

## What is already solid

Target: Samsung Galaxy S22+ `SM-S906B`.

Frozen cellular bridge: `PROVEN_S22`.

Local on-device Polish STT/TTS: `PROVEN_S22`.

Provider-neutral text turn with application-owned approval: `PROVEN_S22`.

Current phone-local text model:

```text
Qwen2.5-1.5B-Instruct Q4_K_M
alias qwen-phone-1.5b
/data/local/tmp/aicall-phone-llm/model-1.5b.gguf
SHA256 6a1a2eb6d15622bf3c96857206351ba97e1af16c30d7a74ee38970e434e9407e
loopback port 18115
```

Product-owned local model runtime start/identity verification/stop is physically proven.

Gate A pre-dial `READY_TO_DIAL` + prepared local text-call session boundary is `HOST_GREEN / PROVEN_S22` off-call.

Authoritative local model proof:

```text
.agent/results/qwen15b-verified-flow-orange-s22-retry-20260919-3720.json
```

Runtime lifecycle proof:

```text
.agent/results/local-phone-runtime-lifecycle-s22-20260919-1151.json
```

## Endpointing is done

Do not reintroduce or describe the old eight-second normal capture wait.

Current detector:

```text
20 ms frames
RMS threshold 600
minimum detected speech 200 ms
trailing silence 700 ms
hard safety maximum 8000 ms
```

Live Orange proof:

```text
.agent/results/live-endpointing-orange-s22-20260919-1214.json

endpoint_capture_ms=1460
endpoint_reason=trailing_silence
endpoint_speech_detected=true
estimated_end_of_speech_elapsed_ms=815
first_tx_elapsed_ms=14227
end_of_speech_to_first_tx_ms=13412
live_endpointing_orange_proven_s22=true
```

The remaining latency is downstream of capture; do not chase it by redesigning frozen Samsung media.

## OpenAI API work is deferred

The repository contains `OPENAI_TEXT` and OpenAI Realtime work. Preserve it, but it is not the current task because there is currently no paid API-token budget.

Do not spend the next chat trying to finish OpenAI API proof unless the user explicitly resumes that direction.

`LOCAL_MAC_LLM` also remains available but is not the main next comparison.

## Agreed execution plan

### A. READY_TO_DIAL + clean product orchestration — DONE / PROVEN_S22

Completed on 2026-09-19 without changing the frozen Samsung media path.

Before dialing:

```text
validate task/target
 -> STT ready
 -> TTS ready
 -> selected local model started
 -> exact model identity verified
 -> bounded warm-up succeeds
 -> scenario/plan data valid
 -> READY_TO_DIAL
 -> dial permitted
```

Do not build this by growing `LocalPhoneLlmLiveCallProbe`.

The new product orchestration reuses the frozen media boundary, `LocalSpeechTextPipeline`, `TextCallTurnController`, model runtime gate and existing authority model.

Implemented with `LocalTextCallReadinessCoordinator`, `AndroidLocalTextCallSpeechPreflight`, one-shot `PreparedLocalTextCall` and `LocalTextCallSession`. The session reuses `LocalSpeechTextPipeline` and does not own telephony media/endpointing.

Evidence:

```text
.agent/results/gate-a-readiness-red-20260919-1325.json
.agent/results/gate-a-readiness-green-20260919-1329.json
.agent/results/gate-a-full-host-20260919-1332.json
.agent/results/gate-a-offcall-ready-s22-20260919-1335.json
```

### B. Text-model quality benchmark — IN PROGRESS

The deterministic text-only comparison seam is now on `main`:

```text
benchmarks/text_model_suite_v1.json
scripts/text_model_benchmark.py
scripts/test_text_model_benchmark.py
```

The harness intentionally isolates model quality from STT/TTS variability. It fixes `temperature=0`, `seed=42`, `max_tokens=96`, verifies local `/props` identity and records responses/timings/findings. Full speech/live comparisons come only after a useful text-model candidate survives this gate.

Current evidence:

```text
Harness RED:        .agent/results/gate-b-benchmark-red-20260919-1405.json
Harness GREEN:      .agent/results/gate-b-benchmark-green-20260919-1410.json
Final HOST_GREEN:   .agent/results/gate-b-benchmark-final-host-20260919-1440.json
Qwen2.5 1.5B S22:  .agent/results/gate-b-qwen15b-baseline-s22-retry-20260919-1424.json
GPT-5.6 reference:  .agent/results/gate-b-gpt56-reference-verify-20260919-1443.json
```

Measured so far:

- Qwen2.5-1.5B Q4_K_M: `6/24` deterministic-safe (`25%`) across three repeats; median request `1465.535 ms`, p95 `2690.038 ms`, warm-up `3588.515 ms`; it incorrectly accepted purchase/appointment commitments;
- GPT-5.6 Sol interactive reference: `8/8` deterministic-safe on one reference pass. Result is in `benchmarks/results/gpt56_sol_interactive_reference_v1.json`. It is not a production backend and its ChatGPT serving latency/RAM are not comparable to phone-local llama.cpp;
- Qwen3-4B-Instruct-2507 Q4_K_M is already present on the S22 with verified SHA and can reach `/health`, but its first completion disconnected after about 76 seconds. The root cause is still unproven because the next diagnostic could not start after the S22 disappeared from direct USB ADB.

Do not infer OOM from the disconnect alone. Resume by capturing the 4B server log, process/RSS and LMKD/OOM evidence around its first completion. If this 4B quant is not viable, choose a lower-memory but meaningfully larger local candidate and run the identical frozen suite.

The ChatGPT relay remains controlled interactive benchmark infrastructure, never a production/background backend.

### C. CallPlan v1 — local LLM as language, not brain

Prepare structured task context before the call:

- goal;
- resolved target;
- authorized facts;
- constraints/preferences;
- presets;
- allowed actions;
- confirmation/escalation rules;
- completion criteria.

Build on existing `CallTask`/`CallWorkflow`/confirmation/commitment semantics rather than creating a second authority system.

Prefer deterministic answers/actions when possible. Use the local LLM for bounded classification/paraphrase. Unknown or low-confidence input asks for repetition or escalates rather than inventing facts.

### D. Multi-turn real tasks

Then prove repeated turns, IVR/recovery, structured outcome and real bounded tasks.

### E. Local audio models

Only after the text baseline is strong, test:

1. mobile local audio-to-text/understanding model;
2. true local speech-to-speech/full-duplex candidate.

Select candidates from current ecosystem at execution time and measure them on the S22 rather than assuming they fit.

## Architecture audit result

Do not perform broad cleanup merely because files are large.

Keep:

- frozen `CallMediaSessionCoordinator`;
- preserved/frozen `CallRealtimeSessionOrchestrator`;
- cohesive `LocalSpeechTextPipeline`;
- cohesive `TextCallTurnController`;
- proven local LLM runtime gate/service.

Gate A product ownership:

- `LocalTextCallReadinessCoordinator` owns fail-closed technical readiness, not workflow authority;
- `AndroidLocalTextCallSpeechPreflight` owns only STT/TTS capability proof;
- `PreparedLocalTextCall` is a one-shot ownership transfer of the warmed backend;
- `LocalTextCallSession` owns only the prepared local dialogue pipeline lifecycle;
- telephony media and endpointing remain outside the Gate A session boundary.

Growth hotspots:

- `DiagnosticProbeActivity` already dispatches many probes. Do not keep adding substantial routes; extract a diagnostic dispatcher when it is next touched.
- `LocalPhoneLlmLiveCallProbe` is a one-turn evidence harness. Do not turn it into the product multi-turn engine.
- `MainActivity` is UI/config only. Do not put session orchestration there.

This targeted separation is the clean starting point.

## Frozen media reminder

Phase 2D checkpoint:

```text
59b0505537a53306acdab6a2a66ca6eed2b3f1c0
```

Preserve RX/TX attribution/order, CALL_ASSISTANT/TELEPHONY_TX routing, mono internal PCM16, TX-boundary stereo, PFD ownership, sibling cleanup, TAKE OVER, heartbeat and `CallModeWatchdog`.

## Exact next task for a new chat

Continue with **Gate B** only:

> Fetch fresh `main` and `agent-control:.agent/status/daemon.json`. Do not rebuild the benchmark harness or rerun the proven 1.5B baseline. First check that S22 `RFCT70L7E8J` is again available over direct USB ADB. The Qwen3-4B-Instruct-2507 Q4_K_M candidate is already on the phone; reproduce its first-completion disconnect while capturing the llama-server log, process/RSS lifetime and LMKD/OOM evidence. Do not assume OOM before measuring it. If this candidate is not viable, choose a lower-memory but still meaningfully larger phone-local model and run the exact frozen `phone-call-text-v1` suite. Compare it against the existing Qwen2.5 1.5B and GPT-5.6 Sol evidence, then decide Gate B. Do not change frozen Samsung media and do not resume paid OpenAI API work.

Do not start Gate C/D/E until Gate B has comparable larger-local-model evidence.
