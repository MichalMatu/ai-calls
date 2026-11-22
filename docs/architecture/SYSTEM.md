# Architektura

Telecom/exact Call → `AndroidIncomingAssistant` → `AndroidConversationRuntime` → STT/Gemma/TTS → `ToolCallingConversationSession`. Po zaakceptowanym TTS: niezależny zaszyfrowany zapis notatki, później opcjonalny autoryzowany END_CALL.

Binder/UID 2000: osobno uruchamiany host ADB; APK nie ma tych uprawnień. Reboot może wymagać ponownej zgody. Pakiet testowy jest odseparowany od stabilnego dialera. Wewnętrzny identyfikator zachowano dla zgodności danych i hosta.

`app`: UI/Android/orkiestracja; `conversation-core` i `conversation-tools`: czyste JVM; `audio-bridge`: PCM; `privileged-helper`: system; `realtime-client`: transport. Granice kontroluje `module_boundary_gate.py`. Duży refaktor adapterów po MVP, nie podczas wydania.

Przed Compose/Material 3: odczyty Home/Agent, wybór języka, parser generowania,
zapis profilu i opcje TTS są wydzielone poza widoki. Nadal trzeba oddzielić
cykl TTS/probe i stany ekranów; bez zmian zachowania GSM.
