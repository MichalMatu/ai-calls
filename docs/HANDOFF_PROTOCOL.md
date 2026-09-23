# Handoff protocol

This document defines how a development session in `MichalMatu/ai-calls` is closed and transferred to a new ChatGPT window/session.

The goal is that a new session can continue from repository state without depending on hidden chat history, stale Local Agent state, or remembered verbal instructions.

## Sources of truth

Keep roles separate:

- `README.md` — current product direction and high-level state;
- `docs/ROADMAP.md` — authoritative execution order and acceptance gates;
- `docs/ARCHITECTURE.md` — component boundaries and ownership;
- `docs/SECURITY_PRIVACY.md` — authority, privacy and live-call safety invariants;
- `docs/HANDOFF_NEXT_CHAT.md` — exact current checkpoint and continuation state;
- `docs/NEXT_CHAT_PROMPT.md` — ready-to-paste bootstrap prompt for the next chat;
- domain runbooks such as `docs/ORANGE_MAPPING_RUNBOOK.md` — operating procedures for a specific side track;
- Git history and `.agent/results` — detailed experiment/evidence history, not the product plan.

Do not create a new status document for every experiment. Update the sources above when their meaning changes.

## When to create a handoff

Create/refesh the handoff when any of these is true:

- a major gate or milestone is completed;
- the active product goal changes materially;
- a long implementation/testing session is ending;
- work is intentionally moving to a fresh chat window;
- the current chat has accumulated enough history that continuation would be clearer from repository state;
- a risky hardware/live-call slice has just been stabilized;
- an architectural spike/dependency decision changes the next implementation path.

## Mandatory close-out order

Before writing the final handoff:

1. Stop starting new feature slices.
2. Finish or explicitly abandon the current bounded slice; do not leave an ambiguous half-completed live experiment.
3. Persist durable product/evidence changes on `main`.
4. Run the verification appropriate to the changed boundary. Use targeted tests plus `bash scripts/verify_host.sh` for code behavior unless the change is documentation-only.
5. Run only the physical/device gate required by changed OEM/hardware/dialogue behavior. Host evidence must not be upgraded to `PROVEN_S22` without a physical proof.
6. If a phone call was made, require cleanup to normal `IDLE` before handoff.
7. Audit branch/worktree state. Preserve historical local branches with unique commits until intentionally archived or judged disposable.
8. Audit documentation for contradictions: active gate, next execution order, frozen/deferred work, safety policy and latest checkpoint must agree.
9. If an external library/framework or architecture spike was evaluated, record the actual decision and rationale in `docs/ROADMAP.md` / `docs/ARCHITECTURE.md`: accepted, rejected, or still pending. Do not make the next chat repeat the same comparison from memory.
10. Update `README.md`, `docs/ROADMAP.md`, `docs/ARCHITECTURE.md`, `docs/SECURITY_PRIVACY.md` and domain runbooks only when their durable meaning changed.
11. Refresh `docs/HANDOFF_NEXT_CHAT.md`.
12. Refresh `docs/NEXT_CHAT_PROMPT.md` last, so it points to the current authoritative documents rather than duplicating a large amount of volatile state.
13. Confirm no prompt or handoff contains secrets, plaintext identity values, stale credentials, an old immutable Local Agent binding, or implied live-call authorization for a future session.

## Required handoff contents

`docs/HANDOFF_NEXT_CHAT.md` must contain at least:

- repository and durable branch;
- active product gate and goal;
- what is physically proven vs host-only vs still open;
- frozen boundaries that must not be casually touched;
- current architectural direction and important decisions;
- exact continuation order, preferably by referencing `docs/ROADMAP.md` rather than duplicating it;
- any incomplete/known-risk work;
- any pending/decided framework or dependency spike that changes the next slice;
- evidence/checkpoint references that materially matter;
- branch/worktree exceptions worth preserving;
- Local Agent / Local Chat Bridge operating rules;
- live-call authorization rule: authorization is session-scoped and never inherited from the handoff;
- the path to `docs/NEXT_CHAT_PROMPT.md`.

The handoff is a state snapshot, not the authority to invent a different plan.

## Continuation prompt rules

`docs/NEXT_CHAT_PROMPT.md` is intentionally a ready-to-paste prompt for a new chat.

It should be short enough to review, but self-contained enough to bootstrap the work. It must:

1. name the exact repository: `MichalMatu/ai-calls`;
2. tell the new session to read fresh repository sources before acting;
3. tell it to fetch/use fresh `origin/main` rather than trusting an embedded historical SHA;
4. state the active gate and first concrete objective;
5. tell it to follow `docs/ROADMAP.md` as authoritative execution order and `docs/HANDOFF_NEXT_CHAT.md` as checkpoint state;
6. include the key frozen/safety boundaries;
7. state the expected working style: audit first, RED -> minimal GREEN -> verification, small cohesive commits, no broad speculative refactors;
8. describe Local Agent / Local Chat Bridge bootstrap without embedding a stale `agent_binding`;
9. never carry physical-call authorization into the new session;
10. identify any first-slice architecture/dependency spike that must happen before implementation lock-in;
11. ask the new session to continue autonomously inside the documented scope instead of re-asking questions already answered by the repository.

The continuation prompt should not duplicate detailed service trees, full test logs, secrets, plaintext identity values or large architecture sections. Those belong in repository sources or protected runtime storage, not in the bootstrap prompt.

## Local Agent and Local Chat Bridge

Local Agent is an execution worker; Git/repository documentation remains the source of truth.

When the Local Chat Bridge mode is used, the chat receives a binding envelope similar to:

```text
[LA_AGENT=...]
[LA_REPO=ai-calls]
[LA_REPOSITORY=MichalMatu/ai-calls]
[LA_CHAT=...]
```

Rules:

- Treat the current chat's binding envelope as immutable for that wake/session.
- Work only in the exact bound repository. Never infer or substitute another repository.
- Never copy an `agent_binding` value from `HANDOFF_NEXT_CHAT.md`, `NEXT_CHAT_PROMPT.md`, Git history or an old task. The new chat must use the fresh bridge-provided binding.
- Every Local Agent task created under a bridge binding must carry exactly that current binding.
- If another repository is genuinely required, use the bridge's explicit rebind mechanism and wait for a fresh bootstrap; never guess a repository ID.
- Before writing the same worktree/branch or queueing another task, inspect fresh daemon state and active-task evidence.
- Do not poll healthy long-running tasks aggressively; inspect terminal result evidence rather than treating queue/ACK as success.
- Use direct GitHub edits when an exact reviewable code/data/docs diff is sufficient.
- Use Local Agent for commands that require the developer machine, Gradle/Android tooling, tests, ADB, device state or other local execution.
- Never launch local Codex from a Local Agent task.
- `.agent/tasks` and `.agent/results` stay on `agent-control`; do not merge them into `main`.
- `main` remains the durable product branch unless the current documented workflow explicitly justifies temporary isolation.
- Do not restart Local Agent merely to work around an unclear task; inspect evidence/root cause first.

Bridge conversation controls are operational controls, not product state. They do not belong in durable product code or architecture.

## Physical-call handoff rule

A future session must never infer permission to dial from:

- this protocol;
- `HANDOFF_NEXT_CHAT.md`;
- `NEXT_CHAT_PROMPT.md`;
- an old chat;
- previous `.agent/results`;
- a previously allowlisted target.

Every new physical-call session requires fresh user/operator authorization appropriate to that target and task.

For real-world appointment development, follow the live-test policy in `docs/ROADMAP.md` and `docs/SECURITY_PRIVACY.md`: small reviewed target sets, test disclosure/consent for test-only calls, genuine user authorization for real bookings, no critical-service test traffic, strict fact-disclosure policy, and strict commitment authority.

## Handoff quality check

A handoff is good when a fresh chat can answer all of these from the repository alone:

- What are we building now?
- What is already proven?
- What must not be changed casually?
- What exact slice comes next?
- Is there a framework/dependency decision that must be made before implementation?
- Which tests/evidence prove the checkpoint?
- How should Local Agent/Local Chat Bridge be used?
- What requires fresh user authorization?
- Where is detailed historical evidence if needed?

If any answer depends only on memory from the old chat, the handoff is incomplete.
