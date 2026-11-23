# Architektura

Telecom/exact Call → `AndroidIncomingAssistant` → `AndroidConversationRuntime` → STT/Gemma/TTS → `ToolCallingConversationSession`. Po zaakceptowanym TTS: niezależny zaszyfrowany zapis notatki, później opcjonalny autoryzowany END_CALL.

Binder/UID 2000: osobno uruchamiany host ADB; APK nie ma tych uprawnień. Reboot może wymagać ponownej zgody. Pakiet testowy jest odseparowany od stabilnego dialera. Wewnętrzny identyfikator zachowano dla zgodności danych i hosta.

`app`: UI/Android/orkiestracja; `conversation-core` i `conversation-tools`: czyste JVM; `audio-bridge`: PCM; `privileged-helper`: system; `realtime-client`: transport. Granice kontroluje `module_boundary_gate.py`. Duży refaktor adapterów po MVP, nie podczas wydania.

Przed Compose/Material 3: odczyty Home/Agent, wybór języka, parser generowania,
zapis profilu i opcje TTS są wydzielone poza widoki. Nadal trzeba oddzielić
cykl TTS/probe i stany ekranów; bez zmian zachowania GSM.

## Status separacji UI i logiki (23.11.2026)

W gałęzi `refactor/modelvoice-presentation-boundary-20261123` przeniesiono właściciela
ustawień modelu i głosu z `audioengines` do `ui/modelvoice`, bez zmiany
reguł zapisu profilu, natywnej weryfikacji i odtwarzania TTS. Dodano kontrolę,
która blokuje importy `ui` lub `presentation` z `audioengines`.

**Nie jest to jeszcze pełna separacja modułowa front/back:** `LanguageModelActivity`
pozostaje w pakiecie `textagent`, a ekran ustawień nadal zarządza cyklem życia
TTS oraz prób modelu. Sześć modułów Gradle ma poprawne dozwolone zależności,
lecz `app` nadal skupia Android UI i adaptery. Dalsze wydzielenia wymagają
charakteryzacyjnych testów, kompilacji na Mac i ponownego fizycznego QA na S22+.
Nie deklarować PASS ani merge do `main` bez tych dowodów.

### Wydzielenie biblioteki modeli — gałąź PR #77

Implementacja widoku biblioteki modeli jest w `ui/modelvoice/ModelLibraryScreenActivity.kt`.
Klasa `textagent.LanguageModelActivity` pozostała w miejscu jako cienki punkt wejścia
zgodny z manifestem i istniejącymi Intentami, w tym trybem menedżera plików.
Transfer modeli, zapisywanie profilu, walidacja plików i mechanizmy ochronne nie
zmieniły zachowania. Test granic pakietów blokuje zależności UI w pozostałym
`textagent`, dopuszczając wyłącznie import klasy ekranu w zgodnym wejściu.
Nadal do wydzielenia: asynchroniczny cykl TTS i odczyty operacyjne do adapterów;
nie należy traktować tej zmiany jako gotowej do wydania bez gradle/QA na urządzeniu.


### Compose migration inventory and gate

[Source-derived screen/settings map](UI_SCREEN_MAP.md) tracks all manifest entrypoints and nested screens. The module gate now rejects Android/AndroidX or UI imports in `presentation`; Agent summaries now use semantic text keys, with Android string-resource mapping in `ui/AgentAiHubActivity`. No UI redesign or runtime behavior changes in this inventory sprint.
