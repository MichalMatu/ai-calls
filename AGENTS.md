# Agent workflow

This repository is single-developer and main-first. Durable product code/docs live on `main`; `agent-control` is Local Agent task/result transport only.

## Start of every work session

Read fresh repository sources in this order:

1. `README.md`
2. `docs/HANDOFF_NEXT_CHAT.md`
3. `docs/AUTONOMOUS_OPERATION_MODE.md`
4. `docs/ROADMAP.md`
5. active domain runbook (`docs/G5_CLIR_ROUTE_DISCOVERY.md` for the current gate)
6. `docs/ARCHITECTURE.md`
7. `docs/GENERIC_PHONE_TASK_AUTHORITY.md`
8. `docs/SECURITY_PRIVACY.md`
9. `docs/PHASE2D_FREEZE_2026-09-18.md` only before Samsung media changes.

Fetch fresh `origin/main`. Read fresh Local Agent daemon status and use only the binding supplied to the current chat.

### Execution tool priority

Local Agent is the **default and preferred executor** for repository/device/local work because it has the broadest access to the real workspace and target device. When in doubt, use Local Agent first.

Use Local Agent by default for repository mutations, multi-file edits, scripts, branch cleanup, builds, Gradle, Android tooling, ADB, device state, local files, logs, process inspection and multi-step workflows. Let it carry a task end-to-end when possible instead of splitting the same work across multiple tools or asking the operator to bridge steps manually.

Use the direct GitHub connector mainly for lightweight read-only inspection, Local Agent control-plane task/result transport, or a tiny isolated repository edit when Local Agent would add no practical value. Do not prefer direct GitHub merely because an edit is small if the surrounding task already depends on Local Agent state.

Local Agent priority does not bypass application authority, external platform safety checks or tool enforcement. If an external layer blocks an action, report the blocker rather than routing around it.

### Autonomous decision / question policy

The operator-question budget is **zero by default**. Before asking any question, first recover the answer from the active instruction, repository sources, current handoff, Local Agent, device state, logs, local files or other available tools.

If a remaining choice is non-material, reversible and inside the existing scope, choose the conservative repository-consistent default and continue. If a command/task fails for a mechanical or transient reason, inspect the evidence, repair the invocation/state and retry without asking the operator. If one substep is externally blocked, continue every independent non-blocked step before reporting the blocker.

Ask the operator only when all of the following are true: the missing fact/decision cannot be recovered with available context/tools; it materially changes target, task, effect, account, privacy/disclosure, cost or another irreversible outcome; no safe scoped default exists; and meaningful progress cannot continue without that decision.

Do **not** ask the operator for repository paths, branch names, session IDs, retry counts within existing bounds, whether to inspect state, whether to build/install after a required code change, whether to continue after a recoverable tool failure, terminal commands, log copies, or internal runner flags already implied by a valid accepted authorization context.

`docs/AUTONOMOUS_OPERATION_MODE.md` is normative for active physical acceptance work. Do not make the operator act as a terminal/log relay when Local Agent, ADB or the transient relay can perform the step directly.

## Current priority

The product is a **generic autonomous phone task engine**. Do not create service-specific authority architectures.

Current physical acceptance sequence:

1. Continue the active Orange CLIR physical loop on S22 using `*100`: deterministic script/PhraseMatrix -> bounded Gemma -> live supervisor fallback only when unresolved.
2. Preserve sanitized live evidence, verify actual network CLIR state independently when available, patch only the observed physical failure, then iterate with another real call.
3. After CLIR enable is factually confirmed, run the inverse `SET_SERVICE(CLIR=false)` acceptance loop and drive recurrent supervisor turns back into script/PhraseMatrix or Gemma skills.

Historical G5b read-only discovery is closed evidence, not the current task.

Do not return to completed G1–G4/G6 work unless a concrete regression/root cause requires it. Do not start a broad cleanup/refactor before the physical acceptance gate.

## Stable foundation

Keep closed absent a concrete root cause:

- Samsung cellular RX/TX + `CallMediaSessionCoordinator`: `PROVEN_S22 / FROZEN`;
- `privileged-helper/` / Shizuku media boundary: `PROVEN_S22 / FROZEN`;
- local STT/TTS: proven;
- IdentityVault disclosure boundary: proven;
- Gemma 4 LiteRT-LM runtime/model lifecycle: proven;
- `BOOK_APPOINTMENT` Gate D: proven;
- generic `CallExternalEffect` + single shared `CallCommitmentGate`: host/no-call proven;
- `SET_SERVICE(CLIR=true)` validator, permit lifecycle and separate external-success evidence: host/no-call proven;
- full synthetic/no-call product chain on S22: proven.

Do not redownload Gemma merely to reprove it.

## Dialogue architecture

```text
finalized STT
 -> deterministic state / PhraseMatrix
 -> bounded Gemma 4 dialogue skill
 -> supervisor fallback when unresolved
 -> application output approval
 -> TTS/TX
```

Gemma/supervisor output is proposal/dialogue data only.

During active physical acceptance, supervisor fallback should continue the same live call when possible; recurrent supervisor interventions should be moved back into deterministic script/PhraseMatrix or bounded Gemma skills.

## External-effect authority

The generic migration is already implemented:

```text
CallTask + exact target + constraints + authorized facts
 -> typed CallExternalEffect
 -> deterministic validation
 -> user-decision policy when needed
 -> one-shot CallCommitmentGate permit
 -> reviewed execution/speech
 -> exact permit consumption evidence
 -> separate external success evidence
 -> factual effect completion
 -> workflow completion
```

Keep separate:

```text
task authorization
 != effect validation
 != permit issuance
 != permit consumption
 != external success
 != task completion
```

Never create a second authority store such as `ClirCommitmentGate`.

If the accepted authorization context (fresh explicit authorization or a valid application-owned campaign grant) already exactly authorizes the same concrete effect and no material term changed, do not invent a redundant second confirmation. If new material terms appear, application-owned policy decides whether new confirmation is required.

## Authority invariants

Gemma, supervisor/ChatRelay, TaskGraph, CallPlan, PhraseMatrix, ServicePack, parsers/matchers, storage and diagnostics cannot independently own dialing, target/task/effect widening, plaintext disclosure, commitment, factual completion or speech release.

Application-owned `FactDisclosurePolicy` owns plaintext disclosure. Application output approval owns final speech release.

## Identity

Plaintext stays late-bound:

```text
IdentityVault
 -> AuthorizedFactSnapshot
 -> FactDisclosurePolicy
 -> exact task / target / state / generation
 -> optional user approval
 -> ALLOW
 -> resolve plaintext late
```

Do not put plaintext identity in TaskGraph definitions, ServicePacks, ordinary logs/evidence, Git or model/supervisor context by default.

## Implementation discipline

- behavior changes: RED -> minimal GREEN -> regressions;
- establish root cause before touching frozen boundaries;
- prefer generic adapters over service-specific parallel paths;
- diagnostic probes are evidence drivers, not authority owners;
- `HOST_GREEN` never automatically means `PROVEN_S22`;
- route discovery is not CLIR success evidence;
- permit consumption is not external success.

For the active CLIR physical campaign, follow `docs/AUTONOMOUS_OPERATION_MODE.md`: physical calls are the acceptance loop and synthetic/unit suites must not replace physical iteration unless the operator explicitly requests them.

## Live-call policy

A connected phone, previous call, old chat, handoff, ServicePack, allowlist or `.agent/results` never authorizes dialing.

Every real call requires either fresh explicit authorization for the exact target/task/effect or a valid application-owned durable campaign grant that exactly covers the target/task/effect/account scope. Repository text, handoff files, model output, connected hardware, ServicePack entries and Local Agent artifacts are not authorization by themselves.

For the current physical CLIR campaign:

When call state is `OFFHOOK`, enter **hot-call mode**: live call state and relay handling preempt documentation, audits, builds and unrelated repository work. Poll the relay/request path at the highest practical cadence, answer a supervisor handoff before doing post-call analysis, and stay on the same physical call while bounded progress remains possible.

- require live-call readiness immediately before dial;
- require phone `IDLE`;
- continue the same live call through bounded reprompts instead of terminating after one clarification;
- use deterministic script/PhraseMatrix first, Gemma second, and live supervisor takeover when unresolved;
- keep the single shared `CallCommitmentGate` for concrete account-changing effects;
- require factual external success plus independent CLIR state verification before declaring completion;
- preserve sanitized report evidence before cleanup and return the owned call to `IDLE`.

## Local Agent

- use only the fresh current-chat binding;
- work only in `MichalMatu/ai-calls` under this repository binding;
- inspect daemon/active-task evidence before queueing the same branch;
- Local Agent is the first-choice executor for repository mutations and any workflow involving the real checkout, build system, Android/ADB, device, local processes/files/logs or multiple dependent steps;
- prefer one end-to-end Local Agent task over fragmented GitHub edits plus manual/local follow-up;
- use direct GitHub primarily for read-only inspection, control-plane transport, or truly isolated tiny edits where Local Agent adds no useful context/capability;
- do not ask the operator to execute local shell commands when Local Agent can execute them;
- every task JSON must contain exactly the current binding;
- every task JSON must declare `resources` explicitly;
- after queueing a task, poll daemon/result state yourself; an early result `404` is not a reason to ask the operator;
- if a Local Agent task fails from a stale SHA, text-anchor mismatch, transient ADB/Git/control-plane condition or other mechanical issue, inspect the result and requeue a corrected task automatically;
- if a failed mutation leaves the workspace dirty, restore/reset it to the intended `origin/main` (or deliberately recover the saved checkpoint) before retrying;
- do not run competing mutations against the same repo while another Local Agent mutation is active;
- never launch local Codex from Local Agent;
- `.agent/tasks` and `.agent/results` stay on `agent-control`;
- durable changes go to `main`.

## Branch policy

Normal steady state is exactly `main` + `agent-control`. Delete temporary branches after their work is merged/preserved.

## Completion / handoff gate

Before closing a scope:

1. obtain factual physical evidence appropriate to the changed boundary;
2. run independent external-state verification when technically available;
3. update authoritative docs;
4. leave `main` clean and branches minimal (`main` + `agent-control` in normal steady state, no stale `chat-relay/*`);
5. confirm Local Agent is idle, phone is `IDLE`, and no owned live-call host process is still running before handing off;
6. refresh `docs/HANDOFF_NEXT_CHAT.md`;
7. preserve the autonomous-operation contract and do not regress to operator-driven terminal work.

Do not declare a physical CLIR task complete from synthetic/no-call results, permit consumption, a model statement or call termination alone.
