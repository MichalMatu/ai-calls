# Android AI Call Bridge

Android prototype for bridging an ordinary cellular call on one stock Samsung phone to selectable AI engines without external audio hardware.

Target device: Samsung Galaxy S22+ `SM-S906B`, Android 16 / API 36 / One UI 8.

## Status

- Cellular RX/TX and fail-safe media lifecycle: `DONE / PROVEN_S22 / FROZEN`.
- Production local Polish STT/TTS: `DONE / PROVEN_S22`.
- Provider-neutral text-agent pipeline with application-owned approval: `DONE / PROVEN_S22`.
- `LOCAL_PHONE_LLM`: implemented and physically proven on-device.
- Verified preferred local model: Qwen2.5-1.5B-Instruct Q4_K_M through Android arm64 `llama-server`.
- Full local off-call path `TTS -> STT -> Qwen -> approval -> TTS`: `PROVEN_S22`.
- Automated allowlisted Orange cellular turn `RX -> STT -> Qwen -> approval -> TTS -> TX`: `PROVEN_S22`.
- `LOCAL_MAC_LLM`: retained as a fallback provider.
- `OPENAI_TEXT`: selected product direction for a higher-quality hybrid text brain; backend/product proof still to be completed.
- OpenAI Realtime Audio: preserved/frozen; genuine Realtime session/audio remains outside the current priority.

The exact continuation point is in `docs/HANDOFF_NEXT_CHAT.md`.

## Current architecture

```text
CallTask + explicit authority
  -> CallWorkflow / deterministic policy
  -> selected audio mode
       |
       +-> LOCAL_STT_TTS
       |     -> local STT
       |     -> selected text provider
       |          |-> LOCAL_PHONE_LLM
       |          |-> LOCAL_MAC_LLM
       |          `-> OPENAI_TEXT        (next hybrid path)
       |     -> application approval / commitment gates
       |     -> local TTS
       |     -> TELEPHONY_TX
       |
       +-> OPENAI_REALTIME_AUDIO         (preserved/frozen)
       `-> LOCAL_REALTIME_AUDIO          (future)

frozen cellular media:
CallMediaSessionCoordinator
  -> Shizuku privileged helper
       |-> VOICE_DOWNLINK RX
       `-> CALL_ASSISTANT / TELEPHONY_TX
```

Continuous PCM crosses the privilege boundary through transferred PFDs, never per-frame Binder calls.

## Verified local 1.5B evidence

A stale 0.5B `llama-server` previously remained on port `18115`, so the first attempted 1.5B quality run was not valid evidence about 1.5B. The corrected gate explicitly stopped stale servers and verified `/props` alias/model path before inference.

Authoritative result:

```text
.agent/results/qwen15b-verified-flow-orange-s22-retry-20260919-3720.json
```

Corrected server identity:

```text
model: Qwen2.5-1.5B-Instruct Q4_K_M
alias: qwen-phone-1.5b
path: /data/local/tmp/aicall-phone-llm/model-1.5b.gguf
SHA256: 6a1a2eb6d15622bf3c96857206351ba97e1af16c30d7a74ee38970e434e9407e
port: 18115
```

Representative evidence:

```text
2+2 -> 4
verified direct math latency ~= 0.595 s

local off-call:
stt_text=to jest test lokalnego modelu na telefonie
approved_text=ok, rozumiem.
local_phone_llm_speech_pipeline_success=true

live Orange turn:
stt_text=orange  dzień dobry jestem max twój wi
approved_text=dzień dobry, Max. Cześć!
telephony_tx_pcm_bytes=72174
turn_complete_elapsed_ms=13468
qwen15b_verified_orange_live_proven_s22=true
idle_after_hangup=True
```

The live path is physically proven for one bounded turn. It is not yet a polished natural multi-turn conversation engine.

## RAM / local-model direction

The verified 1.5B process reached about 2.1 GB high-water RSS during the corrected gate. That means 16 GB device RAM is not required for the current 1.5B Q4 path.

Future 3B/7B-class models must be measured on-device for RAM, KV cache, speed, heat and coexistence with Android/STT/TTS. 12-16 GB provides more headroom for larger models, but RAM capacity alone does not determine useful inference performance.

`LOCAL_PHONE_LLM` should remain available as offline/fallback functionality even if a stronger remote text provider becomes the default.

## Next product direction — local speech + remote text brain

The preferred next path is:

```text
TELEPHONY_RX
 -> local S22 STT
 -> transcript only
 -> OPENAI_TEXT
 -> application-owned approval / commitment policy
 -> local S22 TTS
 -> TELEPHONY_TX
```

This keeps telephony capture/injection and speech conversion local while allowing a substantially stronger conversational model.

Do not embed a standard OpenAI API key in Android. The standard key remains host/backend-only and must use a credential/backend boundary.

The literal interactive ChatGPT conversation UI is not the runtime integration target. Product code should call an OpenAI API text model and explicitly maintain the telephone conversation context.

## Main latency issue

The current live local probe captures a fixed 8-second RX window before STT completion. That dominates the observed ~13.5-second turn and can split/merge IVR speech poorly.

Before judging model latency, replace fixed capture with bounded end-of-utterance/silence endpointing. Then measure from end of remote speech to first local TTS/TX.

## Frozen Samsung invariants

The physically proven media path must not be casually redesigned:

- RX uses `VOICE_DOWNLINK` with the proven construction/attribution ordering;
- TX uses `com.android.shell` attribution and `USAGE_CALL_ASSISTANT` / TELEPHONY_TX;
- internal media is mono PCM16LE;
- stereo duplication exists only at the Samsung TX boundary;
- endpoint loss aborts the whole generation;
- PFD ownership is close-safe;
- TAKE OVER is local and immediate;
- heartbeat and `CallModeWatchdog` remain active safeguards.

See `docs/PHASE2D_FREEZE_2026-09-18.md`.

## Telephone Agent safety

Authority belongs to deterministic application code, not model or counterparty text.

Model text cannot grant dialing authority, widen an allowlist or bypass proposal/confirmation/commitment/output-approval gates. Automated calls are limited to explicitly operator-defined allowlisted targets under `AGENTS.md`.

## Credentials

A standard OpenAI API key must never enter the APK, Android Intent, app-private config, ADB arguments or phone.

The existing repository already contains a host/backend credential-broker approach for Realtime. Reuse the same security principle for future `OPENAI_TEXT` work rather than placing a long-lived key on Android.

## Quality gate

Run:

```bash
bash scripts/verify_host.sh
```

Hardware claims still require physical device evidence; host tests never upgrade a capability to `PROVEN_S22`.

## Branch policy

Durable product development happens on `main`. `agent-control` is reserved for Local Agent task/result traffic and must never be merged into product history.

## Continue

Read `docs/HANDOFF_NEXT_CHAT.md` first. The next practical sequence is:

1. make local model readiness verify exact `/props` identity and own stale-server failure safely;
2. replace fixed 8 s STT capture with end-of-utterance endpointing;
3. implement/prove `OPENAI_TEXT` behind a safe backend credential boundary;
4. compare local 1.5B and remote-text quality/latency in controlled allowlisted live calls.
