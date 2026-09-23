# Next-chat prompt — final S22 no-call proof and Gate D merge

Kontynuuj repozytorium `MichalMatu/android-ai-call-bridge` z aktualnego checkpointu Gate D.

Pracuj na świeżym stanie repo/PR #5; nie ufaj historycznemu SHA bez ponownego fetch. Użyj wyłącznie świeżego Local Chat Bridge bindingu dostarczonego w nowym oknie — nie kopiuj bindingu z poprzednich tasków ani dokumentów.

Najpierw przeczytaj kolejno:

1. `AGENTS.md`
2. `README.md`
3. `docs/HANDOFF_NEXT_CHAT.md`
4. `docs/ROADMAP.md`
5. `docs/ARCHITECTURE.md`
6. `docs/GATE_D_TASKGRAPH_V1_AUDIT_2026-09-22.md`
7. `docs/SECURITY_PRIVACY.md`
8. `docs/HANDOFF_PROTOCOL.md`
9. `docs/PHASE2D_FREEZE_2026-09-18.md` przed jakąkolwiek zmianą media

## Stan wejściowy

Wszystko, co dało się domknąć bez telefonu, jest zakończone.

Finalny no-phone code checkpoint przed close-out docs:

```text
cefe6492c7e714a8124e08cb1f42a68554955832
```

`BOOK_APPOINTMENT` ma host-green pełny owner chain:

```text
proposal -> user CONFIRM -> permit issued -> exact permit consumed
 -> structured COMPLETE deferred
 -> exact SUCCESS evidence
 -> CallWorkflow.complete(outcome)
 -> TaskGraph COMPLETE committed only after workflow owner succeeds
```

`permit issued != permit consumed != business success confirmed` pozostaje twardym invariantem. Generic deterministic/shadow `commit-complete` candidate nie ma factual completion authority. Default/public CallPlan COMPLETE zachowuje stare zachowanie; deferral jest tylko explicit reviewed opt-in.

Final evidence:

```text
chatgpt-gated-book-appointment-completion-red-v041-20260923
BOOK_APPOINTMENT_COMPLETION_RED=true

chatgpt-gated-book-appointment-completion-green-v042-20260923
BOOK_APPOINTMENT_COMPLETION_GREEN=true

chatgpt-gated-book-appointment-completion-canonical-v043-20260923
BOOK_APPOINTMENT_COMPLETION_CANONICAL_GREEN=true

chatgpt-gated-android-completion-contract-build-v044-20260923
ANDROID_COMPLETION_CONTRACT_PACKAGED=true

chatgpt-gated-final-canonical-v045-20260923
FINAL_GATE_D_NO_PHONE_CANONICAL_GREEN=true
```

Android CI #546 dla `cefe6492...` zakończył się `success`.

## Pierwszy i jedyny aktywny gate

Nie zaczynaj kolejnego hostowego feature slice. Najpierw fizyczny **no-call** S22 proof.

Poprzednia próba nie wykazała product failure: `chatgpt-gated-s22-deferred-completion-proof-v039-20260923` zatrzymał się przed Gradle, bo `RFCT70L7E8J` nie był widoczny; `chatgpt-gated-adb-inventory-v040-20260923` pokazał pustą listę ADB.

Gdy telefon jest dostępny:

```bash
adb devices -l

ANDROID_SERIAL=<S22_SERIAL> gradle :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=pl.michalmatu.aicallbridge.localcall.AndroidGateDDeferredCompletionBindingContractTest

ANDROID_SERIAL=<S22_SERIAL> gradle :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=pl.michalmatu.aicallbridge.localcall.AndroidGateDBookAppointmentCompletionContractTest
```

Opcjonalnie dołóż regression:

```text
AndroidGateDBookAppointmentCommitmentContractTest
```

Wymagaj terminalnego PASS; samo queue/ACK/connected device nie jest dowodem.

Jeśli oba nowe kontrakty przejdą:

1. oznacz nowe boundaries jako `PROVEN_S22` w README/ROADMAP/ARCHITECTURE/HANDOFF/PR;
2. uruchom/re-check canonical CI, jeśli repo się zmieniło;
3. re-check PR #5; jeśli nadal clean/mergeable, merge `gate-d-taskgraph-core` do `main`;
4. usuń `gate-d-taskgraph-core` po merge;
5. zostaw `agent-control` jako branch tooling/evidence, dopóki Local Chat Bridge go używa.

Remote branch cleanup z poprzedniej sesji jest już zakończony: poprawny zestaw to tylko `agent-control`, `gate-d-taskgraph-core`, `main`.

## Nie powtarzaj

Nie powtarzaj auditów/slice’ów: `CustomTaskGraphCore`, `TaskGraphApplyBridge`, AppointmentInterpreter, DialogueFit/hysteresis, shadow lifecycle, SupervisorProposalValidator, host/Android IdentityVault, synthetic ingress, proposal owner reuse, policy-neutral proposal graph, user CONFIRM/REJECT, commitment authorization/hardening, consumption evidence, deferred completion ani factual completion owner ordering.

`privileged-helper/`, Samsung media path i `CallMediaSessionCoordinator` pozostają frozen bez osobnego root-cause.

Plaintext identity tylko przez:

```text
AuthorizedFactSnapshot -> FactDisclosurePolicy -> current task/target/state/generation
 -> optional user approval -> ALLOW -> late plaintext resolution
```

## Live-call stop line

**Nie wykonuj realnego połączenia na podstawie tego promptu.** Nowe okno nie dziedziczy żadnej zgody na telefon. Każdy live call wymaga świeżej, jawnej autoryzacji konkretnego targetu i zadania w bieżącej sesji.

Po fizycznym no-call proofie i merge możesz przygotować następny etap, ale przed dial zatrzymaj się po świeżą autoryzację użytkownika.
