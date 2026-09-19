# Handoff — Gate A READY_TO_DIAL proven

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

### B. Text-model quality benchmark — NEXT

Gate A is complete. Next:

- current Qwen2.5 1.5B;
- one larger feasible local phone model;
- GPT-5.6 Sol through this interactive ChatGPT conversation using Local Agent/ADB as a developer relay.

Use the same scenarios, STT/TTS, endpointing and telephony path. Measure quality, hallucinations, latency, load/warm-up, RAM and resources.

The ChatGPT relay is a controlled interactive benchmark, not a production/background backend.

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

> Build the deterministic text-model benchmark harness on top of the proven Gate A readiness/session seam. Keep telephony, endpointing, STT/TTS, authority and scenario inputs fixed. Benchmark the current Qwen2.5 1.5B first off-call, then one larger feasible phone-local model, and use GPT-5.6 Sol through this interactive ChatGPT + Local Agent/ADB relay as the strong reference. Measure response quality, invented/unsafe facts, fallback/escalation, inference latency, load/warm-up, RAM and thermal/resource behavior. Do not change the frozen Samsung media path and do not resume paid OpenAI API work.

Do not start Gate C/D/E until Gate B has comparable evidence.
