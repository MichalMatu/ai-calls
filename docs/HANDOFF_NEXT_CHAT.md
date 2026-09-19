# Handoff — product-owned local LLM + live silence endpointing proven on S22

Date: 2026-09-19

Repository: `MichalMatu/android-ai-call-bridge`

Durable product branch: `main`

Local Agent control branch: `agent-control`

## Start here in a new chat

Read fresh versions in this order:

1. `AGENTS.md`
2. this file
3. `README.md`
4. `docs/ROADMAP.md`
5. `docs/ARCHITECTURE.md`
6. `docs/SECURITY_PRIVACY.md` when touching model/network/credentials
7. `docs/PHASE2D_FREEZE_2026-09-18.md` before changing Samsung media internals.

Then fetch fresh `main` and `.agent/status/daemon.json` from `agent-control` before any write.

A new chat must use its own fresh Local Agent binding. Never copy an old binding from history.

## Exact continuation state

Latest product-code checkpoint at this handoff:

```text
460ef8647f7e2d56cdc56d90e6f5c5cd874a5462
test: let live call probe own local llm runtime
```

The handoff commit itself advances `main`, so always fetch fresh `main` before working.

Latest physical result:

```text
live-endpointing-orange-s22-20260919-1214
```

Target phone remains Samsung Galaxy S22+ `SM-S906B`, Android 16 / API 36 / One UI 8, direct-USB serial `RFCT70L7E8J`.

## Frozen cellular media

Phase 2D remains `PROVEN_S22` and frozen at:

```text
59b0505537a53306acdab6a2a66ca6eed2b3f1c0
```

Preserve RX/TX attribution/order, CALL_ASSISTANT/TELEPHONY_TX routing, internal mono PCM16LE, TX-boundary stereo, PFD ownership, sibling cleanup, TAKE OVER, heartbeat and `CallModeWatchdog`.

The endpointing work did not change the frozen Samsung transport. It only changed when the live probe sends EOF to STT.

## Local speech + provider-neutral text pipeline

Production local STT/TTS remains `PROVEN_S22`.

The application owns output approval. Final STT text goes through `TextCallAgentBackend` / `TextCallTurnController`; only approved complete text reaches local TTS. Model output cannot create commitment authority.

## Local phone LLM provider

`LOCAL_PHONE_LLM` remains a normal product provider behind the provider-neutral boundary.

Preferred model:

```text
Qwen2.5-1.5B-Instruct Q4_K_M
/data/local/tmp/aicall-phone-llm/model-1.5b.gguf
SHA256 6a1a2eb6d15622bf3c96857206351ba97e1af16c30d7a74ee38970e434e9407e
alias qwen-phone-1.5b
llama.cpp Android arm64 server build b10976
loopback port 18115
context 1024
t=4, np=1
```

Do not repeat the earlier mistaken conclusion from the stale 0.5B process. The corrected 1.5B proof remains:

```text
qwen15b-verified-flow-orange-s22-retry-20260919-3720
```

That gate explicitly verified `/props` alias and model path before inference.

## Product-owned local LLM runtime is now proven

The app no longer requires a manually prestarted `llama-server` for the production local-phone provider.

Current implementation includes:

- exact `/props` identity verification before inference;
- dedicated Shizuku `LocalPhoneLlmRuntimeUserService`;
- app-side `ShizukuLocalPhoneLlmRuntimeGate`;
- product start/stop/restart ownership;
- stale/incorrect server recovery path;
- fail-closed backend behavior if runtime readiness or model identity fails.

Physical lifecycle result:

```text
local-phone-runtime-lifecycle-s22-20260919-1151
```

Observed sequence:

```text
initial llama-server pid=20523
first off-call pipeline = success
pid after first run = none
second off-call pipeline = success from a stopped-server state
pid after second run = none
local_phone_runtime_lifecycle_proven_s22=true
```

The old zero-argument `LocalPhoneLlmBackendFactory.create()` compatibility path has been removed. Production callers use the Context-backed product-owned runtime.

## Fixed 8-second live wait has been removed

The old live logic that always collected approximately eight seconds of downlink audio is gone.

`LocalPhoneLlmLiveCallProbe` now reads bounded 20 ms PCM frames through `PcmEndOfUtteranceDetector` and stops input when sustained trailing silence is detected after speech.

Current defaults:

```text
frame = 20 ms
speech RMS threshold = 600
minimum detected speech = 200 ms
trailing silence = 700 ms
hard safety cap = 8000 ms
```

The 8-second value is now only a fail-safe maximum for pathological/no-speech/continuous-noise input. It is no longer the normal waiting behavior.

Host detector tests cover:

- normal speech followed by silence;
- silence-only input reaching the hard cap;
- short impulse/noise not becoming a valid utterance;
- odd PCM byte boundaries.

Full host gate after live integration:

```text
live-endpointing-host-verify-20260919-1211
BUILD SUCCESSFUL
live_endpointing_host_verify=true
```

## Physical Orange endpointing proof

Physical result:

```text
live-endpointing-orange-s22-20260919-1214
```

The test started with no `llama-server` process, so the application-owned runtime had to provide the local LLM itself.

Observed live report:

```text
endpointing=trailing_silence
telephony_rx_pcm_bytes=46720
endpoint_reason=trailing_silence
endpoint_capture_ms=1460
endpoint_speech_detected=true
estimated_end_of_speech_elapsed_ms=815
stt_pcm_eof_sent=true
stt_eof_elapsed_ms=1419
stt_text=orange
stt_elapsed_ms=1435
approved_text=Orange to firma komunikacyjna.
llm_approved_elapsed_ms=11702
first_tx_elapsed_ms=14227
end_of_speech_to_first_tx_ms=13412
telephony_tx_pcm_bytes=65322
local_phone_llm_live_call_success=true
live_endpointing_proven_s22=true
live_endpointing_orange_proven_s22=true
```

This is the key correction: live capture ended after about **1.46 s**, not after 8 s.

After the test:

```text
hangup_requested=true
idle_after_hangup=True
no llama-server leak
no call_media helper leak
```

## Current latency interpretation

The old fixed-capture delay is no longer the dominant cost in this physical turn.

Measured from estimated end of caller speech to the first telephony TX write:

```text
13.412 s
```

Most of that remaining delay is after STT EOF, primarily the current local 1.5B text generation plus local TTS path. This is now a meaningful baseline for comparing the remote text provider.

Do not optimize Samsung media transport to chase this latency; the frozen transport is not the current bottleneck.

## Current limitations

1. Current proven live path is still one turn. Multi-turn history, natural IVR navigation and barge-in are not yet proven for the text-agent path.
2. Qwen 1.5B remains useful for offline/fallback operation, but its current response quality and latency are not the target for the online high-quality mode.
3. The silence detector threshold is physically proven for the current Orange gate but still needs broader real-call validation before treating one threshold as universal for every network/IVR/noise condition.
4. Local model artifact installation/updating is still not a polished end-user distribution mechanism even though runtime ownership is now product-owned.

## Next priority: `OPENAI_TEXT`

Keep `LOCAL_PHONE_LLM` as the offline/fallback provider and add a second provider where S22 retains telephony audio, local STT, application approval and local TTS while a stronger remote OpenAI text model provides the conversational reasoning:

```text
TELEPHONY_RX
 -> local S22 STT
 -> transcript
 -> OPENAI_TEXT backend
 -> application-owned approval / commitment policy
 -> local S22 TTS
 -> TELEPHONY_TX
```

Do not send raw call audio to OpenAI for this path.

Do not interpret this as controlling the interactive ChatGPT UI. Use an OpenAI API/backend implementation behind `TextCallAgentBackend` and maintain conversation history explicitly.

The standard OpenAI API key remains host/backend-only. Never put it in the APK, phone, ADB argv, Intent, logs or repository. Reuse/extend the existing credential-boundary approach.

OpenAI Realtime Audio remains preserved/frozen unless explicitly resumed.

## Exact next work

Continue in this order:

1. audit the existing backend/credential boundary and provider selector for the narrowest `OPENAI_TEXT` integration;
2. implement a real remote text provider behind `TextCallAgentBackend` without moving the standard API key onto Android;
3. prove the provider off-call first with local S22 STT -> remote text -> application approval -> local S22 TTS;
4. measure transcript-to-approved-text and end-of-speech-to-first-TX latency;
5. run one controlled allowlisted Orange live turn;
6. compare `LOCAL_PHONE_LLM` 1.5B vs `OPENAI_TEXT` quality and latency;
7. only then expand to multi-turn/IVR behavior.
