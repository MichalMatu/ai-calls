# AI Calls — LAB-SEMANTIC-02 — długi dialog, F-148 (2026-11-08)

## Cel i granice

Rozszerzenie LAB-SEMANTIC-01 (poprzednio **3×7 tur PASS** na prawdziwej Gemma 4 E2B) o trudniejszą próbę: 13 tur w jednej natywnej sesji tekstowej, dwie fikcyjne kartoteki z losowymi kodami, zmianę tematu, dwie rewizje wartości, presję na przywołanie niepodanego numeru oraz pytania o nieistniejące zewnętrzne działania. Nie przywrócono żadnej z czterech usuniętych fabrycznych historii. Brak Telecom, Call, e-maila i zewnętrznych skutków.

## Mac — źródło i QA

- `main@8768f72564c94ac0a0b2957e44480c17d8437acf`: `AndroidLabAdversarialNativeOfflineTest` z 13 turami i prywatnym arkuszem zdań w `noBackupFilesDir`; heurystyki diagnostyczne nie udają ręcznej oceny.
- `aicalls-semantic2-mac-full-gate-20261108-v1`: **DONE/0**, `scripts/verify_host.sh` **PASS** (365 testów Python, Gradle/JVM, lint, dwie APK, audio audit, clean Mac).
- Po wykryciu utraty materiału przy błędzie dodano `main@68e848d7deaea5e455dc94f104fb69ff8e4c5d1a`: utrwalanie prywatnego transkryptu po każdej turze, prywatna kategoria błędu i arkusz `UNASSESSED`; **pełny Mac gate `aicalls-semantic2-journal-mac-gate-20261108-v1` jest QA_PENDING** — brak opublikowanego wyniku w momencie checkpointu. Nie przedstawiać tej poprawki jako przetestowanej.

## S22 — realna próba i diagnoza

- `aicalls-semantic2-preflight-20261108-v1` DONE/0: S22 `SM-S906B`, ADB, bezpieczny Samsung Launcher, `mCallState=0`, brak aktywnej kampanii, clean checkout.
- `aicalls-semantic2-s22-adversarial-n3-20261108-v1`: **FAILED/88** w pierwszej sesji. AndroidTest APK zainstalowany `adb install -r -t` bez kasowania danych. 9 kolejnych rzeczywistych tur przeszło; **tura 10 zwróciła `SkillLabPhase.FAILED`**, test przerwał wykonanie, N=2/N=3 **NOT_RUN**.
- `aicalls-semantic2-s22-native-failure-diagnostics-20261108-v1` DONE/0: prywatne odpowiedzi nie były publikowane. Telemetria wyłącznie statusowa: `conversation_turn=10`, `conversation_reused=true`, `generate_ms=0`, `output_chars=0`, `status=error_LiteRtLmJniException`. Tury 1–9 reużywały jeden natywny kontekst.
- `LiteRtGemma4Runtime.MAX_CONTEXT_TOKENS=2_048` — wyczerpanie kontekstu jest prawdopodobną, ale **niepotwierdzoną** przyczyną; nie należy globalnie podnosić limitu bez eksperymentalnego dowodu na S22 i oceny wpływu na ścieżki telefoniczne.
- Test v1 utracił odpowiedzi 1–9 na poziomie prywatnego pliku, ponieważ zapis końcowego raportu nastąpił dopiero po wykonaniu 13 tur. Oddzielna luka testu została poprawiona **w źródle** w `68e848d`; jego device reacceptance **NOT_RUN**.

## Decyzja i dalsza bramka

**F-148 = OPEN.** Nie ma podstaw do zaliczenia stabilności 13-turowego dialogu ani ręcznej oceny zdań. Następny zamykalny krok: poczekać na dokładny Mac gate zmiany `68e848d`, a po jego PASS wykonać **jedną** scoped próbę S22 z tym samym 13-turn fixture, ocenić kategorię błędu i sprawdzić zachowanie częściowego prywatnego dowodu. Dopiero po rozstrzygnięciu natywnego błędu zdecydować o opcjonalnym **laboratory-only** wariancie dłuższego kontekstu lub jawnym zarządzaniu kontekstem bez milczącego restartu. Osiem ocen ludzkich pozostaje `UNASSESSED`.

[Poprzedni pozytywny checkpoint N=3×7 tur](2026-11-08-lab-semantic-01-gemma-e2b-three-sessions.md); [rejestr blokera](../../BLOCKERS.md).
