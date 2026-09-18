# Architecture

## Goal

Bridge an ordinary cellular call on the target Samsung S22+ to a Realtime AI agent while keeping telephony privilege, network transport, business authority and user takeover as separate ownership boundaries.

Failure must move toward a normal human call. Local TAKE OVER cannot depend on network/model acknowledgement.

## Boundaries

```text
CallTask / explicit authority
        |
        v
CallWorkflow + CallConfirmationPolicy
        |
        v
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
          Shizuku privileged helper
             /              \
        RX PFD              TX PFD
```

### 1. Authority and workflow

`CallTask`, constraints, preferences and `authorizedFacts` define what the agent may do. `CallWorkflow` and `CallConfirmationPolicy` are deterministic application logic; counterparty/model text cannot widen authority.

`CallRealtimeProposalParser` is a strict side-effect-free decoder for untrusted `evaluate_proposal` JSON. `CallRealtimeProposalFunctionHandler` owns workflow/commitment mutation. Keeping parsing separate prevents malformed input handling from becoming a second responsibility of the authority state handler.

### 2. Realtime session ownership

`CallRealtimeSessionOrchestrator` coordinates exactly one credential/transport/media generation:

```text
FETCHING_CREDENTIAL -> CONNECTING_REALTIME -> STARTING_MEDIA -> ACTIVE
```

It is intentionally a single safety-critical state machine despite its size. Generation invalidation, cleanup ordering, function-response ownership and TAKE OVER belong to one lifecycle; splitting it solely to reduce line count would weaken auditability.

`CallRealtimeMediaSession` attaches the connected transport to one active media generation. `CallRealtimeAudioPump` is the bounded non-owning PCM data plane.

### 3. Realtime client

`realtime-client/` knows nothing about Samsung telephony internals. It owns:

- short-lived credential types/providers;
- trusted OpenAI Realtime WebSocket handshake;
- protocol parsing and typed events/function calls;
- transport generation safety;
- 16 kHz <-> 24 kHz PCM adaptation support;
- optional bounded/redacted `RealtimeEventTrace` instrumentation.

The WebSocket handshake attaches credentials only to the canonical trusted Realtime endpoint.

### 4. App-side media lifecycle

`CallMediaSessionCoordinator` owns one privileged RX+TX generation, bind/prepare/start sequencing, heartbeat lifetime, endpoint generation checks, helper failure observation and whole-generation teardown.

Continuous PCM does not use Binder. The normal app owns transferred PFD endpoints; Binder/AIDL is control only.

### 5. Privileged Samsung media

`privileged-helper/` owns Samsung/private audio behavior and no OpenAI/business policy.

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

## PCM formats

Internal telephony format: signed PCM16LE, mono, 16 kHz.

Realtime raw PCM format: signed PCM16LE, mono, 24 kHz.

`RealtimePcmFrameAdapter` isolates sample-rate conversion. Stereo exists only at the Samsung TX boundary.

## Speech and commitment gates

The external commitment protocol is application-owned:

```text
evaluate_proposal
  -> deterministic policy
  -> one exact opaque permit
  -> forced commit_proposal
  -> consume permit once
```

`CallRealtimeOutputResponseBuffer` intercepts identified model audio before telephony TX. Release requires completed audio, completed final transcript, successful response completion and explicit application approval. Cancelled/failed/incomplete/unsafe responses are dropped.

## Diagnostics

`RealtimeEventTrace` records bounded metadata only: event ordering/timing, byte/character counts, terminal status, sanitized labels and local correlation aliases. It does not retain PCM, transcript text, function arguments/output, credentials, raw provider IDs or raw exception messages.

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
