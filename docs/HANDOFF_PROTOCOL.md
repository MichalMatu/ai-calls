# Handoff protocol

Use repository state, not chat memory, as the durable continuation source.

## Sources of truth

- `README.md` — product and high-level status;
- `docs/AUTONOMOUS_OPERATION_MODE.md` — normative autonomous physical-work contract;
- `docs/ROADMAP.md` — current execution order;
- `docs/ARCHITECTURE.md` — ownership boundaries;
- `docs/SECURITY_PRIVACY.md` — authority/privacy/live-call rules;
- `docs/HANDOFF_NEXT_CHAT.md` — exact continuation checkpoint + start prompt;
- domain runbooks such as `docs/G5_CLIR_ROUTE_DISCOVERY.md` / `docs/ORANGE_MAPPING_RUNBOOK.md` — bounded operating detail;
- Git/PR history and `.agent/results` — detailed evidence/history.

Do not create a new status file for every experiment.

## Close-out checklist

Before handing work to a new chat:

1. finish or explicitly stop the current bounded slice;
2. persist durable changes on `main`;
3. run verification appropriate to the changed boundary; during an active physical campaign, do not substitute synthetic/unit suites for the required physical evidence;
4. never upgrade host evidence to `PROVEN_S22` without physical execution;
5. if a call was made, return the owned call to `IDLE`;
6. remove temporary branches after merge unless they intentionally preserve unique work;
7. make `README`, autonomous-operation mode, roadmap, architecture, security and handoff agree;
8. keep historical logs/details in Git instead of copying them into the handoff;
9. remove secrets, plaintext identity, stale credentials and old Local Agent bindings;
10. preserve the autonomous-operation contract: do not make the next operator act as a shell/log relay when Local Agent can do the work.

## Handoff contents

`docs/HANDOFF_NEXT_CHAT.md` should answer only what the next chat needs:

- repository/product goal;
- what is already proven/frozen;
- active physical gate;
- exact next execution order;
- blockers/stop lines;
- relevant files/scripts;
- important recent PR/commit checkpoints;
- Local Agent operating rule;
- autonomous-operation rule;
- current accepted authorization mechanism/grant state without copying secrets or plaintext identity;
- one concise copy/paste start prompt.

Do not duplicate the full roadmap, architecture or old experiment chronology.

## New chat bootstrap

A fresh chat should:

1. use the fresh bridge binding supplied in that chat;
2. read fresh `origin/main` plus `AGENTS.md`, `HANDOFF_NEXT_CHAT.md`, `AUTONOMOUS_OPERATION_MODE.md`, roadmap and the active domain runbook;
3. inspect current daemon/active-task evidence before queueing Local Agent work;
4. use direct GitHub edits for small reviewable repository changes and Local Agent for local commands/builds/device work;
5. do not make the operator paste commands or copy logs if Local Agent/ADB can perform the step;
6. never copy an `agent_binding` from docs/history;
7. never launch local Codex from Local Agent;
8. keep `.agent/tasks` and `.agent/results` on `agent-control`, never merge them into `main`.

## Authorization continuity

Handoff text, an old chat, connected hardware, ServicePack data, allowlists or `.agent/results` are not themselves authority stores.

A new physical call requires an **accepted application authorization context** for the exact target/task/effect. Today that may be supplied by an explicit current-chat instruction; the product target is a durable, scoped, revocable application-owned campaign grant.

When a durable grant exists and remains valid for an unchanged retry scope, the next chat should rehydrate/use that grant rather than interrupt the workflow for another redundant product confirmation. A material widening of target, task, effect, account/SIM or disclosure scope remains fail-closed.

External platform/tool controls are outside repository authority and must not be bypassed.
