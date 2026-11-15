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
