# G5 CLIR physical acceptance

G5 started as route discovery, but the active work is now iterative physical CLIR execution on the S22. Unknown routing, commitment authority and factual success must still remain separate.

Read `docs/AUTONOMOUS_OPERATION_MODE.md` before continuing this gate.

## Historical G5a — DONE host-only

The historical pure discovery contract is retained at `scripts/aicall_tools/orange/history/g5_clir_route_discovery_plan.py`.

```text
exact allowlisted Orange target
 -> mandatory live-call readiness before dialing
 -> reviewed caller_id_restriction_info turn
 -> OBSERVE_ONLY next turn
 -> nonblank observation evidence
```

This historical phase intentionally had no external effect or commitment permit.

## Historical G5b — PHYSICAL ATTEMPT COMPLETE

On 2026-09-24 the first physical Orange discovery call used the reviewed information turn and returned a generic Orange clarification/reprompt rather than a CLIR-specific route.

Redacted observation:

```text
Przepraszam, że przedłużyć Dale. Chcę mieć pewność, w jakiej sprawie dzwonisz do nas. Powiedz proszę, czego dotyczy twoja sprawa.
```

That evidence remains useful history, but the active implementation has moved beyond the single-turn `OBSERVE_ONLY` probe.

## Active G5c — PHYSICAL EXECUTION LOOP

The current live path uses the Orange on-net route `*100` and a bounded multi-turn dialogue:

```text
readiness + IDLE
 -> real Orange call
 -> deterministic CLIR navigation
 -> deterministic clarification for known generic reprompt
 -> Gemma skill for bounded dialogue
 -> live supervisor takeover when unresolved
 -> application-owned approval/commitment
 -> factual Orange result
 -> independent CLIR network-state interrogation
 -> cleanup to IDLE
```

Observed physical behavior includes the Orange prompt:

```text
podaj dowolny numer twojej usługi lub wprowadź go na klawiaturze
```

Identity data required by that prompt must remain late-bound and transient. Do not copy the current SIM/service number into durable Git history, ServicePacks or ordinary logs.

## Current factual state

The latest independent network interrogation with the supported CLIR status flow returned:

```text
Caller ID defaults to not restricted. Next call: Not restricted
```

Therefore:

```text
CLIR enabled = false
G5c physical success = false
```

Do not treat the previous call, route entry, permit state or model output as success.

## Dialogue order

The required dialogue fallback order is:

```text
known turn -> script/PhraseMatrix
unknown bounded turn -> Gemma skill
Gemma unresolved / TAKE_OVER -> live supervisor in the same call
```

A supervisor takeover is part of the development loop. It should not terminate an otherwise healthy call. Recurrent takeover cases should be converted into deterministic script/PhraseMatrix or bounded Gemma skills in later iterations.

## Commitment lifecycle

A real CLIR mutation must use only the existing generic lifecycle:

```text
accepted authorization context + exact target/task
 -> CallExternalEffect.SetService(CLIR=<desired state>)
 -> deterministic validation
 -> exactly one shared CallCommitmentGate permit immediately before commitment
 -> reviewed effect speech/execution
 -> exact one-shot permit consumption evidence
 -> separate factual external-success evidence
 -> independent network-state verification when practical
 -> cleanup owned call to IDLE
 -> factual workflow completion
```

No `ClirCommitmentGate` may be introduced. Permit consumption is not external success.

## Autonomous campaign direction

The product target is a durable, scoped, revocable campaign grant owned by application policy. It should allow repeated physical retries inside an unchanged Orange CLIR scope without redundant product prompts while preserving exact target/task/effect/account boundaries.

Chat prose, documentation, connected hardware, ServicePacks, model output and old evidence are not themselves the authority store. External platform/tool controls must not be bypassed.

## Physical-first development rule

During the active CLIR campaign:

- real calls are the acceptance loop;
- unit/synthetic suites must not replace physical progress unless the operator explicitly requests them;
- build/compile/install is allowed when code changes;
- preserve a sanitized probe report before cleanup;
- monitor supervisor relay requests while the call is still active;
- patch the smallest observed physical failure, then repeat the real call.

## Completion condition

Enable acceptance requires factual success evidence plus an independent network-state check confirming caller-ID restriction is active.

After enable succeeds, the next physical task is the inverse operation (`SET_SERVICE(CLIR=false)`) and the same loop repeats until the recurrent dialogue can complete through script + Gemma without supervisor help.
