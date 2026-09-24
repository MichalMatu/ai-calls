# Orange evidence runbook

This runbook governs Orange-specific route/evidence work. It is not live-call authorization and it is not product authority.

## Scope

- repository: `MichalMatu/ai-calls`;
- durable branch: `main`;
- Orange service-tree source: `service-packs/orange/service_tree.v1.json`;
- current acceptance work: `docs/G5_CLIR_ROUTE_DISCOVERY.md`.

Orange knowledge exists to support generic phone-task execution. Do not grow broad IVR mapping merely to accumulate edges.

## Evidence levels

Keep these distinct:

- `DISCOVERED` — known capability/seed, not a proven route;
- verified node/edge — exact physical observation;
- `service_route_verified=true` — intended deterministic service route physically proven;
- `HOST_GREEN` — host verification only;
- `PROVEN_S22` — required behavior physically reproduced on S22.

Historical route evidence may become stale when Orange changes its IVR. Never infer an unobserved edge from old wording.

## ServicePack boundary

Orange ServicePack may contain prompts, nodes, edges, reviewed actions, barriers and evidence metadata. It does not grant:

- dialing authority;
- disclosure authority;
- commitment authority;
- permission to treat a route as success evidence.

The task/effect authority remains application-owned.

## Read-only discovery procedure

For a deliberately authorized physical discovery slice:

1. read fresh `origin/main`, `docs/HANDOFF_NEXT_CHAT.md`, `docs/ROADMAP.md`, `docs/G5_CLIR_ROUTE_DISCOVERY.md` and current service tree;
2. if Local Agent is used, verify the fresh binding and idle/active-task state;
3. require fresh live-call authorization for the exact Orange target and task;
4. require phone `IDLE` and pass the app-level live-call readiness check before dialing;
5. use one exact reviewed non-committing utterance;
6. switch to `OBSERVE_ONLY` for the response/evidence turn;
7. never issue or consume a commitment permit during discovery;
8. stop at authentication, credentials, customer-data, payment, purchase, activation/deactivation, tariff/contract/package change or any other commitment barrier;
9. clean up the owned call to `IDLE`;
10. persist only evidence actually observed; do not promote `service_route_verified` without physical route proof;
11. run relevant host verification after repository changes.

For the current CLIR discovery slice the reviewed action is `caller_id_restriction_info`; the next turn is `OBSERVE_ONLY`.

## Execution is a separate gate

Discovering how Orange routes or describes CLIR does not authorize or prove CLIR activation.

A later G5c execution must use the generic authority chain:

```text
CallTask + exact target + explicit service.enabled=true
 -> CallExternalEffect.SetService(CLIR=true)
 -> deterministic validation
 -> one-shot CallCommitmentGate permit
 -> reviewed execution/speech
 -> exact permit consumption
 -> separate external-success evidence
 -> factual effect completion
 -> workflow completion
```

No Orange-specific commitment gate is allowed.

## Retry policy

Retry only narrowly tested transport/root-acquisition conditions. Do not turn observed Orange fragments into broad fuzzy speech rules. `OBSERVE_ONLY` never speaks.

## Safety invariants

- one active cellular call at a time;
- exact authorized target only;
- no arbitrary free-text IVR exploration surface;
- no model-generated commitment authority;
- no invented credentials/customer data/payment data;
- no account-changing action during read-only discovery;
- final owned call state must be `IDLE`;
- frozen Samsung media/Shizuku code is untouched unless a separate physical regression gate justifies it;
- every new physical session needs fresh authorization; old evidence, handoff text or allowlists do not carry permission forward.

Detailed historical experiments belong in Git history and `.agent/results`, not new status documents.