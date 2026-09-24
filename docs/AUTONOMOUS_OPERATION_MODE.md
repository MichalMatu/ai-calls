# Autonomous operation mode

Status: **NORMATIVE for active physical acceptance work**.

This repository is developed toward a fully autonomous phone-task loop. A new chat, supervisor session or Local Agent session must not regress to making the operator act as a terminal proxy, log copier or manual dialogue relay when the system can do the work itself.

## Target working loop

```text
agent/supervisor
 -> Local Agent
 -> readiness + IDLE
 -> real phone call
 -> deterministic script / PhraseMatrix
 -> bounded Gemma skill
 -> live supervisor takeover only when unresolved
 -> application-owned approval / commitment
 -> factual external result
 -> independent state verification when available
 -> cleanup to IDLE
 -> patch code from the physical finding
 -> next physical iteration
```

The development goal is that recurrent supervisor interventions are progressively moved into deterministic script/PhraseMatrix or bounded Gemma skills until the task completes without supervisor help.

## Executor priority

Local Agent is the default executor for autonomous development work that touches the real repository, local environment or phone. It should be used proactively, not only after a direct connector path fails.

Priority order for operational work is:

```text
Local Agent end-to-end when it can perform the task
 -> specialized connector/API only when it is a better fit for a narrow read-only/control-plane action
 -> operator action only when genuinely unavoidable
```

Do not fragment a workflow merely because a connector can perform one small sub-step. If Local Agent has the context and capability to inspect, edit, build, install, observe and verify a slice coherently, prefer that coherent path. External platform/tool controls still apply and must not be bypassed.

## Anti-stall decision protocol

Do not ask a question merely because a value was not supplied in the latest message. Resolve context before interrupting the operator:

```text
current instruction / handoff / repository state
 -> Local Agent / device / logs / files
 -> safe reversible repository-consistent default
 -> repair/retry transient or mechanical failure
 -> continue independent non-blocked work
 -> ask operator only for an unrecoverable material decision
```

A material decision is one that changes target, task, external effect, account/SIM, privacy/disclosure scope, meaningful cost or another irreversible outcome. Paths, branch/session names, routine retry counts, build/install after a required patch, state inspection and recovery from a mechanical tool failure are executor decisions, not operator questions.

Do not ask `continue?`, `should I retry?`, `should I inspect?`, or equivalent when the active goal and scope already answer those questions.

## Local Agent watchdog and recovery

After queueing a Local Agent task, keep ownership of its lifecycle:

1. poll daemon/result state;
2. treat an early result `404` as normal while the daemon has not published the result;
3. on a mechanical failure, read the result, correct the invocation/anchor/state and requeue without operator involvement;
4. restore a dirty failed workspace to the intended `origin/main` or deliberately recover its checkpoint before retry;
5. avoid concurrent mutations of the same checkout;
6. report to the operator only when the blocker is genuinely external or material.

## Hot-call mode

While the owned cellular call is `OFFHOOK`, live conversation handling has absolute execution priority. Do not start documentation cleanup, broad audits, builds or unrelated tasks until the call ends. Monitor the transient relay and call state continuously enough to answer supervisor handoffs during the same call. A pending relay request is handled before post-call diagnostics.

## No-stop rule

During an active authorized campaign:

- do not stop merely to request a redundant confirmation when the same exact task/target/effect authorization is already valid and unchanged;
- do not ask the operator to paste shell commands that Local Agent can execute;
- do not ask the operator to copy logs or transcripts that Local Agent, ADB or the transient relay can read;
- do not end a live call after one reprompt when a bounded next turn can continue toward the task;
- if Gemma cannot progress, the live supervisor takes over the same call instead of treating the handoff as terminal failure;
- do not replace a physical acceptance problem with synthetic/unit-test churn.

If an external platform or tool blocks an action, do not bypass that control and do not pretend the action executed. Continue every non-blocked step autonomously and report the exact external blocker only when operator action is genuinely unavoidable. Internal runner guard flags remain executor-owned: when a valid accepted authorization context covers the exact action, Local Agent supplies the required internal flag itself rather than asking the operator to type a command or flag.

## Dialogue takeover order

```text
known deterministic turn -> script/PhraseMatrix
unknown bounded dialogue -> Gemma skill
Gemma unresolved / TAKE_OVER -> live supervisor
```

Supervisor takeover is an intended fallback during development. The physical call should continue while the fallback is available.

## Durable campaign authorization is a product requirement

Chat text and documentation are not themselves an authority store. The target product mode therefore requires an explicit **durable, scoped, revocable campaign grant** owned by application policy.

A campaign grant must bind at least:

- allowed target(s);
- allowed task/effect set;
- account/SIM scope when relevant;
- issue time and revocation state;
- optional retry/expiry bounds;
- disclosure scope.

When such a grant is valid and the requested action remains inside its exact scope, the product must not prompt the operator again for every retry/call. A material target/task/effect/account widening remains fail-closed.

For the current Orange CLIR development campaign the intended grant scope is the same test SIM/account, Orange CLIR management, repeated physical enable/disable iterations required for acceptance, read-only CLIR state interrogation, and the Orange service route used by that campaign. This does not widen to unrelated services, numbers, purchases, payments, contracts, premium/emergency targets or another account.

Until the durable grant is implemented in application-owned policy, use the strongest currently available authorization context without inventing redundant confirmations, while respecting any external platform/tool confirmation that cannot be represented or bypassed by repository code.

## Commitment and success remain application-owned

Autonomous operation does not remove internal effect controls:

```text
exact CallTask + exact target
 -> typed CallExternalEffect
 -> deterministic validation
 -> exactly one CallCommitmentGate permit immediately before commitment
 -> exact one-shot permit consumption
 -> separate factual external-success evidence
 -> independent state verification when practical
 -> factual workflow completion
```

Authorization, permit consumption and spoken confirmation are not factual success.

For CLIR, prefer an independent network status interrogation after a mutation when technically available.

## Physical-first acceptance policy

During the active CLIR physical campaign, real calls are the acceptance loop. Do not run unit/synthetic suites as a substitute for physical iteration unless the operator explicitly asks for them. Build/compile/install needed to deploy changed code to the S22 are allowed.

A real call that exposes a failure should drive a minimal code change aimed at that observed failure, followed by another real call.

## Live supervision and evidence

While a real call is active:

- monitor call state and relay in time to respond during the same call;
- publish supervisor requests immediately when Gemma hands off;
- deliver supervisor responses before IVR timeout whenever possible;
- do not wait until after hangup to inspect a request that could have been answered live;
- preserve a sanitized final report before cleanup;
- delete transient raw relay branches after the session;
- keep plaintext identity out of durable Git history and ordinary logs.

## Operational invariants

Full autonomy remains bounded by application policy:

- one owned cellular call at a time;
- readiness immediately before dialing;
- phone confirmed `IDLE` before dialing;
- exact authorized target/task/effect only;
- no emergency, crisis, premium-rate or unrelated critical-service targets;
- do not terminate unrelated pre-existing calls;
- late-bound identity disclosure with application-owned policy;
- bounded retries and duration;
- cleanup the owned call back to `IDLE`;
- factual success evidence before declaring completion.

## Repository precedence

`AGENTS.md`, `docs/HANDOFF_NEXT_CHAT.md`, active runbooks and `docs/SECURITY_PRIVACY.md` must remain aligned with this autonomous-mode contract. Older wording that mechanically requests another confirmation despite an unchanged valid authorization context should be removed or narrowed.
