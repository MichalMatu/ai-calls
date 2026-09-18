# Architecture

## Objective

Build a reliable one-phone bridge between an ordinary cellular call and a Realtime AI agent on a stock Samsung S22+, while keeping privileged Samsung media access, Realtime networking, deterministic business authority, diagnostics, and user takeover as separate boundaries.

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
  - evaluate_proposal
  - commit_proposal
  - shared CallCommitmentGate
  - output approval policy
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
     |
 optional bounded/redacted RealtimeEventTrace
```

The cellular media plane and Realtime/network plane remain independently owned. The orchestrator coordinates generations; it does not move PCM through Binder. Diagnostics observe the transport but never own lifecycle.

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

`CallMediaSessionCoordinator` owns one privileged media generation.

State model:

```text
IDLE -> BINDING -> PREPARING -> ACTIVE
                    |            |
                    +--> FAILED <-+
                           ^
ACTIVE/PREPARING -> STOPPING -> IDLE/FAILED
```

It owns bind/prepare/start sequencing, helper death observation, endpoint generation checks, heartbeat lifetime, whole-generation cleanup and local TAKE OVER.

`ShizukuCallMediaSessionBackend` performs blocking service/control work on a dedicated executor rather than the Android main looper.

## Realtime client boundary

`realtime-client/` is independent from Samsung telephony details.

Current production transport is WebSocket because the app already owns raw PCM and explicitly controls both directions. The interface remains transport-neutral so WebRTC can be benchmarked/replaced later.

```text
RealtimeCredentialProvider
  -> short-lived client secret
  -> RealtimeOpenAiWebSocketHandshakeFactory
  -> OkHttpRealtimeSocketConnector
  -> RealtimeWebSocketTransport
  -> optional TracingRealtimeTransport decorator
```

Rules:

- only canonical `wss://api.openai.com/v1/realtime` is accepted by the OpenAI handshake factory;
- standard OpenAI API keys never enter the Android client;
- one transport instance belongs to one Realtime generation;
- stale socket callbacks cannot mutate a newer generation;
- cancel/close are local non-suspending operations; only connection setup is asynchronous;
- generic server errors do not automatically own telephony cleanup.

## Credential architecture

```text
Android
  -> authenticated HTTPS developer backend
  -> server-side OPENAI_API_KEY
  -> OpenAI POST /v1/realtime/client_secrets
  -> short-lived client secret
  -> Android Realtime WebSocket
```

The developer smoke implementation is `scripts/realtime_credential_broker.py`.

The broker reads `OPENAI_API_KEY` from host environment only, listens loopback by default, requires a separate Android bearer, fixes the Realtime model server-side, returns only minimum short-lived secret data and avoids secret/body logging.

Android obtains a pre-authenticated developer-backend request through the typed credential provider/factory boundary. No OkHttp request type is exposed to app business code.

## PCM boundary

Telephony internal format:

```text
PCM signed 16-bit little-endian
mono
16,000 Hz
```

Realtime raw PCM format:

```text
PCM signed 16-bit little-endian
mono
24,000 Hz
```

`RealtimePcmFrameAdapter` isolates 16 kHz <-> 24 kHz conversion. `TelephonyPcmStreamFramer` creates exact 20 ms telephony frames where required. Realtime output accepts valid even-length PCM and the pump chunks it before telephony TX.

## Realtime media/session ownership

`CallRealtimeSessionOrchestrator` owns coordination between one Realtime generation and one media generation.

Bootstrap ordering:

```text
FETCHING_CREDENTIAL
  -> CONNECTING_REALTIME
  -> STARTING_MEDIA
  -> ACTIVE
```

Realtime connects before privileged media starts. Generation checks reject stale credential/connect/function results after TAKE OVER or restart.

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

Telephony -> Realtime:

- dedicated worker reads exact telephony frames;
- converts to 24 kHz;
- sends through the connected transport;
- send failure becomes terminal for the media generation.

Realtime -> telephony:

- callbacks never write directly to PFDs;
- separate TX worker owns telephony writes;
- ordinary backlog is bounded;
- remote speech start clears buffered assistant output and cancels current response;
- overflow/failure reports one terminal error to the session owner.

The pump does not own endpoint lease or transport lifecycle.

## Telephone Agent authority model

Authority is deterministic application code, not model prose.

`CallTask` contains task objective/details, hard `CallConstraints`, soft `CallPreferences`, and explicit `authorizedFacts`.

Counterparty/model speech cannot add authority. `CallWorkflow` owns states including `NEEDS_USER_DECISION`. `CallConfirmationPolicy` evaluates structured `CallProposal` values and fails closed when a hard rule cannot be verified.

## Typed commitment protocol

```text
evaluate_proposal(proposal)
  -> deterministic app policy
  -> if allowed: exact one-shot opaque permit
  -> if outside authority: NEEDS_USER_DECISION and hold function response

commit_proposal(permit)
  -> consume exact permit once
  -> commitment=authorized
```

The proposal is not resubmitted during commit, preventing proposal substitution. Allowed follow-up response-scopes `tool_choice` to force `commit_proposal`; after successful commitment the next response uses `tool_choice=none`.

Permits are invalidated by replacement proposal, session start, TAKE OVER, close, stale generation and successful consumption.

## Speech-integrity interception point

`CallRealtimeOutputResponseBuffer` holds identified model PCM before telephony TX.

Release is impossible until:

```text
audio.done
AND final transcript.done
AND response.done status == COMPLETED
AND application output policy == RELEASE
```

Production `CallRealtimeAgentOutputApprovalPolicy` is created by `CallRealtimeAgentSessionSpec` with the same `CallCommitmentGate` used by proposal/commit handlers and is wired through controller -> orchestrator -> media session -> audio pump.

It RELEASES ordinary speech only in safe `ACTIVE_NEGOTIATION` with no pending commitment permit and DROPS output while a permit is pending, in `NEEDS_USER_DECISION`, and outside active negotiation.

Cancelled, failed, incomplete and unknown-terminal responses fail closed. Buffer memory/part/transcript storage is bounded.

This is defense in depth: transcript text is not cryptographic proof of exact PCM contents.

## Privacy-safe Realtime diagnostics

`RealtimeEventTrace` + `TracingRealtimeTransport` provide bounded physical-test evidence without storing conversation content.

The trace can record relative timing, lifecycle event type, PCM byte count, transcript character count, terminal response status, sanitized function name/error class and local correlation aliases.

It never stores PCM bytes, transcript text, function arguments/output, credentials, raw error messages or raw provider response/item/call IDs. Raw IDs are transient; internal correlation keys are per-trace salted SHA-256 digests and rendered evidence exposes only `R1/I1/C1`-style aliases.

Diagnostic labels are bounded ASCII. Control characters, newline or oversized labels become `REDACTED`. The same sanitizer protects `RealtimeFunctionCall.toString()`.

Both event and identity tables are bounded. The trace is optional/caller-owned and does not own or delay TAKE OVER, transport close or media cleanup.

## Diagnostic network smoke architecture

The Realtime off-call smoke intentionally does not dial.

A one-shot JSON config is staged into app-private storage over ADB stdin and deleted on read. The protected `DiagnosticProbeActivity` (guarded by `android.permission.DUMP`) receives only a boolean trigger.

Success criterion for a genuine OpenAI off-call smoke:

```text
FETCHING_CREDENTIAL
-> CONNECTING_REALTIME
-> STARTING_MEDIA
-> expected off-call media rejection
```

Reaching ACTIVE while no cellular call is active is a safety failure.

The probe now appends an optional compact redacted `trace=...` evidence line. The trace cannot affect PASS/FAIL; the deterministic state tracker remains authoritative.

## Current evidence boundary

Physically proven on S22:

- frozen cellular RX/TX and fail-safe media path;
- current production off-call media lifecycle;
- protected Realtime network-smoke entrypoint fails closed when private config is missing.

Host-green but not yet physically proven against OpenAI:

- production speech approval gating;
- required function `response_id` handling;
- privacy-safe Realtime event trace/off-call trace wiring.

Not yet physically proven:

- genuine OpenAI credential fetch/session handshake from the S22;
- cellular call carrying Realtime AI audio;
- real GA output/function ordering against current speech gate;
- autonomous clinic booking.

Do not collapse host GREEN into device proof.

## Hard architectural rules

1. Hidden/private Samsung/Android audio APIs stay inside the privileged media boundary.
2. No media direction is called proven without target-device evidence.
3. PCM streaming never uses per-frame Binder.
4. Local TAKE OVER cannot depend on network/model completion.
5. App/helper death must disable injection.
6. Long-lived OpenAI credentials never live in APK or phone smoke config.
7. Hard authority lives in deterministic application policy, not prompts/counterparty speech.
8. User approval authorizes one exact proposal, not a broader relaxation of constraints.
9. Speech gating fails closed on missing/unknown lifecycle identity when enabled.
10. Diagnostic evidence is bounded/redacted and does not store speech content by default.
11. No call recording by default.
12. Do not replace the default dialer without a concrete product requirement.
13. Keep `.agent` task/result metadata on `agent-control`, never product history.

Authoritative continuation: `docs/HANDOFF_NEXT_CHAT.md` and `docs/PHASE3_REALTIME_STATUS_2026-09-18.md`.
