# Handoff — verified local phone LLM, live Orange flow, and hybrid text-agent next step

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

A new chat must use its own fresh Local Agent binding. Never copy the previous chat binding from history or from this document.

## Exact continuation state

At handoff creation:

```text
main = 7ae1c7cd4892b780caa7be28974aa04a69e97696
       feat: improve local phone llm call quality

daemon = idle
latest Local Agent result = qwen15b-verified-flow-orange-s22-retry-20260919-3720
```

The latest task was read-only and therefore did not advance `main`.

Target phone remains Samsung Galaxy S22+ `SM-S906B`, Android 16 / API 36 / One UI 8, direct-USB serial `RFCT70L7E8J`.

## What is physically proven now

### Frozen cellular media

`PROVEN_S22` and still frozen:

```text
59b0505537a53306acdab6a2a66ca6eed2b3f1c0
```

Preserve RX/TX attribution/order, CALL_ASSISTANT/TELEPHONY_TX routing, internal mono PCM16LE, TX-boundary stereo, PFD ownership, sibling cleanup, TAKE OVER, heartbeat and `CallModeWatchdog`.

### Local speech

Production local STT/TTS is `PROVEN_S22`:

- on-device Polish STT accepts caller-supplied PCM16LE mono 16 kHz through a live PFD pipe;
- local Polish TTS synthesizes successfully without a network-required voice;
- app converts TTS output to the internal PCM16LE mono 16 kHz contract;
- production TTS -> STT roundtrip is physically proven.

Important checkpoints:

```text
386031a1f9bf891970e4cf6af8a3ec148a65aa7a
fix: stream local STT input through PFD pipe

e953b78ea2b56c3bbc62fded3da295c23ccbf1bb
feat: add production local speech adapters
```

### Provider-neutral text pipeline

`PROVEN_S22`:

```text
67a1bc75e7deb55ec0e4e515ef26195c4587edae
feat: add local text agent pipeline
```

The application owns output approval. Final STT text goes through `TextCallAgentBackend` / `TextCallTurnController`; only approved complete text reaches local TTS. Model output cannot create commitment authority.

### Local phone LLM provider

`LOCAL_PHONE_LLM` is a normal product provider behind the provider-neutral boundary.

Runtime provider checkpoint:

```text
a8c363ae7b05e9da6836156829c2d1fc1d560869
feat: add local phone llm runtime provider
```

The original 0.5B proof remains valid as an early functional proof, but the preferred current model is Qwen2.5-1.5B-Instruct Q4_K_M.

## Critical 1.5B evidence correction

Do not repeat the earlier mistaken conclusion that the first 1.5B quality run actually exercised 1.5B.

The audit task:

```text
qwen15b-chat-template-audit-s22-20260919-3700
```

proved that port `18115` was still served by a stale 0.5B process:

```text
model_alias=qwen-phone-0.5b
model_path=/data/local/tmp/aicall-phone-llm/model.gguf
```

Therefore the earlier 1.5B live response is not valid evidence about 1.5B quality.

The corrected physical gate is:

```text
qwen15b-verified-flow-orange-s22-retry-20260919-3720
```

It explicitly stopped stale servers and required `/props` to identify the exact 1.5B alias and model path before any inference or phone call.

Verified 1.5B model artifact:

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

Corrected direct 1.5B smoke:

```text
2+2 -> 4
latency ~= 0.595 s

ambiguous input "orange" -> "Orange to firma telekomunikacyjna."
latency ~= 3.365 s
```

Process telemetry immediately after verified load included approximately:

```text
VmHWM  2108456 kB
VmRSS  1943192 kB
Threads 15
```

After the live flow the process reported a lower resident set (~760000 kB) while the high-water mark remained ~2.1 GB. Treat the high-water mark as the safer capacity signal.

This proves that an 8 GB-class S22 can run this 1.5B Q4 model together with the app for the current bounded test. 16 GB RAM is not required for 1.5B. Larger models still require separate RAM/latency/thermal measurement; do not infer capability from file size alone.

## Verified full off-call 1.5B flow

The corrected 1.5B gate physically proved:

```text
local test TTS
 -> production on-device STT
 -> verified Qwen 1.5B on the same S22
 -> application-owned approval
 -> local TTS
```

Observed report:

```text
stt_text=to jest test lokalnego modelu na telefonie
approved_text=ok, rozumiem.
output_tts_pcm_bytes=38270
local_phone_llm_speech_pipeline_success=true
```

## Verified automated Orange live call

Project policy permits bounded automated dial/hangup only for explicitly operator-defined allowlisted test destinations on the dedicated test SIM. Model/tool output may never widen that allowlist.

The controlled target used by the current runner is:

```text
510100100
```

The corrected true-1.5B gate physically proved one complete cellular turn:

```text
allowlisted dial
 -> Orange cellular downlink
 -> frozen telephony RX
 -> production local STT
 -> verified local Qwen 1.5B
 -> application approval
 -> local TTS
 -> frozen telephony TX
 -> automated bounded hangup / cleanup
```

Observed evidence from `qwen15b-verified-flow-orange-s22-retry-20260919-3720`:

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

Bluetooth was restored, the voice-call mute cleanup was requested, the call ended idle, and no helper leak remained.

This is a real end-to-end `PROVEN_S22` local telephone-agent turn. It is not yet a natural multi-turn conversation product.

## Current limitations

1. Live STT currently uses a fixed bounded capture window (8 s in the current live probe). That dominates latency and can cut/merge IVR fragments. Replace it with end-of-utterance/silence endpointing before judging conversational latency.
2. `llama-server` lifecycle is still lab-owned/manual under `/data/local/tmp`. The app does not yet robustly own model install/start/stop/recovery. A stale server previously caused a false model-identity assumption, so future readiness must verify `/props` alias/path, not only `/health`.
3. Current live gate is one turn. Multi-turn history, barge-in between local STT/TTS turns and natural IVR navigation are not yet proven for the local text path.
4. Qwen 1.5B is a useful offline/fallback model, not necessarily the final-quality model. Future 3B/7B experiments need measured RAM, heat and latency. 12-16 GB RAM would provide more headroom, especially for 7B-class Q4 plus Android/KV cache, but it is not a blanket requirement for local inference.

## New priority: hybrid high-quality text brain

The user wants to keep `LOCAL_PHONE_LLM` for offline/fallback use and add a second practical path where the S22 still owns telephony audio, STT, safety and TTS, while a stronger OpenAI text model supplies the conversational reasoning:

```text
TELEPHONY_RX
 -> local S22 STT
 -> transcript
 -> OPENAI_TEXT backend
 -> application-owned approval / commitment policy
 -> local S22 TTS
 -> TELEPHONY_TX
```

This is preferred over sending raw call audio to the cloud. Only text needs to leave the phone-side speech pipeline.

Do not interpret this as controlling the literal interactive ChatGPT conversation UI. Product implementation should use an OpenAI API text-model backend behind the repository's credential boundary and preserve conversation state explicitly.

The standard OpenAI API key remains host/backend-only. Never put it in the APK, phone, ADB argv, Intent or repository. Reuse/extend the existing credential-broker security approach rather than embedding a key.

### Latency strategy for the hybrid path

The main latency bottleneck today is fixed STT capture, not only LLM generation. Optimize in this order:

1. endpoint STT on detected end-of-utterance/silence instead of fixed 8 s;
2. keep concise system/context state and short telephone responses;
3. use a low-latency text model/backend;
4. optionally receive text incrementally, but do not weaken application approval;
5. only later consider sentence/chunk TTS streaming after a safe approval design exists.

A practical first target is a natural whole-turn path with a few seconds from end of caller utterance to start of TTS. Measure it; do not promise a target before physical evidence.

## Exact next work

Recommended order in the new chat:

1. confirm fresh `main` and Local Agent idle state;
2. update/implement product-owned `LOCAL_PHONE_LLM` readiness so model identity is verified from `/props` and stale server processes cannot masquerade as the selected model;
3. replace fixed live STT capture with bounded end-of-utterance/silence endpointing and physically measure the improved local turn latency;
4. implement `OPENAI_TEXT` as a real provider behind a backend/credential boundary, reusing the same `TextCallAgentBackend`, approval and commitment stack;
5. prove `OPENAI_TEXT` off-call on S22 without exposing a standard API key to Android;
6. run one controlled allowlisted Orange live turn with local STT + remote text brain + local TTS;
7. only then expand to multi-turn/IVR behavior and compare local 1.5B vs remote-text quality/latency.

OpenAI Realtime Audio remains preserved/frozen unless explicitly resumed. Do not delete it, but do not let it distract from the `LOCAL_STT_TTS + text provider` path.
