# Next-chat prompt — Gate D done; Orange live acceptance paused for dialogue-architecture review

Kontynuuj repozytorium `MichalMatu/android-ai-call-bridge` z aktualnego `main`.

Najpierw pobierz świeży `origin/main` i użyj wyłącznie świeżego/current Local Chat Bridge bindingu z bieżącego okna. Nie kopiuj bindingu z dokumentów ani starych tasków.

Przeczytaj kolejno:

1. `AGENTS.md`
2. `README.md`
3. `docs/HANDOFF_NEXT_CHAT.md`
4. `docs/ORANGE_LIVE_ACCEPTANCE_2026-09-23.md`
5. `docs/ROADMAP.md`
6. `docs/ARCHITECTURE.md`
7. `docs/SECURITY_PRIVACY.md`
8. `docs/HANDOFF_PROTOCOL.md`
9. `docs/PHASE2D_FREEZE_2026-09-18.md` przed jakąkolwiek zmianą media

## Stan wejściowy

Gate D `BOOK_APPOINTMENT` jest `DONE / HOST_GREEN / PROVEN_S22 / MERGED`.

Finalny owner chain i authority invariants są zakończone i nie należy ich ponownie implementować bez konkretnego nowego błędu.

Fizyczny tor Samsung S22+ (`SM-S906B`, Android 16) ma dowody dla call control, downlink capture, STT i reviewed TTS injection.

Orange live acceptance osiągnął następujący checkpoint:

```text
MEDIA / STT / TTS PATH: PROVEN_S22
REVIEWED SINGLE-TURN ORANGE RESPONSE: PROVEN_S22
ORANGE IVR AUTOMATION: EXPERIMENTAL / BRITTLE / PAUSED
CLIR ACTIVATION: NOT COMPLETED
```

W realnym połączeniu system poprawnie wypowiedział reviewed prośbę o włączenie CLIR, a Orange odpowiedział pytaniem, czy sprawa dotyczy numeru, z którego wykonywane jest połączenie. Na tym eksperyment został celowo zatrzymany.

## Pierwszy krok

**Nie wykonuj kolejnego live calla i nie dodawaj kolejnych exact-phrase Orange actions na starcie nowego okna.**

Najpierw przedyskutuj z użytkownikiem dalszą architekturę dialogu.

Preferowany kierunek do oceny:

```text
observed utterance
 -> semantic interpretation into a small typed intent/event set
 -> app-owned dialogue state + expected-slot validation
 -> bounded candidate response
 -> authority/policy re-check
 -> reviewed/generated speech
 -> observe next turn
```

Tematy do decyzji przed implementacją:

- generic dialogue reducer vs dalsze operator-specific skrypty;
- semantic intent classifier + confidence/ambiguity handling;
- deterministic-first + bounded model candidate/shadow;
- operator adapters tylko dla stabilnych faktów/DTMF/USSD/API;
- preflight readiness dla `RECORD_AUDIO`, Shizuku, ADB/device i media;
- replayable redacted conversation fixtures;
- human takeover dla identyfikacji, sekretów i niespodziewanych turnów.

## Czego nie robić

- nie rozbudowuj Orange o kolejne exact phrase aliases jako główną strategię;
- nie używaj transient Git relay jako product runtime transport;
- nie dawaj modelowi/parserowi/shadow/storage/reducerowi dial/target widening/plaintext disclosure/user-confirmation/commitment/completion authority;
- nie otwieraj Samsung media path ani `privileged-helper/` bez konkretnego root cause;
- nie traktuj istniejących Orange fixture jako stabilnego API infolinii.

## Jeśli później wróci live call

Każde realne połączenie nadal wymaga świeżej jawnej autoryzacji konkretnego targetu/numeru i konkretnego zadania w bieżącej sesji.

Pracuj autonomicznie dopiero po ustaleniu nowego kierunku architektonicznego.
