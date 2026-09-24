# Orange evidence runbook

This runbook governs Orange-specific route/evidence work. It is not an authority store and it does not replace application-owned authorization policy.

Read `docs/AUTONOMOUS_OPERATION_MODE.md` for the normative physical-work mode.

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

Orange ServicePack may contain prompts, nodes, edges, reviewed actions, barriers and evidence metadata. It does not independently grant:

- dialing authority;
- disclosure authority;
- commitment authority;
- permission to treat a route as success evidence.

The task/effect authority remains application-owned.

## Physical execution procedure

For active Orange CLIR acceptance:

1. read fresh `origin/main`, `docs/HANDOFF_NEXT_CHAT.md`, `docs/AUTONOMOUS_OPERATION_MODE.md`, `docs/ROADMAP.md`, `docs/G5_CLIR_ROUTE_DISCOVERY.md` and the current service tree;
2. verify fresh Local Agent binding and active-task state;
3. require an accepted application authorization context covering the exact target/task/effect;
4. require phone `IDLE` and pass app-level live-call readiness immediately before dialing;
5. run the real call with deterministic script/PhraseMatrix first;
6. use bounded Gemma skills for unknown dialogue;
7. if Gemma returns `TAKE_OVER` or remains unresolved, the live supervisor continues the same call when possible;
8. issue exactly one shared `CallCommitmentGate` permit immediately before a real account-changing commitment;
9. require separate factual external-success evidence;
10. independently interrogate CLIR network state when technically available;
11. clean up the owned call to `IDLE`;
12. persist only sanitized evidence actually observed and preserve it before transient relay cleanup;
13. patch the smallest physical failure and repeat the real iteration.

Do not ask the operator to paste shell commands, copy logs or manually relay dialogue when Local Agent/ADB/supervisor can perform the step directly.

## Read-only discovery

Read-only `OBSERVE_ONLY` discovery remains available when a genuinely unknown Orange route needs evidence. It does not use a commitment permit and must stop before an account-changing barrier.

The original G5b `caller_id_restriction_info -> OBSERVE_ONLY` attempt is historical; the active CLIR campaign has moved to multi-turn physical execution.

## Generic commitment chain

Orange execution must use the generic authority chain:

```text
accepted authorization context + exact CallTask + target
 -> CallExternalEffect.SetService(CLIR=<desired state>)
 -> deterministic validation
 -> one-shot shared CallCommitmentGate permit
 -> reviewed execution/speech
 -> exact permit consumption
 -> separate external-success evidence
 -> independent network-state verification when practical
 -> factual effect completion
 -> workflow completion
```

No Orange-specific commitment gate is allowed.

## Authorization direction

The target product represents repeated campaign authority as a durable, scoped, revocable application-owned grant. When an unchanged retry remains within a valid grant, the product should not ask for another redundant product confirmation.

Documentation, old evidence, ServicePacks, allowlists and model output are not authority stores. Material target/task/effect/account widening remains fail-closed. External platform/tool controls must not be bypassed.

## Retry policy

Retry narrowly from observed physical failures. Do not turn fragments into broad fuzzy speech rules. Recurrent supervisor interventions should become deterministic rules or bounded Gemma skills only after physical evidence supports them.

During the active CLIR campaign, synthetic/unit suites must not substitute for physical acceptance unless the operator explicitly requests them.

## Safety invariants

- one active owned cellular call at a time;
- exact authorized target/task/effect only;
- no arbitrary free-text IVR exploration surface;
- no model-generated commitment authority;
- no invented credentials/customer data/payment data;
- late-bound plaintext identity disclosure only through application policy;
- final owned call state must be `IDLE`;
- frozen Samsung media/Shizuku code is untouched unless a separate physical regression gate justifies it;
- factual success is not inferred from permit consumption, call termination or dialogue wording alone.

Detailed historical experiments belong in Git history and `.agent/results`, not new status documents.
