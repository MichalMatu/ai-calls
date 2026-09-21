# Handoff — Gate C / CallPlan v1

Date: 2026-09-21

Repository: `MichalMatu/android-ai-call-bridge`

Durable product branch: `main`

Local Agent control branch: `agent-control`

## Start here next time

1. Read fresh `AGENTS.md`, this file, `README.md`, `docs/ROADMAP.md`, and `docs/NIGHT_AUTONOMOUS_RUN_2026-09-21.md`.
2. Read `docs/ARCHITECTURE.md` and `docs/SECURITY_PRIVACY.md` before changing authority or privacy boundaries.
3. Fetch fresh `main` and `agent-control:.agent/status/daemon.json` before any write or Local Agent task.
4. Inspect the exact latest Local Agent terminal result before creating a successor task.
5. Never trust a Local Agent binding or commit SHA copied from prose; use the current Bridge envelope and fresh daemon state.
6. Keep `privileged-helper/` and the frozen Samsung media path untouched.

## Frozen foundation

Target: Samsung Galaxy S22+ `SM-S906B`.

- cellular RX/TX bridge: `DONE / PROVEN_S22 / FROZEN`;
- local Polish STT/TTS: `DONE / PROVEN_S22`;
- provider-neutral text turn + application-owned approval: `DONE / PROVEN_S22`;
- Gate A readiness: `DONE`;
- original llama.cpp phone-local model sweep: frozen;
- interactive ChatGPT relay: developer benchmark infrastructure only;
- `privileged-helper/`: frozen.

## Edge Gallery + Agent Skills checkpoint is closed

Status: `FROZEN / PROVEN_S22 PARTIAL / NOT PRODUCT_READY`.

The development harness remains useful evidence:

- clean official Google AI Edge Gallery upstream base: `6353707057ccc524a6e513e73f0d6d5886f348a8`;
- experimental package: `com.google.aiedge.gallery`;
- loopback API: `127.0.0.1:8080`;
- model: `Gemma-4-E2B-it`;
- official Agent Skills phone tools were proposal-only: `say`, `listenMore`, `takeOver`;
- CallBridge retained target authorization, sensitive-data authority, commitment authority, application approval and TAKE OVER.

Positive evidence remains valid:

- warm Agent Skills `SAY` decisions were repeatedly about `3.58-4.07 s` in v16;
- a decision started from the real late Orange partial completed about `2.33 s` before the simulated final endpoint;
- `/v1/agent/interrupt` was fast and stale work could be cancelled cleanly;
- real Orange partial STT exposed a semantically complete greeting several seconds before final endpoint;
- the temporary v17 speculative integration built, unit-tested and installed cleanly without changing durable product code.

The final bounded live experiment did **not** establish a reliable product path:

- v18 made exactly one allowlisted information-only call to `510100100`;
- Orange RX and local STT worked and captured the full Max question;
- Edge failed with `backend_agent_network_IOException` before application approval, TTS or cellular TX;
- therefore no model-generated reply was transmitted in v18;
- cleanup hung up the created call, restored Bluetooth and returned the phone to idle.

Root-cause evidence from v19/v20:

- after v18 the Edge Gallery process was gone and port 8080 was closed;
- historical logcat showed a crash in `com.google.aiedge.gallery` while `LocalPhoneAgentRuntime.decide()` was contending on the `edge-local-api-worker` monitor;
- after relaunch, loopback from the CallBridge UID worked and a normal off-call decision succeeded, so the basic loopback transport was not the blocker;
- the first post-relaunch decision took about `10.65 s`;
- the Edge process was about `2.58 GB` total PSS after inference, with additional swap pressure.

Conclusion: speculative timing is technically plausible, but the current Gemma 4 E2B / Edge Gallery Agent Skills runtime is not sufficiently reliable or lightweight for the live S22 product path. Do not continue adding Edge probe hacks or repeat Orange calls by default. Preserve the evidence and revisit only after a materially improved runtime/model/hardware condition or an explicit user decision.

Representative evidence:

```text
.agent/results/chatgpt-edge-speculative-offcall-benchmark-v16-20260921.json
.agent/results/chatgpt-edge-speculative-live-build-v17-20260921.json
.agent/results/chatgpt-orange-speculative-live-v18-20260921.json
.agent/results/chatgpt-edge-live-transport-diagnosis-v19-20260921.json
.agent/results/chatgpt-edge-exit-reason-offcall-v20-20260921.json
```

## Active goal: Gate C — CallPlan v1

Gate C is now the active productization gate. The objective is a deterministic call brain for common bounded turns, with any future model/helper restricted to language classification or paraphrase proposals.

### Preimplementation audit result

Existing authority types already cover the important security ownership and should be reused rather than copied:

- `CallTask` — immutable operator/user-authorized task, including `CallConstraints`, `CallPreferences` and `authorizedFacts`;
- `CallResolvedTarget` — one concrete resolved target; it does not itself grant dialing authority or widen a runtime allowlist;
- `CallWorkflow` — task progress, resolved-target state, concrete pending proposal and user-decision state;
- `CallConfirmationPolicy` — deterministic evaluation of a concrete `CallProposal` against immutable task constraints/preferences;
- `CallCommitmentGate` — one-shot permit bound to one exact concrete proposal;
- application-owned output approval — final speech release remains fail-closed outside normal active negotiation and while a commitment permit is pending.

The narrow new responsibility is **not another authority model**. Introduce an immutable `CallPlan` execution context that references existing authority objects instead of duplicating their fields. Its product responsibility is limited to:

- the existing `CallTask`;
- the already resolved `CallResolvedTarget`;
- immutable deterministic dialogue rules for known intents/questions;
- completion criteria;
- bounded repeat/escalation policy.

A deterministic plan engine may classify a final transcript into one of the plan's known rules and produce a **proposal decision** such as `SAY`, `ASK_REPEAT`, `PROPOSAL`, `COMPLETE` or `TAKE_OVER`. It must not dial, mutate the task, create authorized facts, approve a commitment, issue a commitment token, or directly write to telephony TX.

Known-fact answers should refer to an `authorizedFacts` key and resolve the value from the existing `CallTask` at decision time. A plan must fail closed if a referenced fact is absent. Counterparty offers remain `CallProposal` data and continue through the existing `CallWorkflow` / `CallConfirmationPolicy` / `CallCommitmentGate` path.

A future language helper may only suggest a bounded intent/rule match or wording. It may not introduce a new fact, rule, target, action, commitment or authority. The deterministic application layer validates the helper result against the plan before use.

### RED/GREEN matrix before implementation

Start Gate C implementation with host tests only and TDD. The first slice should prove:

| Case | RED expectation | GREEN behavior |
| --- | --- | --- |
| known question -> authorized fact | no deterministic plan path exists yet | returns a `SAY` proposal using exactly the value already present in `CallTask.authorizedFacts` |
| rule references missing fact | current code has no plan validation | construction/decision fails closed; never invents a value |
| unknown or ambiguous final transcript | no bounded deterministic classifier exists | returns `ASK_REPEAT` or `TAKE_OVER` according to bounded fallback policy; never guesses |
| known counterparty offer | no CallPlan routing exists | emits typed `CallProposal` data for the existing workflow policy; never accepts it directly |
| proposal outside constraints/preferences | preserve current authority behavior | existing `CallConfirmationPolicy` returns `NEEDS_USER_DECISION`; plan cannot override it |
| commitment without exact permit | preserve current commitment gate | remains blocked; only the existing one-shot exact-proposal permit can authorize commit execution |
| output outside active negotiation / while permit pending | preserve current output gate | final speech remains dropped/fail-closed |
| target binding | plan has no dialing authority | plan references the resolved target but cannot mutate or widen the runtime dial allowlist |
| completion criterion matched | no deterministic plan completion route exists | produces structured completion proposal/outcome; `CallWorkflow.complete` remains owner of terminal workflow state |
| optional helper proposes unsupported intent/fact/action | helper is untrusted | deterministic validator rejects it and falls back; helper never widens plan or task authority |
| mutable caller collections | new plan types must not leak mutation | all plan/rule/fallback collections are defensively copied and immutable |
| privacy rendering | new plan may contain task/target references | `toString()`/ordinary diagnostics redact task facts and dial target just like existing models |

## Next exact engineering step

Implement only the first host-only Gate C slice after confirming fresh `main`:

1. add the minimal immutable `CallPlan` / rule / decision data model needed for the first deterministic known-question path;
2. RED tests first for authorized-fact lookup, missing-fact fail-closed behavior, unknown-intent fallback, immutability and redacted rendering;
3. GREEN with the smallest deterministic engine; do not wire telephony, STT, TTS, diagnostics or a model yet;
4. reuse `CallTask` and `CallResolvedTarget` by reference; do not duplicate constraints/preferences/facts;
5. do not modify `CallWorkflow`, `CallConfirmationPolicy`, `CallCommitmentGate` or frozen media unless a failing test proves a concrete integration gap;
6. run `bash scripts/verify_host.sh`;
7. update authoritative docs only when behavior actually changes.

No S22 gate is needed for this first pure host/data-policy slice. No further Orange call is part of this continuation.
