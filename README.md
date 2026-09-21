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
- live end-of-utterance signals: `PROVEN_S22`, with the production state machine still to be integrated outside diagnostics;
- interactive ChatGPT developer relay over bounded STT text/local TTS: `DONE / PROVEN_S22` as test infrastructure only.

The old fixed 8-second capture wait is gone, and real Orange evidence proved that a short fixed trailing-silence threshold is not a correct IVR turn boundary. Max can pause for several seconds inside one prompt and Android emits multiple `onEndOfSpeech` callbacks. The proven direction is an end-candidate state machine: resumed speech cancels the candidate; a later recognizer end plus bounded hangover closes the turn; a long watchdog is safety-only. Partial STT is physically proven useful for bounded preparation, but partial/model output never creates authority or reaches TX by itself.

The general-purpose phone-local LLM route is frozen. Qwen2.5-1.5B was too weak as the call brain, while Qwen3-4B caused unacceptable latency/resource pressure and user-visible S22 instability. The runtime and benchmark harness remain available for future hardware/model experiments, but they are not the current product direction.

The separate Google AI Edge Gallery / Gemma 4 E2B / official Agent Skills checkpoint is also now frozen. It proved useful capabilities — official headless Agent Skills, real Orange partial STT, fast warm decisions and cancelable speculative inference — but the final bounded live test exposed an Edge process crash during `LocalPhoneAgentRuntime.decide()`. After relaunch, the first decision was about 10.65 seconds and the Edge process used about 2.58 GB total PSS. The live S22 path therefore remains `NOT PRODUCT_READY`; no further Orange calls or Edge probe hacks are the default plan.

Representative evidence:

```text
.agent/results/chatgpt-edge-speculative-offcall-benchmark-v16-20260921.json
.agent/results/chatgpt-edge-speculative-live-build-v17-20260921.json
.agent/results/chatgpt-orange-speculative-live-v18-20260921.json
.agent/results/chatgpt-edge-live-transport-diagnosis-v19-20260921.json
.agent/results/chatgpt-edge-exit-reason-offcall-v20-20260921.json
.agent/results/live-endpointing-orange-s22-20260919-1214.json
.agent/results/gate-a-offcall-ready-s22-20260919-1335.json
.agent/results/gate-b-qwen15b-baseline-s22-retry-20260919-1424.json
.agent/results/gate-b-qwen3-4b-full-benchmark-s22-retry-20260919-1500.json
.agent/results/chatgpt-relay-orange-active-v4b.json
```

## Architecture

```text
explicit user task / authority
          |
          v
 deterministic CallPlan / workflow
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
 deterministic rule engine
   + optional bounded language helper
          |
          v
 application-owned proposal / confirmation / commitment / output gates
          |
          v
     local S22 TTS
          |
          v
   telephony TX (frozen)
```

The telephony transport, speech layer, language/reasoning helper and authority model are separate boundaries. No model, Agent Skill or developer relay may own Samsung media behavior, dialing authority, sensitive-data authority or commitment authority.

## Active execution plan — Gate C / CallPlan v1

The active productization gate is now `CallPlan v1`: make common known turns deterministic and use a model only as an optional bounded language helper.

The preimplementation audit found that the current authority model should be reused rather than replaced:

- `CallTask` remains the immutable source of user-authorized task data, hard constraints, soft preferences and `authorizedFacts`;
- `CallResolvedTarget` represents the resolved concrete target without granting permission to widen any runtime dial allowlist;
- `CallWorkflow` remains the owner of task progress, pending proposals, user-decision state and terminal outcome;
- `CallConfirmationPolicy` remains the deterministic evaluator of concrete counterparty proposals;
- `CallCommitmentGate` remains the one-shot permit bound to one exact proposal;
- final speech still passes application-owned approval before TTS/TX.

`CallPlan` must therefore be a narrow immutable execution context, not another authority store. It should reference the existing `CallTask` and resolved target, carry deterministic known-turn rules, completion criteria and bounded repeat/escalation policy. For authorized-fact answers, rules store a fact key and resolve the value from `CallTask.authorizedFacts` at decision time; a missing fact fails closed rather than being guessed.

The first implementation slice is host-only and TDD-first: authorized-fact answer, missing-fact fail-closed behavior, unknown-intent fallback, immutability and redacted rendering. It must not wire telephony, STT/TTS, diagnostics or a model yet. See `docs/ROADMAP.md` for the RED/GREEN matrix and `docs/HANDOFF_NEXT_CHAT.md` for the exact continuation point.

## Interactive ChatGPT relay

The repository contains a developer-only relay that can:

```text
live S22 call -> local STT -> transient GitHub relay request
interactive ChatGPT response -> ADB delivery -> local S22 TTS -> cellular TX
```

This path is physically proven for repeated turns on Orange. It is useful for comparing strong-model behavior against the local stack, but it is **not** a production/background backend: it requires an active interactive ChatGPT session. Raw relay text is kept only on a transient relay branch and that branch is deleted during cleanup.

## Architecture discipline

Do not grow product behavior inside diagnostic probes.

`LocalPhoneLlmLiveCallProbe`, Edge diagnostic harnesses and the ChatGPT relay probe are evidence tooling, not the future product session. `DiagnosticProbeActivity` remains a diagnostic entry point. New call planning and multi-turn ownership must live in dedicated product code and reuse the existing media/speech/backend/authority boundaries.

Large safety state machines are not split merely to reduce line count. In particular, the frozen media coordinator and preserved Realtime state machines should not be refactored without a concrete behavioral reason and matching regression evidence.

## Deferred providers

The repository preserves:

- `LOCAL_PHONE_LLM` as experimental infrastructure;
- `EDGE_GALLERY` as a frozen experimental provider;
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
