# Handoff — ready for the API prerequisite gate

Date: 2026-09-18

Repository: `MichalMatu/android-ai-call-bridge`

Durable product branch: `main`

Local Agent control branch: `agent-control`

## Start here

Read only what is relevant:

1. `AGENTS.md`
2. this file
3. `docs/PHASE3_REALTIME_STATUS_2026-09-18.md`
4. `docs/SECURITY_PRIVACY.md` if touching credentials/live calls
5. `docs/PHASE2D_FREEZE_2026-09-18.md` if touching Samsung media internals.

Then verify `main` HEAD and `.agent/status/daemon.json`. In a new chat bootstrap the repository Local Agent and use that chat's fresh binding; never copy an old chat binding.

## Current code state

Latest behavior checkpoint before this documentation refresh:

```text
25153fc52bb5a95d5a85ea44a0bd4b29b2930d96
fix: allow quick tunnel dns warmup
```

Important preceding cleanup commits:

```text
db8afd807f7cd1e45cd41ea36da70d21b947c302
refactor: separate realtime proposal parsing

89891557f26f8f69af43f572405d761a887b0bb8
fix: close host quality gate gaps
```

`scripts/verify_host.sh` is the canonical local/CI quality gate. Strict proposal JSON decoding is separate from workflow/commitment mutation and uses Gson's strict reader API.

Phase 2D physical Samsung media remains frozen at:

```text
59b0505537a53306acdab6a2a66ca6eed2b3f1c0
PROVEN_S22
```

The freeze commit is preserved in `main` history; milestone branches were removed. Do not rerun the full Phase 2D matrix without a concrete regression.

## Current Phase 3 state

Host-green production stack includes:

- Realtime WebSocket/OkHttp transport and generation safety;
- 16 kHz telephony <-> 24 kHz Realtime PCM;
- bounded audio pump/barge-in;
- deterministic task/workflow/authority model;
- strict side-effect-free proposal parser;
- one-shot commitment permit + forced commit tool;
- speech output buffer/approval before cellular TX;
- host credential broker / short-lived client credential boundary;
- bounded redacted event trace;
- protected off-call and controlled live-call probes;
- fail-closed live-call preflight before secret staging.

Physical S22 evidence already covers live-probe off-call refusal, preflight observability and idempotent/reversible voice-call mute. It does **not** cover an actual OpenAI Realtime session or cellular Realtime audio.

## Repository cleanup completed

The project is now main-first:

- product development lives on `main`;
- Local Agent metadata lives only on `agent-control`;
- obsolete work/milestone branches were removed after verifying their commits are ancestors of `main`;
- active docs were reduced to the authoritative current set; historical plans/proof notes remain available in Git history and `.agent/results`;
- CI runs the same full host quality gate as local development.

Do not recreate long-lived branch/document clutter without a specific reason.

## The only next blocker

The only operator-supplied secret prerequisite is `OPENAI_API_KEY` on the host. Do not put it in APK, source, Intent, app-private config, ADB argv or phone.

Preferred gate:

```bash
python3 scripts/realtime_offcall_lab.py RFCT70L7E8J
```

The launcher creates a random one-shot broker bearer, starts the loopback broker, exposes it through a temporary Cloudflare Quick Tunnel, allows for the provider's short DNS warm-up, waits for the public endpoint to reject an unauthenticated request with the broker's `401` boundary, then runs the existing off-call smoke. The standard OpenAI key is present only in the broker child environment; unrelated host secrets are not forwarded. The bearer and tunnel URL exist only for that run and the processes are torn down afterwards.

The exact S22+ must also be connected over direct USB ADB; do not substitute wireless ADB.

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

## Latest pre-API infrastructure proof

The real Quick Tunnel / loopback-broker boundary was exercised without calling OpenAI upstream in `.agent/results/pre-api-public-broker-boundary-proof-retry-20260918-3350.json`. The unauthenticated public broker request reached the local broker and was rejected at `401`; the dummy long-lived key was never used upstream. Quick Tunnels remain development-only infrastructure, not the production credential service.
