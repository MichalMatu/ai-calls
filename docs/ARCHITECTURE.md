# Architecture

## Goal

Bridge an ordinary cellular call on the target Samsung S22+ to a selectable AI engine while keeping telephony privilege, speech/model transport, business authority and user takeover as separate ownership boundaries.

Failure must move toward a normal human call. Local TAKE OVER cannot depend on network/model acknowledgement.

## Top-level runtime selection

```text
                         CallTask / explicit authority
                                   |
                                   v
                    workflow / confirmation / commitment
                                   |
                                   v
                         selected AI engine
                        /        |        \
                       v         v         v
             LOCAL_STT_TTS   OPENAI       LOCAL
               + TEXT LLM    REALTIME     REALTIME
                   |           AUDIO       AUDIO
                   |
          text LLM provider
          /       |        \
         v        v         v
 LOCAL_PHONE   LOCAL_MAC   OPENAI_TEXT
    LLM          LLM      (hybrid next)
```

Tools/context are orthogonal:

```text
none / local tools / MCP
```

MCP is not an LLM provider. It is a tools/context integration layer. No model or tool provider may bypass application-owned authority or commitment gates.

## Shared boundaries

### 1. Authority and workflow

`CallTask`, constraints, preferences and `authorizedFacts` define what the agent may do. `CallWorkflow` and `CallConfirmationPolicy` are deterministic application logic; counterparty/model text cannot widen authority.

Existing proposal/commitment components remain valid evidence for the policy semantics. Provider-neutral text backends reuse the same deterministic policy and one-shot authorization behavior rather than creating provider-specific authority logic.

### 2. Engine selection

The app persists two independent choices:

- audio mode: `LOCAL_STT_TTS`, `OPENAI_REALTIME_AUDIO`, `LOCAL_REALTIME_AUDIO`;
- text LLM provider for `LOCAL_STT_TTS`: `LOCAL_PHONE_LLM`, `LOCAL_MAC_LLM`, `OPENAI_TEXT`.

Realtime audio modes do not consume the text-LLM selector, but the preference remains preserved when switching back to `LOCAL_STT_TTS`.

### 3. Frozen telephony media lifecycle

`CallMediaSessionCoordinator` owns one privileged RX+TX generation, bind/prepare/start sequencing, heartbeat lifetime, endpoint generation checks, helper failure observation and whole-generation teardown.

Continuous PCM does not use Binder. The normal app owns transferred PFD endpoints; Binder/AIDL is control only.

The frozen Samsung media implementation is engine-agnostic: selected AI engines consume/produce the same internal telephony PCM and must not own Samsung/private audio behavior.

### 4. Privileged Samsung media

`privileged-helper/` owns Samsung/private audio behavior and no model/business policy.

RX:

```text
VOICE_DOWNLINK -> SamsungVoiceDownlinkCapture -> SamsungDownlinkPipeSession -> PFD
```

TX:

```text
PFD -> SamsungUplinkPipeSession -> SamsungCallAssistantTrack
    -> mono-to-stereo at boundary
    -> USAGE_CALL_ASSISTANT / TELEPHONY_TX
```

The helper executes under the proven shell/Shizuku privilege model. Narrow lint suppressions exist only where static analysis cannot model that privilege or the target-specific hidden-API path; changing those paths requires targeted device regression.

## Internal PCM contract

Internal telephony format: signed PCM16LE, mono, 16 kHz.

All non-Realtime speech components accept/return this format at the telephony boundary. Provider-specific conversion belongs inside the provider adapter.

OpenAI Realtime raw PCM remains signed PCM16LE, mono, 24 kHz; `RealtimePcmFrameAdapter` isolates 16 <-> 24 kHz conversion. Stereo exists only at the Samsung TX boundary.

## LOCAL_STT_TTS path

Production path:

```text
TELEPHONY_RX PCM16/16k
   -> local SpeechInput
   -> transcript
   -> provider-neutral text agent
   -> approved response text
   -> local SpeechOutput
   -> PCM16/16k
   -> TELEPHONY_TX
```

This path is physically proven for one complete cellular turn with `LOCAL_PHONE_LLM`.

### Local SpeechInput

Physically proven on S22:

- Android on-device `SpeechRecognizer` supports `pl-PL`;
- caller-supplied audio is accepted through `RecognizerIntent.EXTRA_AUDIO_SOURCE`;
- the reliable transport is a live `ParcelFileDescriptor.createPipe()` stream;
- PCM16LE mono 16 kHz is streamed through the pipe;
- closing the write end provides the stream EOF required for completion;
- segmented session support may be used with the audio source;
- production adapters complete a known Polish TTS -> STT roundtrip.

The current live proof still uses a fixed bounded capture window before EOF. That is diagnostic/test behavior, not the desired final conversational endpointing strategy.

Next local-speech optimization: detect end-of-utterance/silence and close the STT input promptly instead of waiting a fixed 8-second window.

### Local SpeechOutput

Physically proven on S22:

- local Polish voices synthesize successfully without requiring a network voice;
- `TextToSpeech.synthesizeToFile` succeeds;
- app-side decoding/downmix/resampling produces PCM16LE mono 16 kHz for the telephony contract;
- generated PCM has been physically injected into the cellular uplink through the frozen Samsung TX path.

### Turn lifecycle

Current safe production behavior is whole-turn:

```text
remote utterance complete
 -> final STT transcript
 -> complete candidate model response
 -> application approval / authority checks
 -> local TTS
 -> TX
```

This preserves output-approval semantics. Sentence/chunk streaming may be optimized later only if it does not weaken approval or commitment safety.

Barge-in/interrupt handling should stop TTS/TX locally and invalidate the active speech generation before asking any model/network component to cancel.

## Text-agent boundary

Provider-neutral interface:

```text
transcript + CallTask/workflow context
    -> TextCallAgentBackend
    -> candidate response
    -> application approval / proposal / commitment logic
```

The provider never owns dialing authority or commitment authority.

### LOCAL_PHONE_LLM

Current preferred local model:

```text
Qwen2.5-1.5B-Instruct Q4_K_M
alias qwen-phone-1.5b
loopback http://127.0.0.1:18115/v1/
```

Authoritative physical proof:

```text
.agent/results/qwen15b-verified-flow-orange-s22-retry-20260919-3720.json
```

Important readiness lesson: `/health` is insufficient to identify the selected model. A stale 0.5B process once remained on the expected port while a 1.5B file had been downloaded. Future local runtime readiness must verify `/props` alias/model path (or equivalent exact identity) before declaring the provider ready.

Current local server lifecycle is still lab-owned/manual under `/data/local/tmp`; product-owned start/stop/recovery is future work.

The verified 1.5B server reached about 2.1 GB high-water RSS in the corrected gate. This proves the current 1.5B Q4 path does not require a 16 GB phone, but larger model capacity remains device-measurement work.

### LOCAL_MAC_LLM

Retained fallback using the same OpenAI-compatible text backend against an explicitly local/LAN endpoint. It must obey the same approval and authority boundaries as the phone-local provider.

### OPENAI_TEXT — hybrid path

Preferred higher-quality next provider:

```text
TELEPHONY_RX
 -> local S22 STT
 -> transcript / bounded conversation context
 -> remote OpenAI text backend
 -> application approval / proposal / commitment logic
 -> local S22 TTS
 -> TELEPHONY_TX
```

The purpose is to remove local-phone model quality/RAM as the default reasoning bottleneck while preserving the proven S22 telephony and local speech path.

Only text/context should traverse the remote text-provider boundary for this mode. Raw call audio does not need to leave the local speech path.

The literal interactive ChatGPT UI/session is not the runtime integration. Product code should use an OpenAI API text-model backend and explicitly maintain conversation state.

A standard OpenAI API key remains host/backend-only. Never place it in Android source, APK, BuildConfig, Intent, ADB argv or phone storage. Reuse/extend the repository's backend credential-boundary principle.

Incremental text delivery may reduce model wait time, but no partial text may reach TTS unless the approval design explicitly supports safe chunk/sentence release. The first implementation should preserve complete-response approval.

## Verified local cellular turn

The true-1.5B corrected gate physically proved:

```text
allowlisted Orange dial
 -> downlink signal
 -> frozen telephony RX
 -> production local STT
 -> verified local Qwen 1.5B
 -> application-owned approval
 -> local TTS
 -> frozen telephony TX
 -> bounded automated hangup / cleanup
```

Representative evidence:

```text
stt_text=orange  dzień dobry jestem max twój wi
stt_elapsed_ms=7975
approved_text=dzień dobry, Max. Cześć!
llm_approved_elapsed_ms=11176
telephony_tx_pcm_bytes=72174
turn_complete_elapsed_ms=13468
qwen15b_verified_orange_live_proven_s22=true
```

This proves one bounded end-to-end turn. It does not yet prove natural multi-turn dialogue, IVR navigation or local-path barge-in.

## OPENAI_REALTIME_AUDIO path

The existing Realtime stack is preserved/frozen as a selectable alternative:

```text
CallRealtimeAgentSessionSpec
  - instructions
  - evaluate_proposal / commit_proposal
  - shared CallCommitmentGate
  - output approval policy
        |
        v
CallRealtimeSessionOrchestrator
       / \
      v   v
Realtime   CallMediaSessionCoordinator
transport          |
                   v
          frozen Samsung media
```

`CallRealtimeSessionOrchestrator` still owns one credential/transport/media generation:

```text
FETCHING_CREDENTIAL -> CONNECTING_REALTIME -> STARTING_MEDIA -> ACTIVE
```

`realtime-client/` remains isolated from Samsung telephony internals and owns short-lived credential handling, WebSocket protocol, generation safety and 16 <-> 24 kHz adaptation.

Do not destructively rename/refactor proven Realtime code merely to make local mode look symmetric. The current priority is `LOCAL_STT_TTS` with selectable text providers.

## LOCAL_REALTIME_AUDIO path

Reserved future third engine:

```text
TELEPHONY_RX PCM
 -> local audio-capable model/server
 -> PCM
 -> TELEPHONY_TX
```

It may internally be speech-to-speech or STT+LLM+TTS, but from the Android app it should behave as an audio engine and reuse the same telephony generation, TAKE OVER and app-owned authority boundaries.

## Speech and commitment gates

The external commitment protocol is application-owned:

```text
evaluate_proposal
  -> deterministic policy
  -> one exact opaque permit
  -> forced commit_proposal
  -> consume permit once
```

For text mode, no unapproved text should be synthesized. For Realtime audio, `CallRealtimeOutputResponseBuffer` continues to intercept identified model audio before telephony TX. Cancelled/failed/incomplete/unsafe outputs are dropped.

## Automated test-call boundary

Automated dial/hangup is allowed only under `AGENTS.md`:

- explicit operator-defined allowlist;
- one active call at a time;
- bounded retries/duration;
- no model/tool expansion of targets;
- no emergency/premium/arbitrary-short-code dialing;
- runner may hang up only the bounded call it created or an explicitly authorized active call.

The current controlled Orange test target is `510100100`.

## Diagnostics

`RealtimeEventTrace` remains Realtime-specific bounded metadata instrumentation. Local speech/model diagnostics follow the same privacy principle: states, durations, sizes, sanitized failure reasons and local correlation IDs rather than raw PCM by default.

For development physical gates, bounded sanitized STT/model text is intentionally captured when needed to validate behavior. Do not expand this into unrestricted production call logging.

Diagnostic probes are test surfaces; they must not become alternate product ownership paths.

## Frozen media invariants

Preserve unless a concrete physical regression requires change:

- proven RX construction/attribution ordering;
- TX `com.android.shell` attribution;
- CALL_ASSISTANT / TELEPHONY_TX route;
- internal mono PCM16LE and TX-boundary-only stereo;
- PFD AutoClose ownership;
- one RX+TX generation and sibling cleanup;
- local TAKE OVER;
- heartbeat and `CallModeWatchdog`.

See `docs/PHASE2D_FREEZE_2026-09-18.md` for physical evidence.
