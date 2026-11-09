## G5.3 — prywatny gate ocen: F-166/F-167 VERIFIED_SCOPED (2026-11-09)

main@c30742b. Mac Local Agent final-gate-v2 DONE/0, 5/5 komend: 11/11 celowanych unittest, verify_host.sh z 382 Python PASS, build/audio/anti-friction PASS, Git clean. Prywatny skan: sessions=6, recorded=0, unassessed=6, invalid=0. F-166/F-167 technicznie zamknięte, ale G5.3 HUMAN_REVIEW_OPEN / UNASSESSED. Nie było ADB, połączeń ani ponownych tur S22. Wcześniejsze dwa v1 FAILED przez pin przestarzałego SHA, zachować historię.

# AI Calls — handoff po G5.3 Skills A/B: human review OPEN

## G5.3 — Skills A/B 6 sesji, native reuse i latencja: S22_TECHNICAL_PASS, HUMAN_OPEN (2026-11-09)

**CONTEXTUAL vs ALL_ACTIVE** na S22: 3 sesje ×3 tury na wariant = **18/18 PASS**, 36 prywatnych WAV, STT WER 0/150 syntetycznych słów, korekta dnia pamiętana w każdym przebiegu. Mediany pełnej odpowiedzi (tury 1/2/3) **CONTEXTUAL 42,6/26,8/13,0 s**, **ALL_ACTIVE 23,0/10,0/9,0 s**; w drugiej turze −62,6%. Natywne `conversation_reused` = `false,false,true` vs `false,true,true` i mediana model + aprobata tury 2 = **17,6 vs 2,8 s**. Odpowiedzi ALL_ACTIVE w turach 2/3 są nawet dłuższe; to niewielkie porównanie dwóch strategii i różnej treści promptu, nie przyczynowy benchmark ani p95. Mac full QA/375 Python i S22 no-wipe/Call IDLE/clean PASS. Pierwszy host task preflight v1 odrzucony bez komend przez `idle_timeout>3600` (F-164 VERIFIED_SCOPED), v2 pełny PASS. **Human quality ośmiu wymiarów nadal UNASSESSED; nie zmieniać trybu produkcyjnego, G6 nieuruchomione.** Lokalny indeks: `~/Library/Application Support/ai-calls/qa/g53-private/skills-ab-index.html`. [Checkpoint](checkpoints/2026-11-09-g53-skills-ab-latency-s22.md).

## G5.3 — prywatny korpus S22 i jakość: OFFCALL_EVIDENCE_PASS, HUMAN_REVIEW_OPEN (2026-11-09)

**3 niezależne sesje × 3 syntetyczne tury = 9/9 PASS; 18 prywatnych WAV**, pełne STT/approved E2B i metryki RX/TX na Mac (0700/0600), lokalny `index.html` z trzema stronami do odsłuchu i ośmioma ocenami `UNASSESSED`. **STT 0/75 błędów syntetycznych słów, WER/CER 0.000**; mediany całego STT→E2B→TTS po pozycji **42.6/26.8/13.0 s** (n=3, *nie p95*). Natywny LiteRT tworzy Engine w turze 1 i Conversation ponownie w turze 2 po zmianie Skills; dopiero tura 3 `conversation_reused=true`. Mac pełne QA i 375 Python PASS, S22 IDLE/profil/no-wipe/clean. F-162 błędne API argumentów testu RED→GREEN `VERIFIED_SCOPED`. **Human naturalness/factuality nadal UNASSESSED; G5.3 nie zamknięty i G6 NOT_RUN.** [Checkpoint](checkpoints/2026-11-09-g53-private-voice-quality-corpus-latency.md).

## G5.3 — techniczny native STOP na S22 PASS; human quality nadal OPEN (2026-11-09)

**S22_OFFCALL_NATIVE_STOP_TECHNICAL_PASS:** Mac `aicalls-g5p3-native-stop-mac-qa-20261109-v1` i `aicalls-g5p3-native-stt-tts-stop-mac-qa-20261109-v1` DONE/0, pełne host QA/371 Python; S22 `aicalls-g5p3-native-active-inference-stop-s22-20261109-v2` 1/1 PASS (E2B GPU `sendMessage` → `cancelProcess`, bez stale callback, recovery) i `aicalls-g5p3-native-stt-tts-stop-s22-20261109-v1` 2/2 PASS (rzeczywiste STT/TTS aktywny STOP, restart i brak stale PCM). Profil i Call IDLE bez zmian, APK bez wipe, zero Call. Błędny host regex v1 odmówił przed instalacją; v2 poprawiony, `F-161 VERIFIED_SCOPED`. **Human semantic/naturalness ośmiu wymiarów nadal UNASSESSED; p95 oraz prawdziwa telefonia NOT_RUN.** [Checkpoint](checkpoints/2026-11-09-g5p3-active-native-stop-stt-e2b-tts-technical-pass.md). Następny jedyny etap G5.3: prywatny przegląd kompletnych tekstów/WAV i niezależna ocena tur; nie powtarzać zaliczonych STOP.

## Status obowiązujący: G5.2 PASS, G5.3 NEXT

**G5.2 = S22_OFFCALL_MULTITURN_AUDIO_AND_STOP_PASS (2026-11-09).** Po Mac full QA `aicalls-g5p2-multiturn-audio-stop-mac-qa-20261109-v2` **DONE/0**, S22 `aicalls-g5p2-three-turn-audio-stop-s22-20261109-v1` **DONE/0**: 5/5 deterministycznych testów STOP/late callback (STT, model, TTS, restart i dwie kolejne generacje) i 1/1 natywna Gemma E2B + Android STT + Android TTS PL, **3/3 syntetyczne tury audio**, `MessageCapture` i `Clarification`, prawidłowa korekta dnia spotkania i pamięć, 3 decyzje routingu. TTS wygenerował **515 894 B PCM**, czasy narastające STT **4340/5723/4905 ms**, aprobata **21158/12699/7157 ms**, końcowy PCM **23042/14224/8694 ms**. Żadnych wykonanych narzędzi, prawdziwych połączeń ani zmian profilu; instalacja obu debug APK bez wipe, Call IDLE przed i po, Mac Git clean. Jest to **syntetyczny off-call Lab**, nie rzeczywista rozmowa, p95, odsłuch naturalności ani sprzętowy STOP dokładnie w trakcie natywnej inferencji (STOP sprawdzono przez kontrolowane spóźnione callbacki). [Checkpoint](checkpoints/2026-11-09-g5p2-multiturn-audio-and-stop-pass.md).

Pierwsza niezamknięta bramka: **G5.3 jakościowa ocena rozmowy i aktywny natywny STOP**. Zachowaj zaliczony G5.2; nie powtarzaj 3-turn S22 bez konkretnej przesłanki. Starsze akapity „G5.1/G5.2 NEXT” niżej są historyczne.


## Najnowszy stan — G5.1 fizyczny audio E2E PASS, następny G5.2

**G5.1 JEDNOTUROWE AUDIO E2E: S22_OFFCALL_PASS (2026-11-09).** Bez zmian toru telefonu i bez Call, natywny test `AndroidG51SpeechSkillsOffcallE2ETest#syntheticPolishPcmPassesSttGemmaSkillsApprovalTtsAndStop` odtworzył rzeczywisty lokalny TTS→syntetyczny PCM→wybrany STT→Gemma E2B/GPU z dynamicznymi Skills→`ConversationReplyBackend`→`TextOutputApprovalPolicy`→wybrany TTS→końcowy PCM, bez odtwarzania na głośniku. S22 `aicalls-g5p1-speech-skills-androidstt-e2b-tts-s22-20261109-v1` **DONE/0**: 1/1 AndroidTest, STT/tura routing 1/1, zatwierdzona odpowiedź, **236416 bajtów PCM16 16 kHz**, żadnego wykonanego narzędzia, STOP/close, profil nietknięty, Call IDLE przed/po, instalacja bez wipe i końcowy clean. Czasy narastające w tej pojedynczej próbie: STT **4622 ms**, zatwierdzona odpowiedź **21680 ms**, TTS PCM gotowe **23574 ms**. Mac `aicalls-g5p1-stt-e2b-skills-tts-mac-qa-20261109-v1` **DONE/0**: build AndroidTest, pełny `verify_host.sh`, 371 Python, lint/audio, anti-friction, clean Git. **Nie jest to wieloturowe audio, p95, pełna ocena naturalności, przerwanie podczas TTS ani EOS→TX/realne Call.** [Checkpoint](checkpoints/2026-11-09-g5p1-offcall-speech-skills-e2e.md).

**Pierwsze nowe zadanie G5.2**: pełne audio 2+ tur przy `CONTEXTUAL`, korekty i pamięć, STOP podczas każdej fazy + ochrona przed spóźnionym wynikiem; target `LocalSpeechTextPipeline`, `TextCallTurnController`, `AndroidSkillLabSessionFactory` i obecny `ConversationReplyBackend`. Nie powtarzać G4.3 ani jednoturowego G5.1 bez nowej przesłanki. Dawne „G5.1 NEXT” niżej to historia. [Checkpoint G5.1](checkpoints/2026-11-09-g5p1-offcall-speech-skills-e2e.md).

## Najnowszy status obowiązujący — 2026-11-09

**G4.3 CLOSED — S22_OFFCALL_E2E_SCOPED_PASS (2026-11-09)**. `name/description/capability` są przypięte do `SkillRevision` z kompatybilnym odczytem starego schema=1; edytor pozwala tworzyć nową wersję i rollback, a Lab pokazuje wybór i aktywację/dezaktywację Skills dla każdej tury. Typowana propozycja `END_CALL` przechodzi jedynie walidację Lab `PROPOSED_ONLY`, bez wykonania. Mac task `aicalls-g4p3-skills-metadata-trace-tools-mac-qa-20261109-v3` **DONE/0** (celowane JVM, AndroidTest build, pełny `verify_host.sh`, 371 Python, lint/audio, anti-friction i clean Git). S22 task `aicalls-g4p3-metadata-ui-native-trace-s22-20261109-v1` **DONE/0** (4 kroki): 2/2 testy metadanych/UI, 1/1 natywny E2B przez `SkillLabRunner` (4 tury, 3 odświeżenia, 4 zdarzenia routingu, pamięć przed/po pivot, STOP); dwie instalacje debug APK `-r -t` bez wipe, `Call IDLE` przed/po, profil zachowany, Git clean. **F-158 i F-159 VERIFIED_SCOPED** po wcześniejszych RED. To tylko **syntetyczny tekstowy Lab**, nie STT/TTS, realne Call ani human semantic review. [Checkpoint](checkpoints/2026-11-09-g4p3-metadata-trace-tools-final.md).

**Pierwszy nowy krok G5.1:** odtwórz stan pełnej ścieżki `STT → E2B + dynamic Skills → TTS` **off-call**, na istniejących adapterach i jednym runtime. Sprawdź dokładne bramki audio/STOP, payloady i raporty opóźnień. Osobno human semantic review, osobno G6 prawdziwe Call. Stare bloki G4.3 NEXT poniżej są historyczne, nie zadaniem do powtórki. [Dokładny checkpoint](checkpoints/2026-11-09-g4p3-metadata-trace-tools-final.md).

## Najnowszy status 2026-11-09 — obowiązujący

**G4.2 fizyczny E2E na S22: PASS (2026-11-09).** `G4.1` kontrakt JVM i Mac full PASS; `G4.2` rzeczywisty `SkillLabRunner` → `AndroidSkillLabSessionFactory` → `DynamicSkillLabBackend` → E2B/GPU, **4/4 natywne tury, 3 przełączenia aktywnego zestawu Skills, fikcyjny kod zachowany przed/po zmianie tematu, STOP PASS, profil i Call IDLE bez zmian**. Ostateczny task `aicalls-f157-g4p2-e2b-memory-s22-e2e-retest-20261109-v4` **DONE/0**: Mac 371 Python, Gradle/JVM/lint/APK/AndroidTest/audio i anti-friction PASS, S22 instrument 1/1 PASS, oba debug APK bez wipe i końcowe Git clean. Poprzedni fizyczny RED pamięci `F-157` naprawiony, historyczne FAIL retained. Tylko syntetyczne **off-call tekstowe E2E** — nie pełne STT/TTS, human naturalness ani rzeczywisty Call. Gemma E2B pozostaje głównym modelem. [Kompletny checkpoint](checkpoints/2026-11-09-g4-dynamic-skills-e2e-final.md).

**Następne: G4.3** — analiza `ConversationSkillLibrary`, `ConversationSkillTurnRouter`, `ConversationSkillLabActivity` i narzędzi: metadane/zmiana intencji oraz per-turn diagnostyka. Przed zmianami sprawdź świeży HEAD, dokładny v4 receipt, `AGENTS.md`, `BLOCKERS.md` i checkpoint. **Nie powtarzaj** G3 A/B 96 tur ani G4.2 czteroturowego S22 bez nowej przesłanki. Dawny niższy akapit „G4.1 NEXT” to poprzedni snapshot.

## Aktualny stan 2026-11-09 — formalna decyzja G3 i pierwsza praca G4

**G3 CLOSED — `DECIDED_KEEP_E2B` (decyzja właściciela 2026-11-09).** Mac+S22: 96/96 natywnych tur, 48/48 prywatnych par, backend E2B GPU/Qwen CPU, 24/24 JSON/tools na model; ostateczny Mac clean postflight i integralność prywatnego pliku **DONE/0**. Lokalny przegląd **wszystkich 96 syntetycznych odpowiedzi** regułowym audytem `aicalls-g3-private-synthetic-semantic-rubric-20261109-v2` **DONE/0**: E2B **150/150**, Qwen **141/150** punktowych kontroli. Qwen: 3× brak tekstowej wzmianki o wtorku i 3× godziny 10 w drugiej turze wyjaśnienia oraz 3× identyczna odpowiedź w obu turach prywatności. Ten ostatni sygnał nie dowodzi naruszenia prywatności. Pominięta reguła z v1 (`wtork`) została naprawiona; v2 jest obowiązującym wynikiem. **Naturalność oceniona przez człowieka nadal UNASSESSED**, przeniesiona do G5. E2B pozostaje głównym modelem; Qwen lab-only, bez zmiany kodu telefonii. [Finalny checkpoint](checkpoints/2026-11-09-g3-keep-e2b-final-decision.md).

**NEXT: G4.1 per-turn Skills routing contract i JVM tests**, bez kodu produkcyjnego Call do czasu potwierdzonego lifecycle promptu LiteRT. Stary blok „G3 semantyka OPEN” niżej jest historycznym snapshotem, nie kolejnym poleceniem. [Jeden master plan](AI_CALLS_MASTER_EXECUTION_PLAN.md) · [checklist G4](checkpoints/2026-11-09-g3-keep-e2b-final-decision.md).

## 2026-11-09 — aktualny handoff: G3 S22 96/96 TECH_PASS, semantyka OPEN

**Zamknięte:** G0 F-158 S22 24/24; G1 Qwen3.5 pin/sprzęt; G2 Qwen3.5 2B CPU S22 3/3 i Mac QA. **G3 fizyczne porównanie 96/96 tur na S22 PASS technicznie**: `aicalls-g3-e2b-qwen35-96turns-s22-20261109-v1` z 48 sesjami, 2 modelowymi summary i 48 prywatnymi parami JSONL na Mac. Oba modele: 24/24 sesji native/JSON/tools. E2B GPU: hint 15/15, cold/warm mediana 24,20/4,73 s, p95 28,89/7,29 s. Qwen3.5 CPU: 12/15, mediana 49,19/7,34 s, p95 54,50/11,21 s. Różne akceleratory i syntetyczne heurystyki **nie są dowodem semantycznej wyższości**. E2B pozostaje domyślna.

**Otwarta końcówka G3:** dokładny task formalnie `failed` wyłącznie przez `task_budget_exhausted`: pierwsze **3 komendy 0/PASS**, końcowy `git status` nie wystartował (wymagał timeoutu 6460 s przy pozostałych 5460,812 s), więc `G3_MAC_SOURCE_CLEAN` **NOT_RUN**. Model/natywna instrumentacja i prywatny eksport PASS. Urządzenie IDLE, bez Call, instalacja bez wipe. Ocenę naturalności/prawdziwości, rozkład 3 brakujących hintów oraz formalną decyzję KEEP_E2B/PROMOTE_QWEN/INSUFFICIENT_EVIDENCE traktować jako **OPEN**.

**Kontynuacja dokładnie w tej kolejności:** świeży HEAD i dokładny receipt → krótki odczytowy Mac clean postflight + lokalne 48-pair JSONL audit (bez tekstu poza Mac) → human semantic review i decyzja G3 → dopiero G4 per-turn Skills, G5, G6, G7. Nie powtarzać 96 tur. [Kanoniczny plan](AI_CALLS_MASTER_EXECUTION_PLAN.md) · [pełny checkpoint/handoff](checkpoints/2026-11-09-g3-e2b-qwen35-pending-host-receipt-handoff.md). Starsze rozdziały to historia.

## 2026-11-09 — Skills F-156 VERIFIED_SCOPED + fizyczne E2B/E4B 18 tur, Google Gallery research

**Ostatni zakres implementacji:** `ConversationInstructionBuilder` przenosi wersjonowane Skills po stałych ograniczeniach roli/uprawnień, tuż przed format odpowiedzi (commit `63e5b9c6483ebe4bd071e16148b588351e02de65`), bez zmiany grantów, call ownership lub STOP. `ConversationSkillLibraryTest` potwierdza kolejność ograniczenia → Skills → odpowiedź oraz pominięcie nieaktywnego Skill (commit `f55fea5`). Test AndroidTest edytuje rzeczywisty interfejs Skills przez izolowany zaszyfrowany debug-only store i przywraca wszystkie oryginalne dane. Google Gallery `Agent Skills` nie jest aktualnie zaimplementowane: to `SKILL.md`, metadata-based selection, lazy loading oraz aplikacyjne JS/native tools, **nie** „magiczny tryb” Gemmy. [Porównanie i plan](AGENT_SKILLS_EDGE_GALLERY_REVIEW_2026-11-09.md).

**F-156 RED → GREEN:** początkowo realna E2B ignorowała aktywny marker w 3 próbach; test położenia instrukcji `aicalls-f156-salience-prompt-s22-probe-20261109-v2` (DONE/0) znalazł `end_position=true`, `minimal=true`. Po fix `aicalls-f156-prompt-fix-native-s22-reaccept-20261109-v1` (DONE/0): `active_marker=true`, `inactive_marker=false`, natywna E2B **1/1 PASS**, rzeczywisty UI copy/edit/toggle/restore/reopen **1/1 PASS** na izolowanym szyfrowanym store, Call IDLE, brak wipe. Mac full gate i friction `aicalls-f156-f157-preflight-e4b-and-promptfix-host-20261109-v1` **DONE/0**. **F-156 VERIFIED_SCOPED**, nie ogólna gwarancja zachowania modelu.

**Gemma E4B jest już lokalnie obecna na S22**, 3 659 530 240 B, E2B 2 588 147 712 B, pinned size PASS. Nie pobierano niczego i nie zmieniano aktywnego wyboru E2B. `aicalls-f157-e2b-e4b-native-differential-s22-20261109-v1` **DONE/0**: pełny Mac host gate PASS + fizyczny AndroidTest 1/1 PASS — 2 modele × [aktywny/nieaktywny Skill] ×2 tury = **8/8** natywnych tur, GPU, kontekst 2048. Marker active/off, pamięć kodu, schemat JSON i tool none **PASS** dla obu modeli. Zimny E2B **12,30–14,38 s**, E4B **40,19–42,51 s**; warm E2B **2,16–2,31 s**, E4B **6,14–10,51 s**. Długości odpowiedzi różne; to nie tokens/s.

**Drugi test jakości:** `aicalls-f157-e2b-e4b-semantic-s22-20261109-v1` **DONE/0** (target AndroidTest build PASS, S22 1/1), E2B i E4B × [3 tury sprostowania +2 tury z cytatem vs rzeczywistym pożegnaniem] = **10/10 natywnych tur**. Oba: nowy kod zapamiętany, stary nie powtórzony, tool none dla cytatu, end_call zaproponowany dopiero przy rzeczywistym zakończeniu **PASS**. Zimny E2B **11,18–13,58 s** / E4B **35,37–38,78 s**, ciepłe E2B **2,13–2,74 s** / E4B **5,89–6,42 s**. Razem **18/18 tur**, ani jednej rzeczywistej akcji telefonicznej; Mac clean, Call IDLE.

**Decyzja robocza:** E2B pozostaje właściwą domyślną na S22 przy braku udowodnionej poprawy jakości z E4B (~3× wolniejsze). To niewielki, punktowy korpus; nie ogólna przewaga jakości E2B. Nie przełączać profilu ani nie usuwać E4B. [Dokładny checkpoint](checkpoints/2026-11-09-e2b-e4b-agent-skills-differential.md).

**Co dalej:** jedna nowa praca rozwojowa: eksperyment na **lazy/contextual Agent Skills** z istniejącymi wersjami/snapshotami i **oddzielną** typowaną kontrolą narzędzi; test A/B przeciw obecnemu promptowi na E2B, z pomiarem opóźnienia selekcji. Potem N≥3, p95, dłuższe fakty/korekty, ocena naturalności i ewentualna obsługa LiteRT MTP na S22. Live Call, efekty, pełne RX/TX nadal poza tym sprintem i wymagają osobnego grantu. Nie wracać do zaliczonych F-150–F-156 ani poprzednich testów bazowych bez nowej przesłanki.


## 2026-11-09 — F-155 VERIFIED: prawdziwa E2B i pomiar cold/warm, diagnostyka Lab na S22

`main@1a30d0aa48d6f58cab682cd265bb916988f8eb99` wprowadził **wyłącznie w Laboratorium** edytowalny budżet kontekstu (domyślnie 4096, dodatnia wartość liczbowa, zapamiętana na kolejną sesję), oraz widoczny panel `lab-model-diagnostics`: rzeczywista tożsamość profilu, GPU/CPU, engine initialization, conversation creation, generate, total, cold/warm, średnia generowania, liczba znaków, PSS procesu i wolna RAM. Lab `AndroidSkillLabSessionFactory` snapshotuje budżet na starcie; default Call nadal **2048**, bez zmian telefonii i backendu modelu. Typowany `NativeModelTiming` nie przenosi tekstu ani sekretów, tokens/s nie jest udawane.

**Rozstrzygnięcie skargi o latencję:** `aicalls-f155-latency-model-context-baseline-s22-20261109-v1` **DONE/0** — faktycznie `GEMMA_4_E2B` na **GPU**. Identyczne promptsy przy 2048: pierwsza 28570 ms (engine 12559, conv 3716, native generate 12285), druga **3318 ms**; przy 4096: pierwsza 27228 ms (engine 10885, conv 3798, native generate 12536), druga **3627 ms**. Oznacza to, że poprzednia średnia 16–23 s mieszała zimny start z kolejną ciepłą turą; w tej małej próbie zmiana 2048→4096 nie jest przyczyną opóźnienia. Nie dowodzi poprawy długości generacji ani reguły dla dłuższych odpowiedzi.

**QA:** Mac `aicalls-f155-lab-model-diagnostics-mac-gate-20261109-v1` **DONE/0**, full `verify_host.sh`/friction PASS; S22 `aicalls-f155-lab-diagnostics-s22-physical-20261109-v1` **DONE/0** — pinned app APK + AndroidTest APK bez wipe, `AndroidLabModelDiagnosticsNativeTest` 1/1, `AndroidSkillPresetMatrixTest` 2/2, `AndroidLabManualNativeOfflineTest` 1/1 = **4/4 PASS**. Model E2B/GPU i nowe ustawienie 2048 pokazane z rzeczywistą telemetrią w GUI; preferencja przywrócona w teście, STOP/restart PASS, Call IDLE. F-155 **VERIFIED**. [Checkpoint](checkpoints/2026-11-09-f155-lab-context-latency-diagnostics.md), [matrix](FUNCTIONAL_ACCEPTANCE_MATRIX.md).

**Kolejny zakres:** nadal **funkcjonalne SKILL-01/02/03**: edycja/aktywacja/wyłączenie własnych instrukcji poprzez UI i test wpływu na rzeczywistą odpowiedź Gemmy oraz separacji presetów. Nie powtarzać F-155/USB zamkniętych testów. 8192 i E4B nie zostały sprawdzone w tej sesji; F-148 długa semantyka i live Call intervention wymagają osobnej próby/grantu.


## 2026-11-09 — jeden przebieg S22 GUI/USB + Skills, fizyczne wyniki i punkt przejścia

**Baza kodu:** `main@4a03f4576c7e7ecc2dcde120065f249a96611831`, po naprawie AndroidTest-only `main@76f0a29ef8f89ad07afcbba4ba97351fc37602cd`. Pełny Mac host gate `aicalls-onepass-mac-build-uiusb-skills-20261109-v1` **DONE/0** (friction audit/Gradle/JVM/lint/Python/audio PASS). S22 `aicalls-onepass-s22-gui-usb-skills-instrument-20261109-v1`: **4/5 grup PASS**, jedna zależna od języka asercja starego testu; `F-154` RED wyizolowany (sztywna polska etykieta), naprawiony poprzez tagi UI i `aicalls-onepass-f154-locale-independent-skills-green-20261109-v2` **DONE/0, 2/2 PASS**. **Razem po naprawie 6/6 AndroidTest**: procesowa dzierżawa GUI vs USB controller 1/1, szyfrowany preset/UI 2/2, Gemma skill decision 1/1, adaptive native 1/1, manual STOP/restart 1/1. **F-150 (lease) i F-154 VERIFIED.** Nie twierdzić, że fizycznie wykonano jednoczesny ADB broadcast z GUI (kontroler wspólny, transport ADB odebrany oddzielnie).

USB `aicalls-onepass-s22-usb-call-negatives-skills-ab-20261109-v1` **DONE/0**: trzy rzeczywiste odmowy takeover/resume/stop-owned bez Call **3/3 PASS**; Gemma E2B 4096 `natural/formal/message` **3 sesje po 2 tury = 6/6**, prywatny zapis i STOP **3/3**, proste recall heuristic **3/3**; średnia latencja na turę ~23,15/17,49/16,81 sekundy. Telefon IDLE przed/po, bez jakiegokolwiek realnego Call, brak wipe/danych osobistych w task-results.

**Następny punkt = funkcjonalne AI Skills w aplikacji**, nie kolejny sprint USB: `SKILL-01/02/03` UI select/edit/activate/disable/revise, trwałość po wyjściu/restart, różnica w **rzeczywiście wygenerowanym** tekście na aktywnym vs nieaktywnym Skill, A/B z niezależnymi powtórzeniami i prywatną oceną odpowiedzi. Dotychczasowe 3 presety + prosty recall to techniczne PASS, **nie** dowód wpływu własnych instrukcji ani pełna semantyka. Real Call takeover/resume/exact-stop **NOT_RUN** — wymaga konkretnego upoważnienia aplikacji (numer/cel, konto/SIM, efekt, zakres danych). F-148 długa semantyka i 8192 nadal OPEN. **Nie powtarzać zamkniętych prób bez nowej przesłanki.** [Nowy pełny checkpoint](checkpoints/2026-11-09-single-pass-ui-usb-ai-skills-acceptance.md), [macierz](FUNCTIONAL_ACCEPTANCE_MATRIX.md), [BLOCKERS](../BLOCKERS.md).


## 2026-11-09 — USB Lab działa w tle; F-153 VERIFIED

**Nie wymagać AI Calls ani Launcher na pierwszym planie przy off-call USB.** Dotychczasowy guard był nadmiernie ograniczający; kanał to DUMP receiver + prywatna skrzynka przez ADB `run-as`, nie UI. Wymagać S22/ADB, Call IDLE, braku kampanii/aktywnego Live AI i konfliktującego Lab. `aicalls-usb-lab-background-two-turn-20261109-v3` **DONE/0**: E2B 4096 `lab-start`, 2× `lab-send`/2× `INPUT_READY` i rosnące `turns=1,2`, prywatny `lab-save` 2 tur na Mac, `lab-stop` `ok=true`, post-IDLE, Mac clean. **F-153 VERIFIED**; Mac full gate poprzednio PASS. Poprzedni REFUSED przy PocketHost był ograniczeniem testu operatora, nie Androida. **Nadal OPEN:** F-150 równoległy GUI+USB, F-148 semantyka, dodatni Call takeover/resume/exact STOP, pełny NAV back-stack/restart. Nie wykonywać realnego Call bez osobnej precyzyjnej zgody. [Checkpoint](checkpoints/2026-11-09-usb-lab-background-two-turn-f153-verified.md).


## 2026-11-09 — USB Lab physical acceptance / F-152 VERIFIED, F-153 host PASS

Na S22 zainstalowana aktualna debug APK i AndroidTest (bez wipe). **USB receiver kontrakt Android 3/3 PASS**; prawdziwe CLI `status/config-get/ram` **3/3 PASS** po poprawie cytowania polecenia ADB (`F-152 VERIFIED`, Mac full gate + fizyczny receipt `aicalls-usb-f152-s22-cli-reacceptance-20261109-v1` DONE/0). Próba USB E2B 4096 `lab-start` wykonała START, ale SEND nie dotarł z powodu przejętego stdin przez pomocnicze `adb shell`; STOP zadziałał, Call IDLE. **F-153 FIXED_IN_SOURCE/HOST_PASS**, full Mac gate `aicalls-usb-f153-mac-host-qa-20261109-v1` DONE/0; kolejny device trial `...two-turn-native-s22-20261109-v2` **REFUSED przed START**, bo PocketHost był foreground (potwierdzono read-only). Brak dwóch tur USB modelu i brak zatwierdzenia F-153 na urządzeniu. Nie obchodzić odmowy i nie przerywać PocketHost. Bez rzeczywistego CALL. [Dokładny checkpoint](checkpoints/2026-11-09-nav01-f151-usb-f152-reacceptance.md), [macierz](FUNCTIONAL_ACCEPTANCE_MATRIX.md), [BLOCKERS](../BLOCKERS.md). Gdy ekran AI Calls/Launcher wolny, retry tylko prywatne 2-turn USB i STOP, potem ewentualnie odrębne upoważnione Call.


## 2026-11-09 — F-151 VERIFIED, fizyczny NAV-01 10/10 scoped

**Nowy kod testu:** `main@b501768`; F-151: starszy `AndroidAppNavigationContractTest` oczekiwał nieistniejącego już wejścia `PhoneEnrollmentActivity`, zamiast `IdentityVaultManagerActivity`. RED S22 4/5, po naprawie **Mac full gate DONE/0** (`aicalls-nav01-f151-targeted-mac-build-20261109-v1`) i **S22 DONE/0** (`aicalls-nav01-f151-s22-reacceptance-20261109-v1`): aplikacja i AndroidTest bez wipe, kontrakt **5/5**, przepływy **2/2**, ustawienia **3/3**, łącznie **10/10 PASS**, Call IDLE i Mac clean. **F-151 VERIFIED. NAV-01 PARTIAL** tylko dlatego, że pełne powtarzane przejścia/back-stack i restart są poza testem; nie duplikować 10 zaliczonych przypadków. Nowy receiver USB nie ma fizycznego dowodu funkcjonalnego; `dumpsys package` nie wykazał nazwy odbiornika, co nie jest wystarczającą weryfikacją jego rejestracji. **USB DEVICE_QA_OPEN**, bez połączeń. [Checkpoint](checkpoints/2026-11-09-nav01-f151-reacceptance.md), [macierz](FUNCTIONAL_ACCEPTANCE_MATRIX.md).


## Najnowszy scoped NAV-01 S22 (2026-11-08)

Na podłączonym S22 (Call IDLE, kampania nieaktywna) istniejący `AndroidAppBottomNavigationFlowTest` przeszedł **2/2 PASS** (5 zakładek docelowych z Home + Settings/General/Back), receipt `aicalls-nav01-safe-device-run-20261108-v2` DONE/0. Oddzielny `AndroidAppNavigationContractTest` miał **5 testów / 1 FAIL**, ale nazwa asercji nie została uchwycona; **nie ogłaszać defektu źródła bez izolowanej reprodukcji**. Test Settings matrix nie wystartował, bo dalszy foreground przeszedł na `NotificationShade`/Dozing. **NAV-01 nadal PARTIAL.** Nie instalowano nowej APK, a zainstalowany USB receiver nadal nieobecny. [Nowy checkpoint](checkpoints/2026-11-08-nav01-existing-apk-reacceptance.md) i [macierz](FUNCTIONAL_ACCEPTANCE_MATRIX.md). Następny krok: odblokowany ekran AI Calls/Launcher, pin APK/test revision, uchwycić dokładną awarię kontraktu, potem brakujące Back/restart. Bez prawdziwego Call.

## Aktualny checkpoint USB/ADB i Laboratorium (2026-11-08)

**Kod przed dokumentacyjnym checkpointem: `main@af571f0`; aktualny HEAD sprawdź przy wznowieniu.** Nowy kanał USB do Lab i telefonii **SOURCE_PASS / MAC_QA_PASS** (370 Python, Gradle/JVM, AndroidTest build, lint, audio), ale **nowa APK na S22 NOT_INSTALLED/NOT_RUN**, a pozytywny test takeover/resume/exact STOP przez USB **NOT_RUN**. Sprzętowego zadania interwencji nie opublikowano z powodu blokady wykonania; nie używać obejścia. E2B 4096: **3×13 tur PASS technicznie**, human semantic **UNASSESSED**. E2B 8192 na S22 **NOT_RUN**. Rejestr: F-148 OPEN, F-149 VERIFIED (poprawka lint), F-150 FIXED_IN_SOURCE/HOST_PASS/DEVICE_OPEN.

**Źródło prawdy:** [kompletny checkpoint](checkpoints/2026-11-08-usb-lab-telephony-full-checkpoint.md) · [USB Lab i telefonia](USB_LAB_TELEPHONY_CHANNEL.md) · [macierz odbioru](FUNCTIONAL_ACCEPTANCE_MATRIX.md). Starsze niżej wpisy mogą zawierać stan PENDING/NOT_RUN aktualny tylko w chwili danej próby; nie są najnowszą decyzją.

**Zabezpieczenie współbieżności 2026-11-08:** `main@fdb003d` w `AndroidSkillLabSessionFactory` rezerwuje pojedynczy backend LiteRT dla GUI i USB; F-150 **HOST_PASS / DEVICE_QA_OPEN**, gate Mac `aicalls-usb-lab-shared-native-lease-mac-qa-20261108-v4` **DONE/0** (370 Python, AndroidTest APK/lint). Odbiór S22 kanału nadal `NOT_RUN`. [Specyfikacja USB](USB_LAB_TELEPHONY_CHANNEL.md).

## USB/ADB — prywatny kanał Lab + telefonia (2026-11-08)

Zaimplementowano odbiornik `UsbLabControlReceiver` (DUMP + debug-only, bez otwierania UI), Mac CLI `scripts/aicall_tools/diagnostics/ai_calls_usb_lab.py` i `AndroidUsbLabControlContractTest`. Status, PSS/monitoring, konfiguracja profilu i incoming, prywatna sesja E2B START/SEND/STOP/transkrypt, takeover/AI resume/exact outgoing STOP przez istniejące kontrolery. **Host PASS:** `aicalls-usb-live-config-contract-mac-qa-20261108-v3` DONE/0, 370 testów Python, Gradle/JVM/AndroidTest/lint/APK/audio PASS, checkout clean. F-149 lint **VERIFIED**. **DEVICE NOT_RUN:** próba przekazania taska sterowania S22 została odrzucona przed wykonaniem; nie było instalacji ani testu takeover. Bez pozytywnej fizycznej próby nie twierdzić PROVEN_S22. Legacy USB export działa niezależnie. [Pełna specyfikacja i komendy](USB_LAB_TELEPHONY_CHANNEL.md).

## E2B context validation — 4096 N=3 passed, 8192 guarded (2026-11-08)

Physical S22 Gemma E2B Lab **4096: three independent 13-turn runs (39/39 turns) PASS technically**, without JNI failure. Sampled peak app PSS **2,348,103 kB**, lowest observed MemAvailable **1,378,220 kB**, mean response approx. 5.35–7.63 s. Heuristics across 3 sessions: **11/13, 13/13, 11/13**; **human semantic review UNASSESSED**, F-148 remains OPEN for root-cause/semantic scope.

Experimental **8192 only through explicit AndroidTest flag** at code `main@faf6ee5` passed the full Mac host gate, but **physical S22 8192 NOT_RUN**: guard refused because PocketHost was foreground. No 8192 inference or install occurred. Normal Lab 4096, Call 2048 unchanged. Next: only gated S22 8192 trial when AI Calls/Launcher is foreground, then separate fact-memory ledger sprint.

[Exact benchmark checkpoint](checkpoints/2026-11-08-e2b4096-n3-ram-and-8192-instrumentation.md) · [memory plan](LAB_E2B_CONTEXT_MEMORY_PLAN.md).

## LAB-SEMANTIC-02 — wynik 4096, próba 13/13 (2026-11-08)

Po dwóch powtarzalnych porażkach JNI przy 2048 tokenach w turze 10 wdrożono wariant **4096 tylko w Laboratory** (`main@f38b5f4`); inne ścieżki Call nadal domyślnie 2048. Mac pełna bramka `scripts/verify_host.sh` PASS (task końcowo failed/125 w dodatkowym etapie), S22 `aicalls-semantic2-s22-context4096-20261108-v1` **DONE/0, natywna E2B 13/13 tur PASS technicznie**, 11/13 heurystyk (2 niepotwierdzone punkty dotyczące nieznanego numeru), średnio 6686 ms, 0 END_CALL, IDLE. Dodatkowe powtórki N=2/3 **nie wystartowały** (guard REFUSED). **F-148 OPEN** do N=3 i pełnej oceny semantycznej; nie twierdzić 3/3.

[Dokładny checkpoint](checkpoints/2026-11-08-lab-semantic-02-long-dialogue-f148.md).

## LAB-SEMANTIC-02 — dłuższy dialog: F-148 OPEN (2026-11-08)

Nowy scoped AndroidTest (dwie kartoteki, wielokrotne korekty, zgadywanie numeru, skutki) przeszedł pełny Mac gate na kodzie `8768f72`, ale **pierwsza prawdziwa sesja S22 przerwała się w turze 10 z `LiteRtLmJniException`**; 9 tur wygenerowano poprawnie, sesje 2/3 nie startowały. Podejrzenie: okno natywnego kontekstu 2048 tokenów, **niepotwierdzone**. Zarejestrowano `F-148 OPEN`; w `main@68e848d` dopisano prywatny zapis częściowych dowodów, ale jego **Mac gate i S22 reacceptance są QA_PENDING/NOT_RUN**. Nie podnosić globalnie token budget ani nie zaliczać semantyki bez surowej prywatnej oceny.

[Checkpoint i dowody](checkpoints/2026-11-08-lab-semantic-02-long-dialogue-f148.md).

## LAB-SEMANTIC-01 — 2026-11-08, rzeczywista Gemma 4 E2B, N=3

Na S22 `SM-S906B` przez ADB/AndroidTest przeprowadzono **trzy niezależne siedmioturowe rozmowy z lokalną `GEMMA_4_E2B`**. Wszystkie 21 tur zakończone, pamięć kodu po zmianie tematu, sprostowanie koloru, końcowa pamięć oraz przyznanie braku nieznanego numeru **PASS 3/3 w kontrolowanej próbie**; średnia odpowiedź ~4,04 s, 0 END_CALL, telefon IDLE. Source test `main@4a3c49b`, full Mac gate PASS; `aicalls-semantic-native-s22-seven-turn-20261108-v1` i `aicalls-semantic-native-s22-repeats-20261108-v2` DONE/0. Każda sesja zapisała prywatny transkrypt wyłącznie w Android `noBackupFilesDir`. Pełna semantyczna ocena człowieka i dowód ogólnego braku halucynacji nadal OPEN. Próba powtórki v1 była bezpiecznie zablokowana przez Launcher, v2 dopuszczała zweryfikowany Launcher i przeszła 2/2.

[Receipts, kryteria i granice dowodu](checkpoints/2026-11-08-lab-semantic-01-gemma-e2b-three-sessions.md).

## LAB-DIALOG-01 — najnowszy odbiór S22, 2026-11-08

**Funkcjonalny kod `992280b`, S22 ACCEPTED:** `aicalls-labdialog01-s22-preflight-20261108-1558-v1` DONE/0 (AI Calls i fokus foreground, Call IDLE, clean Mac); `aicalls-labdialog01-s22-final-device-acceptance-20261108-1601-v1` **DONE/0**: aktualne APK i AndroidTest zainstalowane bez wipe, **2/2 isolated storage + pinned composer/scroll UI PASS**, **1/1 native Gemma adaptive answer-driven PASS**, na końcu Call `IDLE`, Mac clean. Wcześniejszy osobny native manual 2-turn/STOP/reset 1/1 też PASS. Pełny Mac source quality gate kodu `aicalls-labdialog01-layout-final-mac-20261108-v1` DONE/0 (365 Python/JVM/lint/APK/audio).

**Dalsze niezależne punkty:** klikalny ekranowy CRUD edytora, prawdziwa semantyczna ewaluacja pamięci/korekt/faktów, takeover w automatycznym dialogu, A/B N=3 i F2 STT/TTS/PCM — nadal OPEN. Nie wracać do czterech odrzuconych fabrycznych historii; nie wykonywać połączeń telefonicznych.

[Checkpoint i receipts](checkpoints/2026-11-08-lab-dialog-01-implementation-checkpoint.md).

## Historia — wcześniejszy status BLOCKED przed wznowieniem odbioru

- **Mac SOURCE/HOST PASS:** `aicalls-labdialog01-layout-final-mac-20261108-v1` DONE/0: 365 Python, JVM, lint, obie APK, audio audit, czysty HEAD. Dodatkowo adaptacyjne AndroidTest skompilowane, nowy układ panelu/transkryptu i stały STOP/composer.
- **S22 manualny tekst i magazyn PASS (na `e41423e`):** realna Gemma 2 tury, STOP, reset 1/1 oraz izolowany szyfrowany store + kontrolki UI 2/2; nie powtarzać bez potrzeby.
- **S22 adaptive i nowy układ BLOCKED:** oba guarded receipts (`aicalls-labdialog01-s22-adaptive-native-20261108-v1`, `...-adaptive-layout-guarded-20261108-v2`) wykazały `PocketHost` jako obcy foreground przy `IDLE`. Nic nie instalowano ani nie uruchomiono na telefonie w tych próbach. Nowa APK z `992280b` **nie jest odebrana na S22**.
- **Dalej:** po udostępnieniu foreground tylko scoped `AndroidLabDialogStorageAcceptanceTest` 2/2 + `AndroidLabAdaptiveNativeOfflineTest` 1/1, potem prywatny przegląd korekt/pamięci; semantyka/A-B N=3/audio F2 oraz klikalny UI CRUD pozostają osobnymi OPEN.

[Checkpoint aktualny](checkpoints/2026-11-08-lab-dialog-01-implementation-checkpoint.md).

## Historia — LAB-DIALOG-01, poprzedni odbiór tekstu i magazynu

Baza `main@4eae72c`, bieżący commit funkcjonalny `main@6f77401b79a4b2a27dc94a65b591645539e0298d` (pobierz świeży HEAD). A–D **SOURCE IMPLEMENTED**: manualny ciągły chat w `SkillLabRunner`, edytowalne/wersjonowane testy w szyfrowanym store, reguły adaptacyjne i reporty tur z oceną niezależną od END_CALL oraz porównaniem A/B. Model pobierany z wybranego `AudioEngineProfile`, aktywna wersja Skills z presetu, rola i cel z testu. Czterech usuniętych historii nie przywracać.

**QA aktualne:** `aicalls-labdialog01-final-source-gate-20261108-v3` DONE/0 na `e41423e` — full Mac gate PASS (365/365 Python + JVM/lint/APK/AndroidTest/audio; clean checkout). `aicalls-labdialog01-s22-readonly-20261108-v1` DONE/0 (SM-S906B, Launcher, Call IDLE). `aicalls-labdialog01-s22-device-acceptance-20261108-v1` DONE/0 — 2/2 izolowany Keystore/Report/UI i 1/1 realny LiteRT manualny 2-turn/STOP/restart na S22 PASS, instalacja APK zachowała dane, Call IDLE po testach. To nie jest semantyczne PASS ani próbka audio.

Wymagane dalsze potwierdzenia: **semantyczna ocena pamięci i korekt** z prawdziwego transkryptu, pełny ekranowy CRUD przez kliknięcia (na izolowanym magazynie), rzeczywisty adaptacyjny dialog na S22 z różnymi odpowiedziami modelu i prywatne screenshoty; także A/B z powtarzaniem N=3. Ręczny rubric celowo `UNASSESSED` do wpisania dowodu. STT/TTS/PCM i takeover podczas auto-rozmowy to osobne OPEN. Wcześniejsze testy TEXT/STOP i zaszyfrowanej przechowalni już PASS — nie powtarzać bez nowej przesłanki.

[Checkpoint](checkpoints/2026-11-08-lab-dialog-01-implementation-checkpoint.md).


## Historia — zatwierdzona kolejność wdrożenia (sprzed implementacji)

Plan zatwierdzony przez właściciela 2026-11-08: [LAB_DIALOG_IMPLEMENTATION_PLAN](LAB_DIALOG_IMPLEMENTATION_PLAN.md). **A: ręczna rozmowa tekstowa z prawdziwą lokalną Gemmą w jednej sesji i STOP; B: edytor własnych testów i wersje; C: rzetelna ocena tur oraz pamięci/korekt/halucynacji; D: adaptacyjny rozmówca zależny od odpowiedzi; E: A/B i polskie audio.** Unikać ponownego zakodowania stałych historii, stworzenia kopii `SkillLabRunner`, fałszywego `0/0 PASS` oraz publikacji danych. `main@d1bcf2a` ma pełny Mac gate PASS (365/365 Python, JVM, APK, lint, audio; task `aicalls-lab-four-stories-removal-regression-20261108-v2` DONE/0) i clean checkout. To historyczny zapis planu przed wdrożeniem; aktualny status jest wyżej.

## Lab — decyzja o usunięciu 4 sztywnych historii (2026-11-08)

Właściciel odrzucił **wszystkie** 4 fabryczne historie Laboratorium, wyraźnie zabronił zachowywania ich jako regresji. Kod `SkillLabCorpus.stories` ma teraz pusty zestaw; `SkillLabRunner.start()` odrzuca brak przypadków, UI pokazuje PL/EN jasny status i nieaktywny START bez fałszywego 0/0. Stary opcjonalny test `AndroidSkillLabRealOfflineTest` jest oznaczony `@Ignore`, a porównanie presetów na odrzuconym korpusie usunięte. **Testy techniczne START/STOP/timeout** używają wyłącznie nowych, neutralnych fixture w kodzie testów, nie starych rozmów. Najwyższy priorytet następnego sprintu: **edytor testów + manualny rozmówca + adaptacyjne prowadzenie rozmowy + metryki śledzenia wątku**. Patrz `docs/ROADMAP.md` / `docs/checkpoints/2026-11-08-lab-four-scripted-stories-removed.md`. Dawne wyniki Lab/END_CALL pozostają tylko historycznym zapisem starego kodu, nie bieżącą akceptacją. Real CALL bez autoryzacji nie uruchamiać. **Nowe testy urządzenia i instalacja APK z usuniętymi historiami wymagają osobnego QA i wolnego ekranu S22.**

**Data:** 2026-11-08. **Bazowy commit kodu:** `main@edf63c1ea9dc425c91d2a1571a90038631852ffd` (UX-11 CLOSED). Kolejne commity tylko dokumentacyjne nie stanowią nowego QA APK. **Przy wejściu zawsze pobierz świeży HEAD**, sprawdź Mac checkout/clean status i stan Samsung S22.

## F1 — aktualny status po realnych testach urządzenia

- **`main@6b2d813` (kod testowy)**: Mac clean po ff-sync, AndroidTest build PASS.
- S22 `aicalls-f1-s22-fixture-functional-20261108-v2`: **3/3 device PASS** — isolated Vault 4-type CRUD/encryption/reopen, Scenario personal create/revise store, Skills edit/version/bind rollback. Dane właściciela nienaruszone.
- S22 `aicalls-f1-gemma-native-direct-audio-s22-20261108-v2`: **1/1 synthetic PCM AudioBytes native PASS** (~9.3 s), memory/thermal/Call IDLE. **Nie** jest to proof polskiej mowy ani aktywacji `GEMMA_AUDIO_DIRECT`. `v1` nie wykonało testu z powodu nieobecnego narzędzia `timeout` na Macu.
- `AndroidCustomVaultUiAcceptanceTest` dodany i **COMPILE PASS**, urządzeniowa instalacja/test **NOT_RUN**: inna aplikacja `pl.pockethost.companion` przejęła foreground (`aicalls-f1-s22-foreground-after-ui-test-block-20261108-v1`); nie przejmować ekranu. Kolejność kolejnej sesji: ekran UI Sejfu, potem Skills UI, Scenariusze UI, następnie kontrolowane polskie PCM do Gemmy i ewaluacja transkrypcji. Nie rozpoczynać Call.
- [Pełny checkpoint](checkpoints/2026-11-08-f1-s22-device-functional-core-and-gemma-direct.md). **NAV-01 PARTIAL**, UX12 manualne PL/EN nadal OPEN, ale nie blokują badań funkcjonalnych.

## F1 — odbiór właściwy, aktualizacja 2026-11-08

Właściciel wyraźnie priorytetyzuje **Skills → Sejf → własne scenariusze → wejście PCM bezpośrednio do Gemma**, bez czekania na wizualny NAV-01/UX12. Repo `main@6a6d2a1`: nowy izolowany test AndroidTest CRUD custom vault (4 typy, szyfrowanie, reopen, walidacja, delete). Mac `aicalls-f1-custom-vault-test-compile-20261108-v1` **DONE/0, Kotlin AndroidTest COMPILE PASS**. Mac checkout zaktualizowany. Próba fizyczna `aicalls-f1-s22-isolated-skill-vault-scenario-20261108-v1`: AndroidTest APK BUILD PASS, jednak instalacja/testy NOT_RUN, bo foreground przeszedł z Launchera na ShellyLink; `aicalls-f1-s22-recheck-why-install-fenced-20261108-v1` DONE/0 (ADB, IDLE, ShellyLink). Device `aicalls-f1-corepreflight-20261108-v1` **FAILED/1**, ponieważ `RFCT70L7E8J` nie był wykryty przez ADB. Żadna nowa próba device functional PASS. Existing tests Skills i personal scenario są gotowe; nie używać rekordów właściciela. Gemma Audio Direct ma test natywny, lecz w registry `integrated=false`, brak S22 potwierdzenia i aktywacji. [Checkpoint](checkpoints/2026-11-08-f1-owner-priorities-device-unavailable.md).

## UX-12 — ostatni ograniczony sprint UX przed funkcjonalnym odbiorem

- **Kod:** `47bdba6d403611ea0611a3fc4f50dc7e3843c278` (dwa commity: `ac6516b`, `47bdba6`). Historia: kontekst sesji w szczegółach. Audyt: niezależny walidator JPEG 1–3/ekran + prywatny ZIP i izolacja `uiAuditRunId`. Bez zmian Telecom, STT/TTS, modeli, danych sejfu, runtime.
- **Dowód Mac:** `aicalls-ux12-targeted-build-20261108-v1`: **7/7 collector, 2/2 UX12, 5/5 UX09, friction PASS**. Pierwsza bezpośrednia Gradle bez `ANDROID_HOME` przerwana konfiguracją SDK; docelowy `aicalls-ux12-full-host-gate-20261108-v1` zakończony **done/0**: **365/365 Python, JVM, lint, aplikacja+AndroidTest APK, audio audit, `host_quality_gate_green=true`**.
- **Archiwalne fizyczne zdjęcia:** `aicalls-ux12-validate-prior-real-s22-jpegs-20261108-v1` DONE/0: **17/17 JPG UX11** sprawdzonych nowym parserem, nowy prywatny ZIP tylko na Mac. To nie jest odbiór wizualny kodu UX12.
- **S22:** `aicalls-ux12-s22-gate-readonly-20261108-v1` DONE/0 — `RFCT70L7E8J`, `IDLE`, foreground **PocketHost**. Nowych AndroidTest/screenshotów nie uruchomiono, aby nie zaburzyć drugiej aplikacji. Poprzednie wyniki UX11 28/28 + 2/2 zachowane, nie należą do nowego commitu.
- **Dalszy krok:** kiedy S22 jest wolny, targeted `AndroidUx09VisualSemanticsTest` oraz bezpieczne `AndroidVisualScreenAuditTest` z jednorazowym `uiAuditRunId`, prywatny `adb pull` i `ui_screenshot_inventory.py --run-id ... --archive ...`. Fizyczne długie teksty PL/EN dla F-131 i nowe ujęcia F-136 pozostają OPEN. **Następny obszar prac to funkcjonalna macierz, nie dalsze kosmetyczne poprawki.** [Checkpoint](checkpoints/2026-11-08-ux12-final-source-closeout.md).

## Wznowienie odbioru 2026-11-08 — bieżące potwierdzenie

- Aktualny przed odbiorem `main@0e7dd43244edd6c22ce398490806c4a70508d487`, czysty Mac checkout = `origin/main`; GitHub Connector + Mac Local Agent, bez zmiany kodu/APK.
- Read-only tasks **done/0**: `aicalls-fa-preflight-s22-20261108-1018-v1`, `aicalls-fa-models-roles-services-readonly-20261108-v1`. S22 USB/ADB `SM-S906B`, APK 0.2.0, `IDLE`, potrzebne uprawnienia granted; app dialer role, Shizuku process, kampania nieaktywna, profile `ANDROID_STT/GEMMA_4_E2B/ANDROID_TTS_PL`. Cztery modele mają oczekiwany **rozmiar**, nie nowy SHA ani inferencję.
- **NAV-01 PARTIAL**: fizycznie potwierdzone Start → Historia → Agent AI → Laboratorium i właściwe zaznaczenia; tap w Modele nie dał jednoznacznego wyniku. `aicalls-fa-nav01-foreground-lockscreen-check-20261108-v1` wykazał `pl.pockethost.companion/.MainActivity` jako jedyny foreground UI. Modele/Ustawienia/Back/stos/restart NOT_RUN. Nie tworzyć nowego blokeru aplikacji bez izolowanej reprodukcji; nie zakłócać PocketHost.
- Ślady prób: `aicalls-fa-nav01-device-six-tabs-20261108-1023-v1` failed assertion (nie PASS), `aicalls-fa-nav01-android-transport-ui-diagnostics-20261108-v1` done/0, `...tap-diagnosis...v1` invalid task timeout, `...v2` ADB failure, `...models-tap-repro...v1` brak AI Calls w foreground. Dowody pozostają w oryginalnych immutable receipts.
- Brak real CALL i nowych testów offline; wcześniejsze UX11/356/30/offline STT/model/TTS pozostają w oryginalnym zakresie. Bez modyfikacji właścicielskich danych. F-080/F-122/F-123/F-124 nadal OPEN. Kontynuować brakujące NAV-01 tylko przy bezpiecznym wyłącznym foreground AI Calls, potem NAV-02 i HOME bez telefonii.
- [Dokładny checkpoint](checkpoints/2026-11-08-functional-nav01-preflight-interrupted.md).

## Cel kolejnego czatu
Przejść przez **każdy ekran i każdą dostępną funkcję**, potwierdzając nie tylko wizualny stan UI, lecz rzeczywiste zachowanie, zapis, STOP/recovery i konsekwencje. [Macierz 6 zakładek + ekranów podrzędnych](FUNCTIONAL_ACCEPTANCE_MATRIX.md). Pracować etapami, aktualizować macierz, bez dublowania już zaliczonych testów. Przed realną telefonią osobny preflight i dokładny autoryzowany cel.

## Co jest faktycznie potwierdzone
- **UX-11:** Mac `aicalls-ux11-final-mac-gate-20261108-v2`: **356/356 Python** i pełny host gate PASS na `949a854`. Zmiana `3029034` dotyczy tylko timing asercji AndroidTest i ma oddzielny targeted build PASS.
- **S22:** `aicalls-ux11-final-s22-guarded-synthetic-20261108-v2`: **28/28 UI PASS**; pierwotny pomiar pływającego panelu 2/2 FAIL, poprawiony. `aicalls-ux11-s22-synthetic-layout-and-private-archive-20261108-v1`: **2/2 synthetic floating PASS**, 18 prywatnych screenshotów, IDLE przed/po.
- **Funkcje offline na S22:** `aicalls-functional-real-offline-two-turn-stop-s22-20261108-v1`: lokalny model, 2 tury + STOP **PASS**; `aicalls-functional-polish-tts-parakeet-offcall-s22-20261108-v1`: polski TTS → Parakeet STT **PASS**. Żadnego faktycznego CALL.
- Pełne źródła i granice: [checkpoint 2026-11-08](checkpoints/2026-11-08-pre-functional-full-checkpoint.md). Nie traktować tych dowodów jako pełnej akceptacji ekranu, Call, Audio Evidence lub CRUD sejfu.

## Co pozostaje otwarte
- Funkcjonalna macierz: NAV, HOME/CAMP, HIST, Agent AI (Skills, własne scenariusze, Profile, Sejf, Narzędzia), LAB, MODELS/AUDIO, SETTINGS/Incoming/USB i przekrojowe powroty/restarty/zgody.
- Faktyczne RX/TX, audio po przejęciu, status owned Call, niezależny rezultat zadania: **NOT_RUN**.
- Trwałe defekty: **F-080/F-122/F-123/F-124** w [BLOCKERS](../BLOCKERS.md). Custom vault: ekran/źródło zweryfikowane, **CRUD funkcjonalnie NOT_RUN** i brak automatycznego ujawniania AI.
- Prywatne archiwa tylko na Macu:
  - `~/Library/Application Support/AI-Calls/UI-audit/2026-11-08-ux11/AI-Calls-S22-UX11-top17-plus-chat-private.zip`.
  - `~/Library/Application Support/AI-Calls/functional/2026-11-08-lab-offline/AI-Calls-S22-Functional-Offline-Lab-private.zip`.

## Tryb i procedura
1. GitHub Connector + **Mac Local Agent** (host `macbook-michal`, kontrola w `MichalMatu/local-agent:agent-control`); żadnego Codex, Work, GitHub Actions ani sprawdzania ich statusów. `main`, `[skip ci]`, bez nowych gałęzi/force-push.
2. Odczytaj `AGENTS.md`, `README.md`, ten handoff, `FUNCTIONAL_ACCEPTANCE_MATRIX.md`, `ROADMAP.md`, `ARCHITECTURE.md`, `SECURITY_PRIVACY.md`, `AUTONOMOUS_OPERATION_MODE.md` i checkpoint. **Nie proś użytkownika o stan, który da się pobrać z narzędzi**.
3. Wykonaj bezpieczny preflight telefonu: dokładny model, `IDLE`, uprawnienia, aktywny Call/usługa, wersja APK / wersja modelu; oszczędnie uruchamiaj istniejące testy.
4. Zacznij `NAV-01`, potem Start bez realnego połączenia. Zapisuj wynik każdego ID jako `PASS_UI_SYNTHETIC`, `PASS_OFFCALL_FUNCTIONAL`, `PASS_DEVICE_FUNCTIONAL`, `PASS_REAL_CALL`, `FAIL`, `BLOCKED` lub `NOT_RUN` — bez nadużywania PASS.
5. Na defekt: reprodukcja, `BLOCKERS.md`, minimalny fix, dokładny red/green, aktualizuj dokumentację i macierz. Dane z testów izolowane; nie dotykać prywatnego sejfu i nie publikować WAV/JPG/transkryptów.
6. Prawdziwe połączenie dopiero przy konkretnie przyjętym upoważnionym zadaniu, z aktualnym stanem urządzenia i faktycznym owned Call. STOP, Android/Telecom uprawnienia, prywatność i `PCM/PFD` freeze zachować.

**Gotowy startowy tekst:** [NEXT_CHAT_PROMPT](NEXT_CHAT_PROMPT.md). **Nie doklejaj historycznych handoffów ani statusów QA_PENDING starszego kodu — są zachowane w checkpointach/Git.**
