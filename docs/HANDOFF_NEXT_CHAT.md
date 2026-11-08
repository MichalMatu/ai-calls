# AI Calls — handoff do funkcjonalnego odbioru na S22

**Data:** 2026-11-08. **Bazowy commit kodu:** `main@edf63c1ea9dc425c91d2a1571a90038631852ffd` (UX-11 CLOSED). Kolejne commity tylko dokumentacyjne nie stanowią nowego QA APK. **Przy wejściu zawsze pobierz świeży HEAD**, sprawdź Mac checkout/clean status i stan Samsung S22.

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
