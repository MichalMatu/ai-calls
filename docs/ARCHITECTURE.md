# Architecture

## Freeze 2026-11-03 — rozdział odpowiedzialności (bez zmiany runtime)

Pełny [checkpoint organizacyjny](checkpoints/2026-11-03-organizational-freeze-f035-pre-voice-skills.md) ustala, co zweryfikowano na realnym S22 i jakie prace **nie** zostały jeszcze wykonane. **Nie oznaczaj całości rozmowy jako PASS**: techniczne RX/STT/Gemma/TTS/TX z wcześniejszej próby są realne, ale słyszalność u konsultanta i zewnętrzny efekt CLIR pozostają OPEN.

```text
Telefon/Android: autoryzacja + stan Call + RX/STT/TTS/TX + STOP
  → deterministyczny stan zadania / workflow / PhraseMatrix
    → AI model (interpretacja wejścia i propozycja strukturalnego skill/action)
      → kontrakt skilla (dozwolone akcje/fakty/preconditions/fallback)
        → deterministyczny executor + fact/disclosure/effect policies
          → approved speech → TTS/TX → external evidence/verification
```

**Kontrakt:** AI nie jest skryptem i nie ma kontroli nad telefonem; skille nie są uprawnieniami; skrypty (silnik zadania/IVR/tekst odpowiedzi) nie mogą same poszerzać grantu. `service-packs/` to dane domenowe; `scripts/` w repozytorium to również hostowe narzędzia developerskie i nie jest to automatycznie Android skill runtime. Istniejące `DialogueActionBackend`, `DialogueSkillTextBackend`, `CallCommitmentGate` i `FactDisclosurePolicy` należy zachować i migrować stopniowo, z testami równoważności.

**Model przed połączeniem:** `AppOwnedCampaignService.startAuthorizedAttempt` korzysta już z `CampaignGemmaPreflight.prepare`, a `GateCHybridDialogueBackendFactory` dostaje przygotowaną instancję lokalnej Gemmy **przed Telecom dial**. Po prewarm autoryzacja, SIM i IDLE są ponownie sprawdzane. Pierwszy EOS→TX: 47 567 ms przed zmianą, 10 567 ms po niej; pozostałe opóźnienie wymaga profilowania, nie drugiego identycznego prewarm.

**Głos:** natywne wejście audio Gemmy nie oznacza automatycznego wyjścia głosowego ani wsparcia realnego half/full-duplex w tej aplikacji. Nowe modele są **opcją przyszłego benchmarku**, nie zależnością checkpointu. Zmiany w zamrożonym media/Telecom tylko po oddzielnym QA.

## Goal

**Full product definition (owner clarification, 2026-11-02):** the user delegates a real-world outcome to local AI and need not manually place, supervise or end each normal call. The product should accept ordinary lawful user-selected destinations and perform user-authorized read-only or data-changing phone tasks in a unified engine. Its complete lifecycle is task interpretation, approved target/SIM selection, autonomous dial, speech/IVR reasoning, controlled action, independently verified result, call teardown, optional bounded retry/resume and concise user report. Orange CLIR is an acceptance case; manual supervised audio is diagnostic scaffolding, neither defines product scope.

The developer is authorized to independently implement, test, refactor and deploy this architecture on the designated project/test environment, without repetitive operator confirmation for routine development or an unchanged user-approved task. An actual live external effect still uses the application-owned exact task/target/effect/disclosure authority contract; engineering permission is not a blanket license to call arbitrary people or modify unrelated accounts.

AI Calls is a bounded autonomous phone-task engine for ordinary cellular calls. Carrier settings, appointments, reservations, service requests and read-only calls must share one task/dialogue/authority model rather than separate bots.

`docs/AUTONOMOUS_OPERATION_MODE.md` defines the normative physical-operation mode.

## Frozen media boundary

`CallMediaSessionCoordinator` and the Samsung/Shizuku implementation under `privileged-helper/` are `PROVEN_S22 / FROZEN`.

```text
RX: VOICE_DOWNLINK -> privileged helper -> transferred PFD -> app
TX: app -> transferred PFD -> CALL_ASSISTANT / TELEPHONY_TX -> cellular uplink
```

Read `docs/PHASE2D_FREEZE_2026-09-18.md` before changing this layer.

## Runtime

```text
app-owned authorization context / durable campaign grant
 -> exact target + constraints + authorized facts
 -> readiness + IDLE
 -> app-owned dial
 -> telephony RX
 -> STT
 -> PhraseMatrix / deterministic task state
 -> Gemma 4 bounded dialogue skill when useful
 -> optional live supervisor fallback only on developer paths that explicitly enable it
 -> application output approval
 -> TTS/TX
 -> typed external-effect authority if external state must change
 -> factual external-success evidence
 -> independent external-state verification when available
 -> workflow completion
 -> cleanup
```

## Authorization context

The runtime requires an accepted application-owned authorization context for exact target/task/effect scope.

The product implements a durable, scoped, revocable campaign grant so repeated retries inside an unchanged scope do not require redundant product prompts. The current Android `Campaign tools` path creates/revokes the grant locally, binds it to the default voice subscription, reserves attempts durably and executes the dial without ChatGPT/Local Agent/ADB. The grant must bind target(s), task/effect set, account/SIM scope when relevant, revocation state, optional retry/expiry bounds and disclosure scope.

Chat prose, docs, ServicePacks, model output and connected hardware are not authority stores. A material scope widening remains fail-closed. External platform/tool controls are not bypassed by the application.

## Authority owners

- authorization context / durable campaign grant — exact user-authorized execution scope;
- `CallTask` — authorized goal, hard constraints, preferences and facts;
- `CallResolvedTarget` — exact target binding;
- `CallExternalEffectValidator` — deterministic effect/task/target binding;
- `CallCommitmentGate` — the single one-shot commitment store for typed external effects;
- `CallExternalEffectCompletionTracker` — post-consumption exact success-evidence completion;
- `CallWorkflow` / TaskGraph owner — workflow state and terminal completion;
- `FactDisclosurePolicy` — personal-data disclosure;
- application output approval — final text release before TTS/TX.

TaskGraph definitions, PhraseMatrix, CallPlan, Gemma, supervisor/ChatRelay, ServicePacks, parsers, matchers and diagnostics are proposal/evidence layers, not independent authority owners.

## Generic external effects

The generic commitment migration is implemented.

```text
accepted authorization context + CallTask + CallResolvedTarget + constraints + authorized facts
 -> typed CallExternalEffect candidate
 -> deterministic validation
 -> user-decision policy if needed
 -> one-shot CallCommitmentGate permit bound to the exact effect
 -> reviewed execution/speech
 -> exact permit-consumption evidence
 -> separate external-success evidence
 -> independent state verification when practical
 -> factual effect completion
 -> workflow completion
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

Current typed examples include `CallExternalEffect.BookAppointment` and `CallExternalEffect.SetService` (`CLIR=true` / `CLIR=false`). The existing appointment flow uses the same commitment store through compatibility adapters. Do not create task-specific gates such as `ClirCommitmentGate`.

## Dialogue ownership

```text
finalized STT
 -> deterministic state / PhraseMatrix
 -> bounded Gemma dialogue skill
 -> live supervisor fallback on unresolved/low-confidence cases
 -> application validation/output approval
 -> TTS/TX
```

Models may classify, reason conversationally and propose bounded data. They cannot widen target/task/effect scope, disclose unapproved facts, create a commitment permit, declare external success or complete a workflow.

A live supervisor fallback may continue the same authorized call only on a developer path that explicitly enables that fallback. The app-owned campaign executor runs local-only and fails closed if deterministic/Gemma dialogue cannot progress; ChatGPT/ChatRelay is not a product-runtime dependency. Recurrent fallback cases should later move into deterministic script/PhraseMatrix or bounded Gemma skills.

## Read-only calls

Read-only information gathering does not need commitment authority because it changes no external state. It still requires an accepted dial authorization context, exact target binding, readiness, output approval and bounded cleanup.

## ServicePack role

ServicePacks may contain terminology, known IVR nodes/edges, reviewed actions, parsers and evidence knowledge. They are not dialing or commitment authority.

## Identity

```text
IdentityVault
 -> AuthorizedFactSnapshot
 -> FactDisclosurePolicy
 -> exact task / target / state / generation
 -> optional user approval when policy requires it
 -> resolve plaintext late
```

Plaintext identity must not live in ServicePacks, TaskGraph definitions, normal evidence/logs or model context by default.

## Current acceptance boundary

The generic authority path and full no-call product chain are host/S22-no-call proven. Secure PHONE enrollment/disclosure is `PROVEN_S22`. The app-owned campaign grant/dial/runtime boundary is `HOST_GREEN`; its next gate is physical S22 execution through `Campaign tools`, followed by factual Orange success and independent CLIR-state verification.

Current independent network-state evidence says caller ID is still not restricted, so CLIR enable is not yet accepted.

`HOST_GREEN` is not `PROVEN_S22`. Route discovery, permit consumption, dialogue wording and call termination are not external-success evidence.
