# Next-chat prompt — generic autonomous phone task authority

Kontynuuj `MichalMatu/android-ai-call-bridge` z aktualnego `main`.

Najpierw pobierz świeży `origin/main`, świeży `agent-control:.agent/status/daemon.json` i użyj wyłącznie aktualnego Local Agent binding. Przeczytaj w tej kolejności:

1. `AGENTS.md`
2. `README.md`
3. `docs/HANDOFF_NEXT_CHAT.md`
4. `docs/ROADMAP.md`
5. `docs/ARCHITECTURE.md`
6. `docs/GENERIC_PHONE_TASK_AUTHORITY.md`
7. `docs/SECURITY_PRIVACY.md`
8. `docs/HANDOFF_PROTOCOL.md`
9. `docs/PHASE2D_FREEZE_2026-09-18.md` tylko przed zmianami media.

Produkt ma być **generycznym autonomous phone task engine**, a nie Orange/CLIR botem ani appointment-only botem.

Pierwszy task: wykonaj wyłącznie ROADMAP **G1 — preimplementation audit generic commitment subject**. Zmapuj każde miejsce, gdzie appointment-shaped `CallProposal` jest traktowane jako uniwersalny commitment subject (`CallCommitmentGate`, realtime commitment handler, Gate D product integration, confirmation/consumption/completion evidence, `LocalTextCallSession`, testy). Nie zmieniaj jeszcze zachowania.

Następnie zaproponuj minimalną migrację do generycznego typed external-effect commitment subject, zachowując wszystkie istniejące `BOOK_APPOINTMENT` invariants i bez tworzenia drugiego authority store. Nie twórz `ClirCommitmentGate`.

Docelowy flow:

```text
CallTask + exact target + constraints + authorized facts
 -> dialogue: PhraseMatrix / deterministic state / Gemma / supervisor fallback
 -> typed external-effect candidate
 -> application validation
 -> user-decision policy when needed
 -> exact one-shot permit
 -> reviewed speech/execution
 -> consumption evidence
 -> external success evidence
 -> factual completion
```

CLIR ma być pierwszym acceptance case'em `SET_SERVICE(CLIR=true)`, a kolejny szeroki case to przychodnia z negocjowanym terminem/ceną. Orange exact aliases nie są architekturą.

Nie ruszaj frozen Samsung media/privileged-helper ani Gemma download/storage bez nowego konkretnego root cause. Model jest już `PROVEN_S22`.

Nie wykonuj żadnego live calla bez osobnej świeżej autoryzacji w nowym czacie dla konkretnego numeru/targetu i konkretnego zadania. Poprzednia zgoda na Orange/CLIR nie przechodzi przez handoff.
