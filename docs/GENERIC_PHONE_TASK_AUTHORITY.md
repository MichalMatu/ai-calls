# Generic phone task authority

## Product invariant

Different phone tasks use one authority model. Orange CLIR, appointment booking, cancellation, reservation changes and future service actions must not grow separate commitment stacks.

## Implemented authority flow

```text
CallTask + exact CallResolvedTarget + constraints + authorized facts
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

## Current effect types

### Appointment

`CallExternalEffect.BookAppointment` adapts the proven `CallProposal` flow onto the same generic `CallCommitmentGate`. Exact time/price/provider/location evidence is checked before workflow completion.

### Service setting

`CallExternalEffect.SetService` covers bounded service changes such as:

```text
SET_SERVICE(CLIR=true)
```

`CallExternalEffectValidator` binds the effect to the exact `CallTask`, target, service and explicitly authorized value. Permit consumption alone never proves external success.

### Read-only work

Read-only queries do not need a commitment permit because they do not change external state. They still require fresh dial authorization, exact target binding, readiness and normal disclosure/output controls.

## Confirmation policy

Do not invent a second confirmation when the current-chat user instruction already exactly authorizes the concrete effect and no new material term was negotiated.

If dialogue introduces materially new terms, application policy decides whether new confirmation is required. Gemma/supervisor never make that authority decision.

## Dialogue boundary

Gemma and supervisor may interpret language, select bounded dialogue skills and propose candidate data. They cannot independently:

- dial or widen a target;
- widen task/effect scope;
- disclose unapproved plaintext identity;
- issue or consume a commitment permit;
- declare external success;
- complete the workflow.

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
- negotiated appointment authority proof.

Still open physically:

1. read-only Orange CLIR route discovery;
2. after route verification and fresh account-changing authorization, real CLIR execution through the existing generic authority lifecycle.

Route discovery is not commitment authority and must never be promoted to success evidence for CLIR activation.