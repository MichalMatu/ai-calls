# Android AI Call Bridge

Android prototype for bridging an ordinary cellular call on one stock Samsung phone to selectable local or remote AI engines without external audio hardware.

Target device: Samsung Galaxy S22+ `SM-S906B`, Android 16 / API 36 / One UI 8.

## Proven baseline

Current production-facing foundation:

- cellular RX/TX and fail-safe media lifecycle: `DONE / PROVEN_S22 / FROZEN`;
- local Polish STT/TTS: `DONE / PROVEN_S22`;
- provider-neutral text-agent pipeline with application-owned output approval: `DONE / PROVEN_S22`;
- `LOCAL_PHONE_LLM`: `DONE / PROVEN_S22` with Qwen2.5-1.5B-Instruct Q4_K_M;
- product-owned local LLM start/identity-check/stop lifecycle: `DONE / PROVEN_S22`;
- live end-of-utterance detection: `DONE / PROVEN_S22`;
- one bounded Orange call turn `RX -> STT -> local LLM -> approval -> TTS -> TX`: `DONE / PROVEN_S22`.

The old normal fixed 8-second capture wait is gone. In the current Orange proof, input capture ended after about `1.46 s` on trailing silence. The 8-second value remains only as a hard safety cap.

Representative current evidence:

```text
.agent/results/qwen15b-verified-flow-orange-s22-retry-20260919-3720.json
.agent/results/local-phone-runtime-lifecycle-s22-20260919-1151.json
.agent/results/live-endpointing-orange-s22-20260919-1214.json
```

Current local-model identity:

```text
Qwen2.5-1.5B-Instruct Q4_K_M
alias: qwen-phone-1.5b
path: /data/local/tmp/aicall-phone-llm/model-1.5b.gguf
SHA256: 6a1a2eb6d15622bf3c96857206351ba97e1af16c30d7a74ee38970e434e9407e
port: 18115
```

## Architecture

```text
explicit user task / authority
          |
          v
   deterministic workflow
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
   |        |        |
   |        |        `-> remote providers (preserved/deferred)
   |        `----------> local/LAN provider
   `-------------------> local phone LLM
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

The telephony transport, STT/TTS and model choice are separate boundaries. A new model backend must not own Samsung media behavior or dialing/commitment authority.

## Active execution plan

Paid OpenAI API work is currently deferred. The active local-first plan is:

1. create a hard `READY_TO_DIAL` gate and a clean product-owned local text-call orchestration boundary;
2. benchmark the current 1.5B phone model, a larger feasible phone-local text model, and GPT-5.6 Sol through the current ChatGPT + Local Agent/ADB developer relay;
3. add `CallPlan v1` and deterministic conversation state so the local LLM is mainly a language layer, not the authority/planning brain;
4. prove multi-turn real tasks with explicit fallback/escalation;
5. only then evaluate local audio-capable models, first audio-to-text and later true speech-to-speech/full-duplex candidates.

See `docs/ROADMAP.md` for gates and `docs/HANDOFF_NEXT_CHAT.md` for the exact continuation point.

## Architecture rule for the next phase

Do not grow product behavior inside diagnostic probes.

`LocalPhoneLlmLiveCallProbe` is evidence tooling, not the future multi-turn product session. `DiagnosticProbeActivity` is a diagnostic entry point, not a runtime orchestrator. New call planning, readiness and multi-turn ownership must live in dedicated product code and use the existing media/speech/backend boundaries.

Large safety state machines are not split merely to reduce line count. In particular, the frozen media coordinator and preserved Realtime state machines should not be refactored without a concrete behavioral reason and matching regression evidence.

## Deferred providers

The repository preserves:

- `OPENAI_TEXT`;
- `OPENAI_REALTIME_AUDIO`;
- `LOCAL_MAC_LLM`;
- future `LOCAL_REALTIME_AUDIO`.

`OPENAI_TEXT` and OpenAI Realtime are not current execution priorities and require paid API access to prove. Do not delete their working code, and do not resume them unless explicitly requested.

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
