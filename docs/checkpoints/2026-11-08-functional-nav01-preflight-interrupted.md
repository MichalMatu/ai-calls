# AI Calls — preflight i przerwany odbiór NAV-01 (2026-11-08)

**Baseline:** `MichalMatu/ai-calls main@0e7dd43244edd6c22ce398490806c4a70508d487`, Mac checkout czysty, zgodny z origin/main. GitHub Connector + Mac Local Agent. Dokumenty: AGENTS, README, handoff, macierz, checkpoint przedodbiorowy, roadmapa, architektura, BLOCKERS, privacy i autonomous operation.

## Preflight read-only — PASS

- `aicalls-fa-preflight-s22-20261108-1018-v1` — done/0: Mac checkout clean; Samsung `SM-S906B` na USB, serial zgodny, `mCallState=0` w obu odczytach; APK `pl.michalmatu.aicallbridge` v0.2.0/code 2; telefon/mikrofon/Shizuku API granted. Pomocnicza lista pakietów zgłosiła odmowę shell względem innego użytkownika Android 151, bez wpływu na identyfikację APK.
- `aicalls-fa-models-roles-services-readonly-20261108-v1` — done/0: AI Calls ma rolę dialera, proces Shizuku widoczny, brak aktywnej usługi kampanii, telefon `IDLE`; obecne E2B/E4B/Qwen3 1.7B/Qwen3 0.6B mają oczekiwane **rozmiary plików**; nie badano SHA-256, Parakeet ani faktycznego działania każdego modelu. Profil to `ANDROID_STT / GEMMA_4_E2B / ANDROID_TTS_PL` (odczyt, nie zapis).
- `git diff --name-only 3029034..0e7dd432` obejmuje tylko dokumentację; testów Mac 356/356, UI 28/28+2/2, dwóch lokalnych tur/STOP i syntetycznego TTS→Parakeet STT nie powtarzano, a ich zakres nie został rozszerzony.

## NAV-01 — PARTIAL, nie FAIL aplikacji i nie pełny PASS

- Kryterium: wejście we wszystkie sześć zakładek, zaznaczenie aktywnej, powrót, brak utraty stanu i duplikatów Activity.
- `aicalls-fa-nav01-device-six-tabs-20261108-1023-v1` — fizyczne ADB taps i hierarchia potwierdziły Start → Historia → Agent AI → Laboratorium i prawidłową selekcję tych czterech; kolejna asercja `WRONG_SELECTED_TAB:4:[3]` przy próbie Modele przerwała test. Nie została zreprodukowana jako błąd samego kodu.
- `aicalls-fa-nav01-lab-models-tap-diagnosis-20261108-v1` = `invalid_task_file` przed wykonaniem (budżet timeout), `...v2` = ADB error; `aicalls-fa-nav01-android-transport-ui-diagnostics-20261108-v1` = done/0 i ADB/uiautomator działają; `aicalls-fa-nav01-models-tap-repro-20261108-v1` = brak dolnej belki AI Calls, bo nie była na pierwszym planie.
- `aicalls-fa-nav01-foreground-lockscreen-check-20261108-v1` = done/0: S22 awake, brak keyguard/input restriction, `topResumedActivity=pl.pockethost.companion/.MainActivity` i jedynie pakiet PocketHost w zrzucie UI. To konkurencyjny foreground, nie potwierdzony błąd AI Calls. Zatrzymano dalsze tapy.
- Modele/Ustawienia, Back, brak duplikacji Activity, restart/trwałość danych pozostają NOT_RUN. Screenshoty NAV-01 w prywatnym katalogu Mac `~/Library/Application Support/AI-Calls/functional/2026-11-08-nav01`; brak prywatnych danych w Git.

## Granice / continuation

Nie wykonano realnego połączenia, skutku u operatora, instalacji, pobierania modeli, zmiany profilu, pracy na prywatnym sejfie ani modyfikacji danych właściciela. `PASS_REAL_CALL` **NOT_RUN**. F-080/F-122/F-123/F-124 **OPEN** bez nowej reprodukcji defektu aplikacji, więc nie tworzono nowego numeru w BLOCKERS. Kontynuuj brakujące NAV-01, gdy AI Calls będzie bezpiecznie na pierwszym planie (bez zakłócania PocketHost), potem NAV-02 i HOME bez Call. Zachowaj historie receipts.
