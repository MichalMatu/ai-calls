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

## Eksperyment 4096 tokenów tylko w Lab — fizyczny wynik z S22

- Kod `main@f38b5f42dbf46f77e3752ab4a8aa11705372380c`: LiteRT `maxNumTokens` wynosi **4096 wyłącznie dla `AndroidSkillLabSessionFactory` → `createLaboratoryConversationReply`**. `createConversationReply` poza Lab zachowuje domyślną wartość **2048**. Nie zmieniono limitów wiadomości w interfejsie ani nie przywrócono czterech odrzuconych historii.
- `aicalls-semantic2-lab4096-mac-gate-20261108-v1`: host task `failed` przez ostatni krok z kodem 125, lecz **pełny `scripts/verify_host.sh` zakończył się PASS** (Gradle/AndroidTest APK, lint, 365 testów Python, audio audit), potwierdzono zbudowanie artefaktów kolejnym testem S22. Nie oznaczać całego taska jako DONE/0.
- `aicalls-semantic2-s22-context4096-20261108-v1`: **DONE/0**, S22 preflight IDLE i brak kampanii; instalacja app APK i AndroidTest APK bez wipe; prawdziwa lokalna Gemma **13/13 tur bez JNI exception**. Heurystyki zawartości **11/13**, dwa niepotwierdzone punkty `missing_ticket_cautious` oraz `final_ticket_cautious`; **`human_semantic_review=UNASSESSED`**, wymagany prywatny przegląd rzeczywistej odpowiedzi. Średni czas odpowiedzi **6686 ms**, 0 propozycji END_CALL; postflight IDLE, Mac clean.
- `aicalls-semantic2-s22-context4096-repeat-n3-20261108-v1`: **FAILED/71** na preflight przed sesją N=2 (`E2B_4096_REPEAT_2_GUARD=REFUSED`), bez dalszych prób. Nie zaliczać N=3; nie było drugiej ani trzeciej 13-turowej rozmowy.

**Wniosek:** wariant 4096 usuwa obserwowaną awarię w **jednej** pełnej, trudniejszej rozmowie, co wspiera hipotezę niewystarczającego okna przy 2048, lecz nie dowodzi jej ostatecznie. Stabilność N=3, pamięć semantyczna, pomiar zapasu RAM i 8192 tokenów pozostają **OPEN**. Nie przełączać produkcyjnych ścieżek Call na 4096 bez odrębnego odbioru.

## Follow-up — 4096 repeatability and 8192 preflight (same day)

N=3 **technically PASS** with 4096 on physical S22; second and third runs each 13/13 with no JNI; measured peak PSS ~2.35 GB, minimum system MemAvailable ~1.38 GB. Full details in [E2B memory benchmark](2026-11-08-e2b4096-n3-ram-and-8192-instrumentation.md). Opt-in 8192 compiled and passed full Mac QA on `faf6ee5`, **but no S22 run** — PocketHost foreground safety refusal; no install. F-148 stays OPEN for unknown root-cause and semantic review; 4096 mitigation is proven for 13-turn technical scope.
