# Phase 3 Realtime status — 2026-09-18

Repository: `MichalMatu/android-ai-call-bridge`

Work branch: `work/phase1-live-call-probes`

Current behavior HEAD:

```text
c939a9bbcc4603d846ab8e3cd17d2b8dd19d17e5
fix: sanitize realtime diagnostic labels
```

This is the current evidence ledger for Telephone Agent / OpenAI Realtime work above the frozen Phase 2D Samsung media milestone.

## Frozen evidence baseline

```text
Phase 2B: c10f8dde29f245f8f98fb008a3572c21fe73fe35
Phase 2C: 9c136fc05c5b33f383d72b0b7080ad5b9a754bb4
Phase 2D: 59b0505537a53306acdab6a2a66ca6eed2b3f1c0
```

Phase 2D remains `PROVEN_S22`. Preserve RX construction/attribution ordering, TX `com.android.shell`, `USAGE_CALL_ASSISTANT` / TELEPHONY_TX, mono PCM16 internally, stereo only at the Samsung TX boundary, PFD AutoClose ownership, whole-generation cleanup, local TAKE OVER, heartbeat and `CallModeWatchdog`.

Do not rerun the frozen Phase 2 matrix without a concrete regression.

## Telephone Agent production stack

Implemented and host-tested:

- `CallMediaSessionCoordinator` + production `ShizukuCallMediaSessionBackend`;
- `CallRealtimeAgentRuntime`, session controller, orchestrator and media session;
- Realtime WebSocket + OkHttp connector with generation safety;
- mono PCM16 16 kHz telephony <-> 24 kHz Realtime conversion;
- bounded RX/TX audio pump + local barge-in;
- immutable task/constraints/preferences/`authorizedFacts` workflow model;
- deterministic `CallConfirmationPolicy` and `NEEDS_USER_DECISION`;
- typed Realtime function calling;
- strict `evaluate_proposal`;
- one-shot `CallCommitmentGate`;
- forced `commit_proposal` after approval and `NoTools` after commitment;
- backend credential provider + host-only credential broker;
- protected ADB-only off-call network smoke plumbing;
- typed output audio/transcript/response lifecycle;
- bounded full-response PCM buffering before telephony TX;
- production output approval policy using the same commitment gate;
- required Realtime function `response_id` identity;
- bounded privacy-safe Realtime event trace for physical evidence;
- bounded diagnostic-label sanitization for trace/debug rendering.

## Commitment and speech safety — HOST_GREEN

The application owns commitment authority. Counterparty/model text cannot widen it.

Sequence:

1. model reports one structured proposal through `evaluate_proposal`;
2. deterministic application policy evaluates it;
3. autonomous allow or explicit user approval issues an opaque one-shot permit for that exact proposal;
4. response follow-up forces `commit_proposal`;
5. `commit_proposal` consumes the permit once;
6. only then may the model verbally confirm the external commitment.

`CallRealtimeOutputResponseBuffer` holds identified model PCM until `output_audio.done`, final transcript completion and `response.done(COMPLETED)`. `CallRealtimeAgentOutputApprovalPolicy` releases ordinary speech only during safe `ACTIVE_NEGOTIATION` when no commitment permit is pending. It drops output while a permit is pending, in `NEEDS_USER_DECISION`, and outside active negotiation.

Production wiring is:

```text
CallRealtimeAgentSessionSpec
  -> CallRealtimeAgentSessionController
  -> CallRealtimeSessionOrchestrator
  -> CallRealtimeMediaSession
  -> CallRealtimeAudioPump
  -> CallRealtimeOutputResponseBuffer
```

Cancelled/failed/incomplete responses and unknown terminal status fail closed. A Realtime transcript remains defense in depth, not cryptographic proof of exact PCM contents.

## Function response identity — HOST_GREEN

Production parsing requires `response_id` for typed function calls. The stale transport fixture was updated to current GA shape and asserts that `RealtimeFunctionCall.responseId` retains the value. The parser was not weakened.

## Privacy-safe Realtime event trace — HOST_GREEN

Behavior commits:

```text
5e07238150c9e6d88fe530704001db036df12903  feat: add bounded realtime event trace
9f02556247bcc6470f4b93b6bb508491a472883c  fix: harden realtime trace verification
257484dd0f88dac0fee1b05feff4edeef3938044  test: fix realtime trace listener fixture
c8be36d27b05067574d99858e55d25596f2edbdf  feat: include redacted realtime trace in off-call smoke
c939a9bbcc4603d846ab8e3cd17d2b8dd19d17e5  fix: sanitize realtime diagnostic labels
```

`RealtimeEventTrace` + `TracingRealtimeTransport` provide bounded protocol evidence without becoming a recording/transcript feature.

The trace records only metadata such as connect lifecycle, response cancellation, audio byte counts, transcript character counts, output completion, response status, remote speech boundaries, sanitized function name/error class and redacted response/call correlation.

It does **not** retain PCM, transcript text, function arguments/output, credentials, raw exception messages or raw provider IDs. Response/item/call identities are represented as local aliases such as `R1`, `I1`, `C1`; internal correlation keys use a per-trace random salt + SHA-256 rather than raw IDs. Event and identity tables are bounded.

`CallRealtimeAgentRuntime.create` accepts an optional caller-owned trace and wraps each fresh transport only when supplied. Normal production behavior is unchanged with `eventTrace=null`.

The protected `RealtimeNetworkOffCallSmokeProbe` supplies a trace and returns an optional compact `trace=...` evidence line. The trace supplements the existing state gate; it cannot turn a failed smoke into PASS.

### Diagnostic-label injection hardening

The function name is model/provider-controlled enough that diagnostic rendering must not trust it as arbitrary log text. Two explicit RED tests proved that newline/oversized labels were previously renderable through:

- `RealtimeEventTrace`;
- `RealtimeFunctionCall.toString()`.

Evidence:

```text
realtime-trace-label-sanitization-red-20260918-2750
realtime-function-debug-label-red-20260918-2760
```

`RealtimeDiagnosticLabel` now allows only bounded ASCII diagnostic labels (`A-Z`, `a-z`, `0-9`, `_`, `-`, `.`, `:`, `$`, maximum 64 characters). Control characters, newline, empty/oversized or otherwise unsafe labels render as the constant `REDACTED`. The same sanitizer is used by event trace function/error labels and `RealtimeFunctionCall.toString()`.

Implementation plan: `docs/superpowers/plans/2026-09-18-realtime-event-trace.md`.

## Current full host gate — GREEN

Latest evidence:

```text
.agent/results/realtime-diagnostic-labels-host-green-20260918-2770.json
behavior HEAD: c939a9bbcc4603d846ab8e3cd17d2b8dd19d17e5
status: done
exit_code: 0
```

Passed:

- focused `RealtimeEventTraceTest`, including both diagnostic-label injection cases;
- focused `RealtimeNetworkSmokeStateTrackerTest`;
- focused Python network-smoke tests: 5/5;
- full `:realtime-client:testDebugUnitTest`;
- full `:app:testDebugUnitTest`;
- `:app:assembleDebug`;
- Python `scripts/test_*.py`: 59 tests, OK;
- production secret-pattern scan;
- `git diff --check`;
- clean worktree.

No cellular call was made.

## Credential boundary

The long-lived OpenAI API key remains host/backend-only and must never enter APK, Intent, app-private smoke config, ADB argv or phone.

Current developer path:

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

The broker is loopback-only by default, reads `OPENAI_API_KEY` from host environment, requires a distinct broker bearer, fixes the model server-side and returns only the short-lived fields Android needs.

## Genuine OpenAI off-call smoke — BLOCKED BY EXTERNAL PREREQUISITES

Fresh prerequisite recheck after the diagnostic hardening:

```text
.agent/results/realtime-openai-offcall-smoke-recheck-20260918-2780.json
branch HEAD checked: 50377784b98b4396857c1e278afc688513f4e636
openai_api_key_present=false
broker_token_present=false
broker_https_url_present=false
external_prerequisite_blocked=true
```

The task stopped before build/install/network work, so the genuine OpenAI smoke still has **not** executed. No cellular call was made and no security bypass was attempted.

Required externally in the Local Agent host environment:

```text
OPENAI_API_KEY
AI_CALL_BRIDGE_BROKER_TOKEN
AI_CALL_BRIDGE_BROKER_HTTPS_URL
```

Rules:

- `OPENAI_API_KEY` stays only in the host broker process environment;
- broker token is distinct from the OpenAI key, at least 32 characters and not `sk-...`;
- HTTPS URL reaches the loopback broker through a protected/authenticated path;
- Android receives only broker URL + broker bearer through the one-shot ADB-stdin path.

## Exact next physical gate

Once all three prerequisites exist, run the genuine off-call smoke on `RFCT70L7E8J` with cellular call state idle.

Required PASS state path:

```text
FETCHING_CREDENTIAL
  -> CONNECTING_REALTIME
  -> STARTING_MEDIA
  -> FAILED
```

Expected reason: `realtime_connected_off_call_media_rejected`. The final `FAILED` is correct because frozen media must reject startup with no cellular call. `ACTIVE` off-call is a safety failure.

Also require:

- `CALL_STATE=0` before and after;
- one-shot app-private config deleted;
- no call-media helper left alive;
- no standard OpenAI key on Android;
- redacted trace evidence for actual Realtime ordering.

## Gates after genuine off-call PASS

1. First cellular Realtime call must be controlled and non-committing.
2. Use the redacted trace to validate actual GA event ordering/identity and timing alongside RX/TX intelligibility, latency, barge-in, TAKE OVER and cleanup.
3. Only then attempt a real clinic registration using explicit user facts and constraints.
4. Anything outside authority must enter `NEEDS_USER_DECISION`.

Target: Samsung Galaxy S22+ SM-S906B, serial `RFCT70L7E8J`, Android 16 / API 36 / One UI 8.

Safe regression number `510100100` is authorized only when a real cellular regression call is genuinely required; it is not needed for off-call smoke.

## Local Agent continuation rules

- New chat: bootstrap `[LAB:ADD=android-ai-call-bridge]`; use the fresh binding returned for that chat.
- Check `.agent/status/daemon.json` before using/writing the same work branch.
- `.agent/tasks` and `.agent/results` stay only on `agent-control`.
- Direct GitHub edits are preferred for code/docs when diff evidence is sufficient; Local Agent is for local builds/tests/ADB/device work.
- Never launch local Codex from a Local Agent task.
- Do not rerun Phase 2D gates absent concrete regression evidence.
