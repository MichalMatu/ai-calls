# Handoff — Phase 2D frozen / Telephone Agent Phase 3 host GREEN

Date: 2026-09-18

Repository: `MichalMatu/android-ai-call-bridge`

Work branch: `work/phase1-live-call-probes`

Current behavior HEAD before these documentation-only handoff commits:

```text
c939a9bbcc4603d846ab8e3cd17d2b8dd19d17e5
fix: sanitize realtime diagnostic labels
```

Read this file together with `docs/PHASE3_REALTIME_STATUS_2026-09-18.md`.

## New-chat Local Agent binding rule

This handoff came from Local Agent binding:

```text
repository_id: android-ai-call-bridge
repository: MichalMatu/android-ai-call-bridge
control_branch: agent-control
old_chat_binding: c25f88c0-4682-414c-8062-c47fa4034cb0
```

A new chat must bootstrap `[LAB:ADD=android-ai-call-bridge]` if not already bound and use the fresh returned binding immutably for that chat. Never copy the old binding blindly.

Every `.agent/tasks/*.json` must contain exactly the current chat binding.

## Start rule

Before changing the product branch:

1. read `AGENTS.md`;
2. read this handoff;
3. read `docs/PHASE3_REALTIME_STATUS_2026-09-18.md`;
4. read Phase 2D freeze, roadmap, architecture, security/privacy and relevant implementation plans as needed;
5. verify current `work/phase1-live-call-probes` HEAD;
6. verify `.agent/status/daemon.json` on `agent-control` before using/writing the same branch.

Do not rerun the frozen Phase 2 physical matrix without concrete regression evidence.

## Frozen media checkpoint

```text
branch: milestone/phase2d-failsafe-proven-s22-20260918
commit: 59b0505537a53306acdab6a2a66ca6eed2b3f1c0
status: PROVEN_S22
```

Preserve Samsung invariants: RX construction/attribution ordering, TX `com.android.shell`, `USAGE_CALL_ASSISTANT` / TELEPHONY_TX, mono PCM16 internally, stereo only at the Samsung TX boundary, PFD AutoClose ownership, whole-generation cleanup, local TAKE OVER, heartbeat and `CallModeWatchdog`.

## Current Phase 3 production state

Implemented and retained:

- production Shizuku media coordinator/backend/runtime;
- Realtime WebSocket + OkHttp transport with generation safety;
- telephony 16 kHz <-> Realtime 24 kHz mono PCM16 conversion;
- bounded audio pump + local barge-in;
- task/workflow/authority model with hard constraints, preferences and `authorizedFacts`;
- deterministic `CallConfirmationPolicy` + `NEEDS_USER_DECISION`;
- typed Realtime tools, `evaluate_proposal`, one-shot commitment permit and `commit_proposal`;
- forced `commit_proposal` after approval and `NoTools` after commitment;
- host-only credential broker and Android short-lived credential provider;
- protected off-call S22 network-smoke path;
- typed output audio/transcript/response lifecycle;
- full-response speech buffer and production output approval policy;
- required function `response_id`;
- bounded privacy-safe Realtime event trace;
- shared bounded diagnostic-label sanitizer for trace and function debug rendering.

## Previous REDs remain CLOSED

Behavior commit `b0c2bac0ff2615f40e31cf525082fc9e33494bf0` closed production speech approval wiring and the stale function-response fixture.

Production speech policy uses the exact same `CallCommitmentGate` as proposal/commit handlers and is wired through:

```text
SessionSpec
 -> SessionController
 -> SessionOrchestrator
 -> MediaSession
 -> AudioPump
 -> CallRealtimeOutputResponseBuffer
```

Do not regress production to `outputApprovalPolicy=null`. Do not weaken required function `response_id` parsing.

## Realtime evidence trace — HOST_GREEN

`RealtimeEventTrace` and `TracingRealtimeTransport` provide bounded/redacted protocol ordering and relative timing without storing conversation content.

The trace never stores PCM, transcript text, function arguments/output, credentials, raw error messages or raw provider IDs. Response/item/call IDs are exposed only as local aliases (`R1/I1/C1`); internal correlation keys use per-trace salted SHA-256. Event and identity tables are bounded.

`CallRealtimeAgentRuntime.create(eventTrace=...)` makes tracing opt-in. The off-call smoke supplies a trace and may return compact `trace=...` evidence. Trace content never controls PASS/FAIL.

## Diagnostic-label injection hardening — HOST_GREEN

Two test-only REDs proved that model/provider-controlled function names could previously place newline/oversized text into trace/debug rendering:

```text
realtime-trace-label-sanitization-red-20260918-2750
realtime-function-debug-label-red-20260918-2760
```

Behavior commit:

```text
c939a9bbcc4603d846ab8e3cd17d2b8dd19d17e5
fix: sanitize realtime diagnostic labels
```

`RealtimeDiagnosticLabel` now accepts only bounded ASCII diagnostic labels (maximum 64 characters; letters, digits, `_`, `-`, `.`, `:`, `$`). Unsafe labels become `REDACTED`.

The same rule protects trace function names, trace error-type labels and `RealtimeFunctionCall.toString()`. Valid production tool names such as `evaluate_proposal` and `commit_proposal` remain visible.

## Full host gate — GREEN

Latest evidence:

```text
.agent/results/realtime-diagnostic-labels-host-green-20260918-2770.json
behavior HEAD: c939a9bbcc4603d846ab8e3cd17d2b8dd19d17e5
status: done
exit_code: 0
```

Passed:

- focused Realtime trace/diagnostic-label tests;
- focused off-call state/trace tests;
- focused Python smoke tests 5/5;
- full `:realtime-client:testDebugUnitTest`;
- full `:app:testDebugUnitTest`;
- `:app:assembleDebug`;
- all Python script tests: 59 tests, OK;
- production secret scan;
- `git diff --check`;
- clean worktree.

No cellular call was made.

## Current blocker: genuine OpenAI off-call S22 smoke

The next physical gate remains a real OpenAI network/session smoke on the S22 **without dialing**.

Fresh prerequisite recheck after all current host hardening:

```text
.agent/results/realtime-openai-offcall-smoke-recheck-20260918-2780.json
checked branch HEAD: 50377784b98b4396857c1e278afc688513f4e636
openai_api_key_present=false
broker_token_present=false
broker_https_url_present=false
external_prerequisite_blocked=true
```

The recheck stopped before build/install/network work. It did not dial.

Required externally in the Local Agent host environment:

```text
OPENAI_API_KEY
AI_CALL_BRIDGE_BROKER_TOKEN
AI_CALL_BRIDGE_BROKER_HTTPS_URL
```

Never work around this by putting a standard OpenAI key in APK, Intent, app-private config, ADB argv or phone.

Rules:

- `OPENAI_API_KEY` stays only in the host broker process environment;
- broker token is distinct from the OpenAI key, minimum 32 characters and not `sk-...`;
- HTTPS URL reaches the loopback broker through a protected/authenticated path;
- Android receives only broker URL + broker bearer via the existing one-shot ADB-stdin staging path.

## Exact next gate once prerequisites exist

Target device:

```text
Samsung Galaxy S22+ SM-S906B
serial: RFCT70L7E8J
Android 16 / API 36 / One UI 8
```

Run the genuine off-call smoke while cellular call state is idle.

Expected PASS state path:

```text
FETCHING_CREDENTIAL
 -> CONNECTING_REALTIME
 -> STARTING_MEDIA
 -> FAILED
```

Expected reason: `realtime_connected_off_call_media_rejected`.

The final failure is correct because frozen media must reject start with no cellular call. `ACTIVE` off-call is a safety failure.

Also verify:

- `CALL_STATE=0` before and after;
- one-shot app-private config deleted;
- no call-media helper remains alive;
- no standard OpenAI key reaches Android;
- trace is redacted and shows actual Realtime event ordering available during the smoke.

## Only after genuine off-call PASS

1. First cellular Realtime call must be controlled and non-committing.
2. Validate RX/TX intelligibility, latency, barge-in, TAKE OVER, cleanup and real GA event ordering/identity using the redacted trace.
3. Only then attempt a real clinic registration using explicit user facts/constraints.
4. Anything outside authority must enter `NEEDS_USER_DECISION`; never widen authority automatically.

Safe regression number `510100100` is authorized only if a physical cellular regression call is genuinely needed. It is not needed for off-call smoke.

For live-call validation: direct USB-C, Bluetooth off, mute voice-call stream before dial and after ACTIVE, speakerphone off, restore Bluetooth afterwards.

## Local Agent mechanics

- `.agent/tasks` / `.agent/results` only on `agent-control`.
- Direct GitHub edits when diff/docs/code evidence is sufficient.
- Local Agent only for local commands, builds, tests, ADB and device work.
- Before using/writing the same branch, check active daemon task.
- Do not poll healthy multi-minute tasks at short intervals.
- Never launch local Codex from a Local Agent task.
- Never use another repository under this binding.

Proven local paths:

```text
Gradle: $HOME/.gradle/local-agent/gradle-9.6.0/bin/gradle
Android SDK: $HOME/Library/Android/sdk
```
