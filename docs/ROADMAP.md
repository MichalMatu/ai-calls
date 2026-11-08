# AI Calls — bieżąca roadmapa funkcjonalna

**2026-11-08 | Stan:** UX-11 CLOSED; pierwsze fizyczne testy funkcjonalne offline PASS. Bieżące zadanie: [weryfikacja ekran po ekranie](FUNCTIONAL_ACCEPTANCE_MATRIX.md), **bez ponownego sprintu kosmetycznego**. [Checkpoint](checkpoints/2026-11-08-pre-functional-full-checkpoint.md).

> Po UX-11 dodano ograniczony tail UX-12 (Historia + parser screenshotów). Targeted Python QA 14/14 i pełny Mac gate 365/365 PASS; dawny fizyczny zestaw JPEG 17/17 zweryfikowany nowym parserem; nowy wizualny S22 NOT_RUN z powodu zajętego foreground. Funkcjonalne statusy pozostają według macierzy.

## LAB-DIALOG-01 — wdrożenie zatwierdzone, kolejność A → E

**Zatwierdzono 2026-11-08.** Szczegółowy, testowalny plan: [LAB_DIALOG_IMPLEMENTATION_PLAN](LAB_DIALOG_IMPLEMENTATION_PLAN.md). Kolejność: **A manualny dialog w jednej sesji → B edytowalne przypadki → C niezależna ocena tur → D adaptacyjny rozmówca → E porównania i audio**. Pierwsza praca kodowa: rozwój kontraktu obecnego `SkillLabRunner` dla manualnych wejść, bez dublowania runnera. Stare cztery historie pozostają usunięte.

## Priorytet następnego sprintu — LAB-DIALOG-01: własne i adaptacyjne testy rozmowy

**DO ZROBIENIA, niezaimplementowane (decyzja właściciela 2026-11-08).** Wszystkie 4 wcześniejsze sztywne historie zostały usunięte z aktywnego `SkillLabCorpus`, a nie zachowane jako zestaw regresyjny. Teraz `Lab` nie ma aktywnych scenariuszy i świadomie nie pozwala uruchomić pustego testu; nie wolno zaliczać 0/0 jako PASS.

- **Edytor lokalny:** tworzenie/kopiowanie/edycja/usuwanie testów, definicja celu, stanu rozmówcy, wypowiedzi, oczekiwanych faktów i zachowań; wybór wersji testu oraz zapis/ponowne otwarcie.
- **Tryb manualnego rozmówcy:** użytkownik wpisuje każdą następną wypowiedź, a model odpowiada w tej samej sesji; można zmienić temat, wrócić, sprostować lub dopytać.
- **Tryb adaptacyjny:** rozmówca testowy z ograniczonymi regułami/gałęziami reaguje na prawdziwą odpowiedź AI zamiast odczytywać stały skrypt; późniejszy symulator LLM tylko z niezależnym sprawdzaniem wyniku.
- **Ocena zachowania:** utrzymywanie tematu, kontekst i pamięć, poprawki, faktografia, niepotrzebne pytania, prawdziwość obietnic, właściwe dopytanie, naturalność i niezależnie decyzja END_CALL. Oceny jako dowody konkretnych tur, nie samo 2/2/12/12.
- **Integracja:** wybór faktycznego lokalnego modelu, wersji Skills i docelowego scenariusza (np. warsztat lub inna przygotowana rola); porównanie A/B, STOP/recovery, brak wykonywania narzędzi i Call.
- **QA:** osobne techniczne testy runnera używają niewidocznych dla użytkownika neutralnych danych wejściowych, nie odtwarzają usuniętych historii. Przyjąć dopiero po Kotlin/JVM, Mac gate i rzeczywistej próbie na S22 bez telefonu.

[Checkpoint decyzji](checkpoints/2026-11-08-lab-four-scripted-stories-removed.md).

## Etap F1 — odbiór interfejsu i lokalnych funkcji na S22
**Kolejność i definicja PASS:** według macierzy `NAV → HOME/CAMP (bez Call) → HIST → Agent/Skills/Scenarios/Prep/Vault/Tools → Lab → Models/Audio → Settings/Incoming/USB`.

- Każda funkcja: faktyczna akcja → trwała lub ulotna zmiana stanu → wynik/recovery/STOP; także brak zasobu, odmowa, powrót/restart, izolowana fixture.
- Trzy różne poziomy dowodów: wygląd/syntetyka, działanie komponentu offline, działanie faktycznej rozmowy. Nie nadpisywać `NOT_RUN` statusem wynikającym wyłącznie z jednego screenshotu.
- Ponownie sprawdzić szczególnie **Vault standard/custom CRUD, data scope, Skills w modelu, brak wycieku, historia/audio, STOP i wybór silników** — te funkcje nie mają jeszcze pełnej akceptacji.

## Etap F2 — kontrolowana integracja audio i agentów
- STT: korpus polskiego PCM / cisza / zakłócenia / mikrofon, WER/CER, latencja; Parakeet i inne dostępne silniki.
- Model: profile, rzeczywista inferencja, Direct / skrypt, Skills, narzędzia w dry-run, stop, timeout, recovery; żadnych fikcyjnych skutków.
- TTS: głos, wymowa i obciążenie, PCM, cancel oraz integracja STT→model/Skills→TTS.
- Używać wybranych wcześniejszych pozytywnych dowodów jako **baseline**, nie jako substytutu pełnej nowej macierzy.

## Etap F3 — autoryzowany Call i prawdziwy skutek
- Przed telefonowaniem sprawdzić świeży stan `IDLE`, własną rolę, SIM, uprawnienia i dokładny cel/zakres autoryzacji; nie uruchamiać rozmów z szablonu DRAFT_ONLY.
- Zmierzyć rzeczywiste RX/TX, synchronizację i słyszalność STT/TTS, IVR, end, STOP, przejęcie i zapisy.
- Wynik zadania potwierdzić niezależnym dowodem, nie samą deklaracją modelu.
- Aktywne defekty: **F-080** (pełne audio i player), **F-122** (aktywny recorder), **F-123** (Call/attemptId/wynik), **F-124** (poprawne archiwum). Szczegóły i historia tylko w [BLOCKERS](../BLOCKERS.md).

## Bramka zamknięcia
Cały zakres w macierzy ma `PASS` na adekwatnym poziomie lub jawne `BLOCKED/NOT_RUN` z przyczyną; dla napraw: red/green, testy zakresowe oraz Mac quality gate i S22, jeśli dotyczy. Aktualizacja jednego rejestru `BLOCKERS.md`, macierzy, checkpointu i handoffu. Bez ukrywania braków, bez fałszywego REAL_CALL_PASS.

**Zamknięte:** UX-01–11; nie powtarzać ich pełnych bramek bez zmian kodu lub nowego symptomu. Archiwalne roadmapy i ich treść pozostają w historii Git/checkpointach.
