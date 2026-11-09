# AI Calls — handoff do funkcjonalnego odbioru na S22

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
