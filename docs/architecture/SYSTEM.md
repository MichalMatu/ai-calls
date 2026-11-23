# Architektura

Telecom / exact Call → `AndroidIncomingAssistant` → `AndroidConversationRuntime` → STT / Gemma / TTS → `ToolCallingConversationSession`. Po zaakceptowanym TTS: niezależny zaszyfrowany zapis notatki, potem opcjonalny autoryzowany END_CALL.

Binder UID 2000 działa wyłącznie w osobnym hoście ADB, nigdy w APK. Po reboocie może być potrzebna ponowna zgoda. Testowy pakiet jest oddzielony od stabilnego dialera; identyfikator wewnętrzny jest zachowany dla danych i hosta.

## Moduły i kierunek zależności

`app` jest punktem integracji Androida, UI, presentation i adapterów. `conversation-core` oraz `conversation-tools` to czyste JVM; `audio-bridge` — PCM; `privileged-helper` — system; `realtime-client` — transport. `module_boundary_gate.py` blokuje niedozwolone zależności Gradle, importy Android do JVM oraz importy UI/Android/R z presentation i backendu modeli.

## Granica Compose / Material 3

W PR #77 ekrany ustawień modelu i biblioteka plików są w `ui/modelvoice`; prezentery, kontroler profilu, readiness i stan asynchroniczny w `presentation/modelvoice`. `textagent.LanguageModelActivity` pozostaje zgodnym z manifestem punktem wejścia; `AudioEngineSettingsActivity` dziedziczy ten shim. Agent hub mapuje semantyczne klucze tekstowe do zasobów Androida dopiero w UI. Odczyt listy silników i głosów TTS odbywa się w `data/adapters/AndroidTtsVoiceInventoryReader`; UI zachowuje cykl życia i preview.

Kompletna [mapa 34 Activity, zagnieżdżonych ekranów, dialogów i ustawień](UI_SCREEN_MAP.md) jest kontraktem przyszłej migracji, a nie potwierdzeniem QA.

Do wydzielenia za testami charakteryzacyjnymi: cykl natywnego TTS/probe, koordynacja transferów, rozbudowany runtime konwersacji i Lab. Zachować wygląd, stringi, tagi, stan, bezpieczeństwo exact-Call/SIM i dotychczasowe zachowanie; bez nowych modułów Gradle bez uzasadnienia. Nie scalać PR przed kompilacją Gradle i bezpieczną regresją S22+.
