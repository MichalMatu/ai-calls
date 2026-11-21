# Architektura

S22+ GSM: Telecom → `AndroidIncomingAssistant` (exact Call) → `AndroidConversationRuntime` (RX/TX) → STT/model/TTS → `ToolCallingConversationSession` → SAVE_NOTE/END_CALL po TTS.

Jeden transport GSM Binder wymaga hosta UID 2000 uruchamianego przez autoryzowane ADB/Kadb. APK nie ma UID 2000; po restarcie host może wymagać ponownego uruchomienia. Aktywny pakiet to `.s8lab`; release pozostaje fail-closed do osobnej kwalifikacji pakietu, podpisu i IPC.

Moduły: `app` — Android UI, kompozycja i orkiestracja; `conversation-core` — czysty JVM/stany/Lab; `conversation-tools` — kontrakty; `audio-bridge` — PCM; `privileged-helper` — system; `realtime-client` — transport i `audio-bridge`. Zależności pilnuje `module_boundary_gate.py`.

Część orkiestracji GSM i adapterów modelu nadal w `app`; pełna separacja backend/frontend OPEN. [Plan](../PROJECT.md).
