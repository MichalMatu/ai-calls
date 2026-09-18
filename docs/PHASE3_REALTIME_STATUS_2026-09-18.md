# Phase 3 Realtime status — 2026-09-18

Repository: `MichalMatu/android-ai-call-bridge`

Work branch: `work/phase1-live-call-probes`

Behavior HEAD at this handoff: `94594aa8f6e321395d5648dea4dffb243db911fd` (`feat: require function response identity`).

This document is the current evidence ledger for Telephone Agent / OpenAI Realtime work after the frozen Phase 2D Samsung media milestone. It does not supersede the Phase 2D freeze evidence.

## Evidence baseline that remains frozen

Phase 2B: `c10f8dde29f245f8f98fb008a3572c21fe73fe35`

Phase 2C: `9c136fc05c5b33f383d72b0b7080ad5b9a754bb4`

Phase 2D: `59b0505537a53306acdab6a2a66ca6eed2b3f1c0`

The target S22+ has physically proven bidirectional cellular RX/TX, Shizuku parity, app/helper death cleanup, endpoint-loss cleanup, natural call-end cleanup, 20/20 start/abort cycles, and 600 seconds total live media. Resource telemetry is a separate 50.1-second run, not 600 seconds.

Do not rerun the frozen Phase 2 matrix unless a concrete regression points there.

## Telephone Agent layers implemented

The product branch now contains:

- `CallMediaSessionCoordinator` and production `ShizukuCallMediaSessionBackend`;
- production `CallRealtimeAgentRuntime`, `CallRealtimeAgentSessionController`, `CallRealtimeSessionOrchestrator`, and `CallRealtimeMediaSession` ownership layers;
- immutable `CallTask`, hard `CallConstraints`, soft `CallPreferences`, `authorizedFacts`, `CallWorkflow`, structured outcomes, and deterministic `CallConfirmationPolicy`;
- privacy-safe model rendering so ordinary `toString()` paths do not expose task/proposal/outcome details;
- OpenAI Realtime transport-neutral interfaces plus OkHttp WebSocket implementation;
- hardened OpenAI WebSocket target validation and ephemeral-credential-only client authentication;
- mono PCM16 16 kHz telephony <-> mono PCM16 24 kHz Realtime adapter;
- bounded RX/TX workers, local barge-in cancellation, and generation-safe cleanup;
- typed Realtime function calling;
- strict `evaluate_proposal` parsing into `CallProposal` and deterministic application-side policy evaluation;
- one-shot `CallCommitmentGate` and `commit_proposal`, preventing proposal substitution/replay;
- response-scoped `ForceFunction("commit_proposal")` after approval and `NoTools` after commitment;
- task-derived Realtime instructions that separate hard authority, preferences, authorized facts, and untrusted counterparty speech;
- a host-side credential broker and Android backend credential provider boundary;
- ADB-only off-call Realtime network smoke plumbing that never takes broker endpoint/token from an exported Intent.

## Credential boundary

The standard OpenAI API key must remain host/backend-only and must never enter the APK or phone configuration.

Implemented developer path:

`Android -> authenticated HTTPS developer broker -> POST /v1/realtime/client_secrets -> short-lived ek_... secret -> Realtime WebSocket`

Relevant files:

- `scripts/realtime_credential_broker.py`
- `scripts/realtime_network_smoke.py`
- `RealtimeCredentialBackendRequestFactory`
- `RealtimeCredentialBackendProviderFactory`
- `BackendRealtimeCredentialProvider`
- `RealtimeNetworkSmokeConfig`
- `RealtimeNetworkOffCallSmokeProbe`

The broker is loopback-only by default, reads `OPENAI_API_KEY` from host environment, uses a separate client bearer, fixes the model server-side, and returns only the short-lived secret fields required by Android.

The secure smoke runner passed 5/5 focused tests and the Python suite reached 59/59 at `realtime-network-smoke-runner-green-20260918-2055`.

Physical S22 fail-closed dry-run without a config file is GREEN: `CALL_STATE 0 -> 0`, expected `config_error`, no helper left alive, no cellular dial.

### Not yet proven

A real OpenAI network/session smoke from the S22 has **not** happened. The Local Agent host audit found no `OPENAI_API_KEY` and no `cloudflared` in its environment. Do not fake this by embedding a long-lived key in the APK or by sending one to the phone.

## Commitment safety

The application owns commitment authority. Model or counterparty text cannot widen it.

Current intended sequence:

1. model calls `evaluate_proposal` with structured offer data;
2. application evaluates hard constraints/preferences;
3. autonomous approval or explicit user approval issues an opaque, one-shot permit for exactly that proposal;
4. response follow-up forces `commit_proposal`;
5. `commit_proposal` consumes the permit exactly once and returns `commitment=authorized`;
6. only then may the model verbally confirm the external commitment.

A new proposal, new start, TAKE OVER, close, stale generation, or reuse invalidates the permit.

This is stronger than prompt-only safety, but real GA event ordering has not yet been physically/network validated.

## Speech-integrity defense in depth

The Realtime layer now parses and routes output identity/lifecycle events including:

- `response.output_audio.delta` with output-part identity when present;
- `response.output_audio_transcript.delta`;
- `response.output_audio_transcript.done`;
- `response.output_audio.done`;
- `response.done` with terminal status;
- function calls tied to a Realtime `response_id` at the current HEAD.

`CallRealtimeOutputResponseBuffer` is implemented and host-tested. When an output approval policy is supplied:

- identified PCM is held before telephony TX;
- release is impossible until `audio.done`, final `transcript.done`, and `response.done(COMPLETED)`;
- cancelled/failed/incomplete responses are discarded;
- unknown terminal status fails closed;
- buffer defaults are bounded to 12 seconds of 24 kHz mono PCM16, four pending parts, and 16,384 transcript characters;
- final transcript is used for approval; transcript deltas are diagnostic only.

The response-lifecycle pump gate passed full app/realtime build, APK, Python tests, key scan, and clean-tree checks at behavior commit `7d7bd65738568ee5a29ff6d2674b157584264e54` (`realtime-response-lifecycle-pump-green-20260918-2450`).

Important limitation: a Realtime transcript is **not** a cryptographic proof that every PCM sample says exactly the transcript. Treat this as defense in depth, not mathematical equivalence.

## Current RED / unfinished work at behavior HEAD 94594aa

There are two explicit unfinished items. A new chat should start here, not from older plans.

### 1. Production output approval policy is RED and not wired

`CallRealtimeAgentOutputApprovalPolicyTest` defines the intended application policy, but production `CallRealtimeAgentOutputApprovalPolicy` and `CallRealtimeAgentSessionSpec.outputApprovalPolicy` are not implemented at this HEAD.

The test requires:

- RELEASE only during normal `ACTIVE_NEGOTIATION` with no pending commitment authorization;
- DROP while a commitment permit is pending;
- DROP during `NEEDS_USER_DECISION` and outside active negotiation;
- bind the output policy to the exact same `CallCommitmentGate` used by proposal/commit handlers.

Evidence: `realtime-agent-output-policy-red-20260918-2500` is a clean RED due the missing production type/property.

The next implementation must also thread this policy through `CallRealtimeMediaSession`/`CallRealtimeAudioPump` so production sessions actually enable the full-response speech gate. Do not merely make the unit test compile while leaving `outputApprovalPolicy=null` in the production pump.

### 2. Latest function `response_id` hardening has one stale legacy test

Commits:

- `906e28fe7a5f970559e7a45a07bd2d3a68b2f359` — retain function response identity;
- `94594aa8f6e321395d5648dea4dffb243db911fd` — require function response identity.

The focused `RealtimeFunctionToolProtocolTest` passes, but the subsequent full `:realtime-client:testDebugUnitTest` has exactly one failure: `RealtimeWebSocketFunctionTransportTest.incomingFunctionCallReachesTypedListener` still constructs `response.output_item.done` without `response_id`.

Do not weaken the production parser just to satisfy that stale fixture. Update/audit the fixture against current GA event shape, assert the response id reaches `RealtimeFunctionCall`, and rerun the complete host gate.

## Exact next host gate

After implementing production output approval wiring and fixing the stale function-call fixture, run:

- focused output-policy / speech-gate / function-response-id tests;
- full `:realtime-client:testDebugUnitTest`;
- full `:app:testDebugUnitTest`;
- `:app:assembleDebug`;
- all Python `scripts/test_*.py`;
- long-lived-key pattern scan over production Android/Realtime code;
- `git diff --check` and clean working tree.

Do not perform a cellular call merely to close these host-side gaps.

## Next physical gates after host GREEN

1. **Real OpenAI off-call network smoke on the S22.** Supply `OPENAI_API_KEY` only to the host broker environment and expose the loopback broker through an authenticated HTTPS endpoint/tunnel. Use `scripts/realtime_network_smoke.py`. No cellular dial. PASS requires credential fetch + Realtime WebSocket/session setup to reach media startup, followed by expected off-call media rejection. `ACTIVE` while off-call is a safety failure.
2. **First real cellular Realtime call should be non-committing.** Use a controlled/safe number, validate RX/TX intelligibility, latency, barge-in, TAKE OVER, cleanup, and actual GA output/function event ordering. Do not make the first networked call a clinic booking.
3. **Only after that**, attempt a real clinic registration with explicit user-provided facts/constraints. Outside-authority proposals must enter `NEEDS_USER_DECISION`; user TAKE OVER remains available immediately.

Calendar integration is optional/later and is not required for the first successful registration.

## Local Agent rules for continuation

- Read `AGENTS.md` first.
- Check `.agent/status/daemon.json` before any write to the work branch.
- Every task must use exactly `agent_binding=c25f88c0-4682-414c-8062-c47fa4034cb0` in the current bound chat. A new chat must bootstrap/rebind and use the fresh binding it receives; never copy an old binding if bootstrap returns another.
- `.agent/tasks` / `.agent/results` live only on `agent-control`.
- Use direct GitHub edits for code/docs where diff evidence is sufficient; Local Agent for local builds/tests/ADB/device work.
- Never launch local Codex from a Local Agent task.
- Do not rerun Phase 2D physical gates without concrete regression evidence.
