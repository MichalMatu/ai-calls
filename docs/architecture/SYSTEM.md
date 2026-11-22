# Architektura

Telecom/exact Call → `AndroidIncomingAssistant` → `AndroidConversationRuntime` → STT/Gemma/TTS → `ToolCallingConversationSession`. Po zaakceptowanym TTS: niezależny zaszyfrowany zapis notatki, później opcjonalny autoryzowany END_CALL.

Binder/UID 2000: osobno uruchamiany host ADB; APK nie ma tych uprawnień. Reboot może wymagać ponownej zgody. `.s8lab` jest odseparowany od stabilnego dialera.

`app`: UI/Android/orkiestracja; `conversation-core` i `conversation-tools`: czyste JVM; `audio-bridge`: PCM; `privileged-helper`: system; `realtime-client`: transport. Granice kontroluje `module_boundary_gate.py`. Duży refaktor adapterów po MVP, nie podczas wydania.
