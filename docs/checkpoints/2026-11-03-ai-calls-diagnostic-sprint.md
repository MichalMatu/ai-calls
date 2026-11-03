# AI Calls — sprint pętli diagnostycznej i usuwania sztucznego tarcia

## Decyzja właściciela i USB/ADB — 2026-11-03, aktualne

1. **Prywatna diagnostyka**: lokalny Mac Local Agent pobiera istniejący katalog `files/call-session-evidence/` z debug APK `pl.michalmatu.aicallbridge` na dokładnie jednym Samsungu S22 po USB/ADB i tworzy prywatne archiwum w `~/Documents/AI-Calls/private-exports/`. Skrypt: `scripts/aicall_tools/diagnostics/export_private_call_evidence.sh`; instrukcja: `docs/USB_PRIVATE_DIAGNOSTICS.md`. Nie wysyłać WAV/transkryptu do GitHub ani wyników agenta. Upload do czatu jest opcjonalny, nie wymagany.
2. **Wykonano preflight i próbę transferu**: Mac `aicalls-20261103-usb-private-evidence-export-v21` DONE/0; `ADB_AUTHORIZED_DEVICE_COUNT=1`, `DEVICE_MODEL=SM-S906B`, `CALL_STATE=mCallState=0`, `EVIDENCE_DIRECTORY=NOT_PRESENT`, `USB_EXPORT=NOTHING_TO_COPY`. Nie było danych do skopiowania, więc nie nazywać tego archiwum PASS. USB działa, brak danych nie blokuje dalszego kodu.
3. **Zero sztucznych bramek**: F-003/F-017 nie są zależnością rozwoju, właściciel wybrał USB zamiast przycisku ZIP w UI. F-016 to nieblokujący historyczny dług dokumentacyjny; wcześniejszych odmów edycji nie obchodzić. Normatywne `AGENTS.md` i `BLOCKERS.md` mają pierwszeństwo wobec archiwalnych akapitów.
4. **Teraz kod**: wrócić do uruchomienia aplikacji na S22, sprawdzić dokładnie grant, domyślną rolę dialera i gotowość media, następnie doprowadzić rzeczywisty autoryzowany START → RX/STT/AI/TTS/TX → wynik → STOP/ponowienie. Dotychczas S22 off-call 4/4 i 3/3 PASS. Bez udokumentowanej żywej rozmowy nie ogłaszać `END_TO_END_PASS`.

Data: 2026-11-03

## Źródło prawdy i granice
- Normatywny zakaz dokładania sztucznych blokad: pierwsze linie `AGENTS.md`.
- Jedyny aktualny rejestr i status: [BLOCKERS.md](../../BLOCKERS.md).
- Nie zmieniać w ciemno praw rozmówców, zgody nagrywania, bezpieczeństwa STOP, granic własnego Android Call ani odmów narzędzi. Nie usuwać walidacji identyfikatorów akcji / parserów jako pozornego „odblokowania”.
- GitHub Connector + Mac Local Agent; bez Codex/Work/GitHub Actions. Przed każdą kolejną zmianą odczytać aktualny HEAD.

## Zrealizowane źródła
1. **F-002/F-012/F-013/F-014/F-015**: wcześniejsze uproszczenie jednorazowego nagrywania i podwójnych zgód, testów limitorów i ochrony przed auto-start. Mac QA v3 zakończył się PASS na `338ce662`.
2. **F-010**: `CampaignContinuationStore` zapisuje rozpoczęte, dokładnie powiązane z grantem i kartą SIM zadanie, następne podejście, historię niedokończonych prób i STOP. `AppOwnedCampaignService` planuje następną próbę bez nowego kliknięcia, z malejącą częstotliwością bez maksymalnej kwoty prób; próbuje wznowić zaakceptowany bieg usługi Android START_STICKY, lecz nigdy nie ożywia odwołanego grantu.
3. **F-018**: spóźnione wyniki z wcześniejszego uruchomienia nie powinny nadpisywać STOP ani planować dodatkowego połączenia; zablokowane przez `continuationGeneration` oraz kontrolę grantu.
4. **F-011 częściowo**: `LocalTtsTextChunker` + `LocalTtsSpeechOutput` porcjują długie zatwierdzone wypowiedzi zgodnie z limitem pojedynczego wywołania systemowego TTS, zachowując kolejność i pełną treść. `DialogueSkillPolicy` nie odrzuca zatwierdzonej odpowiedzi po 600 znakach, `BackendOpenAiTextBackend` nie odrzuca 2000 znaków z już ograniczonej odpowiedzi JSON.
5. **F-003 częściowo**: `CampaignEvidenceArchive` tworzy ZIP z zakończonego folderu (timeline, WAV RX/TX, COMPLETE), nie pobiera plików obcych. Jest przetestowany jednostkowo, ale **nie jest połączony z UI**. Próba integracji wyboru celu z Android `CampaignToolActivity` została odrzucona przez narzędzie; F-017 OPEN. Nie obchodzić odmowy inną drogą.
6. **Testy i rejestr**: `NoArtificialCampaignBlockersContractTest`, `CampaignContinuationPolicyTest`, `CampaignContinuationStoreContractTest` (kompilacja AndroidTest), `CampaignEvidenceArchiveTest`, `LocalTtsTextChunkerTest`, długościowe testy odpowiedzi i aktualny Python tripwire wymagają także numerów F-016..F-019.

## Dowody QA
- `aicalls-20261103-friction-two-layer-source-qa-v3`, SHA `338ce662`: ANTI_FRICTION/Python/Kotlin build-unit-APK-lint PASS, bez telefonu.
- `aicalls-20261103-continuation-sprint-source-qa-v4`, SHA `a2b5ef8`: PASS source.
- `aicalls-20261103-full-sprint-final-source-qa-v5`, SHA `e057cf8`: Python 222 PASS, Kotlin 633/1 FAIL przez niepoprawne oczekiwanie trailing whitespace; poprawione w `fde032c`, zapis F-019.
- `aicalls-20261103-sprint-verified-source-qa-v6`, SHA `32ff901b20481c5670ce157e856cf42fc217308b`: **PASS** — Python 222/222, skaner, Kotlin units, `:app:assembleDebug`, `:app:assembleDebugAndroidTest`, `:app:lintDebug`. `BUILD SUCCESSFUL in 1m 35s`. To źródłowe QA, nie test live-call.
- `aicalls-20261103-s22-readonly-preflight-v7`: `AUTHORIZED_DEVICE_COUNT=0`; na Macu nie było podłączonego S22. Nie uruchamiano ADB install, zmiany roli dialera, połączeń ani nagrań.

## Gotowe APK na Macu (bez ingerencji w telefon)
Mac Local Agent `aicalls-20261103-sprint-export-debug-apk-v8` DONE/0, source SHA `32ff901b`:
- `/Users/michal/Downloads/ai-calls-sprint-32ff901b-debug.apk` (SHA256 `0cde4749fc86e1ee2b69977f279a10c6de8de1e3c79c3549fa1f2fe8f3226c67`).
- `/Users/michal/Downloads/ai-calls-sprint-32ff901b-androidTest.apk` (SHA256 `12450f21cedecfdc477a2a2f0df09b358d07157183b12f637d22087690d2edd7`).
- Nie było instalacji, zmiany roli ani połączenia; aplikacja gotowa do instalacji po podłączeniu S22.

## Niedomknięte — nie wolno ogłaszać pełnego PASS
- F-003/F-017: wygodny, prywatny eksport przez UI nie działa; moduł archiwum samodzielnie nie zapewnia UX.
- F-011: limit 4000 znaków wejścia brokera i fizyczne długie dialogi; klasyfikator JSON/ID ma własne zasadne limity protokołu. Nie podnosić ich bez kontraktu backendu.
- F-016: nieaktualne historyczne zdania w aktywnych dokumentach; próba usunięcia odrzucona przez narzędzie, nadrzędna jest początkowa zasada AGENTS oraz BLOCKERS.
- S22: brak aktualnego instalacyjnego, instrumentacyjnego i długiego telefonicznego PASS; testy AndroidTest tylko zbudowane.

## Bezpieczny dalszy krok
Gdy dokładnie jeden fizyczny S22 jest widoczny w ADB i w stanie IDLE, zainstalować APK przygotowany z SHA `32ff901b`, uruchomić wyłącznie bezpołączeniowe testy `CampaignContinuationStoreContractTest` oraz `AndroidCampaignAuthorizationStoreContractTest`, potem prowadzić świadomie zainicjowane testy kampanii w już zatwierdzonym zakresie. Nie wykonywać automatycznych połączeń w zadaniu buildowym ani publikować prywatnych WAV/transkryptów w GitHub/task-results.

## Kontynuacja sprintu 06:43 — źródła nowsze niż poprzedni APK

- Punktem wejścia był main `a5c85b8` z poprzednim QA v6 PASS, urządzenie wcześniej niedostępne.
- Mac `aicalls-20261103-s22-check-0643-readonly-v9` DONE/0: SM-S906B, jeden autoryzowany S22 pod ADB, `mCallState=0`.
- Mac `aicalls-20261103-s22-install-readiness-v11` DONE/0: `INSTALL_READY=YES_READONLY`, wersja zainstalowanej aplikacji 2, nieaktywny `AppOwnedCampaignService`, bez ingerencji w telefon.
- F-020: wykryto, że pilotaż audio w czasie oczekiwania na retry mógł przejąć serwis i wyczyścić kontynuację. `AppOwnedCampaignService.startSupervisedAudio` nie przechodzi do pilota, gdy zatwierdzona kampania już czeka; nie dodano dialogu ani limitu. Test kontraktowy z `NoArtificialCampaignBlockersContractTest`.
- F-011: `scripts/aicall_tools/text/openai_text_broker.py` oraz `BackendOpenAiTextBackend` nie odrzucają już tekstu po 4000 znakach wejścia i 2000 znakach wyjścia. Broker utrzymuje ograniczenie rozmiaru HTTP dla ochrony pamięci i tokenów modelu, a nie licznik produktu. Test Unicode STT >4000, długi wynik >2000, bounded HTTP i nieukończony upstream. Bez użycia klucza API i bez zewnętrznych rozmów.
- Mac QA v10 SHA `3f5b345`: Python 225 PASS, Kotlin 637 testów/1 FAIL w teście kontraktowym F-020. Powodem był fałszywy match komentarza `finish()/STOP` do `.contains("finish(")`, **nie błąd aplikacji**. Rejestr F-021, poprawka testu do sprawdzania linii wykonywalnego wywołania.
- Mac pełne QA `aicalls-20261103-broker-pilot-sprint-qa-v12`, pinned `f42ce03e14d9be60ef4036d6850971fc27e99832`: **QA_PENDING** do publikacji potwierdzonego terminalnego wyniku. Nie instalować nowego APK ani nie oznaczać PASS tylko na podstawie kolejki.
- Po v12 PASS można zainstalować nowe APK wyłącznie na dokładnie jednym S22 w stanie IDLE i z nieaktywną usługą kampanii; uruchomić jedynie instrumentację SharedPreferences (bez wykonywania połączeń). Do realnego dialera/rozmowy konieczne są granice systemowej roli i konkretnej zgody aplikacyjnej.
- F-003/F-017 eksport z UI i F-016 historyczna dokumentacja pozostają OPEN z odmową narzędzia; nie próbować obchodzić odmowy.

## S22 — prawdziwe wyniki testów instrumentalnych (po 06:00)

- Mac `aicalls-20261103-broker-pilot-sprint-qa-v12` zakończył się **DONE/0** na SHA `f42ce03e`: ANTI_FRICTION PASS, Python 225/225 PASS, Android unit/build/debug+androidTest APK/lint PASS. To QA źródeł.
- Mac `aicalls-20261103-s22-install-offcall-instrumentation-v13` zakończył się `failed/86`: dokładnie jeden SM-S906B w IDLE, brak aktywnej usługi, oba APK z SHA `f42ce03e` **zainstalowane PASS**, ale **cztery testy Android instrumentation FAIL**. Wszystkie cztery zwróciły NPE na magazynach SharedPreferences, gdy `context.applicationContext` w pakiecie instrumentalnym jest `null`; błędu nie wykryło wcześniejsze QA hostowe. Nie było połączenia, nagrywania ani zmiany roli dialera.
- Rejestr F-022: `CampaignAuthorizationStore`, `CampaignContinuationStore`, `CampaignRuntimeStatusStore` otrzymały fallback `context.applicationContext ?: context`. Gwarantuje zachowanie prywatnego scope test runnera, a nie modyfikację grantów aplikacji produkcyjnej.
- Kolejny Mac `aicalls-20261103-s22-f022-instrumentation-retest-v14` pinned `d2c869d468bd5bff794cabad601cda4c79138779` uruchomiono z warunkami dokładnego S22/IDLE/no-active-service i z ponowną instalacją tylko po kompilacji. **Wynik oczekiwany — nie uznawać za PASS bez potwierdzenia.**

## S22 — izolowane magazyny testowe i PASS 4/4

- **QA źródłowe**: Mac v12 `f42ce03e` PASS (225 Python, Kotlin unit, APK build, lint). Pilot nie może skasować oczekującej kampanii (F-020); broker Android/Python nie ma dawnych granic 4000/2000 znaków (F-011 w zakresie tekstu).
- **S22 v13**: instalacja obu APK PASS, instrumentacja 4/4 FAIL przez nullable `context.applicationContext` (F-022).
- **S22 v14**: instalacja obu APK PASS, NPE zniknął, instrumentacja 4/4 FAIL przez brak trwałego zapisu w kontekście test runnera (F-023). Źródło użytkownika nie zostało wyczyszczone.
- **S22 v15**: `aicalls-20261103-s22-isolated-preferences-retest-v15` **DONE/0** na `5d19bfcf75c43116cf5a3d9314263608cf469018`: Mac ANTI_FRICTION=PASS, Python tripwire PASS, jednostkowe Kotlin i kompilacja obu APK PASS, instalacja obu APK na dokładnie jednym SM-S906B PASS, instrumentacja **OK (4 tests)**, końcowe `mCallState=0`. Testy używają `InstrumentationRegistry.getInstrumentation().targetContext` oraz odrębnych plików `campaign_authorization_grant_instrumentation_v1` i `campaign_continuation_instrumentation_v1`; nie zmieniają rzeczywistego grantu.
- **Mac v16**: `aicalls-20261103-v15-apk-filename-correction-v16` DONE/0 — poprawiono mylące nazwy plików wynikowych z F-024 po weryfikacji SHA256, bez zmiany plików czy telefonu.
  - `~/Downloads/ai-calls-sprint-f023-5d19bfcf-debug.apk` — SHA256 `0018016d6e43b1502914ae211fe5167e04ce3d4b228f1746b0f2c16582f5f3cf`.
  - `~/Downloads/ai-calls-sprint-f023-5d19bfcf-androidTest.apk` — SHA256 `85c52d82c1254089bce398b990578a9b292741cd7a4557e8f31d16a1eebea790`.
- **Pozostają OPEN**: pełny test długiej żywej rozmowy i automatycznego ponowienia (bez inicjowania połączenia w zadaniu buildowym), F-003/F-017 eksport ZIP z UI (narzędzie odmówiło zmiany; nie obchodzić), F-016 historyczny tekst aktywnych instrukcji (narzędzie odmówiło zmiany, pierwsze reguły `AGENTS.md` i `BLOCKERS.md` mają pierwszeństwo).
- Powyższe PASS to **fizyczne S22 off-call** dla magazynów i testów, nie dowód RX/STT/TTS/TX, legalności zapisu czy zamknięcia realnej rozmowy z infolinią.

## Sprint F-025 / F-026 — START, restart i zmiana autoryzowanego grantu

Stan wejściowy: `main=47b35272c6c67dc0e94c5c0b1e5c0c34772f8e1e`; wcześniejsze S22 testy magazynów 4/4 PASS.

**F-025 — znaleziony problem:** `AppOwnedCampaignService.onStartCommand` przy ponownym START mógł skasować trwały termin kolejnej próby (reset `continuationStore.begin`), a zatwierdzenie nowego grantu podczas aktywnego połączenia mogło zgubić nowy harmonogram: `startAuthorizedAttempt` zwracał na `isRunning` bez zaplanowania kolejnego sprawdzenia. Nowa `CampaignStartReconciliationPolicy` daje determistyczne `BEGIN_NEW / REUSE_SAVED / ALREADY_RUNNING / QUEUE_REPLACEMENT`. Ten sam grant zachowuje termin i licznik retry, aktywny START nie wybiera nowego numeru. Nowy zaakceptowany grant podczas kończącej się rozmowy pozostaje oczekujący do faktycznego stanu IDLE, a nie znika. `AppOwnedCampaignService.awaitPhoneIdle()` sprawdza stan bez dodatkowego potwierdzenia i bez kwoty prób. Wciąż obowiązuje dokładna autoryzacja i tylko własne połączenie podlega selektywnemu zakończeniu. Dodano 5 jednostkowych testów polityki, test źródłowy i 3 AndroidTest bez połączenia.

**F-026 — regresja podczas QA:** nowa kontrola `TelecomManager.isInCall` w serwisie wymaga jawnego sprawdzenia uprawnienia `READ_PHONE_STATE`. Mac v18 (SHA `c97a8cde`) miał ANTI_FRICTION PASS i Python PASS, ale `:app:lintDebug` zgłosił `MissingPermission` 1 error. Naprawiono bez wyłączania lint, przez realne `checkSelfPermission(Manifest.permission.READ_PHONE_STATE)`, z dotychczasową kontrolą wymagań w executorze. Wpisy F-025 i F-026 w `BLOCKERS.md` pozostają jako historia.

**Wynik końcowy:** Mac `aicalls-20261103-f025-permission-safe-s22-v19`, pinned `e77b86b6b73bebed5376f0a800d0ad082a53613d`, **DONE/0**: skaner antyregresyjny PASS, Python mutation PASS, wybrane testy Kotlin PASS, debug APK/androidTest APK/lint PASS (`BUILD SUCCESSFUL in 1m 38s`). Fizyczny SM-S906B, `mCallState=0`, nieaktywny serwis, instalacja obu APK **PASS**, `CampaignStartReconciliationContractTest` **3/3 PASS**, stan po testach `mCallState=0`. Test nie wykonywał połączenia, nie nagrywał i nie modyfikował systemowej roli dialera.

**Artefakty Mac:**
- `~/Downloads/ai-calls-f025-e77b86b6-debug.apk` SHA256 `623c50b4700f46f2691d8a0f65fe6859ff40a42443abc175ace59861ea2f3177`
- `~/Downloads/ai-calls-f025-e77b86b6-androidTest.apk` SHA256 `687b94f8e01188d48c7002f664c4501b464ead735eba9cc161f4739fc328b55d`

**Granice dowodu:** START/restart/switch nie zostały sprawdzone przez prawdziwe połączenie. Nie potwierdzono końca rzeczywistego połączenia, poprawności trasy RX/STT/TTS/TX ani autonomicznego retry fizycznego. F-003/F-017 (brak eksportu ZIP z UI, odmowa narzędzia) i F-016 (historyczne opisy, odmowa narzędzia) nadal OPEN. Nie omijać odmów. Źródłem prawdy o statusach pozostaje `BLOCKERS.md`, a ten checkpoint wyłącznie przechowuje dowody i kontekst.

**Następny krok bez powtórek:** pełna diagnostyka żywej rozmowy w *konkretnym wcześniej autoryzowanym zakresie* na S22, z identyfikacją roli dialera, rzeczywistego call handle, RX/STT/TTS/TX, końca i następnej próby. O ile brak takiej bieżącej autoryzacji lub urządzenie jest zajęte, nie uruchamiać samodzielnie nowej rozmowy; rozwijać niezależne testy off-call i dokumentację.

## Decyzja USB — wykonanie i powrót do prawdziwego uruchomienia kodu

- **Cel użytkownika:** zamiast prac nad brakującym ZIP w UI stosować USB/ADB przez Mac Local Agenta, archiwa trzymać lokalnie w folderze projektu; uaktualnić zasadę ZERO sztucznych blokerów, a potem wrócić do właściwego startu aplikacji. Nie oczekiwać ręcznego kopiowania plików ani przesyłania ich do GitHub/task-results. Wgranie prywatnego archiwum tutaj jest wyłącznie opcją użytkownika.
- **USB**: Mac v21 sprawdził S22, jedno urządzenie, `IDLE`, ale brak `call-session-evidence/`. Mac v23 potwierdził `run-as=PASS`, brak katalogu sesji i grantu oraz faktyczne utworzenie `~/Documents/AI-Calls/private-exports` z uprawnieniami `0700`. Nie pomylić `NO_DATA` z odmową dostępu do danych.
- **Skrypt eksportu**: `scripts/aicall_tools/diagnostics/export_private_call_evidence.sh` zwraca teraz `USB_EXPORT=NO_DATA` (sukces bez danych) zamiast błędu, zachowuje weryfikację dokładnego fizycznego S22, prywatny folder i prywatne archiwum. F-027: pierwszy Mac QA v24 wykrył kolizję nazw dwóch szybkich eksportów i możliwość nadpisania; użyto końcowego formatu `mktemp ...XXXXXXXX` zgodnego z macOS i atomowego `ln` bez nadpisywania. Mac v25 na SHA `7a9dae192b9295980a38120823a7eea843cb1579` **DONE/0**: Bash PASS, 4/4 syntetyczne testy eksportu PASS, anti-friction PASS, 3/3 testy polityki PASS; rzeczywisty S22 `USB_EXPORT=NO_DATA`, brak udawania transferu realnych nagrań.
- **Dokumentacja**: [USB_PRIVATE_DIAGNOSTICS.md](../USB_PRIVATE_DIAGNOSTICS.md), `README.md` i normatywny `BLOCKERS.md` wyraźnie mówią, że F-003/F-017 (brak UI ZIP, wcześniejsza odmowa) **nie blokują rozwijania ani testowania kodu**, gdyż użytkownik wybrał USB. F-016 to nieblokujący historyczny dług; nie zmieniać odmówionych wcześniej plików przez alternatywne wykonanie. W archiwalnym tekście poniżej pozostają starsze statusy QA: aktualny status czytać tutaj i w `BLOCKERS.md`.
- **Rzeczywiste uruchomienie aplikacji:** Mac `aicalls-20261103-launch-main-and-live-readiness-v22` **DONE/0**: `MAIN_ACTIVITY_LAUNCH=PASS`, `APP_PROCESS_ALIVE=YES`, S22 `mCallState=0`, cztery istotne uprawnienia Android zweryfikowano w v23 jako przyznane. Stan roli `AI_CALLS_DEFAULT_DIALER=NO`, plik grantu aplikacji `campaign_authorization_grant_v1.xml` nie istnieje. W związku z tym **nie przechodzono do nieautoryzowanego wybierania numerów**.
- **Bieżąca diagnostyka startu:** Mac v26 uruchomił `MainActivity` z bezpołączeniowym `run_probe` (proces uruchomiony, plik `capability-report.txt` nie powstał), v27 wykonał bezpieczny cold-start tylko po potwierdzeniu `IDLE`, braku grantu i aktywnego serwisu — proces nadal działał, raport nadal nie powstał. V28 klasyfikował widoczny UI bez publikacji zawartości ekranu (`AI_CALLS_SCREEN_VISIBLE=false`), v29: urządzenie `Awake`, nie wykryto AI Calls na pierwszym planie; `mCallState=0`. Nie deklarować fizycznego PASS probe lub RX/STT/AI/TTS/TX.
- **Następna praca nad kodem**: sprawdzić rzeczywistą drogę startu i dlaczego off-call capability report nie powstał, bez nadpisywania grantu/roli. Rzeczywisty dial wymaga konkretnej autoryzacji celu w aplikacji i przyznanej przez system roli dialera. Nie jest to nowa blokada produktu; to granica systemowa i świadomie zatwierdzony zakres zadania. Po tych warunkach wykonać pełny test fizycznej rozmowy z późniejszym prywatnym zrzutem USB. `LIVE_CALL_END_TO_END_PASS` wciąż nieudowodniony.

**Dalsza kontrola aktywności po starcie:** Mac `aicalls-20261103-main-foreground-crash-audit-v30` DONE/0 na fizycznym S22 `IDLE`: `MainActivity` jest w historii zadań (`AI_CALLS_ACTIVITY_IN_TASK_HISTORY=true`), nie jest aktualnie aktywnością wznowioną (`AI_CALLS_ACTIVITY_RESUMED=false`), w badanym logu `APP_FATAL_CRASH_EVENTS=0`. Nie ma dowodu awarii aplikacji; nie udawać, że brak raportu capability potwierdza awarię audio. Dostęp do ekranu, rola dialera i ważny grant są sprawami rzeczywistej konfiguracji urządzenia/kampanii. Rozwój i diagnostyka USB nie są nimi blokowane. Zachować prywatność — nie publikowano tekstu UI, logów lub nagrań.
