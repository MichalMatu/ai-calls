# Handoff — Phase 2D frozen / Telephone Agent Phase 3 host GREEN

Date: 2026-09-18

Repository: `MichalMatu/android-ai-call-bridge`

Work branch: `work/phase1-live-call-probes`

Current behavior HEAD before this documentation-only handoff commit:

```text
b0c2bac0ff2615f40e31cf525082fc9e33494bf0
fix: close realtime speech gate reds
```

Read this file together with `docs/PHASE3_REALTIME_STATUS_2026-09-18.md`, which is the detailed evidence ledger.

## New-chat Local Agent binding rule

This handoff came from Local Agent binding:

```text
repository_id: android-ai-call-bridge
repository: MichalMatu/android-ai-call-bridge
control_branch: agent-control
old_chat_binding: c25f88c0-4682-414c-8062-c47fa4034cb0
```

A new chat must bootstrap its own binding with `[LAB:ADD=android-ai-call-bridge]` if not already bound. Treat the fresh returned `LA_AGENT` / `agent_binding` as immutable for that chat. Never blindly reuse the old binding above if bootstrap returns another value.

Every `.agent/tasks/*.json` must contain exactly the current chat binding.

## Start rule

Before changing the product branch:

1. read `AGENTS.md`;
2. read this handoff;
3. read `docs/PHASE3_REALTIME_STATUS_2026-09-18.md`;
4. read `docs/PHASE2D_FREEZE_2026-09-18.md`, `docs/ROADMAP.md`, `docs/ARCHITECTURE.md`, `docs/SECURITY_PRIVACY.md`, and the Telephone Agent plan as needed;
5. verify current `work/phase1-live-call-probes` HEAD instead of assuming it;
6. check `.agent/status/daemon.json` on `agent-control` before writing the same branch.

Do not rerun the frozen Phase 2 physical matrix without concrete regression evidence.

## Frozen media checkpoint

```text
branch: milestone/phase2d-failsafe-proven-s22-20260918
commit: 59b0505537a53306acdab6a2a66ca6eed2b3f1c0
status: PROVEN_S22
```

Preserve Samsung invariants: RX construction/attribution ordering, TX `com.android.shell`, `USAGE_CALL_ASSISTANT` / TELEPHONY_TX, mono PCM16 internally, stereo only at the Samsung TX boundary, PFD AutoClose ownership, whole-generation cleanup, local TAKE OVER, heartbeat, and `CallModeWatchdog`.

Do not repeat the full Phase 2D matrix unless a concrete regression points there.

## Current Phase 3 production state

Implemented and retained:

- `CallMediaSessionCoordinator` + production Shizuku backend;
- `CallRealtimeAgentRuntime`, controller, orchestrator, and media session;
- Realtime WebSocket + OkHttp connector and generation safety;
- 16 kHz telephony <-> 24 kHz Realtime mono PCM16;
- bounded audio pump + barge-in;
- `CallTask`, hard constraints, soft preferences, `authorizedFacts`, deterministic workflow and structured outcomes;
- typed Realtime function calling;
- `evaluate_proposal`;
- one-shot `CallCommitmentGate`;
- forced `commit_proposal` after approval and `NoTools` after commitment;
- backend credential provider + host-only credential broker;
- protected off-call S22 network-smoke plumbing;
- typed output audio/transcript/response lifecycle;
- full-response model PCM buffering before telephony TX.

## The two previous REDs are CLOSED

### Production output approval policy — GREEN

Behavior commit:

```text
b0c2bac0ff2615f40e31cf525082fc9e33494bf0
fix: close realtime speech gate reds
```

`CallRealtimeAgentOutputApprovalPolicy` now:

- RELEASES ordinary model speech only in `ACTIVE_NEGOTIATION` with no pending commitment permit;
- DROPS while a commitment permit is pending;
- DROPS in `NEEDS_USER_DECISION`;
- DROPS outside active negotiation.

`CallRealtimeAgentSessionSpec` creates the output policy with the exact same `CallCommitmentGate` used by `evaluate_proposal` and `commit_proposal`.

Production wiring is complete:

```text
SessionSpec
 -> SessionController
 -> SessionOrchestrator
 -> MediaSession
 -> AudioPump
 -> CallRealtimeOutputResponseBuffer
```

Do not regress this to `outputApprovalPolicy=null` in production.

### Stale function `response_id` fixture — GREEN

The production parser still requires `response_id`.

`RealtimeWebSocketFunctionTransportTest.incomingFunctionCallReachesTypedListener` now emits `response_id` and asserts it reaches `RealtimeFunctionCall.responseId`. The parser was not weakened.

## Full host gate — GREEN

Evidence:

```text
.agent/results/realtime-speech-function-host-green-20260918-2600.json
behavior HEAD: b0c2bac0ff2615f40e31cf525082fc9e33494bf0
status: done
exit_code: 0
```

Passed:

- focused output-policy and speech-gate tests;
- focused function response-id tests;
- full `:realtime-client:testDebugUnitTest`;
- full `:app:testDebugUnitTest`;
- `:app:assembleDebug`;
- all Python script tests: 59 tests, OK;
- production secret scan;
- `git diff --check`;
- clean worktree.

No cellular call was made.

## Current blocker: genuine OpenAI off-call S22 smoke

The next gate is a **real OpenAI network/session smoke on the S22 without dialing**.

A fresh Local Agent prerequisite audit was performed at behavior HEAD `b0c2bac0...`:

```text
.agent/results/realtime-openai-offcall-smoke-20260918-2610.json
openai_api_key_present=false
broker_token_present=false
broker_https_url_present=false
external_prerequisite_blocked=true
```

The smoke therefore did not execute. This is intentional fail-closed behavior.

Do not work around it by placing a standard OpenAI key in the APK, Intent, app-private config, ADB argv, or phone.

The Local Agent host environment must receive all three externally:

```text
OPENAI_API_KEY
AI_CALL_BRIDGE_BROKER_TOKEN
AI_CALL_BRIDGE_BROKER_HTTPS_URL
```

Rules:

- `OPENAI_API_KEY` stays only in the host broker process environment;
- broker token is distinct from the OpenAI key, at least 32 characters, and not `sk-...`;
- HTTPS URL must reach the loopback broker and remain protected/authenticated;
- Android receives only broker URL + broker bearer via the existing one-shot ADB-stdin staging path.

Existing tools:

- `scripts/realtime_credential_broker.py`;
- `scripts/realtime_network_smoke.py`;
- protected `RealtimeNetworkOffCallSmokeProbe`.

## Exact next gate once prerequisites exist

Run the genuine off-call smoke on `RFCT70L7E8J` while cellular call state is idle.

Expected PASS evidence:

```text
FETCHING_CREDENTIAL
 -> CONNECTING_REALTIME
 -> STARTING_MEDIA
 -> FAILED
```

Expected reason:

```text
realtime_connected_off_call_media_rejected
```

The final failure is correct because frozen media must reject start when no cellular call exists. `ACTIVE` while off-call is a safety failure.

Also verify:

- `CALL_STATE=0` before and after;
- one-shot app-private smoke config deleted;
- no call-media helper remains alive;
- no standard OpenAI key reaches Android.

## Only after real off-call PASS

1. First cellular Realtime call must be controlled and non-committing.
2. Verify RX/TX intelligibility, latency, barge-in, TAKE OVER, cleanup, and real GA event ordering / `response_id`.
3. Only then attempt a real clinic registration using explicit user facts/constraints.
4. Anything outside authority must enter `NEEDS_USER_DECISION`; never widen authority automatically.

Target device:

```text
Samsung Galaxy S22+ SM-S906B
serial: RFCT70L7E8J
Android 16 / API 36 / One UI 8
```

Safe regression number `510100100` is authorized only if a physical cellular regression call is genuinely needed. It is not needed for off-call smoke.

For live-call validation: direct USB-C, Bluetooth off, mute voice-call stream before dial and after ACTIVE, speakerphone off, restore Bluetooth afterwards.

## Local Agent mechanics

- `.agent/tasks` / `.agent/results` only on `agent-control`.
- Direct GitHub edits when diff/docs evidence is sufficient.
- Local Agent only for local commands, builds, tests, ADB, and device work.
- Before a task writes/uses the same branch, check active daemon task.
- Do not poll healthy multi-minute tasks every 30 seconds.
- Never launch local Codex from a Local Agent task.
- Never use another repository under this binding.

Proven local paths:

```text
Gradle: $HOME/.gradle/local-agent/gradle-9.6.0/bin/gradle
Android SDK: $HOME/Library/Android/sdk
```
