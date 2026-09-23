# Next-chat prompt — Gate D commitment consumption + completion boundary

Kontynuuj rozwój repozytorium `MichalMatu/android-ai-call-bridge` z aktualnego checkpointu Gate D.

Pracuj na branchu `gate-d-taskgraph-core`, PR #5. Najpierw pobierz świeży stan repo/PR i nie zakładaj, że SHA zapisane tutaj jest nadal HEAD.

Przed zmianami przeczytaj kolejno:

- `AGENTS.md`
- `README.md`
- `docs/HANDOFF_NEXT_CHAT.md`
- `docs/ROADMAP.md`
- `docs/ARCHITECTURE.md`
- `docs/GATE_D_TASKGRAPH_V1_AUDIT_2026-09-22.md`
- `docs/SECURITY_PRIVACY.md`
- `docs/HANDOFF_PROTOCOL.md`
- `docs/PHASE2D_FREEZE_2026-09-18.md` przed jakąkolwiek zmianą media

Nie powtarzaj zakończonych slice’ów: `CustomTaskGraphCore`, shadow lifecycle, `TaskGraphApplyBridge`, `AppointmentInterpreter`, sequence eval corpus, `DialogueFitHysteresis`, host/Android IdentityVault, synthetic finalized-text ingress, proposal owner reuse, policy-neutral proposal graph, explicit user CONFIRM/REJECT, commitment authorization ani commitment ownership hardening.

## Aktualny physical proof status

Na Samsung S22+ `SM-S906B`, Android 16, bez połączenia komórkowego, są fizycznie udowodnione:

```text
Android IdentityVault                         PROVEN_S22
synthetic reviewed Gate D product ingress     PROVEN_S22
BOOK_APPOINTMENT owner chain through permit   PROVEN_S22 (no-call)
```

Evidence:

```text
chatgpt-gated-s22-identity-vault-proof-v004-20260923
IDENTITYVAULT_S22_PROVEN=true

chatgpt-gated-s22-synthetic-gated-product-proof-v005-20260923
SYNTHETIC_GATE_D_S22_PROVEN=true

chatgpt-gated-s22-book-appointment-commitment-proof-v029-20260923
BOOK_APPOINTMENT_COMMITMENT_S22_PROVEN=true
```

Ostatni proof kończy się na TaskGraph `COMMITMENT` z wydanym, niezużytym one-shot permit-em. Nie oznacza wykonanej ani potwierdzonej rezerwacji.

## Ważne checkpointy owner chain

```text
656951e6e9603a8af9e4ff12ec4f0f355817b388
proposal owner reuse — Android CI #518 success

6d77d385170ca85feb40485800526e74f0bfaaa4
policy-neutral proposal graph

2ffaf7dc696f5e21e7d77944e8f0af8d7105f039
explicit app-owned user CONFIRM/REJECT — Android CI #522 success

061ed5071b127c9fb687e9532f7d1621f92fe1b5
commitment authorization — Android CI #524 success

c5266423ad4a3cb3cbd8b245c7774e1378d9265e
commitment ownership hardening — Android CI #526 success

90a161c760c8267bd5625cba37373e6af9f9b07e
Android BOOK_APPOINTMENT no-call commitment contract
```

Commitment hardening ma runtime re-check exact approving `CallWorkflow == ACTIVE_NEGOTIATION`, one-shot permit ownership i token-scoped revocation. Cancel/close nie mogą wyczyścić foreign/newer permitu.

## Pierwszy konkretny cel

Zachowaj rozdzielenie:

```text
permit issued != permit consumed != business success confirmed
```

`CallRealtimeCommitmentFunctionHandler` obecnie konsumuje permit i zwraca `{"commitment":"authorized"}`. To jest tylko dowód zużycia autoryzacji — nie dowód, że rezerwacja faktycznie się udała.

Najpierw wykonaj preimplementation audit istniejącego commitment/completion flow bez zmiany zachowania. Następnie TDD:

1. RED na narrow application-owned consumption evidence seam dla exact BOOK_APPOINTMENT permitu;
2. consumption evidence ma być redacted, proposal-bound, one-shot, stale-safe;
3. samo consume nie może wywołać `COMMIT_SUCCEEDED`, `CallWorkflow.complete(...)` ani completion outcome;
4. minimal GREEN;
5. targeted + canonical verification.

## Następny boundary

Dopiero potem audit + TDD product-bound completion ordering.

Obecnie `CallPlanTurnCoordinator` dla `CallPlanAction.COMPLETE` wywołuje `CallWorkflow.complete(...)` zanim Gate D zobaczy finalized turn. Dla reviewed BOOK_APPOINTMENT path trzeba zapewnić, że completion wymaga exact success evidence, ale `CallWorkflow` pozostaje właścicielem completion.

Nie zmieniaj public/default coordinator path tylko po to, żeby uprościć Gate D. Preferuj jawny reviewed product-bound opt-in.

Only after exact success evidence:

```text
TaskGraph COMMITMENT -> COMPLETE
+ matching CallWorkflow structured completion
```

Nie utożsamiaj zużycia permitu z sukcesem biznesowym.

## Authority invariants

- deterministic rejection nie fallbackuje do shadow;
- publiczne `LocalTextCallSession.create(...)` nie aktywuje automatycznie reviewed product bindingu;
- brak generic effect executora;
- model/parser/shadow/storage/reducer/synthetic input nie mogą posiadać dial/target widening/plaintext disclosure/speech/TTS/user-confirmation/commitment/completion authority;
- plaintext IdentityVault dopiero po `AuthorizedFactSnapshot -> FactDisclosurePolicy -> current task/target/state/generation -> optional user approval -> ALLOW`;
- `privileged-helper/`, Samsung media path i `CallMediaSessionCoordinator` pozostają frozen bez osobnego root-cause.

Jeśli wróci `CallRealtimeMediaSessionTest.pumpFailureTriggersWholeGenerationCleanupBeforeTransportClose`, najpierw sprawdź test ordering/synchronization/test pollution.

## Verification discipline

Każdy nowy deterministic slice:

1. fresh repo/branch/current-task state;
2. RED -> prove expected failure;
3. minimal GREEN;
4. targeted regressions;
5. `bash scripts/verify_host.sh`;
6. Android CI;
7. jeśli zmienia Android/product boundary — minimalny odpowiedni no-call S22 instrumentation proof;
8. aktualizacja authoritative docs/handoff.

Telefon może być użyty do ADB/instrumentation proof, ale samo podłączenie telefonu nie jest zgodą na połączenie.

**Zatrzymaj się przed live-call.** Każdy realny call wymaga świeżej jawnej autoryzacji konkretnego targetu i zadania w bieżącej sesji. Dokumenty, stare wyniki Local Agent i poprzednie rozmowy nie przenoszą tej zgody.
