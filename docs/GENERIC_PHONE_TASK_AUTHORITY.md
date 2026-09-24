# Generic phone task authority

## Product invariant

Different phone tasks use one authority model. Orange CLIR, appointment booking, cancellation, reservation changes and future service actions must not grow separate commitment stacks.

## Implemented authority flow

```text
accepted authorization context + exact CallTask + exact CallResolvedTarget + constraints + authorized facts
 -> typed CallExternalEffect candidate
 -> deterministic validation
 -> application user-decision policy when needed
 -> one-shot CallCommitmentGate permit bound to the exact effect
 -> reviewed execution/speech
 -> exact permit-consumption evidence
 -> separate external-success evidence
 -> factual effect completion
 -> workflow completion
```

Keep these separate:

```text
task authorization
 != candidate validation
 != permit issuance
 != permit consumption
 != external success
 != workflow completion
```

## Authorization context

The authority model must not depend on repeatedly asking the operator the same question for every retry.

The target product supports a durable, scoped, revocable campaign grant owned by application policy. A grant binds at least target(s), task/effect set, account/SIM scope when relevant, revocation state, optional retry/expiry bounds and disclosure scope.

A retry that remains inside a valid unchanged grant does not need another redundant product confirmation. A material widening of target, task, effect, account/SIM or disclosure scope remains fail-closed.

Chat prose, documentation, ServicePacks, model output and connected hardware are not themselves authority stores. External platform/tool controls remain outside repository authority and must not be bypassed.

## Current effect types

### Appointment

`CallExternalEffect.BookAppointment` adapts the proven `CallProposal` flow onto the same generic `CallCommitmentGate`. Exact time/price/provider/location evidence is checked before workflow completion.

### Service setting

`CallExternalEffect.SetService` covers bounded service changes such as:

```text
SET_SERVICE(CLIR=true)
SET_SERVICE(CLIR=false)
```

`CallExternalEffectValidator` binds the effect to the exact `CallTask`, target, service and explicitly authorized value. Permit consumption alone never proves external success.

### Read-only work

Read-only queries do not need a commitment permit because they do not change external state. They still require an accepted dial authorization context, exact target binding, readiness and normal disclosure/output controls.

## Confirmation policy

Do not invent a second confirmation when the active accepted authorization context already exactly covers the concrete effect and no new material term was negotiated.

If dialogue introduces materially new terms, application policy decides whether new confirmation is required. Gemma/supervisor never make that authority decision.

## Dialogue boundary

Gemma and supervisor may interpret language, select bounded dialogue skills and propose candidate data. They cannot independently:

- dial or widen a target;
- widen task/effect scope;
- disclose unapproved plaintext identity;
- issue or consume a commitment permit;
- declare external success;
- complete the workflow.

During physical acceptance the live supervisor may continue the same already-authorized call after a Gemma `TAKE_OVER`; recurrent cases should later move into deterministic script/PhraseMatrix or bounded Gemma skills.

## Service-specific adapters

Service-specific knowledge belongs in narrow adapters/ServicePacks around the generic model. It may provide terminology, known IVR routes, parsers or evidence rules; it must not become a second authority store or a giant exact-phrase script.

Orange CLIR is only the first physical acceptance case. Do not add `ClirCommitmentGate`.

## Evidence status

Completed:

- appointment coupling audit;
- generic typed commitment subject;
- appointment compatibility on the same gate;
- `SET_SERVICE(CLIR=true)` validation;
- one-shot permit lifecycle;
- separate external-success completion tracking;
- full synthetic/no-call product chain on S22;
- negotiated appointment authority proof;
- physical Orange multi-turn `script -> Gemma -> supervisor` execution plumbing;
- independent CLIR state interrogation.

Still open physically:

1. complete real CLIR enable with factual external-success evidence;
2. independently verify caller-ID restriction is active;
3. execute the inverse CLIR disable flow;
4. iterate until recurrent supervisor interventions are removed from the common path.

Current independent network-state evidence says CLIR is still disabled. Route discovery, permit consumption and call termination are not success evidence for CLIR activation.
