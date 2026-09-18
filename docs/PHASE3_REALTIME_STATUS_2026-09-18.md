# Phase 3 Realtime status — 2026-09-18

Repository: `MichalMatu/android-ai-call-bridge`

Durable development branch: `main`.

Latest behavior cleanup checkpoint before this documentation refresh:

```text
d191b6cc8afcee636a3ad3c8b7b4dc7fee6e8417
refactor: use strict Gson reader API
```

Key preceding cleanup checkpoints:

```text
db8afd807f7cd1e45cd41ea36da70d21b947c302
refactor: separate realtime proposal parsing

89891557f26f8f69af43f572405d761a887b0bb8
fix: close host quality gate gaps
```

## Frozen physical baseline

Phase 2D remains `PROVEN_S22` at commit:

```text
59b0505537a53306acdab6a2a66ca6eed2b3f1c0
```

The commit remains in `main` history; permanent milestone branches were removed. Preserve the RX/TX attribution/order, CALL_ASSISTANT/TELEPHONY_TX path, mono internal PCM, TX-boundary stereo, PFD ownership, sibling cleanup, local TAKE OVER, heartbeat and `CallModeWatchdog`. Do not rerun the full matrix without concrete regression evidence.

## Current Telephone Agent stack — HOST_GREEN

Implemented and retained:

- production `CallMediaSessionCoordinator` and Shizuku backend/runtime;
- Realtime WebSocket transport with generation safety;
- telephony 16 kHz <-> Realtime 24 kHz PCM path;
- bounded audio pump and local barge-in;
- task/constraints/preferences/`authorizedFacts` workflow model;
- deterministic policy and `NEEDS_USER_DECISION`;
- typed Realtime function calling;
- strict `evaluate_proposal` plus side-effect-free `CallRealtimeProposalParser`;
- one-shot `CallCommitmentGate` and forced `commit_proposal`;
- full-response output buffer and application-owned speech approval;
- required function `response_id` handling;
- host credential broker and short-lived Android credential provider;
- protected off-call network-smoke plumbing;
- bounded/redacted `RealtimeEventTrace`;
- controlled no-tools live-call probe with fail-closed host preflight.

## Pre-API quality audit and cleanup

The audit measured about 110 production Kotlin/Java source files and ~14.8k source lines. Size alone was not treated as evidence of a god object.

`CallRealtimeSessionOrchestrator` remains intentionally cohesive because one generation state machine owns credential, transport, media, function-response and cleanup ordering. Splitting it merely to reduce line count would make safety invariants harder to audit.

A genuine mixed-responsibility area was refactored: strict proposal JSON decoding moved out of `CallRealtimeProposalFunctionHandler` into `CallRealtimeProposalParser`, with dedicated parser tests. The parser uses Gson `Strictness.STRICT`; workflow/commitment mutation remains outside parsing.

The audit also exposed lint debt that ordinary builds had missed. The cleanup fixed target/API/permission lint contracts, made Realtime URL encoding compatible with `minSdk=29`, and replaced the old build-only CI step with the shared host quality gate.

Canonical verification:

```bash
bash scripts/verify_host.sh
```

It covers all module unit tests, lint for all four Android modules, debug APK assembly, the complete Python script suite, production-secret scan, live-runner no-dial/hangup scan and `git diff --check`.

The final main audit before the last strictness cleanup reported 257 JVM tests with zero failures/errors/skips plus 65 Python tests. `audio-bridge` currently has no direct JVM tests because it is a small contract/model module; its behavior is exercised through higher-level app/session tests. Add direct tests there only when executable logic is added.

## Repository/doc cleanup

The active repository now uses only two branches:

- `main` for product code/current docs;
- `agent-control` for Local Agent task/result traffic.

Historical work/milestone branches were deleted only after their commits were confirmed as ancestors of `main`.

Active documentation was reduced to the current authoritative set. Historical plans, deep-audit notes and intermediate proof documents remain retrievable from Git history and `.agent/results` rather than occupying the active docs tree.

## Physical S22 safety evidence added before the API gate

Protected live-probe off-call refusal:

```text
.agent/results/realtime-live-probe-offcall-s22-refusal-final-20260918-2860.json
```

Observed `CALL_STATE=0 -> 0`, `realtime_live_call_smoke=REFUSED`, reason `cellular_call_not_active`, no config left, no helper left and no cellular call made.

Preflight observability audit:

```text
.agent/results/realtime-live-preflight-s22-audit-20260918-2870.json
```

confirmed direct USB target identification and reliable `dumpsys audio` signals for mode, route and voice-call mute. Bluetooth was ON during that off-call audit, so a future live test must explicitly turn it OFF and restore it afterwards.

Voice-call mute proof:

```text
.agent/results/realtime-voice-call-mute-s22-proof-20260918-2880.json
```

`cmd audio adj-mute 0` changed the target stream from unmuted to muted, remained muted when repeated, and `adj-unmute 0` restored the original state.

Current live runner preflight requires, before broker secret staging:

1. direct USB ADB to `SM_S906B`;
2. Bluetooth OFF;
3. `CALL_STATE=2`;
4. `MODE_IN_CALL`;
5. earpiece active communication device;
6. voice-call stream muted.

The runner never dials/hangs up and does not automatically mutate route/Bluetooth/mute.

## External blocker — genuine OpenAI off-call smoke

The real OpenAI network/session smoke has **not** run. Latest prerequisite recheck showed all three host prerequisites absent:

```text
OPENAI_API_KEY
AI_CALL_BRIDGE_BROKER_TOKEN
AI_CALL_BRIDGE_BROKER_HTTPS_URL
```

The standard OpenAI key stays only in the host broker environment. The broker bearer is separate. Android receives only broker URL/bearer through the one-shot stdin staging path and then a short-lived Realtime secret.

## Exact next physical gate

Run the genuine OpenAI **off-call** S22 smoke first. Required successful safety path:

```text
FETCHING_CREDENTIAL
 -> CONNECTING_REALTIME
 -> STARTING_MEDIA
 -> FAILED
```

Expected reason: `realtime_connected_off_call_media_rejected`.

Also require `CALL_STATE=0` before/after, config deletion, no helper left alive, no standard OpenAI key on Android and a redacted trace of actual Realtime ordering. `ACTIVE` while off-call is a safety failure.

## Only after off-call PASS

Run one controlled non-committing cellular Realtime conversation using the enforced preflight. Validate RX/TX intelligibility, latency, barge-in, TAKE OVER, cleanup and actual event ordering. Only after that succeeds should a real external task such as clinic registration be attempted.

The controlled live Realtime call has not yet been executed; do not phrase it as proven.
