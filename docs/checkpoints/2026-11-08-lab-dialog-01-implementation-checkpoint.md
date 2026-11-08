# AI Calls — LAB-DIALOG-01 — końcowy checkpoint kodu i odbioru S22 (2026-11-08)

## Zakres i historia
Start: `main@4eae72c`. Odbiór końcowego kodu: `main@e41423ed2b0016889e14b8149b478402899f8191`.
Zrealizowano funkcjonalny pionowy sprint A–E w GitHub Connector + Mac Local Agent; bez Codex, Work, GitHub Actions, bez telefonowania i bez modyfikacji danych właściciela. Wszystkie cztery wcześniej odrzucone, zakodowane na stałe historie Laboratorium nadal są usunięte.

## Implementacja
1. **A: Manualny chat — SOURCE COMPLETE.** `SkillLabRunner.startManual/submitManual`: jedna natywna sesja LiteRT, nieograniczona sztywną liczbą tur, kolejne wiadomości zachowujące kontekst silnika, START/STOP, reset, ochrona przed starym callbackiem i błędy/timeouty. UI composer/timeline. Nie ma efektów Telecom ani narzędzi.
2. **B: Edytowalne przypadki — SOURCE COMPLETE.** `LabCaseLibrary` + ekran Laboratory: utwórz, edytuj nową rewizję, wybierz starszą, kopiuj, usuń; cel, rola, pierwsza kwestia, stan rozmówcy, fakty, oczekiwania, reguły i warunek końca. `AndroidConversationPreparationStore` przechowuje prywatne dane przez AndroidKeystore/AtomicFile, bez backupu.
3. **C: Rzetelny raport — SOURCE COMPLETE / SEMANTIC REVIEW MANUAL.** Prywatny zapis tur, latencji, propozycji END_CALL, celu i referencyjnych oczekiwań oraz dowodów reguł. Kryteria TOPIC/MEMORY/CORRECTIONS/FACTUALITY/NATURALNESS/QUESTIONS/PROMISES/ENDING mają `UNASSESSED` aż do jawnej oceny PASS/FAIL z numerem tury. System **nie** nazywa 2/2 naturalnością ani nie uznaje propozycji END_CALL za błąd przy braku oczekiwania.
4. **D: Adaptacja — RULE-DRIVEN SOURCE COMPLETE.** Następna kwestia zależy od **rzeczywistej** odpowiedzi badanego modelu przez `question/end/refusal/contains:/missing:/otherwise`; reguły jednokrotne, zapis śladu wyboru, wspólny runner. To kontrolowany symulator regułowy, **nie** drugi autonomiczny LLM ani pełny arbitraż semantyczny.
5. **E: Integracja — TEXT/DEVICE ACCEPTED, AUDIO F2 OPEN.** Model z aktywnego `AudioEngineProfile`, wersje Skills przez wybrany preset, rola/goal z case; raporty i A/B dla tego samego id i rewizji. Nie wykonano nowej ścieżki STT→Gemma→TTS ani polskiego AudioBytes PCM, i nie testowano wszystkich presetów/modelek metodą N=3.

## Mac i S22 — rzeczywiste receipts
- `aicalls-labdialog01-mac-preflight-20261108-v1`: DONE/0, clean checkout i ADB Samsung SM-S906B.
- Wczesne niezaliczone próby A (tryb Gradle --offline) i ABC (niezescapowany apostrof) naprawiono. Pierwszy pełny Mac gate v1 ujawnił 7 przestarzałych asercji źródłowych; zaktualizowano kontrakty i padding UI, nie osłabiając wymogu STOP i braku Telecom.
- `aicalls-labdialog01-full-mac-gate-20261108-v2`: DONE/0 na `d64843d`, full `scripts/verify_host.sh`, **365/365 Python**, JVM, lint, APK, AndroidTest APK, audio QA, clean checkout.
- **Ostateczny:** `aicalls-labdialog01-final-source-gate-20261108-v3`: DONE/0 na `e41423e`, **365/365 Python**, testy JVM, pełne kompilacje APK i AndroidTest, lint, audio audit, `host_quality_gate_green=true`, clean checkout.
- `aicalls-labdialog01-s22-readonly-20261108-v1`: DONE/0, model SM-S906B, launcher foreground, `mCallState=0` (IDLE).
- **`aicalls-labdialog01-s22-device-acceptance-20261108-v1`: DONE/0, 4/4 komendy**: guarded model/foreground/Call/service preflight PASS; podpisana aktualizacja APK+AndroidTest `adb install -r` PASS bez usuwania danych; `AndroidLabDialogStorageAcceptanceTest` **2/2 PASS** (UI controls, isolated encrypted CRUD/reopen/revision/report/no plaintext); `AndroidLabManualNativeOfflineTest` **1/1 PASS** (real native local 2-turn text dialogue, STOP, fresh restart, no call). Postcheck `IDLE`.
- Nowe syntetyczne unit testy: `LabCaseLibraryTest`, `LabAdaptiveInteractorTest`, `LabDialogueReportTest`, `SkillLabManualDialogueTest` (w tym 16 manualnych tur jednej instancji backendu, późny callback, STOP i nowe sesje) — przechodzą w końcowej pełnej JVM gate.

## Jawne luki odbioru (nie ukrywać jako PASS)
- **SEMANTIC_QUALITY_OPEN:** natywne 2 tury/model odpowiedział, ale nie potwierdzono niezależnie, że rzeczywiście zapamiętał szczegóły, poprawnie uwzględnił korektę lub nie halucynował. Rubric celowo jest `UNASSESSED` do recenzji konkretnych tur. Brak automatycznego niezależnego sędziego LLM.
- **FULL_UI_EDITOR_E2E_OPEN:** S22 potwierdził istnienie kontrolek i odizolowany round-trip przechowalni, ale nie zautomatyzowano całej ścieżki klikania od utworzenia po usunięcie w produkcyjnym ekranie, aby nie zmieniać rekordów właściciela.
- **ADAPTIVE_S22_OPEN:** reguły adaptacyjne przeszły JVM, ale nie wykonano osobnego realnego dialogu adaptacyjnego na S22; brak przycisku natychmiastowego takeover bez STOP.
- **AUDIO_F2_OPEN:** STT/TTS, polska mowa w PCM direct, WER/CER i END-to-END audio nie stanowią wyniku tego tekstowego odbioru. Utrzymują się osobne F-080/F-122/F-123/F-124; żadnego real Call.
- **AB_SAMPLE_OPEN:** funkcjonalny raport porównania istnieje, ale nie uruchomiono N=3 powtórzeń A/B na modelach/Skills ani nie rozdzielono zimnego startu i generacji w statystykach.

## Definicja zamknięcia
**LAB-DIALOG-01 SOURCE/STATIC QA/MANUAL NATIVE OFFCALL: PASS** na `e41423e`.
**Semantyka/adaptacja fizyczna, pełny editor UI i F2 audio: OPEN**, jawnie przekazane do dalszej funkcjonalnej macierzy zamiast udawać kompletny QA wszystkich funkcji.
Mac checkpoint i dokumenty synchronizować z najnowszym main; dokumentacyjny commit po `e41423e` nie zmienia APK.

## Dodatkowe domknięcie sprintu — adaptive i układ UI (2026-11-08)

**Aktualny HEAD kodu:** `main@992280b5d1fd5d1d7bdded3d0874fe404fb7c788`. Poprzednia akceptacja tekstowej Gemmy i zaszyfrowanej przechowalni pozostaje przypięta do `e41423e`. Nie przenosić jej automatycznie na nowe zmiany UI.

- `f2889a2`: nowy `AndroidLabAdaptiveNativeOfflineTest` łączy prawdziwy lokalny model z testowym przypadkiem, wybiera kolejną wypowiedź po odpowiedzi Gemmy i asercją sprawdza zgodność śladu gałęzi z rzeczywistym znakiem zapytania. Na Macu `aicalls-labdialog01-adaptive-full-mac-20261108-v1` **DONE/0, full host PASS**.
- `992280b`: przebudowano układ `ConversationSkillLabActivity` na niezależnie przewijany panel ustawień i przewijany transcript, przy widocznym/podpiętym composerze i STOP. `AndroidLabDialogStorageAcceptanceTest` rozszerzono o warunki fizycznej wysokości transkryptu i widoczności composera.
- `aicalls-labdialog01-layout-final-mac-20261108-v1`: **DONE/0**, aktualny dokładny HEAD `992280b`, `scripts/verify_host.sh` PASS, **365/365 Python**, JVM, lint, APK, AndroidTest APK, audio audit, clean checkout, `host_quality_gate_green=true`.
- `aicalls-labdialog01-s22-adaptive-native-20261108-v1`: **REFUSED before installation**. Świeży S22 był `IDLE`, lecz `pl.pockethost.companion/.MainActivity` zajmował ekran. Nie przejmowano foreground.
- `aicalls-labdialog01-s22-adaptive-layout-guarded-20261108-v2`: zadanie **DONE/0 z logicznym BLOCKED_SAFETY_GATE**, model i połączenie `IDLE` potwierdzone, **foreign_foreground=True**. Wskutek bramki testy, instalacja nowego kodu i native adaptive **NIE ZOSTAŁY WYKONANE**.

**Wynik na bieżącym HEAD:** SOURCE/HOST PASS. Dotychczasowe realne manualne 2 tury/STOP/restart i 2/2 szyfrowany storage/UI PASS na poprzednim sprawdzonym kodzie. Nowa adaptacja/układ nie mają jeszcze dowodu urządzeniowego i pozostają **DEVICE_BLOCKED**. Semantyczny sędzia, naturalność, pełny klikalny edytor, A/B N=3 i F2 audio nadal **OPEN**.

**Bezpieczna kontynuacja:** gdy S22 będzie dostępny z Launcherem lub AI Calls na pierwszym planie i bez Call, wykonaj *tylko* nowy scoped `AndroidLabDialogStorageAcceptanceTest` (2 testy) i `AndroidLabAdaptiveNativeOfflineTest` (1 test), pinned HEAD z nową APK/test APK i `adb install -r` bez kasowania danych. Przed i po: model, `mCallState=0`, brak AppOwnedCampaignService, kontrola foreground. Nie uruchamiać prawdziwego połączenia ani nie ingerować w PocketHost. Następnie osobny prywatny przegląd semantyki rozmowy.
