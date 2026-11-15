# AI Calls — FINAL QUALITY CHECKPOINT, 15.11.2026

**Status: HOST-TESTED / REAL_CALL_PHYSICAL_OPEN.** To przegląd i poprawki dwóch potwierdzonych usterek, a nie pełny odbiór wszystkich ekranów ani Telecom. Repozytorium `MichalMatu/ai-calls`, `main`; punkt bazowy `6a10bca`, kod po poprawkach `d7a9aa4e8a645f8df7f0cb6f42843f5a9d8a2cd6`.

## Usterki naprawione

- `38092fae`: opcjonalny komunikat głosowy QuickAck nie wywołuje już stanowej polityki zgody odpowiedzi modelu, która kasowała oczekującą propozycję `END_CALL`/`SAVE_NOTE`. Dwa wywołania w ścieżce TX korzystają z osobnej zgody na krótkie potwierdzenie. Test regresyjny sprawdza `END_CALL` po zakończeniu odtwarzania.
- `d7a9aa4e`: tekst krótkiego potwierdzenia dobierany z `languageTag` profilu (`pl` lub neutralne angielskie brzmienie), bez modyfikacji zapisanych ustawień. Dodane testy doboru języka.

## Wyniki i granice dowodów

- **PASS — Mac host:** preflight, `:app:compileDebugKotlin`, `:app:testDebugUnitTest`, `:app:assembleDebug` na obu commitach. Mac Local Agent receipts: `aicalls-final-quick-ack-tool-state-fix-20261115-v4`, `aicalls-final-quick-ack-english-profile-20261115-v1`.
- **PASS — fizyczny S22, wcześniejsze SHA, off-call:** 60/60 prób torów audio, 18/18 syntetycznych tur dialogu, 5/5 STOP. Dokładne warunki: [CHECKPOINT_HANDOFF_2026-11-15](CHECKPOINT_HANDOFF_2026-11-15.md) i [AUDIO_ROUTE_QUALITY](AUDIO_ROUTE_QUALITY.md). Te macierze nie zostały ponowione dla nowego HEAD.
- **NOT_TESTED — bieżący HEAD:** emulatorowe przejście całej nawigacji, onboarding, instalacja/aktualizacja i aplikacja na fizycznym S22. Nie instalowano APK, nie zmieniano roli, grantów, zapisanej konfiguracji ani danych.
- **BLOCKED / PHYSICAL_OPEN:** rzeczywiste połączenia 4–6 tur, długi RX, barge-in, STOP podczas Telecom, rozłączenie `END_CALL` po pożegnaniu, odsłuch/nagrywanie, jakość dialogu i angielski E2E. Brak zgody na realny Call i nową instalację; nie wykonywano takich działań.

## Ryzyka odkryte, niezmienione

- `AudioEngineRegistry.incompatibility` odrzuca `languageTag != "pl-PL"`. Nowy angielski tekst QuickAck **nie oznacza** obsługi angielskiego profilu STT/LLM/TTS. Angielski rynek pozostaje niezakwalifikowany.
- Picker dialogu w `ModelVoiceSettings` pokazuje Gemma E2B oraz modele właściciela z importu, lecz `LanguageModelCatalog` nadal przechowuje statyczne metadane E4B/Qwen dla istniejących ścieżek legacy/lab. Bez dowodu bezpiecznej migracji nie usuwano wpisów ani nie rozszerzano silników. Generic import i kompatybilność wymagają pełnego flow na emulatorze i urządzeniu.
- Nie potwierdzono wszystkich ekranów ani ścieżek wychodzących. Nie deklarować zakończonego audytu UX/Telecom ani gotowości produkcyjnej.

## Handoff i bramka końcowa

Zachować czysty `main`, Gemma E2B jako bazę, aktualny profil S22 i istniejący system UI. Dalszy odbiór: emulator PL/EN w dozwolonym zakresie, import → weryfikacja → wybór → powrót do Gemmy, onboarding/uprawnienia/historia, a po osobnej zgodzie i sprawdzeniu IDLE dokładny test przychodzącego Call C1–C3. Nie uruchamiać automatycznych połączeń, instalacji ani schedulerów. **FINAL QUALITY: PARTIAL; real-call acceptance open.**

## Uzupełnienie — sprint zamykający 15.11.2026 (po zgodzie użytkownika)

**Wynik: HOST + S22 APK/OFF-CALL PASS; REAL_CALL_PHYSICAL_OPEN.**
Stan kodu po poprawkach: `7fb1648ff5e6c2c9aca804802db460ff634f1c07` (`main`; ostatni commit wyłącznie androidTest).

### Kod i model
- `4c69016`: jawny profil rozmowy `en-US` z Android on-device STT, lokalnym Android TTS, angielskim powitaniem, promptem, komunikatami ciszy i błędu; EN nie używa polskiego Parakeet/Piper. Picker pokazuje tylko zaimportowane modele z pozytywną kontrolą kontenera i zgodności. Mac: preflight, jednostkowe i assemble PASS.
- `2c70e0f`: naprawa wypowiadania dodatkowego pytania po angielskim `Goodbye` przy propozycji `END_CALL`; test jednostkowy i APK PASS. Nie zastępuje fizycznego potwierdzenia Telecom.
- `7fb1648`: test przełącznika języka PL→EN→PL oraz zerowania niekompatybilnych głosów, skompilowany jako `androidTest`; uruchomienie w macierzy emulatorowej jeszcze do potwierdzenia.

### Fizyczny Samsung S22+ — faktyczne dowody na nowym APK
- `aicalls-s22-safe-install-preflight-20261115-v1`: sprzęt dokładnie zweryfikowany, Android 16, `IDLE`, AI Calls **już było** domyślnym dialerem, Shizuku działa.
- `aicalls-s22-authorized-update-apk-20261115-v1`: za zgodą właściciela zaktualizowano debug APK **in-place** z kodu `2c70e0f`, SHA-256 `bd683497a252b5bfbec05a373779359c94ed519ab09bcca8bbcf18d50216a040`, wersja `0.2.0`. Po instalacji IDLE, dialer i Shizuku potwierdzone; bez uninstall, kasowania danych, zmian grantu/roli.
- `aicalls-s22-postinstall-app-config-readonly-20261115-v1`: 1 SIM, zachowany zapisany Parakeet PL → Gemma 4 E2B → Piper (Android TTS), nagrywanie przychodzących włączone, brak aktywnego zadania. Nie mylić z profilem domyślnym `AudioEngineProfile.STABLE`.
- `aicalls-s22-current-apk-offcall-audio-smoke-20261115-v1`: **PASS** pojedynczej stałej próby Piper→Parakeet, 0 błędów / 6 słów; TTS 4605 ms, prewarm STT 1958 ms, inferencja 434 ms. To test syntetyczny, nie jakość rozmowy.
- `aicalls-s22-physical-ui-navigation-smoke-20261115-v1` i `aicalls-s22-history-tab-diagnosis-20261115-v1`: potwierdzone ekrany Home, Ustawienia, Model/Głos, wybór języka, Agent i Historia. W pierwszym odczycie Historii wystąpiła krótkotrwała rozbieżność; ponowny test potwierdził otwarcie Activity. Test bez zmian preferencji.
- `aicalls-s22-test-enable-incoming-ai-20261115-v1` i `aicalls-s22-consented-incoming-call-observation-restore-20261115-v1`: krótkie przełączenie MANUAL→AI z zachowaniem SIM/nagrywania; podczas obserwacji **nie przyszło żadne połączenie z drugiego telefonu** (`incoming_calls_observed=0`). Automatyczny powrót do **MANUAL**, `IDLE`, zapis nagrywania bez zmian. Zero połączeń inicjowanych przez agenta i zero globalnych rozłączeń.

### Emulator / import
- Izolowany `medium_phone`: pojedyncze testy renderowania modelu/głosu, importu i przełączania głównych zakładek zwróciły `OK (1 test)`; zbiorcza próba wcześniej uległa awarii lub timeoutowi instrumentacji. Oddzielna ośmiotestowa macierz `aicalls-emulator-final-targeted-ui-matrix-20261115-v1`: **wynik końcowy oczekiwany**. Test importu używa syntetycznego pliku — nie dowodzi natywnej zgodności dowolnego prawdziwego modelu.
- Cały onboarding i każda ścieżka UI nie są jeszcze fizycznie zakwalifikowane, a angielski E2E STT→LLM→TTS wymaga uruchomienia na urządzeniu z faktycznym offline en-US głosem i rozpoznawaniem.

### Ostatnia bramka
**C1–C3 / END_CALL / barge-in / STOP / odsłuch: PHYSICAL_OPEN** — brak realnego połączenia oznacza brak dowodu jakości, rozłączenia oraz nagrywania. W następnym nadzorowanym oknie właściciel dzwoni na S22 z drugiego telefonu; po teście weryfikacja prywatnych, pozbawionych treści zdarzeń `END_CALL`, RX/TX i przywrócenie `MANUAL`. Nie wolno ogłaszać produkcyjnego PASS, automatycznie dzwonić, pozostawić sekretarki w trybie AI ani kopiować numerów, dialogów czy WAV do GitHub.

