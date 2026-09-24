# Generic phone task authority

## Goal

The product should accept flexible phone tasks such as:

- "call Orange and enable CLIR";
- "call a clinic and book a dermatologist next week after 16:00, preferably under 250 PLN";
- "cancel my reservation";
- "ask the service provider for the current status, but do not change anything".

These are different tasks using one authority model, not separate hard-coded architectures.

## Existing good foundation

`CallTask` is already generic. It carries the user-authorized task, constraints, preferences and authorized facts. `CallResolvedTarget` binds the concrete target. Dialogue, TaskGraph, PhraseMatrix, Gemma, ServicePacks and supervisor output are data/proposal layers rather than authority owners.

The main remaining coupling is commitment: the proven Gate D flow binds `CallCommitmentGate` directly to appointment-shaped `CallProposal` data. That worked for `BOOK_APPOINTMENT`, but must not become the shape of every future task.

## Target abstraction

Introduce one application-owned typed **external effect candidate**. The exact implementation name may change after the preimplementation audit; this document uses `ExternalEffectCandidate` descriptively.

Conceptually it contains:

```text
ExternalEffectCandidate
  effect type
  exact target
  typed parameters
  material terms
  evidence requirements
```

Examples:

```text
SET_SERVICE(setting=CLIR, enabled=true)
BOOK_APPOINTMENT(specialty=dermatology, time=..., price=..., provider=...)
CANCEL_RESERVATION(reference=...)
CHANGE_RESERVATION(reference=..., time=...)
READ_ONLY_QUERY(topic=...)
```

`READ_ONLY_QUERY` does not need commitment authority because it produces no external state change.

## Authority flow

```text
CallTask + CallResolvedTarget + constraints + AuthorizedFactSnapshot
 -> dialogue discovers/negotiates candidate data
 -> application constructs typed ExternalEffectCandidate
 -> deterministic validation against task scope and constraints
 -> user-decision policy if material terms were not already explicitly authorized
 -> one-shot permit bound to that exact candidate
 -> reviewed effect execution / reviewed speech
 -> exact consumption evidence
 -> external success evidence
 -> factual completion
```

Keep these distinct:

```text
task authorization
 != candidate validation
 != permit issuance
 != permit consumption
 != external success
 != workflow completion
```

## No redundant confirmation

Do not add an artificial second confirmation when the current-chat user instruction already exactly authorizes the concrete effect.

Example:

```text
"Call Orange and enable CLIR on my number"
```

If the candidate is exactly `SET_SERVICE(CLIR=true)` on the exact authorized Orange target and no new material term was negotiated, application policy may treat the original explicit instruction as the user decision.

By contrast:

```text
"Call a clinic and book me a dermatologist next week after 16:00, preferably under 250 PLN"
```

may require a user decision if the counterparty offers materially new terms outside already-authorized hard constraints/preferences, depending on the product policy. The model does not decide whether confirmation is required.

## Dialogue ownership

Target dialogue path:

```text
finalized STT
 -> PhraseMatrix / deterministic task state
 -> Gemma 4 bounded dialogue-skill classifier
 -> application-owned exact response when possible
 -> supervisor fallback for unresolved conversational reasoning
 -> application validation/output approval
 -> TTS/TX
```

Gemma and supervisor may help understand a turn, choose a bounded dialogue action and fill candidate data. They must not independently:

- dial or widen the target;
- disclose unapproved plaintext identity;
- expand task scope;
- create commitment authority;
- decide that an external action succeeded;
- complete the workflow.

## Effect adapters

Service/task-specific behavior should live behind narrow adapters around the generic effect model.

### Appointment

The existing proven `BOOK_APPOINTMENT` flow becomes the first compatibility adapter. Its current `CallProposal` semantics and proof must remain green while the generic commitment subject is introduced.

### CLIR

CLIR becomes the first new acceptance adapter:

```text
CallTask: enable caller-ID restriction
ExternalEffectCandidate: SET_SERVICE(CLIR=true)
Success evidence: counterparty confirmation that CLIR was enabled
```

Do **not** create a separate `ClirCommitmentGate`.

### Future clinic flow

The next broad acceptance case should exercise negotiation rather than a fixed service toggle:

```text
CallTask: book dermatologist
constraints: date/time/price/etc.
ExternalEffectCandidate: BOOK_APPOINTMENT(...negotiated exact terms...)
Success evidence: confirmed appointment details
```

This is the more representative target product experience.

## ServicePack role

ServicePacks may provide terminology, known IVR hints, evidence parsers or service-specific typed adapters. They are not authority and must not become exact-phrase scripts that define the product dialogue engine.

Orange exact phrase mappings remain acceptance fixtures only.

## Implementation order

1. **DONE** — audit every place where `CallProposal` was treated as the universal commitment subject.
2. **DONE** — introduce `CallExternalEffect` as the generic commitment subject while preserving `BOOK_APPOINTMENT` through the same single gate.
3. **DONE** — keep existing appointment Gate D behavior green through compatibility adapters without a second authority store.
4. **DONE for SET_SERVICE** — deterministic validator binds service effects to `CallTask`, exact resolved target and explicitly authorized service value.
5. **DONE synthetically** — add `SET_SERVICE(CLIR=true)`, exact permit lifecycle and separate exact external-success evidence.
6. **DONE** — the product acceptance runner uses deterministic routing, `LOCAL_GEMMA_4`, bounded supervisor fallback, application output approval and the generic effect authority path.
7. **DONE on S22/no-call** — synthetic RX/TX substitutes surround the real on-device STT/Gemma/TTS path; exact permit consumption, external success, factual effect completion and workflow completion are proven separately.
8. **NEXT, requires fresh authorization** — run one bounded Orange CLIR acceptance call only after a new explicit live-call authorization.
9. Add the clinic booking acceptance case to prove the architecture is truly generic.

## Non-goals for this refactor

- no Samsung media rewrite;
- no privileged-helper rewrite;
- no Gemma model/download changes;
- no growth of Orange aliases as the primary strategy;
- no second authority store;
- no model-owned commitment/completion;
- no special-case CLIR gate that would need to be replaced later.
