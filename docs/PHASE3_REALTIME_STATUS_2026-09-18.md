# Phase 3 Realtime status — 2026-09-18

Repository: `MichalMatu/android-ai-call-bridge`

Work branch: `work/phase1-live-call-probes`

Current behavior HEAD:

```text
b0c2bac0ff2615f40e31cf525082fc9e33494bf0
fix: close realtime speech gate reds
```

This document is the current evidence ledger for Telephone Agent / OpenAI Realtime work after the frozen Phase 2D Samsung media milestone.

## Frozen evidence baseline

```text
Phase 2B: c10f8dde29f245f8f98fb008a3572c21fe73fe35
Phase 2C: 9c136fc05c5b33f383d72b0b7080ad5b9a754bb4
Phase 2D: 59b0505537a53306acdab6a2a66ca6eed2b3f1c0
```

Phase 2D remains `PROVEN_S22`. The target S22+ physically proved bidirectional cellular RX/TX, Shizuku parity, app/helper death cleanup, transferred-endpoint cleanup, natural call-end cleanup, 20/20 start/abort cycles, and 600 seconds total live media. Resource telemetry is a separate 50.1-second run.

Do not rerun the frozen Phase 2 matrix without a concrete regression. Preserve RX construction/attribution ordering, TX `com.android.shell`, `USAGE_CALL_ASSISTANT` / TELEPHONY_TX, mono PCM16 internally, stereo only at the Samsung TX boundary, PFD AutoClose ownership, whole-generation cleanup, local TAKE OVER, heartbeat, and `CallModeWatchdog`.

## Telephone Agent production stack

The branch contains:

- `CallMediaSessionCoordinator` + production `ShizukuCallMediaSessionBackend`;
- `CallRealtimeAgentRuntime`, `CallRealtimeAgentSessionController`, `CallRealtimeSessionOrchestrator`, and `CallRealtimeMediaSession`;
- Realtime WebSocket + OkHttp connector with generation-safe ownership;
- mono PCM16 16 kHz telephony <-> mono PCM16 24 kHz Realtime adaptation;
- bounded RX/TX audio workers and barge-in cancellation;
- immutable `CallTask`, hard constraints, soft preferences, `authorizedFacts`, workflow state, structured outcomes, and deterministic `CallConfirmationPolicy`;
- typed Realtime function calling;
- strict `evaluate_proposal` parsing and application-side policy evaluation;
- one-shot `CallCommitmentGate` plus `commit_proposal`;
- response-scoped forced `commit_proposal` after approval and `NoTools` after commitment;
- backend credential provider and host-only credential broker;
- protected ADB-only off-call network smoke plumbing;
- typed output audio/transcript/response lifecycle;
- bounded full-response PCM buffering before telephony TX.

## Commitment safety

The application owns commitment authority. Counterparty/model text cannot widen it.

Current sequence:

1. model calls `evaluate_proposal` with structured proposal data;
2. application evaluates deterministic policy;
3. autonomous approval or explicit user approval issues an opaque one-shot permit for exactly that proposal;
4. follow-up forces `commit_proposal`;
5. `commit_proposal` consumes the permit exactly once and returns `commitment=authorized`;
6. only then may the model verbally confirm the external commitment.

A new proposal, new start, TAKE OVER, close, stale generation, or permit reuse invalidates authority.

## Production speech-integrity gate — GREEN

`CallRealtimeOutputResponseBuffer` retains identified model PCM until all required response lifecycle evidence is complete. Release requires:

- identified output-part identity;
- `output_audio.done`;
- final `output_audio_transcript.done`;
- `response.done(COMPLETED)`;
- application-owned approval.

Cancelled/failed/incomplete responses and unknown terminal status fail closed. Buffer limits remain bounded.

At behavior HEAD `b0c2bac0ff2615f40e31cf525082fc9e33494bf0`, the previously missing production approval policy is implemented:

- `CallRealtimeAgentOutputApprovalPolicy` RELEASES only in `ACTIVE_NEGOTIATION` when no commitment permit is pending;
- it DROPS while a permit is pending;
- it DROPS in `NEEDS_USER_DECISION`;
- it DROPS outside normal active negotiation;
- `CallRealtimeAgentSessionSpec` creates it with the exact same `CallCommitmentGate` used by `evaluate_proposal` and `commit_proposal`.

Production wiring is complete through:

```text
CallRealtimeAgentSessionSpec
  -> CallRealtimeAgentSessionController
  -> CallRealtimeSessionOrchestrator
  -> CallRealtimeMediaSession
  -> CallRealtimeAudioPump
  -> CallRealtimeOutputResponseBuffer
```

The production pump therefore no longer receives `outputApprovalPolicy=null` for a normal Telephone Agent session.

Important limitation: the Realtime transcript is defense in depth, not cryptographic proof that every PCM sample exactly matches the transcript.

## Function response identity — GREEN

Production parsing still requires a function call `response_id`; that hardening was not weakened.

The stale `RealtimeWebSocketFunctionTransportTest.incomingFunctionCallReachesTypedListener` fixture now emits a GA-shaped top-level `response_id` and asserts that the value is retained in `RealtimeFunctionCall.responseId`. The malformed-call fixture also includes response identity so it continues testing the intended malformed arguments path.

## Full host gate — GREEN

Evidence task:

```text
realtime-speech-function-host-green-20260918-2600
behavior HEAD: b0c2bac0ff2615f40e31cf525082fc9e33494bf0
status: done
exit_code: 0
```

Passed:

- focused `CallRealtimeAgentOutputApprovalPolicyTest`;
- focused `CallRealtimeAudioPumpSpeechGateTest`;
- focused `RealtimeFunctionToolProtocolTest`;
- focused `RealtimeWebSocketFunctionTransportTest`;
- full `:realtime-client:testDebugUnitTest`;
- full `:app:testDebugUnitTest`;
- `:app:assembleDebug`;
- Python `scripts/test_*.py`: 59 tests, OK;
- production secret-pattern scan for `OPENAI_API_KEY`, `sk-...`, `apiKey`, `api_key`;
- `git diff --check`;
- clean working tree.

No cellular call was made for this gate.

## Credential boundary

The long-lived OpenAI API key must remain host/backend-only and must never enter the APK, Intent, app-private smoke config, or phone.

Implemented developer path:

```text
Android
  -> authenticated HTTPS developer broker
  -> POST /v1/realtime/client_secrets
  -> short-lived client secret
  -> Realtime WebSocket
```

Relevant files:

- `scripts/realtime_credential_broker.py`;
- `scripts/realtime_network_smoke.py`;
- `RealtimeCredentialBackendRequestFactory`;
- `RealtimeCredentialBackendProviderFactory`;
- `BackendRealtimeCredentialProvider`;
- `RealtimeNetworkSmokeConfig`;
- `RealtimeNetworkOffCallSmokeProbe`.

The broker remains loopback-only by default, reads `OPENAI_API_KEY` only from host environment, requires a distinct broker bearer, fixes the model server-side, and returns only the short-lived fields Android needs.

## Real OpenAI off-call smoke — BLOCKED BY EXTERNAL PREREQUISITES

Evidence task:

```text
realtime-openai-offcall-smoke-20260918-2610
behavior HEAD: b0c2bac0ff2615f40e31cf525082fc9e33494bf0
status: done
```

The task intentionally checked only presence before any build/install/network work and found:

```text
openai_api_key_present=false
broker_token_present=false
broker_https_url_present=false
external_prerequisite_blocked=true
```

Therefore the real OpenAI off-call smoke was **not executed**. No security bypass was attempted.

Required host environment for the next attempt:

- `OPENAI_API_KEY` — host broker environment only;
- `AI_CALL_BRIDGE_BROKER_TOKEN` — a separate strong bearer, minimum 32 characters, not an OpenAI key;
- `AI_CALL_BRIDGE_BROKER_HTTPS_URL` — authenticated HTTPS URL reaching the loopback broker.

`OPENAI_REALTIME_MODEL` and `OPENAI_SAFETY_IDENTIFIER` remain optional broker-side settings.

## Exact next physical gate

Once all three external prerequisites above are available to the Local Agent environment, run the genuine S22 off-call smoke without dialing.

Required PASS path:

```text
FETCHING_CREDENTIAL
  -> CONNECTING_REALTIME
  -> STARTING_MEDIA
  -> FAILED
```

The final `FAILED` is expected only because frozen media must reject startup while no cellular call is active. Expected reason: `realtime_connected_off_call_media_rejected` with detail containing `cellular call is not active`.

`ACTIVE` during an off-call smoke is a safety failure.

The runner must preserve `CALL_STATE=0` before and after, delete the one-shot app-private config, and leave no call-media helper alive.

## Gates after genuine off-call PASS

1. First cellular Realtime call: controlled and non-committing. Validate RX/TX intelligibility, latency, barge-in, TAKE OVER, cleanup, and actual GA event ordering / `response_id`.
2. Only after that, attempt a real clinic registration using explicit user-provided facts and constraints. Anything outside authority must enter `NEEDS_USER_DECISION`.

Safe regression number `510100100` is authorized only when a real cellular regression call is actually needed; it is not needed for off-call smoke.

## Local Agent continuation rules

- New chat: bootstrap `[LAB:ADD=android-ai-call-bridge]` and use the fresh returned binding; never copy an older binding blindly.
- Check `.agent/status/daemon.json` before writing the same work branch.
- `.agent/tasks` and `.agent/results` stay only on `agent-control`.
- Direct GitHub edits are preferred when code/docs evidence is sufficient; Local Agent is for local builds/tests/ADB/device work.
- Never launch local Codex from a Local Agent task.
- Do not rerun Phase 2D gates absent concrete regression evidence.
