# Generic phone task authority

**Bieżąca faza — decyzja właściciela 2026-11-05:** modułowe laboratorium porównań STT/model/strategia/instrukcje/TTS i scenariuszy; docelowy flow powstaje później ze sprawdzonych części. [Zakres i architektura laboratorium](TEST_LAB_SCOPE.md). Poniższe zasady zadania, danych, skutków i własności Call dotyczą rzeczywistego wykonania także w aplikacji testowej.


## Przyjęcie zadania bez dodatkowych grantów — 2026-11-05

Polecenie użytkownika przekazane przez lokalny Codex jest przyjmowane przez aplikację jako konkretne zadanie. Jawny START z chronionej `android.permission.DUMP` ścieżki wykonawczej albo z UI przechodzi przez ten sam `CampaignAgentControl`: zapis dokładnego celu, efektu, SIM/konta i dozwolonych pól, następnie wykonanie. Nie ma osobnego przyznawania/odnawiania grantu, potwierdzenia na telefonie ani 24-godzinnego TTL. Podgląd/preflight nie przyjmuje zadania. Automatyczna kontynuacja korzysta z istniejącego zapisu i nie odtwarza cofnięcia; świeże polecenie użytkownika może przyjąć nowe zadanie. Wewnętrzny `CampaignAuthorizationStore` pozostaje jednym zapisem zakresu, a nie dodatkową procedurą zgody.

Zachowane są rzeczywiste granice: uprawnienia Androida/Shizuku, wyłącznie własny Call, STOP/anulowanie, dokładny zakres, `FactDisclosurePolicy`, aplikacyjny release i pojedynczy `CallCommitmentGate`. Model i supervisor nie otrzymują prawa tworzenia zadań. Nagrania są osobną opcją diagnostyczną, przekazywaną w poleceniu lub UI, i nie warunkują rozmowy. Bieżący rejestr: [BLOCKERS](../BLOCKERS.md), F-062/F-065/F-066/F-067.


## Product invariant

**Owner's desired delegation model (2026-11-02):** AI Calls is a local autonomous caller, not a permanent human-supervised call attachment. A person issues a task with a destination or criteria and permitted scope; local AI executes telephone dialogue, can resume bounded attempts, act on user-authorized data and service tasks, terminate its own session and return factual results. The system should support the user's normal lawful destinations and must not be restricted by a product-wide Orange number allowlist.

**One authorization, autonomous execution:** a valid scoped application grant should allow the entire matching call/IVR/conversation/retry/cleanup workflow without forcing manual phone interaction or a repeated app prompt for every step. The owner has given broad permission for independent development and testing, which is not itself a production grant for unspecified destinations, other persons' accounts, new purchases or unapproved disclosure. Material changes still require the product's real authorization checks; never treat a Markdown sentence or chat message as the external-effect permit.

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

The target product supports a durable, scoped, revocable campaign grant owned by application policy. A grant binds at least target(s), task/effect set, account/SIM scope when relevant, revocation state, explicit cancellation without an artificial task expiry or attempt quota and disclosure scope.

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
