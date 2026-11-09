# AI Calls — macierz funkcjonalna ekran po ekranie

## G5.3 — Skills A/B 6 sesji, native reuse i latencja: S22_TECHNICAL_PASS, HUMAN_OPEN (2026-11-09)

**CONTEXTUAL vs ALL_ACTIVE** na S22: 3 sesje ×3 tury na wariant = **18/18 PASS**, 36 prywatnych WAV, STT WER 0/150 syntetycznych słów, korekta dnia pamiętana w każdym przebiegu. Mediany pełnej odpowiedzi (tury 1/2/3) **CONTEXTUAL 42,6/26,8/13,0 s**, **ALL_ACTIVE 23,0/10,0/9,0 s**; w drugiej turze −62,6%. Natywne `conversation_reused` = `false,false,true` vs `false,true,true` i mediana model + aprobata tury 2 = **17,6 vs 2,8 s**. Odpowiedzi ALL_ACTIVE w turach 2/3 są nawet dłuższe; to niewielkie porównanie dwóch strategii i różnej treści promptu, nie przyczynowy benchmark ani p95. Mac full QA/375 Python i S22 no-wipe/Call IDLE/clean PASS. Pierwszy host task preflight v1 odrzucony bez komend przez `idle_timeout>3600` (F-164 VERIFIED_SCOPED), v2 pełny PASS. **Human quality ośmiu wymiarów nadal UNASSESSED; nie zmieniać trybu produkcyjnego, G6 nieuruchomione.** Lokalny indeks: `~/Library/Application Support/ai-calls/qa/g53-private/skills-ab-index.html`. [Checkpoint](checkpoints/2026-11-09-g53-skills-ab-latency-s22.md).

## G5.3 — prywatny korpus S22 i jakość: OFFCALL_EVIDENCE_PASS, HUMAN_REVIEW_OPEN (2026-11-09)

**3 niezależne sesje × 3 syntetyczne tury = 9/9 PASS; 18 prywatnych WAV**, pełne STT/approved E2B i metryki RX/TX na Mac (0700/0600), lokalny `index.html` z trzema stronami do odsłuchu i ośmioma ocenami `UNASSESSED`. **STT 0/75 błędów syntetycznych słów, WER/CER 0.000**; mediany całego STT→E2B→TTS po pozycji **42.6/26.8/13.0 s** (n=3, *nie p95*). Natywny LiteRT tworzy Engine w turze 1 i Conversation ponownie w turze 2 po zmianie Skills; dopiero tura 3 `conversation_reused=true`. Mac pełne QA i 375 Python PASS, S22 IDLE/profil/no-wipe/clean. F-162 błędne API argumentów testu RED→GREEN `VERIFIED_SCOPED`. **Human naturalness/factuality nadal UNASSESSED; G5.3 nie zamknięty i G6 NOT_RUN.** [Checkpoint](checkpoints/2026-11-09-g53-private-voice-quality-corpus-latency.md).

## G5.3 — native STOP: `PASS_DEVICE_OFFCALL_SCOPED`; dialog quality: `UNASSESSED`

Mac full QA 2× DONE/0, 371 Python, AndroidTest compile i clean. S22 native E2B aktywny STOP/recovery 1/1 (`aicalls-g5p3-native-active-inference-stop-s22-20261109-v2`) i natywne STT/TTS STOP/restart 2/2 (`aicalls-g5p3-native-stt-tts-stop-s22-20261109-v1`), obie bramki Call IDLE bez wipe/profil zmian. Osiem `LabDialogueReport` wymiarów jakości `UNASSESSED`; real Call i p95 NOT_RUN. F-161 regex host testu VERIFIED_SCOPED. [Checkpoint](checkpoints/2026-11-09-g5p3-active-native-stop-stt-e2b-tts-technical-pass.md).

## G5.2 — audio wielu tur i STOP: `S22_PASS_OFFCALL_SCOPED`

**G5.2 = S22_OFFCALL_MULTITURN_AUDIO_AND_STOP_PASS (2026-11-09).** Po Mac full QA `aicalls-g5p2-multiturn-audio-stop-mac-qa-20261109-v2` **DONE/0**, S22 `aicalls-g5p2-three-turn-audio-stop-s22-20261109-v1` **DONE/0**: 5/5 deterministycznych testów STOP/late callback (STT, model, TTS, restart i dwie kolejne generacje) i 1/1 natywna Gemma E2B + Android STT + Android TTS PL, **3/3 syntetyczne tury audio**, `MessageCapture` i `Clarification`, prawidłowa korekta dnia spotkania i pamięć, 3 decyzje routingu. TTS wygenerował **515 894 B PCM**, czasy narastające STT **4340/5723/4905 ms**, aprobata **21158/12699/7157 ms**, końcowy PCM **23042/14224/8694 ms**. Żadnych wykonanych narzędzi, prawdziwych połączeń ani zmian profilu; instalacja obu debug APK bez wipe, Call IDLE przed i po, Mac Git clean. Jest to **syntetyczny off-call Lab**, nie rzeczywista rozmowa, p95, odsłuch naturalności ani sprzętowy STOP dokładnie w trakcie natywnej inferencji (STOP sprawdzono przez kontrolowane spóźnione callbacki). [Checkpoint](checkpoints/2026-11-09-g5p2-multiturn-audio-and-stop-pass.md).

Pięć testów STOP jest deterministycznych, a jedna realna sekwencja E2B obejmuje 3 tury z prawdziwym STT/TTS. Hardware STOP *w środku* inferencji, realny Call, p95 i naturalność nie mają PASS.


## G5.1 — audio STT→E2B/Skills→TTS `S22_PASS_OFFCALL_SINGLE_TURN` (2026-11-09)

Mac `aicalls-g5p1-stt-e2b-skills-tts-mac-qa-20261109-v1` DONE/0, compile AndroidTest/APK, full host 371 Python, lint/audio, friction i clean. S22 `aicalls-g5p1-speech-skills-androidstt-e2b-tts-s22-20261109-v1` DONE/0: 1/1 natywny audio test, lokalny STT final po 4622 ms, E2B odpowiedź zaakceptowana po 21680 ms, ukończone TTS PCM 236416 bajtów po 23574 ms, routing 1 tura, tool nie wykonany, STOP final cleanup, IDLE i profil bez zmian. Tylko syntetyczny głos, 1 tura; `G5.2` wieloturowe audio/interrupt, `G5.3` human review, `G6` prawdziwy Call otwarte. [Dowody](checkpoints/2026-11-09-g5p1-offcall-speech-skills-e2e.md).


## G4.3 — Lab Skills metadata, per-turn trace i typed tool proposals: **S22_PASS_OFFCALL** (2026-11-09)

Mac task `aicalls-g4p3-skills-metadata-trace-tools-mac-qa-20261109-v3` **DONE/0**: targeted JVM, build AndroidTest, pełny host `verify_host.sh` (371 Python, lint/audio), anti-friction, clean. S22 task `aicalls-g4p3-metadata-ui-native-trace-s22-20261109-v1` **DONE/0**: `AndroidG43SkillMetadataAcceptanceTest` **2/2** (zaszyfrowany update/rollback i odczyt UI metadata/lab tags), `AndroidG4DynamicSkillsNativeE2ETest` **1/1** (prawdziwe E2B 4 tury, 3 refresh, `g43_route_events=4`, pamięć przed/po, STOP), oba APK bez wipe, Call IDLE i profil nienaruszone. F-158, F-159 VERIFIED_SCOPED po RED. **Nie jest to PASS pełnego STT/TTS, human naturalness ani rzeczywistego Call.** [Finalny checkpoint](checkpoints/2026-11-09-g4p3-metadata-trace-tools-final.md).

## G4.2 — prawdziwy dynamiczny Skills E2E, S22 `PASS_OFFCALL_FUNCTIONAL` (2026-11-09)

`AndroidG4DynamicSkillsNativeE2ETest#runnerGemmaDynamicSkillsPreservesMemoryAndStops`, Mac `aicalls-f157-g4p2-e2b-memory-s22-e2e-retest-20261109-v4` **DONE/0**: E2B/GPU, kontekst 4096, 4/4 natywne tury tekstowe z pełnym `SkillLabRunner`, 3 zmiany Skills i natywne odświeżenie instrukcji/Conversation, kod `ZX74` przypomniany przed/po zmianie tematu, STOP i odmowa wysłania po STOP, brak własnego Call, profil nietknięty, instalacja bez wipe, Mac full QA (371 Python) PASS i końcowe Git clean. **Scope:** tekstowe off-call; nie oznacza `PASS_REAL_CALL`, ludzkiej oceny naturalności, UI ręcznego klikania wszystkich zmian ani pełnej integracji STT/TTS. [Finalny checkpoint](checkpoints/2026-11-09-g4-dynamic-skills-e2e-final.md). `F-157 VERIFIED_SCOPED` po rzeczywistym wcześniejszym RED.

## S22 — F-156 Skills oraz Gemma E2B/E4B (2026-11-09)

`F-156` **VERIFIED_SCOPED**: aktualna kompozycja promptu przenosi aktywne wersjonowane Skills po zasadach bezpieczeństwa, przed odpowiedzią; natywna E2B na S22: aktywne `true`, nieaktywne `false`, izolowany UI edit/revision/toggle/rollback PASS. Dwa modele, **18/18 tur** 2048 na GPU, identyczne zadania: marker zgodny z aktywacją, przypomnienie i sprostowanie kodu, cytowane vs rzeczywiste zakończenie, JSON i proponowane narzędzie tylko zgodne z semantyką **PASS w obu modelach**. E4B była około trzykrotnie wolniejsza; nie potwierdzono przewagi jakościowej w tym małym zestawie. MTP, p95, wielokrotne N≥3 i selektor Google Gallery-style Agent Skills **NOT_RUN**. [Checkpoint](checkpoints/2026-11-09-e2b-e4b-agent-skills-differential.md).


## Diagnostyka natywnego modelu i kontekst Lab — S22 VERIFIED (2026-11-09)

F-155 **VERIFIED**: profil `GEMMA_4_E2B`, backend GPU, baseline 2048 i 4096 po 2 tury przy identycznych promptach: pierwsza z zimnym ładowaniem **28,57/27,23 s**, druga po utrzymaniu kontekstu **3,32/3,63 s**. Nowa konfiguracja kontekstu *wyłącznie Lab* (domyślnie 4096, można wpisać dowolną dodatnią liczbę; Call nadal 2048), widoczny live breakdown inicjalizacji/konwersacji/generowania i PSS/RAM. Mac full gate DONE/0, S22 po nowej instalacji **4/4 AndroidTest PASS**, IDLE, bez wipe. Nie traktować średniej cold+warm jako czasu samej generacji. [Checkpoint](checkpoints/2026-11-09-f155-lab-context-latency-diagnostics.md).


## USB/ADB Laboratorium i telefonia — wynik bieżący (2026-11-09)

**PASS_DEVICE_OFFCALL / PARTIAL_LIVE_CALL.** Na fizycznym S22: `AndroidUsbLabControlContractTest` **3/3 PASS**, rzeczywiste `status/config-get/ram` **3/3 PASS** (F-152/153 VERIFIED), USB E2B 4096 **3 presety × 2 tury = 6/6** z prywatnym SAVE i STOP, bez wymogu AI Calls foreground (`aicalls-onepass-s22-usb-call-negatives-skills-ab-20261109-v1` DONE/0). **Współbieżność GUI i kontrolera USB 1/1 PASS** w `AndroidUsbGuiLabConcurrencyTest`: drugi natywny backend odrzucony, po GUI STOP USB uruchomił backend (F-150 VERIFIED dla współdzielonej dzierżawy; nie osobny simultaniczny broadcast ADB). **3/3 odmowy** takeover/resume/owned-stop przy braku Call. Brak pozytywnych testów działań podczas autoryzowanej rozmowy; brak pełnej oceny semantycznej i testów audio w tym przebiegu. [Checkpoint](checkpoints/2026-11-09-single-pass-ui-usb-ai-skills-acceptance.md).

## LAB-SEMANTIC-02 — bieżący odbiór długiej sesji Gemma E2B (2026-11-08)

**PARTIAL / PASS_DEVICE_TECHNICAL:** E2B 4096 na realnym S22 **3×13 = 39/39 tur bez JNI**; heurystyki 11/13, 13/13, 11/13 i **human semantic UNASSESSED**. Dwa testy 2048 zawiodły w turze 10 (`LiteRtLmJniException`, F-148 OPEN). Wariant 8192 ma Mac build PASS, ale na S22 NOT_RUN. [Dokładne PSS/latencja](checkpoints/2026-11-08-e2b4096-n3-ram-and-8192-instrumentation.md).

## Historia LAB-SEMANTIC-02 — stan przed poprawą do 4096

## LAB-SEMANTIC-02 — dłuższy test rzeczywistego dialogu (2026-11-08)

**PARTIAL / BLOCKED_MODEL_NATIVE:** S22, 13-turn próba, 9/13 tur z odpowiedziami, 10. `FAILED` przez `LiteRtLmJniException`. Mac gate źródła testu PASS; źródłowy częściowy zapis dowodów `68e848d` ma QA_PENDING. N=2/3 i ręczny review `NOT_RUN/UNASSESSED`. **F-148 OPEN**, a LAB-SEMANTIC-01 N=3×7 PASS nie jest dowodem stabilności dłuższych kontekstów. [Checkpoint](checkpoints/2026-11-08-lab-semantic-02-long-dialogue-f148.md).

## LAB-SEMANTIC-01 — fizyczna sonda dialogu N=3 (2026-11-08)

**PARTIAL / PASS_DEVICE_FUNCTIONAL w wąskiej próbie:** `GEMMA_4_E2B` na realnym S22, **3 niezależne sesje, 21 tur**. Pamięć losowego kodu po zmianie tematu, korekta koloru, powrót do nowych faktów i zasygnalizowanie braku nieznanego numeru **3/3 PASS**; szczegóły i przedziały `docs/checkpoints/2026-11-08-lab-semantic-01-gemma-e2b-three-sessions.md`. Brak real Call, stopień ukończenia naturalności/halucynacji z innymi danymi **OPEN**; pełny ekranowy CRUD i ręczna ocena rubric **NOT_RUN**. Prywatne transkrypty wyłącznie na S22.

**Wersja bazowa:** `main@edf63c1ea9dc425c91d2a1571a90038631852ffd` (stan po UX-11, przed porządkowym commitem dokumentacji), 2026-11-08. **Cel:** rzeczywisty odbiór każdej funkcji widocznej w aplikacji na Samsung S22, nie ponowny przegląd estetyki. Źródło nawigacji: `AppNavigation.AppTab` — sześć zakładek: Start, Historia, Agent AI, Laboratorium, Modele, Ustawienia. Ekrany potomne wskazano poniżej.

## Protokół jednego przypadku
1. Nadaj ID, HEAD, wersję APK z urządzenia, urządzenie/SIM/kanał i izolowane dane testowe (bez sekretów).
2. Określ **przed próbą** oczekiwane działanie: wejście → reakcja UI → trwały stan/rezultat → STOP/rollback → ponowne otwarcie. Zapisz źródło prawdy (UI + store/log/dowód, jeżeli potrzebny).
3. Sprawdź normalną ścieżkę, przypadek negatywny, ponowienie, odwracalność, bezpieczeństwo danych i odporność na powrót/rotację/restart, gdy ma zastosowanie. Weryfikuj aktywne uprawnienia i stan urządzenia.
4. Używaj istniejących testów, fixture i ADB; nie traktuj renderowania kontrolki jako potwierdzenia wykonania. Nie zmieniaj ani nie kasuj danych właściciela: przygotuj odrębne, fikcyjne wpisy i usuń **tylko** je. Zrzuty, WAV, transkrypty i dane osobowe pozostają prywatnie na telefonie/Macu.
5. Po każdym przypadku aktualizuj poniższy status i krótką referencję do **konkretnego** dowodu. Defekt → `BLOCKERS.md` z nowym numerem i testem; naprawa minimalna, regresja tylko zmienionego obszaru. Nie kończ na stwierdzeniu „przycisk istnieje”.

**Statusy:** `NOT_RUN`, `PASS_UI_SYNTHETIC`, `PASS_OFFCALL_FUNCTIONAL`, `PASS_DEVICE_FUNCTIONAL`, `PASS_REAL_CALL`, `PARTIAL`, `FAIL`, `BLOCKED`. Statusy nie są zamienne. Dla przypadków wielofunkcyjnych status obejmuje **wszystkie** wymienione podpunkty, inaczej `PARTIAL`. **Dziedziczone dowody** to podpowiedź do testów, nie zaliczenie całego ekranu.

## F1 — fizyczne testy S22 zakończone, UI do dokończenia (2026-11-08)

- **S22 `SM-S906B`, ADB `RFCT70L7E8J`, Android Call IDLE:** `aicalls-f1-s22-ready-preflight-20261108-v2` DONE/0; AI Calls foreground; źródło i Mac clean `e30799e`.
- **PASS_DEVICE_FUNCTIONAL w izolowanych magazynach (3/3):** `aicalls-f1-s22-fixture-functional-20261108-v2` DONE/0: `AndroidCustomVaultFunctionalAcceptanceTest` (4 typy, CRUD, rename, reopen, AES-GCM, validation), `AndroidPersonalScenarioStoreContractTest` (create/revise/encrypted store), `AndroidSkillPresetMatrixTest#aEncryptedEditVersionSwitchAndRollbackInIsolatedStore` (edit/version/enable/rollback). Test APK `adb install -r -t`, bez wipe i bez danych właściciela. Postcheck IDLE / clean.
- **Gemma Audio native synthetic PASS:** `aicalls-f1-gemma-audio-s22-resource-preflight-20261108-v1` (RAM dostępne 2.94 GiB-ish kB, thermal 0), `aicalls-f1-gemma-native-direct-audio-s22-20261108-v2` DONE/0: `AndroidGemmaAudioNativeOffcallTest` 1/1 PASS, **9.328 s**, PCM **cisza syntetyczna** → natywny Engine AudioBytes → terminalna odpowiedź / dopuszczony pusty transkrypt; Android Call IDLE, thermal 0. `v1` nie uruchomił testu z powodu braku `timeout` na Macu, poprawiono portable Python runner. **Nie** potwierdzono rozpoznawania słów, jakości PL ani integracji w profilu; `GEMMA_AUDIO_DIRECT` nadal `integrated=false`.
- **Nowy UI test Sejfu:** `AndroidCustomVaultUiAcceptanceTest` na `main@6b2d813` kompiluje się i APK AndroidTest buduje (pierwsza komenda `aicalls-f1-s22-vault-ui-acceptance-20261108-v1` PASS). Próba instalacji przerwana warunkiem foreground, **test ekranowy NOT_RUN**. `aicalls-f1-s22-foreground-after-ui-test-block-20261108-v1` DONE/0: ekran zajmuje `pl.pockethost.companion/.MainActivity`, Call IDLE. Nie przejmować PocketHost.
- **Znaczenie:** backendowe Skills/Scenarios/Sejf mają odbiór na urządzeniu; pełne **UI→zapis→powrót→odczyt** i realne polskie audio dla Gemmy są osobnymi dalszymi próbami. `NAV-01` nie blokuje tego etapu. [Checkpoint](checkpoints/2026-11-08-f1-s22-device-functional-core-and-gemma-direct.md).

## Początek właściwego F1 — 2026-11-08, Skills / Sejf / scenariusze / Gemma Audio

- **Decyzja właściciela:** nie blokować testów funkcjonalnych zaległymi screenshotami UX12 ani niedokończonym NAV-01. Priorytet: edytowanie Skills, CRUD Sejfu, własne scenariusze, działający model i wejście audio.
- **Preflight:** `main@fa21f88` = czysty Mac checkout, S22 ADB `RFCT70L7E8J` obecnie **niepodłączony** (`aicalls-f1-corepreflight-20261108-v1`, failed/1 wyłącznie na `adb get-state`). **Nie** przypisywać PASS urządzenia.
- **Ponowna próba S22:** urządzenie wróciło do ADB, ale ekran przejął równolegle **ShellyLink**. `aicalls-f1-s22-isolated-skill-vault-scenario-20261108-v1`: assembleDebugAndroidTest **PASS**, **instalacja i testy NOT_RUN**, zatrzymane przez fence foreground; `aicalls-f1-s22-recheck-why-install-fenced-20261108-v1` potwierdza ShellyLink i IDLE. Nie instalować/przejmować telefonu, dopóki ekran nie jest wolny. Nie mylić device-busy z defektem.
- **Test Sejfu:** nowy `AndroidCustomVaultFunctionalAcceptanceTest` izoluje `noBackupFilesDir`, nie dotyka rekordu właściciela; obejmuje cztery typy, zakres wrażliwości, rename, walidację/duplikaty, brak plaintext w ciphertext, delete/reopen. Test AndroidTest Kotlin **COMPILE PASS** `aicalls-f1-custom-vault-test-compile-20261108-v1`; uruchomienie na S22 **NOT_RUN**.
- **Gotowe do uruchomienia obok:** istniejące izolowane `AndroidSkillPresetMatrixTest.aEncryptedEditVersionSwitchAndRollbackInIsolatedStore` oraz `AndroidPersonalScenarioStoreContractTest.personalScenariosUseExistingEncryptedStoreAndPreserveTemplate`; te testy nie zastępują ręcznego UI CRUD. Native `AndroidGemmaAudioNativeOffcallTest` jest osobną, ryzykowniejszą próbą modelu, przed którą sprawdzić dostępność RAM i bezpieczeństwo hosta. Audio Direct **nie jest włączone w registry**.
- [Checkpoint](checkpoints/2026-11-08-f1-owner-priorities-device-unavailable.md). F-080/F-122/F-123/F-124 bez zmian.

## Sesja odbioru 2026-11-08 — fizyczny preflight i NAV-01

- **Źródło:** czysty Mac checkout `main@0e7dd43244edd6c22ce398490806c4a70508d487` = `origin/main`; różnice od `3029034` tylko w dokumentacji. **Urządzenie:** Samsung S22 `SM-S906B`, USB/ADB, APK `0.2.0` code 2, telefon `IDLE`.
- **Bezpieczna gotowość:** telefon/mikrofon/Shizuku API granted, AI Calls domyślnym dialerem, proces Shizuku widoczny, usługa kampanii nieaktywna. Cztery pliki modeli E2B/E4B/Qwen3 1.7B/0.6B mają oczekiwane rozmiary — **nie** oznacza to weryfikacji SHA/inferencji. Odczyt profilu `ANDROID_STT / GEMMA_4_E2B / ANDROID_TTS_PL`. Receipts: `aicalls-fa-preflight-s22-20261108-1018-v1`, `aicalls-fa-models-roles-services-readonly-20261108-v1`.
- **NAV-01 = PARTIAL:** przejścia i zaznaczenia pierwszych czterech zakładek fizycznie potwierdzone, reszta nierozstrzygnięta po wejściu PocketHost na pierwszy plan. Nie oznaczać całości PASS ani otwierać nowego F bez reprodukcji defektu samej aplikacji. Przy kolejnym bezpiecznym foreground AI Calls dokończyć brakujące przejścia i Back/stos Activity. Nie przerywać innych aplikacji.
- **Bez skutków:** brak dial, pobrań, instalacji, zmian profilu, edycji sejfu i danych właściciela. Screenshoty na Macu prywatnie. [Checkpoint](checkpoints/2026-11-08-functional-nav01-preflight-interrupted.md).

## 0. Powłoka, nawigacja i dostępność
| ID | Funkcja / kryterium | Stan i dowód |
|---|---|---|
| NAV-01 | Każda z 6 zakładek, selekcja, powrót, bez znikających danych i dubli Activity | **PARTIAL / 2026-11-09 DEVICE 10/10 PASS (scoped)** — aktualne debug APK i AndroidTest zainstalowano bez wipe na S22; `aicalls-nav01-f151-s22-reacceptance-20261109-v1` DONE/0: kontrakt 5/5, przejścia zakładek i Settings/Back 2/2, macierz ekranów/prefs 3/3. F-151 `VERIFIED` (nieaktualne oczekiwanie testu PhoneEnrollment → IdentityVault). Mac full gate PASS. Całkowity back stack, duplikaty Activity po wielokrotnych przejściach oraz restart/trwałość sesji w NAV-01 **jeszcze NOT_RUN**. Nie podnosić do pełnego NAV-01 PASS; [checkpoint](checkpoints/2026-11-09-nav01-f151-reacceptance.md). |
| NAV-02 | Dolna belka, toolbar potomnych ekranów, klawiatura, długie teksty/scroll, pion/poziom | NOT_RUN |
| NAV-03 | PL/EN/Auto, ponowne wejście, zmiana języka bez utraty konfiguracji | NOT_RUN |
| NAV-04 | TalkBack, content descriptions, fokus, 48dp dotyk, kontrast i dynamiczny font | NOT_RUN manualnie; poprzednie testy automatyczne UX-05 |
| NAV-05 | Screenshoty dozwolone, prywatne wartości ukryte, brak wycieku w logach | PARTIAL: source/UX synthetic; prywatność całej ścieżki NOT_RUN |

## 1. Start / zadanie / Kampanie / sterowanie Call
| ID | Funkcja / kryterium | Stan i dowód |
|---|---|---|
| HOME-01 | Informacja o zadaniu, autoryzacji, fazie AI i ostatnim wyniku nie jest mylona z aktualnym Call | NOT_RUN |
| HOME-02 | Stan gotowości roli Dialer, SIM, zgody, wymagane uprawnienia i czytelne błędy odmowy | NOT_RUN |
| HOME-03 | START tylko dla dokładnie przyjętego celu; odmowa, anulowanie, restart i deduplikacja | NOT_RUN / real Call wymaga właściwego upoważnienia |
| HOME-04 | Pływający czat: brak przed pierwszą linią, icon-only, otwarcie, dopisywanie, zamknięcie, zniknięcie po końcu | PASS_UI_SYNTHETIC — `aicalls-ux11-s22-synthetic-layout-and-private-archive-20261108-v1` 2/2; real CALL NOT_RUN |
| HOME-05 | STOP AI przerwie obliczenia, TX i skutki; pozostanie dostępny bez czatu | NOT_RUN w Call; LAB-03 dowodzi tylko offline STOP |
| CAMP-01 | Wejście w Kampanie, stan wykonywania, diagnostyka, status i niezależny zapis próby | NOT_RUN |
| CAMP-02 | Dwa tryby: skrypt + model oraz model Direct; prawidłowa konfiguracja/wybrany model | NOT_RUN w Call |
| CAMP-03 | Zgoda na przyszły zapis vs zatrzymanie aktywnego rejestratora, wycofanie zgody | BLOCKED dla aktywnego rejestratora: F-122 |
| CAMP-04 | Ponowienie, odwołanie, zakończenie, zgodność attemptId / sesji z rzeczywistym Android Call | BLOCKED częściowo: F-123 |
| CALL-01 | Ekran sterowania własną rozmową, przyjęcie/rozłączenie/przejęcie/STOP bez obcego Call | **PARTIAL / OFFCALL_DENIAL 3/3 PASS** na S22: prawdziwy USB CLI odrzucił takeover, resume i stop-owned przy Call IDLE / brak tracked Call (`aicalls-onepass-s22-usb-call-negatives-skills-ab-20261109-v1`). Pozytywne działanie podczas oddzielnie autoryzowanego Call **NOT_RUN**. |
| CALL-02 | Połączenia przychodzące: tryb manual/AI, powrót właściciela i polityka ciszy | NOT_RUN end-to-end; istnieją testy jednostkowe/Android |

## 2. Historia
| ID | Funkcja / kryterium | Stan i dowód |
|---|---|---|
| HIST-01 | Lista sesji, status, czas, kierunek, filtry, pusty stan, odświeżanie | NOT_RUN |
| HIST-02 | Otwieranie pojedynczej sesji, kolejność tur, poprawne powiązanie faktycznego wyniku | NOT_RUN |
| HIST-03 | Odtwarzanie WAV z dymku, pauza/STOP, błędny lub brakujący plik, rewind/seek | NOT_RUN fizycznie |
| HIST-04 | Usunięcie jednej rozmowy i usuwanie zbiorcze na **prywatnych fixture**, bez zmiany cudzych danych | NOT_RUN funkcjonalnie |
| HIST-05 | Cała oś audio: RX/TX/manual, ciągłość i odsłuch całej sesji + fragmentów | BLOCKED: F-080 |
| HIST-06 | „Ostatni odsłuch” pokazuje właściwą sesję również dla INCOMING / długich tur | BLOCKED: F-124 |

## 3. Agent AI — Skills, scenariusze, przygotowanie, Sejf, Narzędzia
| ID | Funkcja / kryterium | Stan i dowód |
|---|---|---|
| SKILL-01 | Presety: wybór, aktywacja, włączenie/wyłączenie Skills i wpływ na wygenerowaną wypowiedź | **PARTIAL / S22 REAL CUSTOM SKILL VERIFIED_SCOPED**: fizyczny UI copy/activate/disable/restore/reopen **1/1 PASS** w izolowanym zaszyfrowanym store; Gemma E2B A/B po F-156: `active_marker=true`, `inactive_marker=false` **1/1 PASS**, E2B i E4B w korpusie 2048 marker/recalled fact/JSON **8/8 tur PASS**. N≥3, całościowe testy własnych kategorii i dynamiczny Agent Skills **NOT_RUN**. [Checkpoint](checkpoints/2026-11-09-e2b-e4b-agent-skills-differential.md). |
| SKILL-02 | Instrukcja: edycja, walidacja, wersje/rewizje, zapisz/przywróć, brak wpływu nieaktywnej instrukcji | **PARTIAL / S22 UI VERSION + MODEL A/B PASS**: rzeczywiste kliknięcia GUI kopiowania, rewizji, wyłączania i ponownego aktywowania, rollback i odczyt z osobnego zaszyfrowanego magazynu **1/1 PASS**; oryginalne presety niezmienione. F-156 scoped VERIFIED dla aktywnej/nieaktywnej instrukcji w Gemma E2B; nie wykonywano długotrwałego restartu systemu/pełnego testu wszystkich rewizji. |
| SKILL-03 | Własne umiejętności, warunki użycia i separacja stanów pomiędzy presetami | **PARTIAL / CUSTOM SKILL REAL MODEL PASS**: własna wersja `ConversationStyle` przeszła izolowany GUI edit/toggle/restore i fizyczne A/B na E2B i E4B; fabryczne presety pozostają nienaruszone. Automatyczna selekcja Skills jak w Gallery, typowane execution tools, wiele kategorii i warunki uruchamiania **NOT_RUN/PLANNED**. [Analiza](../docs/AGENT_SKILLS_EDGE_GALLERY_REVIEW_2026-11-09.md). |
| SCEN-01 | Fabryczne scenariusze: przegląd i poprawne oznaczenie DRAFT_ONLY vs wykonawcze | NOT_RUN |
| SCEN-02 | Własny scenariusz: kopia, nazwa, cel, zapis, rewizje, przywrócenie, usunięcie fixture | **PARTIAL / PASS_DEVICE_FUNCTIONAL backend** — `AndroidPersonalScenarioStoreContractTest` 1/1 na S22 PASS create/revise i zaszyfrowany zapis; UI/przywróć/usuń NOT_RUN. |
| PREP-01 | Profil OUTGOING/INCOMING: edycja, zapis, wznowienie, niewyciekający kontekst i zakres pól | NOT_RUN |
| VAULT-01 | 7 pól standardowych: utwórz/zmień/usuń **fikcyjny testowy** wpis, odczyt po restarcie, błędy walidacji | NOT_RUN; widok PASS_UI_SYNTHETIC w UX-11 |
| VAULT-02 | Własne: typ tekst/liczba/data/sekret, poziom wrażliwości, dodaj/zmień/usuń, szyfrowanie/odporność | **PARTIAL / PASS_DEVICE_FUNCTIONAL backend** — `AndroidCustomVaultFunctionalAcceptanceTest` 1/1 na S22 PASS, 4 typy, CRUD+reopen+walidacja i ciphertext; nowy UI test kompiluje się, ale NOT_RUN z powodu PocketHost foreground. Custom nie jest automatycznie udostępniany AI. |
| VAULT-03 | PESEL/data urodzenia: ukrycie w UI, zgody zakresowe, brak wypowiedzi/wysłania bez autoryzacji | NOT_RUN; nie używać prawdziwego PESEL |
| TOOL-01 | Lista/stan narzędzi, zmiana ustawień, trwałość i zachowanie wyłączonych akcji | NOT_RUN |
| TOOL-02 | Dry-run wyboru narzędzia i bezpieczne zakończenie bez prawdziwego efektu | NOT_RUN |

## 4. Laboratorium Skills

**Nowy zakres odbioru LAB-DIALOG-01:** [plan A–E](LAB_DIALOG_IMPLEMENTATION_PLAN.md). Kod źródłowy A–D wdrożony, fizyczny S22 dla ręcznej rozmowy i zaszyfrowanej przechowalni **PASS 3/3**. Adaptacyjne S22 i nowy układ z `992280b` **PASS_DEVICE_FUNCTIONAL** (S22 testy 2/2 UI/store i 1/1 native adaptive; receipt `aicalls-labdialog01-s22-final-device-acceptance-20261108-1601-v1` DONE/0); pełny klikalny UI editor i semantyka **NOT_RUN**, bez dziedziczenia PASS po dawnym korpusie.


**Uwaga 2026-11-08:** usunięte 4 sztywne historie nadal nie istnieją. Nowa ręczna sesja startuje bez przypadku; adaptacyjna wymaga testu utworzonego przez użytkownika. Dawne wyniki są historyczne.

| ID | Funkcja / kryterium | Stan i dowód |
|---|---|---|
| LAB-DIA-A | Manualny wieloturowy chat z tym samym kontekstem modelu, STOP, odtworzenie nowej sesji, bez Call | **PASS_OFFCALL_FUNCTIONAL:** S22 native 2 tury/STOP/restart 1/1; Mac runner 16 tur/STOP/late callback test w pełnym gate; QA receipts v3 i device v1. Semantyczna pamięć NOT_SCORED. |
| LAB-DIA-B | Własne przypadki CRUD, wersje, zapis/reload i ręczne wykonanie | **PASS_DEVICE_COMPONENT:** S22 izolowany Keystore CRUD/reopen/revision/report; UI controls test PASS (2/2). Pełny klikany UI CRUD NOT_RUN. |
| LAB-DIA-C | Ocena każdej tury: temat, pamięć, korekta, brak zmyślania, naturalność i uczciwe NOT_SCORED | **SOURCE/HOST PASS:** manualny rubric per tura i zaszyfrowany raport S22 PASS; prawdziwa semantyczna ocena modelu UNASSESSED / NOT_RUN. |
| LAB-DIA-D | Adaptacyjny rozmówca zależny od odpowiedzi modelu, ślad gałęzi, człowiek przejmuje | **PASS_DEVICE_FUNCTIONAL native adaptive:** JVM i S22 realna Gemma 1/1 test wyboru gałęzi na podstawie rzeczywistej odpowiedzi (`aicalls-labdialog01-s22-final-device-acceptance-20261108-1601-v1`); ślad przejścia. Drugi LLM i takeover bez STOP niezaimplementowane. |
| LAB-DIA-E | A/B model/Skills + prywatne STT/TTS/PCM, bez Call i efektów | **PARTIAL / DEVICE_PRESETS 3/3 PASS**: realna E2B 4096, 3 presety po 2 tury, prosta pamięć 3/3; bez osobnych danych właściciela. Nie wykonano porównania różnych modeli, powtórzeń N=3 na preset ani pełnego STT→Skills→TTS/PCM. |
| LAB-01 | Preset/model, nowa sesja, ręczny composer, edytor przypadków, poprawny stan podczas pracy | **PASS_UI_CONTROLS (S22 1/1) + PASS_NATIVE_OFFCALL 1/1**; fizyczny całkowity workflow ręcznego edytora przez ekran NOT_RUN. |
| LAB-02 | Prawdziwy lokalny model: 2 kolejne tury, odpowiedź i kryteria końca bez Telecom | PASS_OFFCALL_FUNCTIONAL — `aicalls-functional-real-offline-two-turn-stop-s22-20261108-v1` |
| LAB-03 | STOP w trakcie, stan STOPPED, brak dalszych tur, ponowny START | PASS_OFFCALL_FUNCTIONAL — ten sam receipt, tylko izolowane dwie tury i restart/STOP |
| LAB-04 | Timeline podczas długiego biegu, ręczny scroll w górę, auto-follow na dole, widoczne błędy | SOURCE/HOST PASS `992280b` oraz **S22 AndroidTest layout 1/1 PASS** w grupie 2/2 (`aicalls-labdialog01-s22-final-device-acceptance-20261108-1601-v1`): transcript zachowuje wysokość, composer jest widoczny; pełny długi wizualny scroll/auto-follow i błędy **PARTIAL/NOT_RUN**. |
| LAB-05 | Metryki, zgodność celu, odpowiedzi błędne/puste, timeout, ocena naturalności | **PARTIAL / NATIVE_TIMINGS_DEVICE_PASS** F-155 VERIFIED: S22 1/1 realny model + kontekst 2048 i diagnostyka GPU, init/conversation/generate/total, cold/warm, PSS/available RAM; Mac pełny gate PASS. Ocena naturalności, zgodności celu i pełnego zestawu błędów nadal UNASSESSED/NOT_RUN. [Checkpoint](checkpoints/2026-11-09-f155-lab-context-latency-diagnostics.md). |
| LAB-06 | Wszystkie presety i typy Skills, powtarzalność N=3, opóźnienia median/p95 | **PARTIAL / S22 PHYSICAL 18 NATIVE TURNS**: trzy fabryczne presety E2B 4096 3×2 PASS wcześniej; teraz E2B i E4B, kontekst 2048, 4 sesje ×2 = **8/8** oraz złożony dialog 2×(3+2) = **10/10**. Wszystkie mierzone punkty jakości obu modeli PASS; zimny start E2B ok. 11–14s/E4B ok. 35–43s, ciepłe ok. 2–3s vs 6–11s. N≥3, p95, pełne STT/TTS i ocena człowieka nadal NOT_RUN. |

## 5. Modele i silniki audio
| ID | Funkcja / kryterium | Stan i dowód |
|---|---|---|
| MODEL-01 | Katalog, szczegóły, aktualny model, lokalny stan artefaktu i READY vs niedostępny | **PARTIAL / E2B+E4B FILES READY ON S22**: fizyczny USB `stat` i katalog potwierdziły oczekiwane rozmiary obu `.litertlm` oraz rzeczywistą gotowość do generacji 2048 E2B i E4B. Pełny UI katalog/wybór, rehash SHA na urządzeniu i wszystkie inne modele **NOT_RUN**. |
| MODEL-02 | DOWNLOAD / Cancel / retry / VERIFY SHA-256 / niepełny plik / brak miejsca | NOT_RUN fizycznie; nie pobierać dużych plików automatycznie |
| MODEL-03 | Wybór modelu, trwałość, presety Direct i skrypt, brak podmiany w aktywnej próbie | NOT_RUN |
| MODEL-04 | Lokalna inferencja: gotowość, wyjście/odpowiedź, STOP i ponowienie | **PARTIAL / E2B+E4B REAL GPU VERIFIED_SCOPED**: S22 E2B/E4B lokalnie 18/18 tur, 2048, wszystkie odpowiedzi poprawnie dekodowane jako JSON; porównanie jakości Skills/korekty faktu/pożegnania i cold/warm. Profil aktywny E2B zachowany, modele otwierane sekwencyjnie, bez realnego Call. Testy innych modeli i interfejsowych zmian/uruchomień E4B **NOT_RUN**. [Checkpoint](checkpoints/2026-11-09-e2b-e4b-agent-skills-differential.md). |
| AUDIO-01 | Profile STT/model/TTS/głos i ich trwałość, niezależny wybór oraz rzeczywiste użycie | NOT_RUN |
| AUDIO-02 | Polski TTS → syntetyczny PCM → Parakeet STT, niepusty transkrypt / zgodność leksykalna | PASS_OFFCALL_FUNCTIONAL — `aicalls-functional-polish-tts-parakeet-offcall-s22-20261108-v1` |
| AUDIO-03 | STT na kontrolowanym nagraniu i mikrofonie, WER/CER, cisza, szum, stop i recovery | NOT_RUN |
| AUDIO-04 | Eksperymentalne bezpośrednie PCM16 → Gemma `Content.AudioBytes` → tekst; inicjalizacja native, pamięć/latencja, STOP, brak cichego fallback | **PARTIAL / PASS_OFFCALL_FUNCTIONAL (native synthetic)** — `AndroidGemmaAudioNativeOffcallTest` na S22 1/1 PASS, 9.328 s, cisza PCM, thermal 0; **polska mowa, jakość, STOP/rzeczywisty dialog i włączenie profilu NOT_RUN**. `GEMMA_AUDIO_DIRECT` nadal `integrated=false`; nie deklarować gotowości produkcyjnej. |
| AUDIO-04 | TTS (system/Piper), polska wymowa, szybkość, latencja, cancel oraz audio output | NOT_RUN w pełnej macierzy |
| AUDIO-05 | Integracja STT→model/Skills→TTS przez wspólny runtime, faktyczne RX/TX | NOT_RUN end-to-end |

## 6. Ustawienia, sekretarka i diagnostyka
| ID | Funkcja / kryterium | Stan i dowód |
|---|---|---|
| SET-01 | Ustawienia ogólne: każda kontrolka, zapis, restart, Reset tylko fixture | NOT_RUN |
| SET-02 | Sekretarka INCOMING: profil, enable, zgoda, zachowanie dla połączeń własnych i manual takeover | NOT_RUN fizycznie |
| SET-03 | Developer: każdy bezpieczny probe, wyjście, log oraz obsługa braku zasobu | NOT_RUN |
| SET-04 | USB Debug: stan ADB/Shizuku, diagnostyka, czytelne błędy i prywatny eksport | NOT_RUN |
| SET-05 | PL/EN/Auto: poprawność tłumaczeń i wartości, po wznowieniu i restarcie | NOT_RUN manualnie |

## 7. Integracja i bezpieczeństwo — bramki końcowe
| ID | Kryterium | Stan i dowód |
|---|---|---|
| INT-01 | Dowód jednej konkretnej sesji: Call ID, SIM, kierunek, rola dialer, RX→STT→model→TTS→TX ACK | NOT_RUN |
| INT-02 | Realna skuteczność zadania (np. CLIR) + niezależne potwierdzenie efektu, nie sam tekst modelu | NOT_RUN |
| INT-03 | Incoming/Outgoing, IVR, cisza, rozmowa nakładająca się na TTS, STOP/recovery, przejęcie | NOT_RUN |
| INT-04 | Bezpieczeństwo danych: ujawnienie tylko dozwolonych pól, revocation, poufność i retencja | NOT_RUN end-to-end |
| INT-05 | Recorder: wyłącz aktywny, korelacja, ciągłość i poprawny odsłuch archiwum | BLOCKED/OPEN: F-122, F-123, F-124, F-080 |
| INT-06 | Brak pozostałego aktywnego Call/usługi po STOP, IDLE, bez wycieku sesji/testowych fixture | NOT_RUN real-call |

## Kolejność wykonania w nowym czacie
**Krok 1:** pełny preflight stanu APK/urządzenia bez powtarzania zaliczonych testów. **Krok 2:** NAV + Start (bez telefonii), następnie Historia, Agent AI, Laboratorium, Modele/audio i Ustawienia. **Krok 3:** integracyjne próby offline i testy odporności. **Krok 4:** rzeczywisty Call tylko po ustaleniu i przyjęciu konkretnego celu/autoryzacji oraz po preflight IDLE; dokumentuj również odmowę/uprawnienia. **Krok 5:** aktualizacja macierzy, checkpoint, synchronizacja dokumentacji i czysty handoff.

**Zamknięcie ekranu:** każda jego funkcja ma osobny wynik albo jawne `BLOCKED/NOT_RUN` z przyczyną; UI i backend zgodne, błędy opisane, regresja na zmienionym obszarze; brak niezamierzonej mutacji danych właściciela.
