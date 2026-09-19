# Roadmap

Capabilities advance only when their required evidence gate is actually proven.

Evidence levels:

- `HOST_GREEN` — deterministic tests/build/lint pass;
- `PROVEN_S22` — physically reproduced on the target Samsung S22+;
- `PRODUCT_READY` — proven, fail-safe and acceptable for normal use.

## Phase 0 — repository and verification discipline

Status: `DONE`

The project is modularized into app, audio contracts, privileged helper and Realtime client. `scripts/verify_host.sh` is the canonical local/CI quality gate. Durable work is main-first; Local Agent metadata remains isolated on `agent-control`.

## Phase 1 — cellular media capability

Status: `DONE / PROVEN_S22`

Production directions:

```text
RX: VOICE_DOWNLINK -> privileged helper -> PFD -> app
TX: app -> PFD -> CALL_ASSISTANT / TELEPHONY_TX -> cellular uplink
```

Generic media/voice-communication TX experiments are not the production route on this S22.

## Phase 2 — stable local bridge

Status: `DONE / PROVEN_S22 / FROZEN`

Physical evidence covers simultaneous bidirectional media, endpoint loss, app/helper death cleanup, repeated start/abort, natural call end, 600 seconds total live bidirectional media and separate resource telemetry.

Frozen checkpoint commit:

```text
59b0505537a53306acdab6a2a66ca6eed2b3f1c0
```

Do not repeat the full physical matrix without concrete regression evidence. Details: `docs/PHASE2D_FREEZE_2026-09-18.md`.

## Phase 3 — selectable telephone-agent engines

Status: `LOCAL STT/TTS + LOCAL PHONE LLM LIVE TURN PROVEN_S22 / HYBRID OPENAI_TEXT NEXT / OPENAI REALTIME AUDIO FROZEN`

The product direction is selectable rather than Realtime-only.

### Runtime choices

```text
Audio mode
├── LOCAL_STT_TTS
│   └── text LLM provider
│       ├── LOCAL_PHONE_LLM
│       ├── LOCAL_MAC_LLM
│       └── OPENAI_TEXT
├── OPENAI_REALTIME_AUDIO   (preserved/frozen)
└── LOCAL_REALTIME_AUDIO    (future local speech-to-speech/server engine)
```

Tool integration is independent from model selection. MCP is a tools/context transport and must not be treated as an LLM provider. No model or tool provider may bypass application-owned authority/commitment/output-approval rules.

Implemented and retained shared stack includes:

- production app-side media coordinator/backend/runtime;
- deterministic task/constraints/preferences/authorized-facts workflow model;
- `CallConfirmationPolicy` and `NEEDS_USER_DECISION`;
- strict side-effect-free proposal parsing;
- one-shot commitment authorization;
- output approval before cellular TX;
- local TAKE OVER and frozen Samsung media fail-safe invariants;
- selectable runtime preferences for audio and text providers;
- provider-neutral `TextCallAgentBackend` + `TextCallTurnController`;
- production local STT/TTS adapters;
- OpenAI-compatible local text backend used by Mac and phone-loopback providers.

### Gate 3L-A — production local speech adapters

Status: `DONE / PROVEN_S22`

Production-owned local STT/TTS components provide:

- streaming PCM16LE mono 16 kHz into on-device STT;
- local TTS synthesis returning PCM16LE mono 16 kHz;
- cancellation/generation ownership and bounded cleanup.

Relevant checkpoints:

```text
386031a1f9bf891970e4cf6af8a3ec148a65aa7a
fix: stream local STT input through PFD pipe

e953b78ea2b56c3bbc62fded3da295c23ccbf1bb
feat: add production local speech adapters
```

### Gate 3L-B — provider-neutral local conversation engine

Status: `DONE / PROVEN_S22`

Final STT transcript -> provider-neutral text backend -> application approval -> local TTS is physically proven.

Checkpoint:

```text
67a1bc75e7deb55ec0e4e515ef26195c4587edae
feat: add local text agent pipeline
```

### Gate 3L-C — local text LLM providers

Status: `DONE / PROVEN_S22 FOR LOCAL_MAC_LLM AND LOCAL_PHONE_LLM`

`LOCAL_PHONE_LLM` is a normal runtime provider:

```text
a8c363ae7b05e9da6836156829c2d1fc1d560869
feat: add local phone llm runtime provider
```

Preferred current local phone model:

```text
Qwen2.5-1.5B-Instruct Q4_K_M
alias qwen-phone-1.5b
/data/local/tmp/aicall-phone-llm/model-1.5b.gguf
SHA256 6a1a2eb6d15622bf3c96857206351ba97e1af16c30d7a74ee38970e434e9407e
```

Critical evidence correction: an earlier attempted 1.5B quality run was actually served by a stale 0.5B process. The audit `qwen15b-chat-template-audit-s22-20260919-3700` exposed this via `/props`. It must not be used as 1.5B evidence.

Authoritative corrected physical result:

```text
.agent/results/qwen15b-verified-flow-orange-s22-retry-20260919-3720.json
```

The corrected gate explicitly stopped stale servers and verified exact alias/model path before inference.

Verified direct 1.5B evidence:

```text
2+2 -> 4             (~0.595 s)
"orange" -> "Orange to firma telekomunikacyjna." (~3.365 s)
```

Verified off-call speech flow:

```text
local TTS
 -> production STT
 -> verified Qwen 1.5B
 -> application approval
 -> local TTS
```

Observed:

```text
stt_text=to jest test lokalnego modelu na telefonie
approved_text=ok, rozumiem.
local_phone_llm_speech_pipeline_success=true
```

The verified 1.5B process reached approximately 2.1 GB high-water RSS. This proves 16 GB RAM is not required for this 1.5B Q4 use case. Larger models remain measurement work, not an assumption.

### Gate 3L-D — controlled automated local-speech cellular call

Status: `DONE / ONE-TURN PROVEN_S22`

The frozen cellular bridge is physically connected to the local speech/text path:

```text
allowlisted dial
 -> telephony RX
 -> local STT
 -> LOCAL_PHONE_LLM
 -> application approval
 -> local TTS
 -> telephony TX
 -> bounded hangup / cleanup
```

Current allowlisted test destination used by the runner:

```text
510100100
```

Authoritative corrected 1.5B live evidence:

```text
.agent/results/qwen15b-verified-flow-orange-s22-retry-20260919-3720.json
```

Observed live report:

```text
orange_downlink_signal=true
telephony_rx_pcm_bytes=256000
stt_text=orange  dzień dobry jestem max twój wi
stt_elapsed_ms=7975
approved_text=dzień dobry, Max. Cześć!
llm_approved_elapsed_ms=11176
telephony_tx_pcm_bytes=72174
turn_complete_elapsed_ms=13468
local_phone_llm_live_call_success=true
qwen15b_verified_orange_live_proven_s22=true
hangup_requested=true
idle_after_hangup=True
```

The gate also restored Bluetooth and requested voice-call unmute cleanup. No helper leak remained.

This proves the complete local one-turn flow. It does not yet prove polished multi-turn conversation, IVR navigation, local-path barge-in, or production lifecycle ownership of the LLM server.

### Gate 3L-E — conversational latency / endpointing

Status: `NEXT`

Current live local speech captures a fixed 8-second input window. That dominates latency and can split or merge IVR speech.

Replace fixed capture with bounded end-of-utterance/silence endpointing while preserving fail-closed cleanup. Measure:

```text
end of remote utterance
 -> final STT
 -> model response approved
 -> first TTS/TX audio
```

Only after this gate should local 1.5B conversational latency be judged fairly.

### Gate 3L-F — hybrid local-speech + remote text brain

Status: `NEXT / PREFERRED QUALITY PATH`

Keep local phone LLM as offline/fallback, and implement a stronger remote text provider:

```text
TELEPHONY_RX
 -> local S22 STT
 -> transcript
 -> OPENAI_TEXT
 -> application-owned approval / commitment policy
 -> local S22 TTS
 -> TELEPHONY_TX
```

Product rationale:

- telephony audio capture/injection stays on the proven S22 media path;
- local speech remains available;
- only transcript/context and response text need the remote model path;
- this removes phone LLM RAM/quality as the default bottleneck;
- `LOCAL_PHONE_LLM` remains valuable offline/fallback functionality.

Requirements:

1. implement a real `OPENAI_TEXT` backend behind `TextCallAgentBackend`;
2. never place a standard OpenAI API key on Android;
3. reuse/extend a backend credential boundary;
4. explicitly own conversation context/history;
5. retain full app-owned approval/commitment rules;
6. prove off-call first on S22;
7. then run one bounded allowlisted Orange live turn and compare quality/latency against local 1.5B.

The literal ChatGPT web/app conversation is not the product runtime. Use a model API through the repository's safe backend architecture.

### OpenAI Realtime Audio branch

Status: `FROZEN / PRESERVED`

The existing OpenAI Realtime implementation, credential broker, off-call smoke and live-call path remain in the repository as a selectable alternative. Do not delete or destructively refactor them while developing the local/hybrid text path.

A standard OpenAI API key remains host/backend-only and is never placed on Android.

### Local Realtime Audio branch

Status: `LATER`

Future third audio engine where telephony PCM is handled by a local audio-capable model/server rather than Android STT/TTS + text LLM. It must reuse the same app-owned authority, output and TAKE OVER boundaries.

## Phase 4 — product UX and runtime ownership

Status: `INCREMENTAL`

Runtime selectors already exist. Remaining work includes:

- model/provider readiness and concise diagnostics;
- product-owned local model lifecycle or an explicit supported external-runtime contract;
- exact local server identity verification (`/props`, not only `/health`);
- prominent TAKE OVER;
- user-decision surfaces and structured outcomes;
- clear offline/local vs remote-text provider state.

Do not replace the default dialer without a concrete requirement.

## Phase 5 — robustness matrix

Status: `LATER`

After multi-turn behavior is working, validate longer calls, screen/background behavior, endpoint/model/network failure, route/Bluetooth changes, Wi-Fi Calling, incoming/outgoing variants and repeated sessions. Exit condition: failures have defined fail-safe behavior and never leave AI injection active.

## Current decision

Preserve the fully local path and verified Qwen 1.5B as offline/fallback. Next improve STT turn endpointing/latency, then make `OPENAI_TEXT` a real higher-quality text provider behind a safe backend credential boundary. Compare both on the same controlled Orange live-call gate. OpenAI Realtime Audio remains frozen unless explicitly resumed.
