# Android AI Call Bridge

Android prototype for bridging an ordinary cellular call on one stock Samsung phone to selectable AI engines without external audio hardware.

Target device: Samsung Galaxy S22+ `SM-S906B`, Android 16 / API 36 / One UI 8.

## Proven baseline

Current foundation:

- cellular RX/TX and fail-safe media lifecycle: `DONE / PROVEN_S22 / FROZEN`;
- local Polish STT/TTS: `DONE / PROVEN_S22`;
- provider-neutral text-agent pipeline with application-owned output approval: `DONE / PROVEN_S22`;
- product-owned local LLM start/identity-check/stop lifecycle: `DONE / PROVEN_S22`;
- pre-dial local `READY_TO_DIAL` + prepared local text-call session boundary: `DONE / PROVEN_S22` (off-call readiness);
- live end-of-utterance detection: `DONE / PROVEN_S22`;
- interactive ChatGPT developer relay over bounded STT text/local TTS: `DONE / PROVEN_S22` as test infrastructure only.

The old fixed 8-second capture wait is gone, but real Orange evidence also proved that a short fixed trailing-silence threshold is not a correct IVR turn boundary. Max can pause for several seconds inside one prompt and Android emits multiple `onEndOfSpeech` callbacks. The current experimental direction is an end-candidate state machine: resumed speech cancels the candidate; a later recognizer end plus bounded hangover closes the turn; a long watchdog is safety-only. Partial STT is now physically proven useful for preparing the next decision before the final endpoint.

The general-purpose phone-local LLM route is currently **frozen**. Qwen2.5-1.5B is operationally usable but too weak as the call brain, while Qwen3-4B caused unacceptable latency/resource pressure and user-visible S22 instability. The runtime and benchmark harness remain available for future hardware/model experiments, but they are not the current product direction.

Representative evidence:

```text
.agent/results/chatgpt-orange-agent-skills-endpoint-v9b-20260921.json
.agent/results/chatgpt-edge-agent-full-greeting-latency-v10-20260921.json
.agent/results/chatgpt-edge-agent-session-reset-v11-20260921.json
.agent/results/chatgpt-orange-partial-stt-v12d-20260921.json
.agent/results/live-endpointing-orange-s22-20260919-1214.json
.agent/results/gate-a-offcall-ready-s22-20260919-1335.json
.agent/results/gate-b-qwen15b-baseline-s22-retry-20260919-1424.json
.agent/results/gate-b-qwen3-4b-full-benchmark-s22-retry-20260919-1500.json
.agent/results/chatgpt-relay-orange-active-v4b.json
.agent/results/chatgpt-relay-full-host-tx-pacing-v2.json
.agent/results/chatgpt-relay-orange-pacing-confirm-v1.json
```

## Architecture

```text
explicit user task / authority
          |
          v
 deterministic workflow / future CallPlan
          |
          v
   telephony RX (frozen)
          |
          v
  end-of-utterance detector
          |
          v
     local S22 STT
          |
          v
 TextCallAgentBackend
          |
          v
 application-owned approval / commitment rules
          |
          v
     local S22 TTS
          |
          v
   telephony TX (frozen)
```

The telephony transport, speech layer, reasoning provider and authority model are separate boundaries. No model or developer relay may own Samsung media behavior, dialing authority, sensitive-data authority or commitment authority.

## Interactive ChatGPT relay

The repository contains a developer-only relay that can:

```text
live S22 call -> local STT -> transient GitHub relay request
interactive ChatGPT response -> ADB delivery -> local S22 TTS -> cellular TX
```

This path is physically proven for repeated turns on Orange. It is useful for comparing strong-model behavior against the local stack, but it is **not** a production/background backend: it requires an active interactive ChatGPT session. Raw relay text is kept only on a transient relay branch and that branch is deleted during cleanup.

The latest physical pacing confirmation also records blocking TX-write time separately from the remaining playback hold, so time already spent inside a blocking write is not double-counted. The hold still keeps a 250 ms guard and never intentionally truncates PCM.

## Active execution plan

Paid OpenAI API work and the old llama.cpp phone-model sweep are deferred. The current experimental checkpoint is `EDGE_GALLERY` + Gemma 4 E2B + the official Agent Skills runtime.

What is already proven on the S22:

- Edge Gallery/Gemma local inference through the separate loopback provider;
- a real Orange cellular STT -> Gemma -> approval -> TTS -> TX turn;
- a spoken information-only IVR selection ("oferta na kartę") that Orange classified into the SIM branch;
- official Agent Skills runtime use headlessly, with a narrow phone skill producing application-reviewed `SAY` proposals;
- rich partial STT during the complete Orange greeting.

The current blocker is latency/turn timing, not basic connectivity. ReAct/load-skill decisions were too slow for IVR. Warm already-loaded sessions can produce tool decisions in a few seconds, while resetting/reinitializing at the wrong point adds large delay. The next experiment prepares the skill/session and may run cancelable speculative inference from stable partial STT while the counterparty is still speaking; output remains quarantined until the final endpoint and policy gate.

After this feasibility checkpoint, **Gate C — `CallPlan v1`** remains the productization gate: reuse existing task/authority types, make known turns deterministic, fail closed on unknown/low-confidence input, and keep any model/skill as a bounded language/action-proposal helper rather than authority.

See `docs/ROADMAP.md` for gates and `docs/HANDOFF_NEXT_CHAT.md` for the exact continuation point.

## Architecture discipline

Do not grow product behavior inside diagnostic probes.

`LocalPhoneLlmLiveCallProbe` and the ChatGPT relay probe are evidence tooling, not the future product session. `DiagnosticProbeActivity` remains a diagnostic entry point. New call planning and multi-turn ownership must live in dedicated product code and reuse the existing media/speech/backend/authority boundaries.

Large safety state machines are not split merely to reduce line count. In particular, the frozen media coordinator and preserved Realtime state machines should not be refactored without a concrete behavioral reason and matching regression evidence.

## Deferred providers

The repository preserves:

- `LOCAL_PHONE_LLM` as experimental infrastructure;
- `OPENAI_TEXT`;
- `OPENAI_REALTIME_AUDIO`;
- `LOCAL_MAC_LLM`;
- future `LOCAL_REALTIME_AUDIO`.

Paid OpenAI paths are not current execution priorities. Do not place a standard OpenAI API key on Android and do not resume API-dependent gates unless explicitly requested.

## Frozen Samsung path

Before changing Samsung call-media internals, read `docs/PHASE2D_FREEZE_2026-09-18.md`.

Preserve the proven RX/TX attribution/order, CALL_ASSISTANT/TELEPHONY_TX route, internal mono PCM16LE, TX-boundary-only stereo, transferred-PFD ownership, sibling cleanup, TAKE OVER, heartbeat and `CallModeWatchdog`.

## Verification

Canonical host gate:

```bash
bash scripts/verify_host.sh
```

`HOST_GREEN` is not `PROVEN_S22`. OEM/audio/model behavior that depends on the target phone requires physical S22 evidence.

Durable code and current docs live on `main`; Local Agent task/result traffic stays on `agent-control`.
