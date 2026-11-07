# Security and privacy

**Bieżąca faza — decyzja właściciela 2026-11-05:** modułowe laboratorium porównań STT/model/strategia/instrukcje/TTS i scenariuszy; docelowy flow powstaje później ze sprawdzonych części. [Zakres i architektura laboratorium](TEST_LAB_SCOPE.md). Poniższe zasady zadania, danych, skutków i własności Call dotyczą rzeczywistego wykonania także w aplikacji testowej.


## Przyjęcie zadania bez dodatkowych grantów — 2026-11-05

Polecenie użytkownika przekazane przez lokalny Codex jest przyjmowane przez aplikację jako konkretne zadanie. Jawny START z chronionej `android.permission.DUMP` ścieżki wykonawczej albo z UI przechodzi przez ten sam `CampaignAgentControl`: zapis dokładnego celu, efektu, SIM/konta i dozwolonych pól, następnie wykonanie. Nie ma osobnego przyznawania/odnawiania grantu, potwierdzenia na telefonie ani 24-godzinnego TTL. Podgląd/preflight nie przyjmuje zadania. Automatyczna kontynuacja korzysta z istniejącego zapisu i nie odtwarza cofnięcia; świeże polecenie użytkownika może przyjąć nowe zadanie. Wewnętrzny `CampaignAuthorizationStore` pozostaje jednym zapisem zakresu, a nie dodatkową procedurą zgody.

Zachowane są rzeczywiste granice: uprawnienia Androida/Shizuku, wyłącznie własny Call, STOP/anulowanie, dokładny zakres, `FactDisclosurePolicy`, aplikacyjny release i pojedynczy `CallCommitmentGate`. Model i supervisor nie otrzymują prawa tworzenia zadań. Nagrania są osobną opcją diagnostyczną, przekazywaną w poleceniu lub UI, i nie warunkują rozmowy. Bieżący rejestr: [BLOCKERS](../BLOCKERS.md), F-062/F-065/F-066/F-067.


## Objective

**Product goal clarification (2026-11-02):** autonomy means the local AI can place, conduct, resume and end its authorized ordinary phone calls, including legitimate user-authorized inquiries, reservations and account/service changes. A human initiating each call is a temporary test setup, not an intended permanent policy. Owner-approved engineering work may proceed without redundant confirmations; however neither this file nor that engineering mandate authorizes arbitrary external numbers, disclosures, purchases or mutations outside a concrete task. Apply the existing scoped, revocable grant and platform permissions to real effects. The formerly missing app-owned call controller is now implemented and S22 live RAW dialogue has been observed; this is not proof of carrier-side service modification or authorization to widen disclosure.

The agent may dial, speak, disclose data or change external state only inside authority explicitly granted by the user and enforced by application-owned policy. Failure must narrow capability or return control to the human; it must never broaden authority.

`docs/AUTONOMOUS_OPERATION_MODE.md` defines the operational target: high autonomy without weakening application-owned authority boundaries.

## Frozen privilege/media boundary

Samsung call-audio access remains inside the privileged helper / Shizuku UserService. Continuous PCM crosses through transferred PFDs; Binder/AIDL is control only.

This path is `PROVEN_S22 / FROZEN`. Read `docs/PHASE2D_FREEZE_2026-09-18.md` before changing it.

## Authority owners

- `CallTask` — exact task, constraints, preferences and authorized facts;
- `CallResolvedTarget` — exact target;
- application-owned authorization context / durable campaign grant — exact scope in which repeated attempts are allowed;
- `CallExternalEffectValidator` — exact effect/task/target binding;
- `CallCommitmentGate` — single one-shot commitment permit store;
- `CallExternalEffectCompletionTracker` — exact post-consumption success evidence;
- workflow/TaskGraph owner — terminal task state;
- `FactDisclosurePolicy` — plaintext personal-data disclosure;
- application output approval — final text release before TTS/TX.

TaskGraph definitions, PhraseMatrix, CallPlan, Gemma, supervisor/ChatRelay, ServicePacks, parsers, storage and diagnostics are not independent authority owners.

## External-effect rule

```text
user task authorization
 != effect validation
 != permit issuance
 != permit consumption
 != external success
 != workflow completion
```

A model may help extract or propose effect data. It cannot create authority, widen the effect, declare success or complete the workflow.

Do not create per-service commitment gates such as `ClirCommitmentGate`.

## Durable campaign grant

The autonomous product mode implements an explicit durable, scoped, revocable campaign grant owned by application policy. The grant is created/revoked locally, persisted privately, bound to the current default voice subscription, and checked again immediately before an app-owned dial. Task acceptance comes directly from the owner START; no separate grant UI is required.

A grant must bind at least:

- allowed target(s);
- allowed task/effect set;
- account/SIM scope when relevant;
- issue/revocation state;
- trwały zakres zadania i jawne anulowanie; bez sztucznego TTL lub limitu prób;
- disclosure scope.

When a valid grant already covers an unchanged retry, the product should not ask for another redundant confirmation. A material widening of target, task, effect, account or disclosure scope remains fail-closed.

Chat prose, markdown files, ServicePacks, model output, connected hardware and diagnostic artifacts are not substitutes for this authority object.

External platform/tool confirmation requirements remain outside repository control and must not be bypassed. The product runtime therefore does not depend on ChatGPT/Local Agent/ADB for authority or dialing; removing those developer transports from the execution path is architectural decoupling, not a bypass of Android permissions or application policy.

## Confirmation rule

Do not require a redundant second confirmation when the active accepted authorization context already exactly covers the same concrete effect and no material term changed.

If the counterparty introduces new material terms, application policy decides whether new confirmation is required.

## Identity and disclosure

A value existing in IdentityVault is not permission to disclose it.

**Status after live S22 (2026-11-04):** Gemma RAW has dynamic, grant-scoped CLIR task instructions and prompt-only skills, but it has **no implemented authorized `FactRequest` adapter** to IdentityVault. That absence explains the owner's observation that the assistant could continue dialogue but lacked information requested by the call recipient. Future retrieval must be typed and application-controlled: exact task, destination, active revocable grant and disclosure field must all match under `FactDisclosurePolicy` before plaintext resolution, and output must be approved before TTS/TX. Never insert private values into a global system prompt or unrestricted model tool arguments. See F-048 and [live checkpoint](checkpoints/2026-11-04-raw-live-dialogue-facts-turn-taking-freeze.md).

```text
IdentityVault
 -> AuthorizedFactSnapshot
 -> FactDisclosurePolicy
 -> exact task / target / state / generation
 -> optional user approval
 -> ALLOW
 -> resolve plaintext late
```

Plaintext identity values stay out of TaskGraph definitions, ServicePacks, ordinary logs/evidence, Local Agent JSON, Git history and model/supervisor context by default.

## Dialogue/model boundary

```text
STT
 -> deterministic state / PhraseMatrix
 -> bounded Gemma dialogue skill
 -> optional supervisor fallback only on an explicitly enabled developer path
 -> application validation/output approval
 -> TTS/TX
```

Gemma and supervisor cannot independently dial, widen target/task/effect scope, disclose unauthorized facts, bypass output approval, issue/consume commitment authority, mark external success or complete the task.

Supervisor fallback is permitted only when an explicit developer path enables it. The app-owned campaign executor disables ChatRelay/supervisor fallback and fails closed when local dialogue cannot progress. Recurrent fallback cases should be moved into deterministic script/PhraseMatrix or bounded Gemma skills in later iterations.

## Live-call policy


**Historia 2026-11-01:** brak identyfikacji własnego Call blokował wówczas autonomiczny start. Własny Call i selektywny teardown są teraz zaimplementowane i obserwowane na S22; stary hold nie jest aktywną przeszkodą. Niezależna weryfikacja skutku CLIR pozostaje wymagana przed stwierdzeniem sukcesu.

A call must fail closed before dialing unless the exact task/target is covered by an accepted application authorization context and required readiness gates pass.

Rules:

- one active cellular call at a time;
- exact target only; no model/tool target widening;
- readiness immediately before dial;
- phone confirmed `IDLE` before dial;
- bounded duration/retries;
- do not touch emergency, urgent-care, crisis, premium-rate or unrelated critical-service targets;
- do not terminate unrelated pre-existing calls;
- do not infer authority from a ServicePack, allowlist, connected phone, prior call, handoff text, model output or `.agent/results`;
- if a physical call is made, cleanup must return the owned call to `IDLE`.

Product autonomy means the Android app performs authorized campaign execution directly. Local Agent/ADB/supervisor remain development and diagnostic tooling and are not product authority or a prerequisite for dialing. This does not weaken application authority or platform controls.

## Orange CLIR physical campaign

Historical G5a/G5b discovery established the initial route behavior. The active work is now physical enable/disable acceptance.

Requirements:

- exact Orange CLIR campaign target/task/effect only;
- pre-dial readiness + `IDLE`;
- script/PhraseMatrix first, Gemma second; the app-owned campaign path is local-only and fails closed when unresolved;
- late-bound identity disclosure only through application policy;
- exactly one shared `CallCommitmentGate` permit immediately before each real account-changing commitment;
- separate factual external-success evidence;
- independent CLIR network-state interrogation when technically available;
- cleanup to `IDLE`;
- no unrelated service/account widening.

Current physical evidence after the latest iteration says caller ID is still not restricted, so the enable task is not complete.

## Physical-first acceptance

During the active CLIR campaign, physical calls and independent state interrogation are the acceptance evidence. Synthetic/unit suites must not be treated as proof of live success or used as a substitute for the physical iteration loop.

## Data minimization

Do not retain by default raw PCM, recordings, full transcripts, credentials, plaintext identity values or unrelated counterparty data. Prefer typed IDs, redacted evidence, sizes/timings and bounded correlation IDs. Durable external-success reports must store typed/redacted evidence, never the matched transcript text.

Transient supervisor relay branches may contain raw turn data only for the active session and must be deleted during cleanup. Sanitized evidence must be preserved before cleanup when needed for debugging.

## TAKE OVER / failure ordering

```text
stop accepting/releasing unsafe AI output
 -> supervisor may continue the same authorized dialogue when takeover is the intended fallback
 -> abort telephony media generation when the session must terminate
 -> stop STT/TTS/audio workers
 -> invalidate controller/model/supervisor generation
 -> best-effort cancel remaining work
 -> hang up only the call owned by this session when appropriate
```

## Evidence rule

`HOST_GREEN` is not `PROVEN_S22`. A connected phone is not authorization. Permit consumption is not external success. The CLIR task is not complete until exact factual external-success evidence has been accepted by application-owned completion logic and, when available, independent network-state verification confirms the expected state.

## Screen capture policy (owner decision 2026-11-07)

**Do not block device-owner screenshots** using FLAG_SECURE or similar flags on any AI Calls screen, including phone enrollment, conversations, skills and private vault UI. This is an explicit product requirement, logged as F-104 in BLOCKERS.md. Existing private-file storage, consent, disclosure restrictions and authorization boundaries remain enforced. The application must not automatically export screenshots, phone numbers, identity values, transcripts, audio or private data to external logs or services. Users should treat screenshots with personal data as sensitive when sharing them.
