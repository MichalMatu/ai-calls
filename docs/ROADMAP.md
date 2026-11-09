# AI Calls — bieżąca roadmapa funkcjonalna

## Priorytet od 2026-11-09: rzeczywisty UI i działanie AI Skills

Zbiorczy przebieg sprzętowy na S22 zakończył się scoped PASS: współdzielona procesowa dzierżawa LiteRT GUI/USB **1/1**, natywna decyzja Gemmy w Skill **1/1**, adaptacyjny dialog **1/1**, manualny STOP/restart **1/1**, szyfrowane presety/kontrakty UI **2/2** po naprawie testu zależnego od języka (F-154 VERIFIED), realny USB CLI trzy fabryczne presety × 2 tury **6/6**, prosty recall **3/3**, zapis/STOP **3/3**, odmowy interwencji bez własnego Call **3/3**. Mac full gate PASS. [Dokładny checkpoint](checkpoints/2026-11-09-single-pass-ui-usb-ai-skills-acceptance.md).

**Następny aktywny zakres = SKILL-01/02/03:** prawdziwe operacje edycji/włączenia/wyłączenia/wersjonowania z UI, trwałość po restarcie oraz porównanie rzeczywistych odpowiedzi Gemmy przy aktywnych i nieaktywnych instrukcjach — nie samo uruchamianie fabrycznych presetów. Próbki 3×2 mierzą podstawowe działanie i latencję, nie N=3 na preset ani p95. Zachować prywatne transkrypty i oddzielić ocenę semantyczną od PASS technicznego. Nie powtarzać już zaliczonych bramek USB bez wykrytego problemu.

Pozytywne takeover/resume/exact owned STOP podczas rzeczywistego Call nadal **NOT_RUN**, wymaga niezależnie skonkretyzowanego grantu aplikacyjnego. F-148 długi dialog/8192 i pełny audio pipeline pozostają osobnymi otwartymi pozycjami.

## Backlog historyczny — stan zapisany 2026-11-08

## Aktualny backlog i potwierdzone bramki (historyczny 2026-11-08)

| Priorytet | Status |
| --- | --- |
| Kod USB/ADB: Lab START/SEND/STOP, prywatny transkrypt, status, RAM, konfiguracja i kontrolowane interwencje Call | **MAC_QA_PASS**: 370/370 Python, Gradle/JVM, AndroidTest build, lint/audio |
| Odbiór nowego kanału USB na S22 | **NOT_RUN** (nie instalowano nowej APK) |
| Przejęcie/wznowienie/exact owned STOP podczas rzeczywistego, autoryzowanego Call przez USB | **NOT_RUN** |
| E2B Lab 4096 tokenów | **S22 PASS_DEVICE_TECHNICAL**: 3×13, 39/39 tur; semantyka człowieka UNASSESSED |
| E2B 8192 tokenów | **HOST_PASS / S22_NOT_RUN** |
| Pamięć faktów i sprostowań, rollover; E4B i inne modele | **PLANNED / NOT_RUN** |

**Kolejność:** fizyczny odbiór nowego USB bez Call → pozytywne testy interwencji w oddzielnie autoryzowanej rozmowie → porównanie 8192/4096 i semantyka → rejestr faktów/rollover → E4B. [Checkpoint zbiorczy](checkpoints/2026-11-08-usb-lab-telephony-full-checkpoint.md). Blokady: F-148 OPEN, F-149 VERIFIED, F-150 FIXED_IN_SOURCE/DEVICE_OPEN.

## Dziennik historyczny (wcześniejsze stany PENDING/NOT_RUN nie są aktualnym wynikiem)

**USB Lab F-150:** procesowe wykluczenie współbieżnych natywnych modeli UI/ADB dodane do `main@fdb003d`, **Mac gate PASS**, fizyczny odbiór konkurencji UI/USB `NOT_RUN`. [Specyfikacja](USB_LAB_TELEPHONY_CHANNEL.md).

**USB/ADB Lab & telephony 2026-11-08:** pełny Mac gate **PASS** na nowym kodzie `114d338` (370 Python, Kotlin/JVM, lint, AndroidTest APK), chroniony receiver DUMP i prywatny Lab chat/STOP oraz in-call takeover/resume/owned-stop. **Fizyczny S22 nowego kanału NOT_RUN** (zadanie sprzętowe odrzucone przed wykonaniem), live intervention positive proof OPEN. [Specyfikacja](USB_LAB_TELEPHONY_CHANNEL.md); F-149 lint VERIFIED.

**E2B 4096 2026-11-08:** S22 **N=3×13, 39/39 tur PASS technicznie**, sampled PSS peak 2,348,103 kB, min system MemAvailable 1,378,220 kB, heuristics 11/13,13/13,11/13, semantic human review OPEN. **8192 test-only** at `faf6ee5` full Mac QA PASS but **S22 BLOCKED by PocketHost foreground** before installation/inference. Standard Lab 4096 and Call 2048 unchanged. [Checkpoint](checkpoints/2026-11-08-e2b4096-n3-ram-and-8192-instrumentation.md); [memory sprint plan](LAB_E2B_CONTEXT_MEMORY_PLAN.md).

**LAB-SEMANTIC-02, okno 4096 (2026-11-08):** realna Gemma E2B na S22 **13/13 tur PASS technicznie** na eksperymentalnym Lab `4096`, pozostałe ścieżki nadal `2048`. Heurystyki **11/13**, dwa punkty niepotwierdzone; ręczna semantyka **UNASSESSED**. Powtórki N=2/3 **nie rozpoczęły się przez urządzeniowy guard**. **F-148 OPEN**, RAM/N=3 nadal do odbioru. [Checkpoint](checkpoints/2026-11-08-lab-semantic-02-long-dialogue-f148.md).

**LAB-SEMANTIC-02 (2026-11-08):** trudniejszy 13-turn real S22 **BLOCKED po turze 9** — `LiteRtLmJniException` przy 10. turze, `F-148 OPEN`; nie mylić technicznego Mac PASS wcześniejszego testu z zaakceptowanym długim dialogiem. Częściowy prywatny journaling dodany `main@68e848d`, Mac gate **QA_PENDING**, fizyczne powtórzenie **NOT_RUN**. [Checkpoint](checkpoints/2026-11-08-lab-semantic-02-long-dialogue-f148.md).

**LAB-SEMANTIC-01 (2026-11-08):** realne `GEMMA_4_E2B` na S22, **3/3 niezależne sesje × 7 tur = 21/21**, kontrolowana pamięć, zmiana tematu, korekta i jawna niepewność **PASS 3/3**; średnia ~4,04 s, bez Call i bez publikowania transkryptów. Mac pełny gate PASS na `main@4a3c49b`. **Pełny human review i szeroka wiarygodność faktów nadal OPEN.** [Checkpoint](checkpoints/2026-11-08-lab-semantic-01-gemma-e2b-three-sessions.md).

**LAB-DIALOG-01 (2026-11-08, finalny odbiór):** kod `main@992280b`, pełny gate Mac **PASS** (365 Python, JVM, lint, Android/APK, audio). S22 **PASS 3/3 nowych testów** (`AndroidLabDialogStorageAcceptanceTest` 2/2 i `AndroidLabAdaptiveNativeOfflineTest` 1/1) na zainstalowanej APK; osobny manualny 2-turn/STOP/restart 1/1 zachowuje PASS. Receipt: `aicalls-labdialog01-s22-final-device-acceptance-20261108-1601-v1` DONE/0, Call IDLE, bez utraty danych. Pozostają otwarte semantyczna ocena rzeczywistego transkryptu, pełne klikanie edytora, A/B N=3 i F2 audio. [Dokładny checkpoint](checkpoints/2026-11-08-lab-dialog-01-implementation-checkpoint.md).

**2026-11-08 | Stan:** UX-11 CLOSED; pierwsze fizyczne testy funkcjonalne offline PASS. Bieżące zadanie: [weryfikacja ekran po ekranie](FUNCTIONAL_ACCEPTANCE_MATRIX.md), **bez ponownego sprintu kosmetycznego**. [Checkpoint](checkpoints/2026-11-08-pre-functional-full-checkpoint.md).

> Po UX-11 dodano ograniczony tail UX-12 (Historia + parser screenshotów). Targeted Python QA 14/14 i pełny Mac gate 365/365 PASS; dawny fizyczny zestaw JPEG 17/17 zweryfikowany nowym parserem; nowy wizualny S22 NOT_RUN z powodu zajętego foreground. Funkcjonalne statusy pozostają według macierzy.

## LAB-DIALOG-01 — kod źródłowy wdrożony, odbiór fizyczny otwarty

**2026-11-08:** kod A–D na `main`: ręczny wieloturowy lokalny dialog, edytor własnych przypadków i rewizji, niezależny raport ocen per tura z `UNASSESSED`, adaptacyjne gałęzie oraz A/B. Wspólny `SkillLabRunner`, szyfrowany store bez kopii fabrycznych 4 historii. [Plan i precyzyjny status](LAB_DIALOG_IMPLEMENTATION_PLAN.md).

**Bramki:** końcowy `aicalls-labdialog01-final-source-gate-20261108-v3` **DONE/0, pełny Mac gate PASS**, 365/365 Python, Kotlin/JVM, lint, APK+AndroidTest, audio QA na `e41423e`. S22 `aicalls-labdialog01-s22-device-acceptance-20261108-v1` **DONE/0, 3/3 testy fizyczne PASS** (2 storage/UI + 1 native 2-turn/STOP/restart), Call IDLE. Ocena semantyczna, pełny UI editor click-through i nowa ścieżka STT/TTS **OPEN**; natywny adaptive **PASS na S22** (osobny receipt z 2026-11-08). Regresji starego odrzuconego korpusu nie wykonywać.

**Poza odbiorem:** semantyczne osądzanie halucynacji nie jest automatycznym PASS; ręczny recenzent zapisuje turę/dowód. Osobny takeover w trakcie autonomicznej gałęzi, fizyczne porównania A/B N=3, pełny klikany UI editor i kompletna integracja STT/TTS/PCM pozostają OPEN.

[Checkpoint sprintu](checkpoints/2026-11-08-lab-dialog-01-implementation-checkpoint.md).

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
