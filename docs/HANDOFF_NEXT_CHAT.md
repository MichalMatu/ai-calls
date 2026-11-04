# AI Calls — handoff operacyjny (2026-11-04)

## AKTUALNY ETAP — zamrożony RAW v1, F-047, dynamiczne F-044

- **FROZEN baseline:** `0d44b7d5a7e01daa118da10c2e04c458086dced9`, realna rozmowa 03:46 z 2 kolejnymi odpowiedziami Gemmy RAW i 2 poprawnymi TX. Nie zmieniaj silnika RAW, defaultu SCRIPT_GEMMA i Samsung/Shizuku media. [Punkt zamrożenia i dowód przyczyny RX](checkpoints/2026-11-04-gemma-raw-live-v1-frozen.md).
- **F-047:** 17,08 s końcowego RX nie miało transkryptu, bo po rozpoczęciu mowy było najwyżej 1,0 s ciszy (VAD wymaga 1,5 s), a Call zakończył się przed endpointem i `pipeline.finishInput()`. `finish(owned_call_ended)` anulowało STT; WAV istnieje. Dodano beztekstowy marker `RX_INTERRUPTED` i syntetyczny regression. **Brak odzyskanego tekstu trzeciego fragmentu, F-047 nadal OPEN.**
- **F-044 kod:** `GemmaDirectTaskBriefBuilder` buduje system prompt po `CampaignAuthorizationPolicy.authorize` z typed request, przypisuje rolę DZWONIĄCEGO, cel CLIR i ograniczenia; cztery `GemmaDirectPromptSkill` jako instrukcje (cel/IVR/procedura/wyjaśnienie). `CampaignToolActivity → Skills i zadanie Gemma Direct`: checkboxy, zapis per-grant, podgląd. Model i prewarm dostają identyczny prompt; nie ma funkcji ani uprawnień do ujawniania sekretów w RAW. Nie obsługuje jeszcze innych executorów zadania poza CLIR.
- **QA na ten etap:** Mac Local Agent `aicalls-20261104-f044-brief-skills-f047-host-qa-v1` z kodu `28c1456e753baab0be6997384c2110a3c87cd940`. **Status sprawdzić z terminalnego task-results, nie zakładać PASS**. Nowy TaskBrief/menu nie są jeszcze fizycznie przetestowane na S22. Nie przeprowadzać nowego live-call przed QA i off-call syntetycznym testem 4 tur.
- [Checkpoint implementacji F-044](checkpoints/2026-11-04-f044-dynamic-taskbrief-skill-menu.md). Kolejny krok: otrzymać pełne Mac QA i wykonać bezpieczny test na S22 tylko w stanie IDLE, z zachowaniem grantu/retry; potem dopiero nadzorowany realny test właściciela. F-045 (semantyczna kontrola swobodnej mowy) nadal na końcu kolejki.


## Mapa menu S22 — potwierdzona screenshotem i kodem 2026-11-04

**Ekran główny** `MainActivity` zawiera: `ROZPOCZNIJ AUTORYZOWANE ZADANIE AI`, `CAMPAIGN TOOLS`, `STOP AI`, oraz akordeony `Tryb rozmowy i model`, `Model Gemma 4`, `Uprawnienia i konfiguracja`, `Diagnostyka deweloperska`. W głównym widoku **nie ma osobnego przycisku Gemma Direct**.

- `CAMPAIGN TOOLS` → `CampaignToolActivity` → `Start authorized CLIR enable — Gemma Direct`: **jawny start GEMMA_DIRECT**, obecnie eksperymentalny RAW dla zadania CLIR. W tym samym ekranie jest przycisk `Start authorized CLIR enable` dla starego `SCRIPT_GEMMA` oraz `Authorize CLIR enable campaign` i `STOP AI`.
- `ROZPOCZNIJ AUTORYZOWANE ZADANIE AI` na głównym ekranie otwiera `CampaignToolActivity` z `EXTRA_START_AUTHORIZED_CAMPAIGN=true`, co **automatycznie wywołuje start w trybie domyślnym `SCRIPT_GEMMA`**, a nie nowy Gemma Direct. Nie używać tego przycisku w teście Direct.
- Nie sugerować użytkownikowi wyboru Gemma Direct z akordeonu `Tryb rozmowy i model`. Obecna ścieżka Direct jest w `CAMPAIGN TOOLS`.
- Ekran główny wyświetla `Audio mode: Local STT + TTS (S22)`, `LLM provider: Local Gemma 4 E2B (S22)`, `Gemma 4 model: READY`; to stan dostawcy/modelu, **nie potwierdzenie trybu konkretnej kampanii**.


**Nowy priorytet właściciela (2026-11-04, 02:51 Europe/Warsaw):** kolejność została świadomie odwrócona na **realny live-call Gemma Direct z obecnym stałym promptem CLIR → dopiero po działającym flow dynamiczny TaskBrief (F-044) → na końcu dodatkowa semantyczna kontrola tekstu (F-045)**. To eksperymentalny test, nie zdjęcie obowiązujących grantów, polityk faktów/efektów, STOP lub ochrona praw rozmówcy; nie rozpoczynać połączenia przez zablokowany zewnętrzny tool. Sprawdzić gotowość S22 read-only i poprosić właściciela o naciśnięcie przycisku w aplikacji. Surowe audio/teksty tylko prywatnie na Macu.

## CHECKPOINT WZNOWIENIA — Gemma Direct przed testem live

**Czytaj najpierw:** [2026-11-04-gemma-direct-next-live-handoff.md](checkpoints/2026-11-04-gemma-direct-next-live-handoff.md) — pełna prawda o promptach, datach/skillsach, SHA, Mac receipts, S22 APK, RX/STT/TTS/TX, prywatności, znanych brakach i konkretnym teście.

- Baseline kodu na fizycznym S22: `2424118318caaca03d6330129bddf4190715677c`. Ostatni zapamiętany kod źródłowy na `main` jest identyczny; kolejne commity dokumentacyjne nie były instalowane. **Przed wznowieniem zawsze odczytaj świeży HEAD.**
- Mac full `aicalls-20261104-gemma-direct-full-host-qa-v1` DONE/0: Gradle unit/lint/assemble AndroidTest + 248 Python PASS. S22 off-call `aicalls-20261104-gemma-direct-s22-offcall-two-turn-v1` DONE/0, bez telefonu, AndroidTest PASS, 2 tury GPU complete, druga reuse (`generate_ms=6525 → 1598`), brak naruszenia grantu/continuation.
- Realny Gemma Direct **NIE testowany na żywo**. Co gorsza, `GemmaDirectTaskBrief.PROMPT` jest na dziś stałym opisem **CLIR**, a nie dynamicznym `TaskBrief` z zatwierdzonego zadania i katalogu skillsów. `SPEAK` ma tylko syntaktyczny speech filter; nie potwierdza pełnej walidacji semantycznej zobowiązań. Nowe `F-044` i `F-045` w `BLOCKERS.md` są OPEN. Najbliżej: **eksperymentalna, użytkownik-uruchamiana próba Gemma Direct w rzeczywistym autoryzowanym CLIR**, z obecnym stałym promptem. F-044 i F-045 pozostają otwarte i są kolejnymi sprintami w tej kolejności (najpierw F-044, potem F-045). Bez obchodzenia dawnej odmowy zdalnego wywołania.
- Oba tryby nadal muszą mieć taki sam grant/SIM/target/STOP/FactDisclosure/commitment/TTS/TX; `Script + Gemma` pozostaje domyślny. Nie ruszać Samsung/Shizuku frozen media, nie ujawniać PII, WAV ani tekstu rozmowy w repo/receiptach.

## NOWY STAN — Gemma Direct dual-mode (ostatni sprint 2026-11-04)

- **Zainstalowany i testowany APK na S22:** kod aplikacji z `2424118318caaca03d6330129bddf4190715677c`; świeży `main` HEAD sprawdź ponownie (kolejne commity mogą dotyczyć tylko dokumentacji). Mac `aicalls-20261104-gemma-direct-full-host-qa-v1` DONE/0: Gradle unit/lint/app+AndroidTest APK, 248 Python, anti-friction PASS.
- **Dwa niezależne tryby:** stary `Script + Gemma` pozostaje domyślny; jawnie wybrany przycisk `Start authorized CLIR enable — Gemma Direct` uruchamia model generujący własną wypowiedź z zadaniem CLIR i pamięcią jednej rozmowy. Nie zmieniono grantu, target/SIM, STOP, ujawniania faktów, commitment gate, TTS/TX. Tryb przypisany do grantu dla kontynuacji.
- **Fizyczny synthetic-only S22:** Mac `aicalls-20261104-gemma-direct-s22-offcall-two-turn-v1` DONE/0. Test urządzeniowy `AndroidGemmaDirectOnDeviceTest` OK (1 test), dwie kolejne natywne odpowiedzi Gemmy GPU, bez telefonu i TTS. Odczyt Perf `aicalls-20261104-gemma-direct-s22-perf-metadata-v1`: pierwsza tura engine 6198 ms + conversation 3097 ms + generate 6525 ms; druga 0 + 0 + 1598 ms, `conversation_reused=true`, oba `complete`. Po teście IDLE, dialer/grant/continuation unchanged.
- **OPEN:** nie przeprowadzono rozmowy telefonicznej w trybie Gemma Direct, nie ma jeszcze dowodu jego naturalnych wypowiedzi słyszanych przez konsultanta, ani osiągnięcia CLIR. Istniejące prywatne RX/TX starszych sesji nie stanowią dowodu nowego Direct. Nie obchodzić żadnego narzędziowego zakazu zdalnego inicjowania rozmowy.
- Zobacz [checkpoint Gemma Direct](checkpoints/2026-11-04-gemma-direct-independent-engine.md). **Dalsze sekcje tego handoffu zawierają historyczne wyniki starszego F-042/F-043 i należy czytać je w tym kontekście.**

## Stan źródeł i urządzenia

- Repozytorium: `MichalMatu/ai-calls`, gałąź docelowa `main`. Ostatnie zainstalowane i hostowo zweryfikowane źródło aplikacji: `032b6f5138a1c10f8d7f646065a5aa539e2a766a` (F-043 + F-042 pacing). Dokumentacyjny reaudyt rozpoczęty po `a834d217`; **przed kolejnym zadaniem zawsze odczytaj świeży HEAD**, nie używaj tu zapisanego SHA jako aktualnego.
- **F-043 VERIFIED** na fizycznym Samsung S22+ `SM-S906B`: instalacja aplikacji i AndroidTest z podanego SHA, GPU, natywny `Conversation` użyty dwukrotnie (`false → true`); `generate_ms=6233 → 3998`, `engine_ready_ms=6709 → 0`, `conversation_create_ms=3191 → 0`. To test syntetyczny bez połączenia. [Pełny dowód](checkpoints/2026-11-04-f043-gemma-one-conversation-per-call.md).
- **F-042:** neutralna zapowiedź, diagnostyka GPU/CPU i korekta podwójnego oczekiwania po TX ACK. Mac 2026-11-04 RED i full GREEN (248 Python, Android build/unit/lint, anti-friction PASS); S22 z nowym APK `032b6f5`, bez połączenia. **OPEN:** rzeczywisty EOS→zapowiedź/pełny TX, słyszalność i brak nakładania po drugiej stronie. [Aktualny checkpoint](checkpoints/2026-11-04-f042-ack-pacing-measurement-sprint.md), [poprzedni](checkpoints/2026-11-03-f042-parallel-ack-gemma-latency.md).
- F-041 eksport prywatnych nagrań przez USB/ADB VERIFIED; wcześniejsze 11,7–11,9 s EOS→TX pochodzi z prawdziwej rozmowy **sprzed** F-042/F-043. Audio pozostaje na Macu w prywatnym katalogu, nie na GitHub. [Dowód](checkpoints/2026-11-03-f041-private-rx-tx-silence-f042-latency.md).
- Po zainstalowaniu F-042 na S22 Mac Local Agent potwierdził `IDLE`, brak aktywnej kampanii, zachowaną rolę dialera, grant i odcisk danych kontynuacji. Instalację poprzedziło sprawdzenie braku aktywnego wznowienia. To **ostatni pomiar**, nie bieżący ciągły stan.
- **Niezamknięte niezależnie od F-043:** słyszalność TX u rozmówcy, realny wynik CLIR, żywe wznowienie po STT 6/7/pustym finale, generyczne umiejętności wizyty/mechanika. Statusy F-xxx wyłącznie w [BLOCKERS.md](../BLOCKERS.md).

## Następna mierzalna praca

1. Preflight: świeży `main` HEAD, brak nieodebranej pracy w Mac Local Agent, dokładny model S22+, `IDLE`, brak aktywnej kampanii, role/grant/STOP. Nie instaluj nowego APK podczas połączenia lub aktywnego retry.
2. W ramach konkretnego, nadal ważnego grantu i dozwolonego toru działania uzyskaj jeden test rzeczywistej rozmowy F-042: EOS→zapowiedź TX, EOS→pełny TX, czasy STT/Gemma/TTS, brak nakładania głosów, niezależna słyszalność odbiorcy, STOP i stan końcowy. Nie obchodź odmowy zewnętrznego narzędzia połączenia.
3. Zapisz tylko liczby i anonimowe statusy w rejestrze/checkpoincie; surowe RX/TX/numery/transkrypcje tylko na prywatnym Macu. Dopiero po dowodach dobierz optymalizację TTS/modelu i test regresji.
4. Nie powtarzaj zakończonego syntetycznego testu F-043 ani pełnego hostowego QA na niezmienionym źródle.

## Trasa pracy / bezpieczeństwo

- GitHub Connector + **Mac Local Agent**. Bez Codex, Work i GitHub Actions. Host `macbook-michal`; instalacja `cfea4cc3-b54c-4b6d-b9a6-758d12733e98`. Aktualny protokół i stan czytaj w `MichalMatu/local-agent`, na gałęzi `agent-control`, a nie z historycznego statusu w repo AI Calls.
- Prywatne zadania Mac Host Ops: `.agent/hosts/macbook-michal/supervisor/cfea4cc3-b54c-4b6d-b9a6-758d12733e98/tasks/<task-id>.json`, wyniki w analogicznym `task-results/<task-id>.json`, każdorazowo z dokładnym target ID i jednoznacznym terminalnym wynikiem. Nie wpisuj zadań hostowych ani ich surowych wyników do publicznego repo AI Calls.
- Repo `ai-calls:agent-control` służy osobnej starszej skrzynce projektu, **nie** jest kolejką Mac Host Ops. Nie traktuj commita ani historycznego heartbeat jako wyniku urządzenia.
- Grant zadania, prawa uczestników, Android, prywatność, selektywne rozłączanie wyłącznie własnego `Call`, STOP i odmowy narzędzia są nienaruszalne. Rozwój autonomiczny ≠ prawo wykonywania nieautoryzowanych skutków.

## Gałęzie — kontrola porządkowa

- `main`: jedyna gałąź produktu. `agent-control`: zachować, bo to odrębna skrzynka projektu; brak wspólnego przodka z `main`.
- `checkpoint/2026-11-03-f035-s22-baseline`: **USUNIĘTA** po potwierdzeniu `CHECKPOINT_UNMERGED_COMMITS=0`; Mac task `aicalls-20261104-doc-audit-branch-prune-v2` potwierdził `CHECKPOINT_DELETE=PASS` i `CHECKPOINT_REMOTE_PRESENT=false`.
- `fix/stt-startup-errors-20261102`: **USUNIĘTA** przez Mac task `aicalls-20261104-stt-branch-content-equivalence-prune-v1` DONE/0. Mimo 4 własnych commitów, cały diff gałęzi od wspólnego przodka obejmował tylko 2 pliki (`OnDeviceSpeechInput.kt`, `AndroidLocalSpeechMvpContractTest.kt`), a oba bloby były identyczne z `main` (`PATH_CONTENT_EQUIVALENT=true` 2/2). `STT_REMOTE_DELETE=PASS`, `STT_REMOTE_STILL_PRESENT=false`; kod nie został utracony.
- **Końcowy zdalny stan:** wyłącznie `main` + `agent-control` (GitHub list 2/2, kolejna strona pusta). Bez force-push, bez zmian aplikacji lub telefonu.

## Reaudyt dokumentacji — potwierdzone QA

Mac `aicalls-20261104-doc-audit-branch-prune-v2` **DONE/0** na `main` `1c9dca9`: `DOC_LINKS_AND_ANTI_BLOCKER_HEADING=PASS`, `ANTI_FRICTION=PASS`, pięć aktywnych dokumentów razem **21 396 znaków** po usunięciu sprzecznej chronologii. Zmieniono wyłącznie dokumentację, bez nowych buildów, instalacji i połączeń. Dotychczasowe F-042/F-043 testy kodu zachowują zakres swoich wcześniejszych receiptów. To nie jest nowe urządzeniowe QA.

## Nawigacja

[README](../README.md) — krótki opis produktu i bieżący status; [ROADMAP](ROADMAP.md) — etapy; [ARCHITECTURE](ARCHITECTURE.md) — granice modułów; [AGENTS](../AGENTS.md) — reguły; [BLOCKERS](../BLOCKERS.md) — jedyne statusy; checkpointy — historyczne dowody, nie bieżące instrukcje.

## Sprint F-042 — wynik z 2026-11-04

- Źródło zmian: test RED `c6e69b7`, helper `b1546f8`, integracja `032b6f5`. Dotknięte wyłącznie `ChatRelayLiveCallProbe.kt`, `ChatRelayTurnPacing.kt` i test pacing. Nie zmieniono grantów, STOP, Gemmy ani zamrożonego Samsung RX/TX.
- **Mac RED** `aicalls-20261104-f042-ack-pacing-red-v2`: DONE/0 (oczekiwany brak symbolu), po nieuruchomionym v1 bez SDK. **Mac GREEN** `aicalls-20261104-f042-ack-pacing-green-full-v1`: DONE/0, pin `032b6f5`, `ANTI_FRICTION=PASS`, `F042_GREEN_FULL_HOST_QA=PASS`, trzy udane fazy Android Gradle, 248 Python PASS, `host_quality_gate_green=true`.
- **S22 odczyt** `aicalls-20261104-f042-s22-readonly-preflight-v2`: DONE/0, dokładny `SM-S906B`, `IDLE`, dialer tak, kampania nieaktywna; pierwsze zlecenie v1 przerwane *przed wykonaniem* z powodu budżetu zadania.
- **S22 instalacja** `aicalls-20261104-f042-s22-safe-apk-deploy-v1`: DONE/0, build/install APK PASS, powtórny bezpieczny precheck, grant i stan kontynuacji bez zmian, końcowy `IDLE`, brak kampanii. **Nie wykonywano połączenia ani testów RX/TX na linii.**
- Do pomiarów realnej rozmowy raport zawiera `turn_N_eos_to_quick_ack_tx_ms`, `turn_N_eos_to_full_tx_ms`, `turn_N_quick_ack_write_wall_ms` i `turn_N_quick_ack_post_write_hold_ms`. To czasy lokalne, nie akustyczny dowód słyszalności. [Checkpoint źródłowy i testy](checkpoints/2026-11-04-f042-ack-pacing-measurement-sprint.md).
- **Granica:** etap source/QA/install VERIFIED; F-042 real-call acoustic oraz wynik CLIR nadal OPEN. Następny etap to jeden dozwolony, jawnie autoryzowany test realnego dialogu; nie powtarzaj syntetycznego F-043 bez nowej przesłanki.
