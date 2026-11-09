# AI Calls — bieżąca roadmapa funkcjonalna

## SINGLE_TRACK_OWNER — JEDEN CZAT OD 2026-11-09

Decyzja właściciela: zakończyć oba stare czaty i odtąd rozwijać AI Calls z **jednego nowego czatu**. Kanoniczny punkt startowy: [HANDOFF_SINGLE_TRACK_2026-11-09](HANDOFF_SINGLE_TRACK_2026-11-09.md), jeden plan poniżej, pełna referencyjna analiza źródeł: [EXTERNAL_REUSE_AUDIT_2026-11-09](EXTERNAL_REUSE_AUDIT_2026-11-09.md). **Nie rozpoczynać dalszych równoległych sprintów**. Nowy czat zaczyna od najświeższego HEAD i nierozliczonych prac, bez resetu działającego kodu/testów.

**Zachować:** Gemma 4 E2B/GPU, CONTEXTUAL Skills, Android SIM/Samsung/Shizuku, STT/TTS, STOP, manual takeover, grant/CallCommitmentGate/IdentityVault, prywatną historię. **Adaptować celowo:** Pipecat (G6.1 turn/interruption), call-use (G6 IVR + outcome), Clinic (G6 booking ideas, nie kod bez licencji), DeskRoute (G7 historia/UX bez kopiowania AGPL), LiveKit (późniejszy SIP transport). [Handoff i źródłowe moduły](HANDOFF_SINGLE_TRACK_2026-11-09.md). Nie przenosić Python/Go/TypeScript jako drugiego runtime Android.

**Testy:** nie powtarzać G3 96 tur, G4, G5.3 18 tur/36 WAV, S22 STOP, Mac 386 Python bez powodu; powtórzyć tylko nową lub dotkniętą regresyjnie ścieżkę. Właściciel pozytywnie ocenił rozmowę, lecz 0/6 prywatnych per-session ocen — nie oznaczać fikcyjnego human PASS. G6 live INCOMING→OUTGOING wymaga konkretnej autoryzacji, G6 E2E NOT_CLOSED.


## Jeden zintegrowany plan G6 + reuse gotowych projektów (2026-11-09)

**Obowiązuje JEDEN plan: ten dokument.** Audyt pięciu projektów, piny i mapa gotowych kontraktów: [EXTERNAL_REUSE_AUDIT_2026-11-09](EXTERNAL_REUSE_AUDIT_2026-11-09.md). Nie przenosić całego Pipecat/LiveKit/call-use/Clinic/DeskRoute do Androida; tylko sprawdzone brakujące kontrakty. HISTORYCZNY podział czatów zakończony: SINGLE_TRACK_OWNER, tylko nowy czat. Nie ponawiać 96 G3 tur, 18 G5.3 tur, natywnego STOP ani pełnych historycznych prób S22 bez zmiany ich kontraktu. Nowe testy dotyczą tylko nowych funkcji i rzeczywistego G6 INCOMING, następnie OUTGOING; pozostałe kanały WhatsApp/SIP/VoIP i dashboard opcjonalnie po G6.

**Ocena właściciela:** pozytywna ogólna ocena rozmowy została wyrażona, więc prace projektowe G6 mogą postępować. 6 prywatnych human-review JSON i 48 ocen wciąż nie ma — nie wpisywać im PASS i nie udawać odsłuchu. Realny Call G6 wymaga oddzielnej konkretnej autoryzacji i właściwego źródła dowodu. Dotychczasowe historyczne live A8→S22 INCOMING/end_call są dowodem zawężonym, nie pełnym PASS G6.


## G5.3 — Skills A/B 6 sesji, native reuse i latencja: S22_TECHNICAL_PASS, HUMAN_OPEN (2026-11-09)

**CONTEXTUAL vs ALL_ACTIVE** na S22: 3 sesje ×3 tury na wariant = **18/18 PASS**, 36 prywatnych WAV, STT WER 0/150 syntetycznych słów, korekta dnia pamiętana w każdym przebiegu. Mediany pełnej odpowiedzi (tury 1/2/3) **CONTEXTUAL 42,6/26,8/13,0 s**, **ALL_ACTIVE 23,0/10,0/9,0 s**; w drugiej turze −62,6%. Natywne `conversation_reused` = `false,false,true` vs `false,true,true` i mediana model + aprobata tury 2 = **17,6 vs 2,8 s**. Odpowiedzi ALL_ACTIVE w turach 2/3 są nawet dłuższe; to niewielkie porównanie dwóch strategii i różnej treści promptu, nie przyczynowy benchmark ani p95. Mac full QA/375 Python i S22 no-wipe/Call IDLE/clean PASS. Pierwszy host task preflight v1 odrzucony bez komend przez `idle_timeout>3600` (F-164 VERIFIED_SCOPED), v2 pełny PASS. **Human quality ośmiu wymiarów nadal UNASSESSED; nie zmieniać trybu produkcyjnego, G6 nieuruchomione.** Lokalny indeks: `~/Library/Application Support/ai-calls/qa/g53-private/skills-ab-index.html`. [Checkpoint](checkpoints/2026-11-09-g53-skills-ab-latency-s22.md).

## G5.3 — prywatny korpus S22 i jakość: OFFCALL_EVIDENCE_PASS, HUMAN_REVIEW_OPEN (2026-11-09)

**3 niezależne sesje × 3 syntetyczne tury = 9/9 PASS; 18 prywatnych WAV**, pełne STT/approved E2B i metryki RX/TX na Mac (0700/0600), lokalny `index.html` z trzema stronami do odsłuchu i ośmioma ocenami `UNASSESSED`. **STT 0/75 błędów syntetycznych słów, WER/CER 0.000**; mediany całego STT→E2B→TTS po pozycji **42.6/26.8/13.0 s** (n=3, *nie p95*). Natywny LiteRT tworzy Engine w turze 1 i Conversation ponownie w turze 2 po zmianie Skills; dopiero tura 3 `conversation_reused=true`. Mac pełne QA i 375 Python PASS, S22 IDLE/profil/no-wipe/clean. F-162 błędne API argumentów testu RED→GREEN `VERIFIED_SCOPED`. **Human naturalness/factuality nadal UNASSESSED; G5.3 nie zamknięty i G6 NOT_RUN.** [Checkpoint](checkpoints/2026-11-09-g53-private-voice-quality-corpus-latency.md).

## G5.3 — techniczny native STOP na S22 PASS; human quality nadal OPEN (2026-11-09)

**S22_OFFCALL_NATIVE_STOP_TECHNICAL_PASS:** Mac `aicalls-g5p3-native-stop-mac-qa-20261109-v1` i `aicalls-g5p3-native-stt-tts-stop-mac-qa-20261109-v1` DONE/0, pełne host QA/371 Python; S22 `aicalls-g5p3-native-active-inference-stop-s22-20261109-v2` 1/1 PASS (E2B GPU `sendMessage` → `cancelProcess`, bez stale callback, recovery) i `aicalls-g5p3-native-stt-tts-stop-s22-20261109-v1` 2/2 PASS (rzeczywiste STT/TTS aktywny STOP, restart i brak stale PCM). Profil i Call IDLE bez zmian, APK bez wipe, zero Call. Błędny host regex v1 odmówił przed instalacją; v2 poprawiony, `F-161 VERIFIED_SCOPED`. **Human semantic/naturalness ośmiu wymiarów nadal UNASSESSED; p95 oraz prawdziwa telefonia NOT_RUN.** [Checkpoint](checkpoints/2026-11-09-g5p3-active-native-stop-stt-e2b-tts-technical-pass.md). Następny jedyny etap G5.3: prywatny przegląd kompletnych tekstów/WAV i niezależna ocena tur; nie powtarzać zaliczonych STOP.

**English-first / przyszła komercjalizacja (research-only):** modele do sprawdzenia, w tym LFM2.5 Audio/Instruct, Granite, Phi, Qwen, Moonshine Streaming, Parakeet, Kokoro i Pocket TTS, oraz ryzyka Android/Google Play/licencje/VoIP są zapisane w [shortliście modeli i commercial readiness](ENGLISH_MARKET_MODEL_SHORTLIST_2026-11-09.md). Gemma 4 E2B pozostaje główna. Ten backlog nie zmienia bieżącej bramki G5.3 → G6 → G7, profilu, kodu ani statusów QA.


## 2026-11-09 — G5.2 zaakceptowane na S22, G5.3 następne

**G5.2 = S22_OFFCALL_MULTITURN_AUDIO_AND_STOP_PASS (2026-11-09).** Po Mac full QA `aicalls-g5p2-multiturn-audio-stop-mac-qa-20261109-v2` **DONE/0**, S22 `aicalls-g5p2-three-turn-audio-stop-s22-20261109-v1` **DONE/0**: 5/5 deterministycznych testów STOP/late callback (STT, model, TTS, restart i dwie kolejne generacje) i 1/1 natywna Gemma E2B + Android STT + Android TTS PL, **3/3 syntetyczne tury audio**, `MessageCapture` i `Clarification`, prawidłowa korekta dnia spotkania i pamięć, 3 decyzje routingu. TTS wygenerował **515 894 B PCM**, czasy narastające STT **4340/5723/4905 ms**, aprobata **21158/12699/7157 ms**, końcowy PCM **23042/14224/8694 ms**. Żadnych wykonanych narzędzi, prawdziwych połączeń ani zmian profilu; instalacja obu debug APK bez wipe, Call IDLE przed i po, Mac Git clean. Jest to **syntetyczny off-call Lab**, nie rzeczywista rozmowa, p95, odsłuch naturalności ani sprzętowy STOP dokładnie w trakcie natywnej inferencji (STOP sprawdzono przez kontrolowane spóźnione callbacki). [Checkpoint](checkpoints/2026-11-09-g5p2-multiturn-audio-and-stop-pass.md).

Otwarta G5.3: jakość/naturalność pełnego audio, aktywny przerwany natywny proces (inne niż deterministyczne callbacki), N≥3 latency/cold-warm. G6 autoryzowany real Call i G7 UX nadal osobno.


## 2026-11-09 — G5.1 jednoturowe audio E2E zamknięte, G5.2 następne

**G5.1 JEDNOTUROWE AUDIO E2E: S22_OFFCALL_PASS (2026-11-09).** Bez zmian toru telefonu i bez Call, natywny test `AndroidG51SpeechSkillsOffcallE2ETest#syntheticPolishPcmPassesSttGemmaSkillsApprovalTtsAndStop` odtworzył rzeczywisty lokalny TTS→syntetyczny PCM→wybrany STT→Gemma E2B/GPU z dynamicznymi Skills→`ConversationReplyBackend`→`TextOutputApprovalPolicy`→wybrany TTS→końcowy PCM, bez odtwarzania na głośniku. S22 `aicalls-g5p1-speech-skills-androidstt-e2b-tts-s22-20261109-v1` **DONE/0**: 1/1 AndroidTest, STT/tura routing 1/1, zatwierdzona odpowiedź, **236416 bajtów PCM16 16 kHz**, żadnego wykonanego narzędzia, STOP/close, profil nietknięty, Call IDLE przed/po, instalacja bez wipe i końcowy clean. Czasy narastające w tej pojedynczej próbie: STT **4622 ms**, zatwierdzona odpowiedź **21680 ms**, TTS PCM gotowe **23574 ms**. Mac `aicalls-g5p1-stt-e2b-skills-tts-mac-qa-20261109-v1` **DONE/0**: build AndroidTest, pełny `verify_host.sh`, 371 Python, lint/audio, anti-friction, clean Git. **Nie jest to wieloturowe audio, p95, pełna ocena naturalności, przerwanie podczas TTS ani EOS→TX/realne Call.** [Checkpoint](checkpoints/2026-11-09-g5p1-offcall-speech-skills-e2e.md).

**NEXT:** G5.2 wieloturowy głos i STOP/interrupt podczas STT→E2B/Skills→TTS, z realnym S22 i oddzielną oceną jakości. G6 prawdziwe Call dopiero po jednoznacznym, aktualnym grancie; G7 UX. Wszystkie stare „NEXT G4/G5.1” poniżej są archiwum.


## 2026-11-09 — G4.3 zamknięte technicznie, G5.1 pierwsza niezamknięta bramka

**G4.3 CLOSED — S22_OFFCALL_E2E_SCOPED_PASS (2026-11-09)**. `name/description/capability` są przypięte do `SkillRevision` z kompatybilnym odczytem starego schema=1; edytor pozwala tworzyć nową wersję i rollback, a Lab pokazuje wybór i aktywację/dezaktywację Skills dla każdej tury. Typowana propozycja `END_CALL` przechodzi jedynie walidację Lab `PROPOSED_ONLY`, bez wykonania. Mac task `aicalls-g4p3-skills-metadata-trace-tools-mac-qa-20261109-v3` **DONE/0** (celowane JVM, AndroidTest build, pełny `verify_host.sh`, 371 Python, lint/audio, anti-friction i clean Git). S22 task `aicalls-g4p3-metadata-ui-native-trace-s22-20261109-v1` **DONE/0** (4 kroki): 2/2 testy metadanych/UI, 1/1 natywny E2B przez `SkillLabRunner` (4 tury, 3 odświeżenia, 4 zdarzenia routingu, pamięć przed/po pivot, STOP); dwie instalacje debug APK `-r -t` bez wipe, `Call IDLE` przed/po, profil zachowany, Git clean. **F-158 i F-159 VERIFIED_SCOPED** po wcześniejszych RED. To tylko **syntetyczny tekstowy Lab**, nie STT/TTS, realne Call ani human semantic review. [Checkpoint](checkpoints/2026-11-09-g4p3-metadata-trace-tools-final.md).

**NEXT G5.1:** pełny wieloturowy tor audio STT→Gemma E2B/Skills→TTS w Lab bez Call: granice EOS/interrupt/STOP, przenoszenie kontekstu, poprawki, błąd STT i recovery, latency rozdzielone na STT/model/TTS oraz wynik pełnej rozmowy. Nie przepisywać działającego Lab runnera; brak dostępu do Call, IdentityVault i efektów. Wymagane realne S22 audio i osobna human semantic ocena; G6 następnie autoryzowana telefonia, G7 UX.

## 2026-11-09 — G4.1/4.2 zamknięte technicznie, G4.3 następne

**G4.2 fizyczny E2E na S22: PASS (2026-11-09).** `G4.1` kontrakt JVM i Mac full PASS; `G4.2` rzeczywisty `SkillLabRunner` → `AndroidSkillLabSessionFactory` → `DynamicSkillLabBackend` → E2B/GPU, **4/4 natywne tury, 3 przełączenia aktywnego zestawu Skills, fikcyjny kod zachowany przed/po zmianie tematu, STOP PASS, profil i Call IDLE bez zmian**. Ostateczny task `aicalls-f157-g4p2-e2b-memory-s22-e2e-retest-20261109-v4` **DONE/0**: Mac 371 Python, Gradle/JVM/lint/APK/AndroidTest/audio i anti-friction PASS, S22 instrument 1/1 PASS, oba debug APK bez wipe i końcowe Git clean. Poprzedni fizyczny RED pamięci `F-157` naprawiony, historyczne FAIL retained. Tylko syntetyczne **off-call tekstowe E2E** — nie pełne STT/TTS, human naturalness ani rzeczywisty Call. Gemma E2B pozostaje głównym modelem. [Kompletny checkpoint](checkpoints/2026-11-09-g4-dynamic-skills-e2e-final.md).

**NEXT:** G4.3 metadane `name/description/capability` w istniejącym wersjonowanym edytorze, obserwowalność per-turn i `tool proposal → application validation`, bez wprowadzania niezaufanych wykonawczych JS do Call. Potem G5 STT→E2B+Skills→TTS, human review; G6 oddzielnie autoryzowany rzeczywisty Call; G7 UX. F-148 długie konteksty pozostaje OPEN.

## Aktualny priorytet: G4 po decyzji G3 KEEP_E2B (2026-11-09)

**G3 CLOSED — `DECIDED_KEEP_E2B` (decyzja właściciela 2026-11-09).** Mac+S22: 96/96 natywnych tur, 48/48 prywatnych par, backend E2B GPU/Qwen CPU, 24/24 JSON/tools na model; ostateczny Mac clean postflight i integralność prywatnego pliku **DONE/0**. Lokalny przegląd **wszystkich 96 syntetycznych odpowiedzi** regułowym audytem `aicalls-g3-private-synthetic-semantic-rubric-20261109-v2` **DONE/0**: E2B **150/150**, Qwen **141/150** punktowych kontroli. Qwen: 3× brak tekstowej wzmianki o wtorku i 3× godziny 10 w drugiej turze wyjaśnienia oraz 3× identyczna odpowiedź w obu turach prywatności. Ten ostatni sygnał nie dowodzi naruszenia prywatności. Pominięta reguła z v1 (`wtork`) została naprawiona; v2 jest obowiązującym wynikiem. **Naturalność oceniona przez człowieka nadal UNASSESSED**, przeniesiona do G5. E2B pozostaje głównym modelem; Qwen lab-only, bez zmiany kodu telefonii. [Finalny checkpoint](checkpoints/2026-11-09-g3-keep-e2b-final-decision.md).

**NEXT: G4.1** — kontrakt zmian Skills między turami na niemutowalnym snapshocie, celowane JVM tests i Mac QA. Prawdziwe per-turn przełączanie aktywnych instrukcji w silniku i S22 pozostają G4.2/PENDING. G5 human semantic/naturalness + pełny dialog głosowy; G6 rzeczywiste Call; G7 UX. Stare wpisy poniżej to archiwum.

## Aktywna bramka 2026-11-09: G3 ocena semantyczna i decyzja

**G0 S22 24/24 PASS; G1 pin/S22 PASS; G2 Qwen3.5 CPU 3/3 PASS; G3 porównanie 96/96 tur na S22 TECH_PASS**. G3 `aicalls-g3-e2b-qwen35-96turns-s22-20261109-v1`: Mac latest AndroidTest build PASS, fizyczny `am instrument` PASS, 48/48 syntetycznych par prywatnie na Mac 0600, Call IDLE. Gemma E2B GPU: JSON/tools/native 24/24, hint 15/15, mediana cold/warm 24,20/4,73 s; Qwen3.5 CPU: 24/24, 12/15, 49,19/7,34 s. Formalne `failed` dotyczy tylko **pominiętego ostatniego clean postflight** (`task_budget_exhausted`), nie AndroidTest.

**Jeden następny krok:** krótki Mac read-only Git clean check oraz analiza 48 prywatnych par (per-model/scenario, bez transkryptów w GitHub) → semantyczny review → formalna decyzja `KEEP_E2B / PROMOTE_QWEN / INSUFFICIENT_EVIDENCE`. Qwen pozostaje Lab-only, E2B default. Dopiero potem G4 per-turn Skills, G5 pełna mowa, G6 autoryzowane Call i G7 UX. [Plan](AI_CALLS_MASTER_EXECUTION_PLAN.md) · [checkpoint](checkpoints/2026-11-09-g3-e2b-qwen35-pending-host-receipt-handoff.md).

### Archiwum poprzednich priorytetów (zachowane dowody, nie aktualny NEXT)

## Priorytet po fizycznym F-156 / E2B vs E4B (2026-11-09)

**F-156 VERIFIED_SCOPED:** aktywna wersja własnego Skill przeszła rzeczywisty UI edit/activate/off/rollback/reopen i native E2B A/B po przesunięciu instrukcji bliżej końca promptu. Mac full PASS i S22 2/2; izolowana, zaszyfrowana biblioteka testowa, brak zmian profilu i brak połączeń.

**Modele:** S22 ma obie lokalne Gemmy, identyczny korpus 2048, **18/18 native turns PASS** na GPU, również sprostowania, cytowane pożegnanie i prawdziwa propozycja zakończenia. E4B ~3× wolniejsza (cold i warm) bez przewagi poprawności na próbie; nie nadawać jej statusu produkcyjnie szybszej/lepszej. E2B nadal preferowana dla real-time, E4B do złożonych testów. [Dowody](checkpoints/2026-11-09-e2b-e4b-agent-skills-differential.md).

**Następny nowy sprint:** implementacja/eksperyment *selektora kontekstowych Skills* na wzór Google AI Edge Gallery: krótkie metadane `name/description`, selektywne instrukcje wersjonowanych Skills, osobna rejestracja narzędzi, propozycja→kontrola grantu→skutek; nie kopiować niekontrolowanego WebView/URL execution do telefonii. Test A/B z dotychczasowym, naprawionym statycznym promptem na tym samym E2B/2048 i korpusie. [Analiza Gallery](AGENT_SKILLS_EDGE_GALLERY_REVIEW_2026-11-09.md).

**Otwarte osobno:** N≥3, p95, długi kontekst/fakty F-148, ocena naturalności, MTP na rzeczywistym LiteRT/S22, fizyczny STT/TTS i autoryzowane realne Call. Bez powtórzeń PASS-ów F-150–F-156.


## S22 wydajność Gemmy — 2026-11-09, F-155 VERIFIED

`GEMMA_4_E2B` jest rzeczywiście wybrana i generuje na **GPU**, nie E4B/CPU. Zimna pierwsza tura przy 2048/4096: ~28,6/27,2 s (silnik + konwersacja + generacja); druga na zachowanym kontekście ~3,3/3,6 s. Średnich z obu tur nie porównywać do samej rozgrzanej generacji; w tej próbie brak dowodu regresji spowodowanej 4096. Nowe **Laboratorium** ma konfigurowalny budżet kontekstu, rzeczywisty podział czasów oraz monitoring GPU/CPU i pamięci. Telefoniczna ścieżka 2048 pozostaje niezmieniona. Mac full PASS, S22 po instalacji **4/4 PASS**, F-155 VERIFIED. [Checkpoint](checkpoints/2026-11-09-f155-lab-context-latency-diagnostics.md).

**Priorytet dalej:** SKILL-01/02/03, porównanie aktywnych/nieaktywnych instrukcji przez prawdziwy model oraz trwałość edycji w UI. Test 8192 i większa E4B, długi dialog oraz p95 w osobnej próbie, bez arbitralnego podnoszenia budżetu produkcyjnego Call.


## Priorytet od 2026-11-09: rzeczywisty UI i działanie AI Skills

Zbiorczy przebieg sprzętowy na S22 zakończył się scoped PASS: współdzielona procesowa dzierżawa LiteRT GUI/USB **1/1**, natywna decyzja Gemmy w Skill **1/1**, adaptacyjny dialog **1/1**, manualny STOP/restart **1/1**, szyfrowane presety/kontrakty UI **2/2** po naprawie testu zależnego od języka (F-154 VERIFIED), realny USB CLI trzy fabryczne presety × 2 tury **6/6**, prosty recall **3/3**, zapis/STOP **3/3**, odmowy interwencji bez własnego Call **3/3**. Mac full gate PASS. [Dokładny checkpoint](checkpoints/2026-11-09-single-pass-ui-usb-ai-skills-acceptance.md).

**Następny aktywny zakres = SKILL-01/02/03:** prawdziwe operacje edycji/włączenia/wyłączenia/wersjonowania z UI, trwałość po restarcie oraz porównanie rzeczywistych odpowiedzi Gemmy przy aktywnych i nieaktywnych instrukcjach — nie samo uruchamianie fabrycznych presetów. Próbki 3×2 mierzą podstawowe działanie i latencję, nie N=3 na preset ani p95. Zachować prywatne transkrypty i oddzielić ocenę semantyczną od PASS technicznego. Nie powtarzać już zaliczonych bramek USB bez wykrytego problemu.

Pozytywne takeover/resume/exact owned STOP podczas rzeczywistego Call nadal **NOT_RUN**, wymaga niezależnie skonkretyzowanego grantu aplikacyjnego. F-148 długi dialog/8192 i pełny audio pipeline pozostają osobnymi otwartymi pozycjami.

## Backlog historyczny i potwierdzone bramki — stan zapisany 2026-11-08

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
