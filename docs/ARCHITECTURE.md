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
          /              \
 OPENAI_TEXT       LOCAL_MAC_LLM

Tools/context are orthogonal:
none / local tools / MCP
```

MCP is not an LLM provider. It is a tools/context integration layer. No model or tool provider may bypass application-owned authority or commitment gates.

## Shared boundaries

### 1. Authority and workflow

`CallTask`, constraints, preferences and `authorizedFacts` define what the agent may do. `CallWorkflow` and `CallConfirmationPolicy` are deterministic application logic; counterparty/model text cannot widen authority.

Existing Realtime proposal/commitment components remain valid evidence for the policy semantics. As provider-neutral text backends are introduced, reuse the same deterministic policy and one-shot authorization behavior rather than creating provider-specific authority logic.

### 2. Engine selection

The app persists two independent choices:

- audio mode: `LOCAL_STT_TTS`, `OPENAI_REALTIME_AUDIO`, `LOCAL_REALTIME_AUDIO`;
- text LLM provider for `LOCAL_STT_TTS`: `OPENAI_TEXT`, `LOCAL_MAC_LLM`.

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

All non-Realtime speech components should accept/return this format at the telephony boundary. Provider-specific conversion belongs inside the provider adapter.

OpenAI Realtime raw PCM remains signed PCM16LE, mono, 24 kHz; `RealtimePcmFrameAdapter` isolates 16 <-> 24 kHz conversion. Stereo exists only at the Samsung TX boundary.

## LOCAL_STT_TTS path

Target production path:

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

### Local SpeechInput

Physically proven on S22:

- Android on-device `SpeechRecognizer` supports `pl-PL`;
- caller-supplied audio is accepted through `RecognizerIntent.EXTRA_AUDIO_SOURCE`;
- the reliable transport is a live `ParcelFileDescriptor.createPipe()` stream, not a seekable file descriptor kept open;
- PCM16LE mono 16 kHz is streamed through the pipe;
- closing the write end provides the stream EOF required for completion;
- segmented session support may be used with the audio source;
- a known Polish TTS phrase round-trips to the correct transcript.

Production code should extract this behavior into a small lifecycle-owned adapter. The diagnostic loopback remains test/evidence code and must not become the runtime engine.

### Local SpeechOutput

Physically proven on S22:

- multiple Polish voices do not require a network connection;
- local `TextToSpeech.synthesizeToFile` succeeds;
- target S22 TTS produced mono 24 kHz WAV in the proof;
- app-side decoding/downmix/resampling produces PCM16LE mono 16 kHz for the telephony contract.

Production `SpeechOutput` should return/stream internal-format PCM and own temporary synthesis resources/cancellation.

### Turn lifecycle

Initial safe production behavior should be whole-turn rather than aggressively streamed:

```text
caller utterance complete
 -> final STT transcript
 -> complete candidate model response
 -> application approval / authority checks
 -> local TTS
 -> TX
```

This preserves existing output-approval semantics. Sentence/chunk streaming can be optimized later only if it does not weaken approval or commitment safety.

Barge-in/interrupt handling should stop TTS/TX locally and invalidate the active speech generation before asking any model/network component to cancel.

## Text-agent boundary

Introduce a provider-neutral interface above speech, conceptually:

```text
transcript + CallTask/workflow context
    -> TextAgentBackend
    -> candidate response + typed proposals/tool requests
```

Planned implementations:

- `OPENAI_TEXT` — remote text provider through a safe backend credential boundary;
- `LOCAL_MAC_LLM` — local/LAN model server on the user's Mac.

Do not expose raw model authority. Existing proposal parsing, confirmation policy, one-shot commitment authorization and `NEEDS_USER_DECISION` remain application-owned.

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

`realtime-client/` remains isolated from Samsung telephony internals and owns short-lived Realtime credentials, WebSocket protocol, generation safety and 16 <-> 24 kHz adaptation.

Do not destructively rename/refactor proven Realtime code merely to make local mode look symmetric. Add provider-neutral seams around it only when useful.

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

## Diagnostics

`RealtimeEventTrace` remains Realtime-specific bounded metadata instrumentation. Local speech/model diagnostics should follow the same privacy principle: states, durations, sizes, sanitized failure reasons and local correlation IDs rather than raw PCM/transcripts by default.

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
