# Architecture

## Objective

Build a reliable one-phone bridge between an ordinary cellular call and a realtime AI agent on a stock Samsung S22+, while keeping privileged Samsung media access, Realtime networking, business authority, and user takeover as separate replaceable boundaries.

Failure must move toward a normal human call. No remote/model/network operation may be required for immediate TAKE OVER.

## Current high-level system

```text
CallTask / explicit authority
        |
        v
CallWorkflow + CallConfirmationPolicy
        |
        v
CallRealtimeAgentSessionSpec
  - instructions
  - evaluate_proposal tool
  - commit_proposal tool
  - shared commitment authority
        |
        v
CallRealtimeAgentRuntime / SessionController
        |
        v
CallRealtimeSessionOrchestrator
       / \
      /   \
     v     v
Realtime   CallMediaSessionCoordinator
WebSocket          |
     |              v
     |       Shizuku UserService (shell UID 2000)
     |             / \
     |        RX PFD   TX PFD
     |           /       \
     +---- PCM bridge / guarded output ----+
```

The cellular media plane and Realtime/network plane remain independently owned. The orchestrator coordinates generations; it does not move PCM through Binder.

## Frozen Samsung media boundary

Phase 2D is frozen and physically proven on the target S22+.

Production RX:

```text
VOICE_DOWNLINK AudioRecord
  -> SamsungVoiceDownlinkCapture
  -> SamsungDownlinkPipeSession
  -> PFD
  -> app-side endpoint lease
```

Production TX:

```text
app mono PCM16LE
  -> PFD
  -> SamsungUplinkPipeSession
  -> SamsungCallAssistantTrack
  -> duplicate mono to stereo only here
  -> USAGE_CALL_ASSISTANT / AUDIO_STREAM_CALL_ASSISTANT
  -> AUDIO_DEVICE_OUT_TELEPHONY_TX
  -> cellular uplink
```

Required target invariants:

- direct-shell RX construction ordering remains preserved;
- Shizuku uses its separately-proven attributed-context ordering;
- RX uses system/`android` attribution;
- TX uses `com.android.shell` attribution;
- internal media is signed PCM16LE mono;
- stereo duplication occurs only at the Samsung TX boundary;
- transferred PFDs use AutoClose stream ownership;
- one RX+TX generation fails together;
- no per-frame Binder transactions;
- one helper heartbeat/watchdog covers the generation;
- `CallModeWatchdog` ends media when cellular call mode ends.

Do not modify these rules without concrete device regression evidence.

## Privileged helper boundary

`privileged-helper/` owns protected/private Android/Samsung audio primitives only. It contains no OpenAI client or business-policy logic.

`ShizukuCallMediaUserService` runs with shell privilege and exposes bounded control/AIDL operations plus transferred PFD endpoints. Continuous PCM does not travel through Binder.

The normal app owns the PFD ends and higher lifecycle. Helper or app death fails toward media teardown while the cellular call can remain available for the human user.

## App-side media ownership

`CallMediaSessionCoordinator` is the production owner of one app-side privileged media generation.

State model:

```text
IDLE -> BINDING -> PREPARING -> ACTIVE
                    |            |
                    +--> FAILED <-+
                           ^
ACTIVE/PREPARING -> STOPPING -> IDLE/FAILED
```

It owns:

- bind/prepare/start sequencing;
- helper death observation;
- endpoint lease generation checks;
- heartbeat lifetime;
- whole-generation cleanup;
- local TAKE OVER.

`ShizukuCallMediaSessionBackend` performs blocking service/control work on a dedicated executor rather than the Android main looper.

## Realtime client boundary

`realtime-client/` is independent from Samsung telephony details.

Current production transport is WebSocket because this project already owns raw PCM and explicitly controls both stream directions. The interface remains transport-neutral so WebRTC can be benchmarked/replaced later.

Current WebSocket path:

```text
RealtimeCredentialProvider
  -> short-lived client secret
  -> hardened RealtimeOpenAiWebSocketHandshakeFactory
  -> OkHttpRealtimeSocketConnector
  -> RealtimeWebSocketTransport
```

Rules:

- only canonical `wss://api.openai.com/v1/realtime` is accepted by the OpenAI handshake factory;
- model selection belongs to the connection URL;
- standard OpenAI API keys never enter the Android client;
- one transport instance belongs to one Realtime generation;
- stale socket callbacks cannot mutate a newer generation;
- media controls/cancel/close are local non-suspending operations; only connection setup is asynchronous;
- generic server errors do not automatically own telephony cleanup.

OkHttp is kept behind the `realtime-client` module boundary. The project remains on compileSdk/API 36-compatible networking rather than raising the Android toolchain solely for a newer OkHttp artifact.

## Credential architecture

Production security direction:

```text
Android
  -> authenticated HTTPS developer backend
  -> server-side OPENAI_API_KEY
  -> OpenAI POST /v1/realtime/client_secrets
  -> short-lived client secret
  -> Android Realtime WebSocket
```

The developer smoke implementation is `scripts/realtime_credential_broker.py`.

The broker:

- reads `OPENAI_API_KEY` from host environment only;
- listens on loopback by default;
- requires a separate Android/client bearer;
- fixes the Realtime model server-side;
- returns only the minimum short-lived secret data;
- avoids secret/body logging.

Android obtains a pre-authenticated developer-backend request through the typed credential provider/factory boundary. No OkHttp request type is exposed to app business code.

## PCM boundary

Telephony internal format:

```text
PCM signed 16-bit little-endian
mono
16,000 Hz
```

Realtime raw PCM format currently used:

```text
PCM signed 16-bit little-endian
mono
24,000 Hz
```

`RealtimePcmFrameAdapter` performs the isolated 16 kHz <-> 24 kHz conversion. The current deterministic frame-local resampler is suitable for the first lab path and can later be replaced by a higher-quality streaming resampler without touching Samsung media code.

`TelephonyPcmStreamFramer` creates exact 20 ms telephony frames where required. The Realtime->telephony side accepts valid even-length PCM and the pump chunks output before writing toward TX.

## Realtime media/session ownership

`CallRealtimeSessionOrchestrator` owns coordination between one Realtime generation and one media generation.

Bootstrap ordering:

```text
FETCHING_CREDENTIAL
  -> CONNECTING_REALTIME
  -> STARTING_MEDIA
  -> ACTIVE
```

Realtime connects before privileged media starts. This avoids leaving RX/TX pipes open while network setup is pending.

Generation checks reject stale credential/connect/function results after TAKE OVER or restart.

`CallRealtimeMediaSession` owns the active attachment between an already-connected Realtime transport and one ACTIVE call-media generation.

TAKE OVER ordering remains local-first:

```text
mark STOPPING
-> coordinator.takeOverNow() / close telephony endpoints
-> stop PCM pump
-> best-effort response.cancel / Realtime close
```

Remote acknowledgement is never required to restore the human path.

## Bounded PCM pump

`CallRealtimeAudioPump` is a non-owning data plane between `CallRealtimePcmBridge` and `RealtimeTransport`.

RX/telephony -> Realtime:

- dedicated worker reads exact telephony frames;
- converts to 24 kHz;
- queues directly to connected Realtime socket;
- next send failure becomes terminal for the media generation.

Realtime -> telephony:

- callbacks never write directly to PFDs;
- a separate TX worker writes toward telephony;
- ordinary output backlog is bounded (500 ms and 64 chunks);
- remote speech start clears buffered assistant output and cancels the current response;
- overflow/failure reports one terminal error to the session owner.

The pump does not own endpoint lease or transport lifecycle.

## Telephone Agent authority model

Authority is ordinary deterministic application code, not model prose.

`CallTask` contains:

- task objective/details;
- hard `CallConstraints`;
- soft `CallPreferences`;
- explicit `authorizedFacts`.

Only hard constraints and explicitly-authorized facts can authorize actions. Counterparty/model speech cannot add authority.

`CallWorkflow` owns product states including `NEEDS_USER_DECISION`. A business failure such as “no suitable appointment” is a structured completed outcome, not a technical crash.

`CallConfirmationPolicy` evaluates structured `CallProposal` values and fails closed when required data is missing or a hard rule cannot be verified.

## Typed commitment protocol

Commitment uses two Realtime tools rather than transcript heuristics:

```text
evaluate_proposal(proposal)
  -> deterministic app policy
  -> if allowed: one-shot opaque permit
  -> if outside authority: NEEDS_USER_DECISION and hold function response

commit_proposal(permit)
  -> consume exact permit once
  -> commitment=authorized
```

The proposal is not resubmitted during commit, preventing the model from changing price/time/provider between evaluation and authorization.

The follow-up after an allowed proposal response-scopes `tool_choice` to force `commit_proposal`. After successful commit, the following response uses `tool_choice=none`.

Permits are invalidated by replacement proposal, session start, TAKE OVER, close, stale generation, and successful consumption.

## Speech-integrity interception point

A model could otherwise verbally imply acceptance without honoring the intended business-tool flow. The app therefore owns the final telephony TX boundary as an additional defense.

Realtime protocol support now retains:

- `RealtimeOutputPartId(response_id, item_id, output_index, content_index)`;
- output audio PCM deltas;
- final output transcript deltas/done;
- output audio done;
- response terminal status;
- function response identity.

`CallRealtimeOutputResponseBuffer` can hold identified model PCM before telephony TX. When enabled, release is impossible until:

```text
audio.done
AND final transcript.done
AND response.done status == COMPLETED
AND application output policy == RELEASE
```

Cancelled, failed or incomplete responses are dropped; unknown terminal state fails closed. Memory/part/transcript storage is bounded.

This is defense in depth. Transcript text is not treated as cryptographic proof that the PCM contains exactly the same utterance.

### Current incomplete production wiring

At behavior HEAD `94594aa`, the generic buffering/lifecycle mechanism is implemented and host-tested, but the production Telephone Agent output policy is still RED.

`CallRealtimeAgentOutputApprovalPolicyTest` specifies the missing policy and `CallRealtimeAgentSessionSpec` does not yet expose it. Production session/media construction must thread the same policy into `CallRealtimeAudioPump`; otherwise production sessions still run with the optional speech gate disabled.

See `docs/PHASE3_REALTIME_STATUS_2026-09-18.md` for exact current REDs and continuation steps.

## Diagnostic network smoke architecture

The Realtime off-call smoke intentionally does not dial.

Secrets are not Intent extras. A one-shot JSON config is staged into app-private storage and deleted on read. The protected `DiagnosticProbeActivity` (guarded by `android.permission.DUMP`) receives only a boolean trigger.

Success criterion for a real-network off-call smoke:

```text
FETCHING_CREDENTIAL
-> CONNECTING_REALTIME
-> STARTING_MEDIA
-> expected off-call media rejection
```

Reaching ACTIVE while no cellular call is active is a safety failure.

## Current evidence boundary

Physically proven on S22:

- frozen cellular RX/TX and fail-safe media path;
- current production off-call media lifecycle;
- protected Realtime network-smoke entrypoint fails closed when private config is missing.

Not yet physically proven:

- real OpenAI credential fetch from the S22;
- real OpenAI WebSocket/session handshake from the S22;
- real cellular call carrying Realtime AI audio;
- real GA output/function identity ordering against the current speech gate;
- autonomous clinic booking.

Do not collapse host GREEN into device proof.

## Hard architectural rules

1. Hidden/private Samsung/Android audio APIs stay inside the privileged media boundary.
2. No media direction is called proven without target-device evidence.
3. PCM streaming never uses per-frame Binder.
4. Local TAKE OVER cannot depend on network/model completion.
5. App/helper death must disable injection.
6. Long-lived OpenAI credentials never live in the APK or phone smoke config.
7. Hard authority lives in deterministic application policy, not prompts or counterparty speech.
8. A user approval authorizes one exact proposal, not a broader relaxation of constraints.
9. Speech gating is defense in depth and must fail closed on missing/unknown lifecycle identity when enabled.
10. No call recording by default.
11. Do not replace the default dialer until a concrete product requirement justifies it.
12. Keep `.agent` task/result metadata on `agent-control`, never product history.

Authoritative current continuation state: `docs/HANDOFF_NEXT_CHAT.md` and `docs/PHASE3_REALTIME_STATUS_2026-09-18.md`.
