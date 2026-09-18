# Handoff — Phase 2D frozen / Telephone Agent Phase 3 in progress

Date: 2026-09-18

Repository: `MichalMatu/android-ai-call-bridge`

Work branch: `work/phase1-live-call-probes`

Current behavior baseline before these documentation-only handoff commits:

```text
94594aa8f6e321395d5648dea4dffb243db911fd
feat: require function response identity
```

Read this file together with `docs/PHASE3_REALTIME_STATUS_2026-09-18.md`. That status file is the detailed evidence ledger for the current Phase 3 work.

## New-chat binding rule

This handoff came from Local Agent binding:

```text
repository_id: android-ai-call-bridge
repository: MichalMatu/android-ai-call-bridge
control_branch: agent-control
old_chat_binding: c25f88c0-4682-414c-8062-c47fa4034cb0
```

A **new chat must bootstrap its own Local Agent binding** with `[LAB:ADD=android-ai-call-bridge]` if it is not already bound. Treat the binding returned by that bootstrap as immutable for the new chat. Do not blindly reuse the old binding above if the bootstrap returns another value.

Every Local Agent task JSON must contain exactly the active chat's current `agent_binding`.

## Start rule

Before changing the product branch:

1. read `AGENTS.md`;
2. read this handoff;
3. read `docs/PHASE3_REALTIME_STATUS_2026-09-18.md`;
4. read `docs/PHASE2D_FREEZE_2026-09-18.md`, `docs/ROADMAP.md`, `docs/ARCHITECTURE.md`, `docs/SECURITY_PRIVACY.md`, and `docs/superpowers/plans/2026-09-18-telephone-agent-v1.md` as needed;
5. check the current work-branch HEAD;
6. check `.agent/status/daemon.json` on `agent-control` before writing the same branch.

Do not rerun the frozen Phase 2 physical matrix unless concrete regression evidence requires it.

## Frozen media checkpoints

```text
Phase 2B
branch: milestone/phase2b-proven-s22-20260916
commit: c10f8dde29f245f8f98fb008a3572c21fe73fe35

Phase 2C
branch: milestone/phase2c-shizuku-live-proven-20260916
commit: 9c136fc05c5b33f383d72b0b7080ad5b9a754bb4

Phase 2D
branch: milestone/phase2d-failsafe-proven-s22-20260918
commit: 59b0505537a53306acdab6a2a66ca6eed2b3f1c0
```

Phase 2D physical evidence includes normal-app death, helper death, transferred endpoint loss, 20/20 cycles, natural call end, and 600 seconds total bidirectional live media. Resource telemetry is a separate 50.1-second run; never describe it as 600 seconds of resource telemetry.

Preserve the frozen Samsung invariants: RX construction/attribution ordering, TX `com.android.shell` attribution, `USAGE_CALL_ASSISTANT` / TELEPHONY_TX, mono PCM16 internally, stereo only at the Samsung boundary, PFD AutoClose ownership, whole-generation cleanup, local TAKE OVER, helper heartbeat, and `CallModeWatchdog`.

## Phase 3 components already implemented

The branch now contains the production media coordinator/runtime, production Shizuku backend, Realtime WebSocket stack, PCM conversion, bounded audio pump, Realtime/media session ownership, Telephone Agent workflow/policy models, typed function calling, proposal evaluation, one-shot commitment permits, response-scoped `commit_proposal` forcing, and production Realtime runtime composition.

The commitment path is application-owned:

```text
structured proposal
  -> evaluate_proposal
  -> deterministic CallConfirmationPolicy
  -> autonomous allow OR NEEDS_USER_DECISION
  -> exact one-shot permit
  -> forced commit_proposal
  -> permit consumed once
  -> only then commitment is authorized
```

Counterparty text never widens hard constraints or `authorizedFacts`.

## Credential/backend work already implemented

The Android app never contains a standard OpenAI API key.

Implemented:

- short-lived typed Realtime credentials;
- hardened canonical OpenAI WebSocket handshake;
- `BackendRealtimeCredentialProvider` and safe request/provider factories;
- `scripts/realtime_credential_broker.py`, with host-only `OPENAI_API_KEY`, separate client bearer, loopback default, server-controlled model, minimal response, and no secret logging;
- private one-shot Android network-smoke config;
- protected ADB-only `RealtimeNetworkOffCallSmokeProbe`;
- `scripts/realtime_network_smoke.py`, which stages endpoint/token via ADB stdin rather than argv/Intent.

The secure smoke runner reached 59/59 Python tests.

A physical S22 dry-run with no private config is GREEN fail-closed: expected `config_error`, `CALL_STATE=0 -> 0`, no helper left behind, and no cellular dial.

A real OpenAI S22 network smoke has **not** yet been performed. At the latest environment audit, Local Agent had neither `OPENAI_API_KEY` nor `cloudflared`. Never solve this by putting the long-lived key in the APK or phone.

## Speech-integrity work already implemented

Realtime output now retains typed identity and lifecycle information for output PCM/transcript/response completion. The app has a bounded `CallRealtimeOutputResponseBuffer` that can hold model PCM before telephony TX until the output has:

- identified output-part identity;
- `output_audio.done`;
- final `output_audio_transcript.done`;
- `response.done` with `COMPLETED` status;
- an application-owned release decision.

Cancelled/failed/incomplete responses are discarded. UNKNOWN response status fails closed. Buffering is bounded.

The full response-lifecycle pump path was GREEN at:

```text
7d7bd65738568ee5a29ff6d2674b157584264e54
feat: gate speech release on response completion
```

That gate included focused speech/output-buffer tests, full `realtime-client + app` unit tests, `:app:assembleDebug`, Python tests, key scan, `git diff --check`, and clean tree.

Important: Realtime transcript text is a defense-in-depth signal, not cryptographic proof of the exact PCM contents.

## Current branch is intentionally NOT fully GREEN

This is the critical continuation point.

### RED 1 — production output approval policy

`CallRealtimeAgentOutputApprovalPolicyTest` exists and is a clean RED. At behavior HEAD `94594aa`, production `CallRealtimeAgentOutputApprovalPolicy` and `CallRealtimeAgentSessionSpec.outputApprovalPolicy` are still missing.

The intended policy is already specified by the test:

- RELEASE ordinary speech only during normal `ACTIVE_NEGOTIATION` with no pending commitment permit;
- DROP while a commitment permit is pending;
- DROP during `NEEDS_USER_DECISION`;
- DROP outside active negotiation;
- bind the policy to the exact same `CallCommitmentGate` used by `evaluate_proposal` / `commit_proposal`.

The implementation must then be threaded into the production `CallRealtimeMediaSession` / `CallRealtimeAudioPump`. Do not merely make the test compile while production still constructs the pump with `outputApprovalPolicy=null`.

Evidence task: `realtime-agent-output-policy-red-20260918-2500`.

### RED 2 — latest function response identity hardening leaves one stale legacy fixture

Latest behavior commits:

```text
906e28fe7a5f970559e7a45a07bd2d3a68b2f359  feat: retain realtime function response identity
94594aa8f6e321395d5648dea4dffb243db911fd  feat: require function response identity
```

The focused `RealtimeFunctionToolProtocolTest` is GREEN, but the full `:realtime-client:testDebugUnitTest` has exactly one failure:

```text
RealtimeWebSocketFunctionTransportTest.incomingFunctionCallReachesTypedListener
```

Its fixture still emits `response.output_item.done` without `response_id`. Production parsing now deliberately requires `response_id` for a function call. Do not weaken the parser to preserve that stale fixture. Update/audit the test event to the current GA shape and assert `RealtimeFunctionCall.responseId` is retained.

Evidence task named `realtime-function-response-id-green-20260918-2525` actually ended `status=failed` because of this one legacy test; do not describe it as a full GREEN.

## Exact recommended continuation

1. Confirm branch HEAD and daemon idle.
2. Implement `CallRealtimeAgentOutputApprovalPolicy` to satisfy the already-existing RED contract.
3. Add `outputApprovalPolicy` to `CallRealtimeAgentSessionSpec` using the same `CallCommitmentGate`.
4. Thread that policy through production session/media construction into `CallRealtimeAudioPump`, enabling the full-response gate in real Telephone Agent sessions.
5. Update the stale `RealtimeWebSocketFunctionTransportTest` fixture with `response_id`; assert identity reaches the typed function call.
6. Run focused tests, then full `:realtime-client:testDebugUnitTest`, `:app:testDebugUnitTest`, `:app:assembleDebug`, all Python tests, key scan, `git diff --check`, clean tree.
7. Only after full host GREEN, proceed to a **real OpenAI off-call network smoke** on S22 using the host broker + HTTPS tunnel. No cellular dial.
8. If that passes, first real Realtime cellular call should be non-committing and controlled. Validate audio, latency, barge-in, TAKE OVER, cleanup, and actual GA event ordering.
9. Only after those gates should a real clinic booking be attempted with explicit user facts/constraints and `NEEDS_USER_DECISION` for anything outside authority.

## Device facts

```text
Samsung Galaxy S22+ SM-S906B
serial: RFCT70L7E8J
Android 16 / API 36 / One UI 8
Shizuku shell UID: 2000
```

Safe regression number `510100100` remains authorized only when a real cellular regression test is genuinely needed. A Realtime off-call network smoke does not need it.

For future live-call validation: direct USB-C, Bluetooth off during test, voice-call stream muted before dial and after ACTIVE, speakerphone off, restore Bluetooth afterwards.

## Local Agent mechanics

Use `agent-control` for `.agent/tasks` / `.agent/results` only; never merge those execution files into the product branch.

Typical task shape:

```json
{
  "id": "...",
  "agent_binding": "<EXACT CURRENT CHAT BINDING>",
  "mode": "commands",
  "work_branch": "work/phase1-live-call-probes",
  "allow_write": false,
  "resources": [],
  "command_timeout": 900,
  "idle_timeout": 300,
  "task_timeout": 1200,
  "memory_limit_mb": 4096,
  "commands": ["..."]
}
```

System Gradle path proven locally:

```text
$HOME/.gradle/local-agent/gradle-9.6.0/bin/gradle
```

Android SDK:

```text
$HOME/Library/Android/sdk
```

Never assume `./gradlew` exists. Never launch local Codex from a Local Agent task.
