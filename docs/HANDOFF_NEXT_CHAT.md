# Handoff — ready for the API prerequisite gate

Date: 2026-09-18

Repository: `MichalMatu/android-ai-call-bridge`

Durable product branch: `main`

Local Agent control branch: `agent-control`

## Start here

Read:

1. `AGENTS.md`
2. this file
3. `docs/PHASE3_REALTIME_STATUS_2026-09-18.md`
4. `docs/SECURITY_PRIVACY.md` if touching credentials/live calls
5. `docs/PHASE2D_FREEZE_2026-09-18.md` if touching Samsung media internals.

Then verify `main` HEAD and `.agent/status/daemon.json`. In a new chat bootstrap the repository Local Agent and use that chat's fresh binding; never copy an old chat binding.

## Current code state

Pre-documentation behavior checkpoint:

```text
db8afd807f7cd1e45cd41ea36da70d21b947c302
refactor: separate realtime proposal parsing
```

Before it:

```text
89891557f26f8f69af43f572405d761a887b0bb8
fix: close host quality gate gaps
```

The cleanup made `scripts/verify_host.sh` the canonical local/CI gate and separated strict proposal JSON parsing from workflow/commitment mutation.

Phase 2D physical Samsung media remains frozen at:

```text
59b0505537a53306acdab6a2a66ca6eed2b3f1c0
PROVEN_S22
```

Do not rerun the full Phase 2D matrix without a concrete regression.

## Current Phase 3 state

Host-green production stack includes:

- Realtime WebSocket/OkHttp transport and generation safety;
- 16 kHz telephony <-> 24 kHz Realtime PCM;
- bounded audio pump/barge-in;
- deterministic task/workflow/authority model;
- strict proposal parser;
- one-shot commitment permit + forced commit tool;
- speech output buffer/approval before cellular TX;
- host credential broker / short-lived client credential boundary;
- bounded redacted event trace;
- protected off-call and controlled live-call probes;
- fail-closed live-call preflight before secret staging.

Physical S22 evidence already covers live-probe off-call refusal, preflight observability and idempotent/reversible voice-call mute. It does **not** cover an actual OpenAI Realtime session or cellular Realtime audio.

## The only next blocker

Host environment must provide:

```text
OPENAI_API_KEY
AI_CALL_BRIDGE_BROKER_TOKEN
AI_CALL_BRIDGE_BROKER_HTTPS_URL
```

Do not put the standard OpenAI key in APK, source, Intent, app-private config, ADB argv or phone.

## Next gate after credentials exist

Run the genuine OpenAI **off-call** network/session smoke on S22 serial `RFCT70L7E8J` while cellular call state is idle.

Expected PASS path:

```text
FETCHING_CREDENTIAL
 -> CONNECTING_REALTIME
 -> STARTING_MEDIA
 -> FAILED
```

Expected reason: `realtime_connected_off_call_media_rejected`.

Require call state idle before/after, one-shot config deleted, helper absent after cleanup, no long-lived key on Android and redacted Realtime ordering evidence. `ACTIVE` off-call is a safety failure.

## Gate after off-call PASS

One controlled non-committing cellular Realtime call only. Before broker secret staging the runner requires direct USB, Bluetooth OFF, `CALL_STATE=2`, `MODE_IN_CALL`, earpiece and muted voice-call stream. The runner does not dial or hang up.

Validate real RX/TX quality, latency, barge-in, TAKE OVER, cleanup and event ordering. Only after that passes attempt a real user-authorized task.

## Repository hygiene

Keep product work on `main` and Local Agent metadata on `agent-control`. Do not recreate long-lived work/milestone branches or per-task planning documents without a specific need; Git history and `.agent/results` preserve the evidence.
