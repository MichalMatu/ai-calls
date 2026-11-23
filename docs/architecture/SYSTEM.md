# Architektura

Telecom / exact Call → `AndroidIncomingAssistant` → `AndroidConversationRuntime` → STT / Gemma / TTS → `ToolCallingConversationSession`. Po zaakceptowanym TTS: niezależny zaszyfrowany zapis notatki, potem opcjonalny autoryzowany END_CALL.

Binder UID 2000 działa wyłącznie w osobnym hoście ADB, nigdy w APK. Po reboocie może być potrzebna ponowna zgoda. Testowy pakiet jest oddzielony od stabilnego dialera; identyfikator wewnętrzny jest zachowany dla danych i hosta.

## Moduły i kierunek zależności

`app` jest punktem integracji Androida, UI, presentation i adapterów. `conversation-core` oraz `conversation-tools` to czyste JVM; `audio-bridge` — PCM; `privileged-helper` — system; `realtime-client` — transport. `module_boundary_gate.py` blokuje niedozwolone zależności Gradle, importy Android do JVM oraz importy UI/Android/R z presentation i backendu modeli.

## Granica Compose / Material 3

Manifest i 34 ekrany są opisane w [mapie](UI_SCREEN_MAP.md). Zachować Android Views, wygląd, tagi, ustawienia, dane i nawigację bez redesignu. Compose + Material 3 jest migracją przyszłą, nie częścią tej serii PR.

Po #79/#80/#81/#83: `ui/modelvoice` i Activity nadal składają widoki; `presentation/modelvoice` zawiera prezentery, walidację profilu i asynchroniczny stan weryfikacji; `data/adapters` zawiera czytniki stanu, fabrykę STT/TTS dla runtime oraz natywny probe importowanego modelu. `SkillLabStartPolicy` wyznacza tryb i waliduje start Lab bez efektów. Bramki blokują importy z Domain i Data wstecz do UI/Presentation oraz Android/UI w czystych modułach JVM.

Nadal wymagają refaktoru: pełny cykl sesji TTS widoku (PR #78 oczekuje fizycznego QA), transfery modeli, obszerna koordynacja runtime/Lab oraz pozostałe Activity. Brak kwalifikacji realnych rozmów przez bezpieczne testy UI; przed scalaniem następnych PR wymagane są kompilacja Gradle i regresja S22+ na dokładnym SHA.
