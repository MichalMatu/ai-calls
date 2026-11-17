# AI Calls — archiwalny final quality checkpoint, 15.11.2026

**Archiwalny stan: HOST-TESTED / REAL_CALL_PHYSICAL_OPEN; nieaktywny plan.** Dzisiejszy backlog: [PROJECT](../../docs/PROJECT.md). To przegląd i poprawki dwóch potwierdzonych usterek, a nie pełny odbiór wszystkich ekranów ani Telecom. Repozytorium `MichalMatu/ai-calls`, `main`; punkt bazowy `6a10bca`, kod po poprawkach `d7a9aa4e8a645f8df7f0cb6f42843f5a9d8a2cd6`.

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

## Historyczna ocena jakości

Na dzień 15.11 nie wykonano pełnego GSM E2E mimo testów off-call i emulatora. Gemma E2B i profil S22 zachowano. Ten zapis nie zastępuje późniejszego potwierdzenia użytkownika, że aplikacja działa z GSM. Plan: [PROJECT](../../docs/PROJECT.md).

## Wyniki z 15.11.2026

**Wynik: HOST + S22 APK/OFF-CALL PASS; REAL_CALL_PHYSICAL_OPEN.**
Stan kodu po poprawkach: `77fdc864fc97b9f95f1f518bf60b58c550edbdf2` (`main`; od `2c70e0f` zmieniały się wyłącznie testy Android i ten checkpoint).

### Kod i model
- `4c69016`: jawny profil rozmowy `en-US` z Android on-device STT, lokalnym Android TTS, angielskim powitaniem, promptem, komunikatami ciszy i błędu; EN nie używa polskiego Parakeet/Piper. Picker pokazuje tylko zaimportowane modele z pozytywną kontrolą kontenera i zgodności. Mac: preflight, jednostkowe i assemble PASS.
- `2c70e0f`: naprawa wypowiadania dodatkowego pytania po angielskim `Goodbye` przy propozycji `END_CALL`; test jednostkowy i APK PASS. Nie zastępuje fizycznego potwierdzenia Telecom.
- `7fb1648`, `b35552c`, `77fdc86`: instrumentacyjny test selektora rozmowy PL→EN→PL. Usunięto z testu nieobecny w emulatorze zewnętrzny silnik Piper i poprawiono identyfikowanie listy AlertDialog po typie widoku. Kompilacja PASS; celowany test po poprawkach: `OK (1 test)` i `passed=true` (`aicalls-emulator-english-picker-isolated-20261115-v4`; opakowanie zadania Local Agent zgłosiło status failed/126 mimo potwierdzonego wyniku wewnątrz).

### Fizyczny Samsung S22+ — faktyczne dowody na nowym APK
- `aicalls-s22-safe-install-preflight-20261115-v1`: sprzęt dokładnie zweryfikowany, Android 16, `IDLE`, AI Calls **już było** domyślnym dialerem, Shizuku działa.
- `aicalls-s22-authorized-update-apk-20261115-v1`: za zgodą właściciela zaktualizowano debug APK **in-place** z kodu `2c70e0f`, SHA-256 `bd683497a252b5bfbec05a373779359c94ed519ab09bcca8bbcf18d50216a040`, wersja `0.2.0`. Po instalacji IDLE, dialer i Shizuku potwierdzone; bez uninstall, kasowania danych, zmian grantu/roli.
- `aicalls-s22-postinstall-app-config-readonly-20261115-v1`: 1 SIM, zachowany zapisany Parakeet PL → Gemma 4 E2B → Piper (Android TTS), nagrywanie przychodzących włączone, brak aktywnego zadania. Nie mylić z profilem domyślnym `AudioEngineProfile.STABLE`.
- `aicalls-s22-current-apk-offcall-audio-smoke-20261115-v1`: **PASS** pojedynczej stałej próby Piper→Parakeet, 0 błędów / 6 słów; TTS 4605 ms, prewarm STT 1958 ms, inferencja 434 ms. To test syntetyczny, nie jakość rozmowy.
- `aicalls-s22-physical-ui-navigation-smoke-20261115-v1` i `aicalls-s22-history-tab-diagnosis-20261115-v1`: potwierdzone ekrany Home, Ustawienia, Model/Głos, wybór języka, Agent i Historia. W pierwszym odczycie Historii wystąpiła krótkotrwała rozbieżność; ponowny test potwierdził otwarcie Activity. Test bez zmian preferencji.
- `aicalls-s22-test-enable-incoming-ai-20261115-v1` i `aicalls-s22-consented-incoming-call-observation-restore-20261115-v1`: krótkie przełączenie MANUAL→AI z zachowaniem SIM/nagrywania; podczas obserwacji **nie przyszło żadne połączenie z drugiego telefonu** (`incoming_calls_observed=0`). Automatyczny powrót do **MANUAL**, `IDLE`, zapis nagrywania bez zmian. Zero połączeń inicjowanych przez agenta i zero globalnych rozłączeń.

### Emulator / import
- Izolowany `medium_phone`: w macierzy `aicalls-emulator-final-targeted-ui-matrix-20261115-v1` **7/8 PASS** (nawigacja, ustawienia, zasoby PL/EN, historia, przejście ekranów, import/odrzucenia); test nowego selektora języka początkowo przekroczył limit. W kolejnej izolowanej próbie wykryto zły identyfikator ListView w samym teście, poprawiono i `aicalls-emulator-english-picker-isolated-20261115-v4` wykazał `passed=true` dla PL→EN→PL, bez dotykania S22. Zatem każdy z 8 przypadków ma pojedynczy instrumentacyjny sukces na docelowych wersjach kodu/testu; **nie oznacza to ponownej pełnej macierzy 8/8 w jednym uruchomieniu**. Przy pierwszej próbie zbiorczej pojawiały się też awarie/timeouty. Fake `.litertlm` sprawdza mechanikę importu i izolację bazowego modelu, nie autentyczną kompatybilność natywną.
- Cały onboarding i każda ścieżka UI nie są jeszcze fizycznie zakwalifikowane, a angielski E2E STT→LLM→TTS wymaga uruchomienia na urządzeniu z faktycznym offline en-US głosem i rozpoznawaniem.

### Zakres historycznych dowodów

C1–C3, END_CALL, barge-in, STOP i odsłuch nie zostały wtedy zweryfikowane podczas pełnej faktycznej rozmowy. Brak nieautoryzowanych połączeń i publikacji prywatnych danych. Obecny P0 GSM: [PROJECT](../../docs/PROJECT.md).

## Checkpoint MVP — 15 listopada 2026

**Oznaczenie:** tag `mvp-s22-2026-11-15` (kompletny punkt powrotu kodu i dokumentacji). **Bazowy kod na S22:** `956b0785f7c4dac5c20a91906a6716facd7e7ce0`; APK `versionName=0.2.0`, `versionCode=2`. Commit checkpointu zmienia tylko dokumentację, bez aktualizacji APK.

### Ostatnie domknięcia

- **Model/import/Test Lab:** `fdaccbf` — test zgodności dowolnego importowanego LiteRT z karty pliku; `d99ea26` — czytelny, trwały wynik PASS/FAIL z przyczyną i komunikatem. Mac `aicalls-model-feedback-build-20261115-v1`: kompilacja i test celowany PASS. Użytkownik sam sprawdził importowany model Granite 4.0 H 1B i Test Lab na S22: działa. To potwierdzenie użytkownika **off-call**, a nie jakość rzeczywistego połączenia.
- **Historia:** sesja miała `timeline.jsonl`, ale brakowało `COMPLETE`, więc „Usuń wszystkie” jej nie obejmowało — metadane S22: `aicalls-s22-history-marker-readonly-20261115-v3`. `e0cb06d` wprowadził usuwanie starego nieukończonego archiwum za zgodą i z kontrolą IDLE/10 min bez zapisu; Mac `aicalls-history-single-delete-guarded-cleanup-build-20261115-v1`: kompilacja + testy celowane PASS. `956b078` uprościł listę: zwykły tap otwiera szczegóły; przytrzymanie wywołuje tylko potwierdzenie usunięcia; bez przycisku ⋮ i dodatkowego menu. Użytkownik potwierdził działanie UX na S22.
- **Instalacja:** Mac Local Agent `aicalls-s22-longpress-delete-ux-install-20261115-v1` — **PASS** budowy i instalacji kodu `956b078`. APK SHA-256 `5671935fa58f2960b5396860a2604859f77db53f2003ad265df00a974811e7e6`, 226287719 bajtów. S22 `IDLE` przed/po, domyślny dialer i Shizuku zachowane. Bez uninstall, kasowania historii i modeli, zmiany roli, połączeń inicjowanych przez agenta. Ostatnia aktualizacja bez dodatkowych testów — potwierdzona kompilacja, instalacja i ręczny odbiór UX.
- Wcześniejsze 60/60 prób torów off-call, 18/18 tur syntetycznych i 5/5 STOP dotyczą **wcześniejszych SHA**, a nie ponownego pełnego przebiegu na `956b078`. Nie przypisywać checkpointowi pełnego produkcyjnego PASS.

### Ograniczenie checkpointu

MVP off-call i historię wtedy potwierdzono, lecz pełny GSM Call nie miał zamkniętego odbioru. Ten stan nie wyznacza obecnej technologii ani priorytetu. [Aktualny plan](../../docs/PROJECT.md).

### Archiwalny punkt powrotu Git

Tag `mvp-s22-2026-11-15` zachowuje dawne pliki i stan kodu, ale zawarty w nim plan nie jest już aktualny. Obecna kolejność w [PROJECT](../../docs/PROJECT.md). Izolowany checkout: `git worktree add --detach <ścieżka> mvp-s22-2026-11-15`. **Nie resetuj ani nie force-pushuj `main`**. Tag obejmuje Git, nie prywatne modele/dane telefonu; wymagają zachowania osobno. Bez schedulerów, automatycznych połączeń i PII/WAV w Git.
