# AI Calls — jeden przebieg S22: GUI/USB, odmowy Call i AI Skills (2026-11-09)

## Wykonanie i baza

- Repozytorium `MichalMatu/ai-calls`, `main` bez nowych gałęzi, działanie przez GitHub Connector + Mac Local Agent (`macbook-michal`). Baza testu `4a03f4576c7e7ecc2dcde120065f249a96611831`, później poprawka **tylko AndroidTest** `76f0a29ef8f89ad07afcbba4ba97351fc37602cd`.
- Telefon `SM-S906B`, ADB `RFCT70L7E8J`, `Call IDLE`, brak kampanii. Nie wymagano AI Calls na pierwszym planie dla USB. Nie zestawiano żadnego prawdziwego Call.
- Mac `aicalls-onepass-mac-build-uiusb-skills-20261109-v1` **DONE/0**: `audit_product_friction.py` PASS, pełny `scripts/verify_host.sh` PASS (JVM, Gradle/AndroidTest, lint, Python i audio), czysty checkout. APK aplikacji SHA256 `e3e542405e65bef43527832acbec8a9cef501aa0ff5b93dac4af52ee430e0da8`. Nowa AndroidTest APK została zainstalowana przez `adb install -r -t` bez wipe.

## Fizyczne testy UI/Skills i własności backendu

`aicalls-onepass-s22-gui-usb-skills-instrument-20261109-v1` **FAILED/92 jako całość, bo jeden test UI używał starej etykiety**, ale poniższe samodzielne przypadki z tego samego receipt przeszły:

| Przypadek | Wynik na S22 |
| --- | --- |
| `AndroidUsbGuiLabConcurrencyTest` | **1/1 PASS**: rzeczywisty GUI Lab zajął backend LiteRT, równoległy kontroler USB odmówił drugiego backendu; po GUI STOP USB Lab ponownie uzyskał zasób i STOP. Nie uruchomiono dwóch modeli równocześnie. |
| `AndroidGemma4DialogueSkillContractTest` | **1/1 PASS**: realny lokalny Gemma zwrócił decyzję w kontrakcie Skill na syntetycznym tekście |
| `AndroidLabAdaptiveNativeOfflineTest` | **1/1 PASS**: realna odpowiedź Gemmy determinowała gałąź adaptacyjną |
| `AndroidLabManualNativeOfflineTest` | **1/1 PASS**: wieloturowy manualny dialog, STOP i nowa sesja |
| `AndroidSkillPresetMatrixTest` | Pierwotnie **1/2**: test izolowanego szyfrowania/wersji PASS, UI FAIL na `kt:74` przez sztywne polskie etykiety |

Dokładny RED izolowany: `aicalls-onepass-skills-matrix-isolate-failure-20261109-v1` **DONE/0** (`aEncryptedEditVersionSwitchAndRollbackInIsolatedStore` 1/1 PASS, `bSkillsAndLaboratoryScreensLaunchWithoutMutatingActivePreset` 1/1 FAIL). Zapisano **F-154**. Poprawiono wyłącznie test na stabilne tagi UI, bez zmiany presetów ani języka aplikacji. `aicalls-onepass-f154-locale-independent-skills-green-20261109-v2` **DONE/0**: targeted `:app:assembleDebugAndroidTest` PASS, S22 **2/2 PASS**, IDLE, bez wipe i Mac clean. Pierwsza v1 próba QA miała błąd środowiska zadania (brak ANDROID_HOME przed kompilacją), usunięty w v2.

**Skonsolidowany wynik tych pięciu grup po poprawce testu: 6/6 przypadków AndroidTest PASS.** F-150 (procesowa wyłączność GUI/USB-controller) i F-154 (test UI locale-independent) **VERIFIED** w dokładnie opisanym zakresie. Nie twierdzić, że wykonano równoczesny fizyczny broadcast ADB w chwili aktywnego GUI; test używał rzeczywistego wspólnego kontrolera `UsbLabControl`, a osobne badania potwierdziły transport ADB.

## Fizyczne USB i trzy presety AI Skills

`aicalls-onepass-s22-usb-call-negatives-skills-ab-20261109-v1` **DONE/0**, S22 off-call:

- Odmowy bez własnego Call: `call-takeover` = `no_tracked_call`, `call-resume` = `no_tracked_call`, `call-stop-owned` = `no_exact_outgoing_ai_call`; **3/3 PASS**. To **nie** jest dodatni test przejęcia/wznowienia/STOP rzeczywistego Call.
- Prawdziwa Gemma E2B, okno Lab 4096, trzy oddzielne presety `natural`, `formal`, `message`; dla każdego START → 2× SEND → INPUT_READY → prywatny SAVE (2 tury) → STOP **PASS**. Łącznie **3/3 sesje, 6/6 technicznych tur i 3/3 zapisów/STOP**. Każda sesja używała wyłącznie fikcyjnego losowego kodu i pytania o jego zapamiętanie, bez danych właściciela.
- Prosty automatyczny test przypomnienia losowego kodu z poprzedniej tury: **3/3 heuristic PASS**. Taka heurystyka nie potwierdza naturalności, poprawności wszystkich informacji, stylu presetu ani spełnienia prawdziwego zadania.
- Średni czas generacji jednej tury: `natural` **23 151 ms**, `formal` **17 486 ms**, `message` **16 807 ms**. Trzy sesje to próbka porównawcza, **nie** miarodajny benchmark N=3/p95 ani dowód, że preset message jest zawsze szybszy.
- Prywatne transkrypty pozostają wyłącznie w `~/Documents/AI-Calls/private-exports/lab-sessions/` na Macu; żaden surowy tekst/telefon/WAV nie trafił do GitHub/task-result. Telefon pozostał **IDLE**, kod i Mac clean.

## Poza zakresem tego przebiegu

- **Pozytywne interwencje w realnym Call** wymagają odrębnego ważnego grantu aplikacyjnego z dokładnym numerem/celami, SIM/kontem, skutkami i danymi do udostępnienia. Nie testowano z realnym rozmówcą ani nie zmieniano polityk.
- F-148: semantyka długiego dialogu i opcja 8192 nadal OPEN; tych pomiarów nie powtarzano.
- **SKILL-01/03 i LAB-06 pozostają częściowe:** potwierdzono procesowanie trzech fabrycznych presetów i prostą pamięć, ale nie wykonano pełnego **UI edit → save/activate/disable → reopen → zmiana faktycznej odpowiedzi Gemmy** dla własnych rewizji Skills, ani N=3/human review.
- Brak testu pełnego RX/STT/TTS audio i niezależnego zewnętrznego skutku rzeczywistej rozmowy w tym przebiegu.

## Następny etap: właściwe funkcje AI Skills aplikacji

Skoncentrować się na `SKILL-01/02/03`: przejście przez realny UI edycji, aktywacja/wyłączenie wersji, restart, weryfikacja zaszyfrowanej trwałości i A/B z tą samą syntetyczną rozmową oraz *rzeczywistą* lokalną Gemmą. Zmierzyć odpowiedzi, zgodność z instrukcją aktywnego Skill, brak wpływu nieaktywnego Skill, poprawki faktów i latencję, z prywatnym protokołem oceny. Bez zaczynania nowego sprintu transportu USB lub bez podstaw powtarzania powyższych zamkniętych testów.
