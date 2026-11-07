# AI Calls — Skills i Laboratorium Gemmy (2026-11-07)

**Implementacja i odbiór:** [Finalny Sprint 6](checkpoints/2026-11-07-ux-sprint6-final-handoff.md) **SOURCE PASS / S22 PASS**, rzeczywiste lokalne 2 tury i STOP **PASS** po Android hotfixie `1b359146`, 46 prywatnych screenshotów. To test działania modelu i STOP, nie automatyczna ocena jakości odpowiedzi. [Sprint 5](checkpoints/2026-11-07-ux-sprint5-live-lab-s22-acceptance.md) ma osobne, zakończone dowody 2 tur i STOP.

## Presety i instrukcje

- Fabryczne: Naturalna sekretarka, Formalna sekretarka, Zbieranie wiadomości. Skills: `MessageCapture`, `Clarification`, `NaturalClosure`, `ConversationStyle`.
- Edycja fabrycznego presetu wymaga własnej kopii. Każda zmiana tworzy wersję, którą można wybrać lub przywrócić. Włączenie skilla i jego wersja to instrukcja, **nie grant skutków**.
- Aktywacja tego samego presetu jest wyłączona; etykieta wskazuje, że już jest aktywny. Wybór w Laboratorium nie aktywuje presetu produkcyjnego.
- Konfiguracja pozostaje w istniejącym szyfrowanym `ConversationPreparation` no-backup; nowa rozmowa dostaje zamrożony snapshot. Edycja nie zmienia w locie działającej inferencji.

## Laboratorium: dokładne znaczenie danych

- Tryb **szybki**: pierwsza syntetyczna historia, **2 tury**. Tryb **pełny**: wszystkie cztery historie (łącznie 12 tur). Każda historia zaczyna od własnego kontekstu, tury jednej historii dzielą go.
- Wybrany lokalny provider/model otrzymuje przygotowaną instrukcję i tekst symulowanego rozmówcy. UI pokazuje turę w toku, rozkodowany tekst odpowiedzi, propozycję END_CALL, oczekiwanie i latencję. Przy aktywnym auto-follow ma pokazywać ostatni dymek po zmianie wysokości ekranu.
- Test offline **nie** wywołuje Telecom, STT/TTS, grantu, executora, rozłączenia ani innych narzędzi. Model może *zaproponować* END_CALL; Lab tylko to ocenia.
- **Wynik 2/2 czy 12/12 oznacza wyłącznie zgodność decyzji END_CALL**, nie jakość informacji, prawdziwość, naturalność czy brak zbędnych pytań. Wykrywacz `?` i wybranych polskich zwrotów żądających danych jest **heurystyczną flagą do ręcznej weryfikacji**, nie oceną semantyczną. Ocena naturalności 1–5 jest ręczna.
- STOP ma zatrzymać model/wątek i pozostawić widoczny status ukończonych tur; stare callbacki i nowy run nie mogą modyfikować poprzedniego stanu. Rzeczywisty test dwóch tur i STOP po hotfixie jest **PASS** na S22; syntetyczne testy auto-follow, stanu STOP i skorowania również PASS. Nie wykonywano połączeń ani skutków END_CALL.

## Dowody

| Etap | Model / zakres | Wynik |
|---|---|---|
| 2026-11-06 presety | GEMMA_4_E2B; 4 historie / 12 tur na preset | Native off-call, historyczny PASS |
| 2026-11-07 Sprint 5 | wybrany lokalny LiteRT, 2 tury, STOP | Fizyczny S22 PASS, 4 zdjęcia w zestawie 43 |
| 2026-11-07 Sprint 6 | 2 tury, widoczna końcowa karta i STOP | **Source/host + fizyczny S22 PASS**, hotfix 1b359146 |

Historyczna matryca 2026-11-06 (jest wyłącznie porównaniem decyzji END_CALL):

| Preset | END_CALL | Za wcześnie | Brak końca | Pytania — dawna heurystyka | Śr. generacja |
|---|---:|---:|---:|---:|---:|
| natural | 12/12 | 0 | 0 | 3 | 7029 ms |
| formal | 12/12 | 0 | 0 | 3 | 11012 ms |
| message | 12/12 | 0 | 0 | 3 | 12361 ms |

[Pełny dowód matrycy](checkpoints/2026-11-06-gemma-skills-three-presets-s22-offcall.md). Bez nowego pomiaru nie porównywać tych czasów z bieżącym modelem.

## Dalszy rozwój

`SkillLabScoring` pozostaje czystą logiką. Planowany `SkillLabRunner` ma przejąć model/generation/cancellation/timeout, a Activity wyłącznie rysować immutable `SkillLabUiState`. Pierwszy krok dopiero po osobnej analizie zależności, z testami spóźnionych callbacków, STOP, restartu i 120-sekundowego błędu. [Plan](SAFE_MODULARIZATION_PLAN.md). Nie mieszać ze zmianami telefonii ani recorder F-080.
