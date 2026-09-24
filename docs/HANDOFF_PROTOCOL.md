# Handoff protocol

Use repository state, not chat memory, as the durable continuation source.

## Sources of truth

- `README.md` — product and high-level status;
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
3. run verification appropriate to the changed boundary (`verify_host.sh` for code; docs-only changes do not need device proof);
4. never upgrade host evidence to `PROVEN_S22` without physical execution;
5. if a call was made, return the owned call to `IDLE`;
6. remove temporary branches after merge unless they intentionally preserve unique work;
7. make `README`, roadmap, architecture, security and handoff agree;
8. keep historical logs/details in Git instead of copying them into the handoff;
9. remove secrets, plaintext identity, stale credentials and old Local Agent bindings;
10. explicitly state that live-call authorization does not transfer to the new chat.

## Handoff contents

`docs/HANDOFF_NEXT_CHAT.md` should answer only what the next chat needs:

- repository/product goal;
- what is already proven/frozen;
- active gate;
- exact next execution order;
- blockers/stop lines;
- relevant files/scripts;
- important recent PR/commit checkpoints;
- Local Agent operating rule;
- fresh live-call authorization rule;
- one concise copy/paste start prompt.

Do not duplicate the full roadmap, architecture or old experiment chronology.

## New chat bootstrap

A fresh chat should:

1. use the fresh bridge binding supplied in that chat;
2. read fresh `origin/main` plus `HANDOFF_NEXT_CHAT.md`, roadmap and the active domain runbook;
3. inspect current daemon/active-task evidence before queueing Local Agent work;
4. use direct GitHub edits for small reviewable repository changes and Local Agent for local commands/builds/tests/device work;
5. never copy an `agent_binding` from docs/history;
6. never launch local Codex from Local Agent;
7. keep `.agent/tasks` and `.agent/results` on `agent-control`, never merge them into `main`.

## Live-call rule

A handoff, old chat, connected phone, ServicePack entry, allowlist or old `.agent/results` file never authorizes dialing.

Every new physical-call session requires fresh explicit authorization for the concrete target and task. If the task includes an external state change, the authorization must cover that concrete effect; read-only discovery permission does not automatically authorize a later account-changing action.