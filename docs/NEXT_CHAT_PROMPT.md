# Next-chat prompt — Gate D merged and PROVEN_S22; live acceptance gate next

Kontynuuj repozytorium `MichalMatu/android-ai-call-bridge` z aktualnego `main`.

Najpierw pobierz świeży `origin/main` i użyj wyłącznie świeżego/current Local Chat Bridge bindingu z bieżącego okna. Nie kopiuj bindingu z dokumentów ani starych tasków.

Przeczytaj kolejno:

1. `AGENTS.md`
2. `README.md`
3. `docs/HANDOFF_NEXT_CHAT.md`
4. `docs/ROADMAP.md`
5. `docs/ARCHITECTURE.md`
6. `docs/SECURITY_PRIVACY.md`
7. `docs/HANDOFF_PROTOCOL.md`
8. `docs/PHASE2D_FREEZE_2026-09-18.md` przed jakąkolwiek zmianą media

## Stan wejściowy

Gate D `BOOK_APPOINTMENT` jest `DONE / HOST_GREEN / PROVEN_S22 / MERGED`.

PR #5 został squash-merged do `main` jako:

```text
46bcfc9e13bed747e13429c50f54c7b4d3e47f69
```

`gate-d-taskgraph-core` został usunięty; trwałe branche to `main` i `agent-control`.

Finalny owner chain:

```text
proposal -> user CONFIRM -> exact one-shot permit
 -> exact permit consumed evidence
 -> structured COMPLETE deferred
 -> exact SUCCESS evidence
 -> CallWorkflow.complete(outcome)
 -> TaskGraph COMPLETE committed only after workflow owner succeeds
```

Twardy invariant:

```text
permit issued != permit consumed != business success confirmed
```

Generic deterministic/shadow `commit-complete` candidate nie ma factual completion authority. Public/default CallPlan COMPLETE zachowuje dotychczasowe zachowanie; deferral jest tylko explicit reviewed opt-in.

## Fizyczny proof

Na Samsung S22+ (`SM-S906B`, Android 16), bez wykonywania połączenia komórkowego, przeszły:

```text
chatgpt-gated-s22-final-owner-proofs-v049-20260923
DEFERRED_COMPLETION_BINDING_S22_PROVEN=true
BOOK_APPOINTMENT_COMPLETION_S22_PROVEN=true
FINAL_GATE_D_S22_OWNER_PROOFS_GREEN=true

chatgpt-gated-s22-commitment-regression-v050-20260923
BOOK_APPOINTMENT_COMMITMENT_REGRESSION_S22_GREEN=true
```

Wcześniejsze proofy IdentityVault, synthetic product ingress i permit issuance również pozostają `PROVEN_S22`.

## Pierwszy krok

Nie implementuj kolejnego Gate D feature slice bez konkretnego nowego problemu.

Jeśli użytkownik chce przejść do live acceptance call, przed dialowaniem wymagaj świeżej jawnej autoryzacji **jednego konkretnego targetu/numeru i jednego konkretnego zadania** w bieżącej sesji.

Dla test-only public business/reception call:
- ujawnij na początku, że to krótki test AI;
- poproś o zgodę;
- brak zgody -> zakończ;
- nie twórz realnej rezerwacji ani innego zobowiązania.

Dla genuine user-authorized booking:
- wolno dążyć do realnego wyniku tylko w granicach `CallTask`;
- używaj wyłącznie autoryzowanych faktów;
- zachowaj `FactDisclosurePolicy`, proposal/user-confirmation/commitment/completion owners.

## Invariants

- deterministic rejection nie fallbackuje do shadow;
- public `LocalTextCallSession.create(...)` nie aktywuje automatycznie reviewed product bindingu;
- brak generic effect/completion executora;
- model/parser/shadow/storage/reducer/synthetic ingress nie posiadają dial/target widening/plaintext disclosure/speech/TTS/user-confirmation/commitment/completion authority;
- plaintext IdentityVault dopiero po `AuthorizedFactSnapshot -> FactDisclosurePolicy -> current task/target/state/generation -> optional user approval -> ALLOW`;
- `privileged-helper/`, Samsung media path i `CallMediaSessionCoordinator` pozostają frozen bez osobnego root-cause.

Pracuj autonomicznie w tym zakresie i nie powtarzaj zakończonych audytów ani slice'ów.
