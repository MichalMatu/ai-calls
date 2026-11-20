# Architektura

S22+ GSM: Telecom → `AndroidIncomingAssistant` (exact Call) → `AndroidConversationRuntime` (RX/TX) → STT/model/TTS → `ToolCallingConversationSession` → SAVE_NOTE/END_CALL po TTS.

GSM Binder wymaga hosta UID 2000: autoryzowane ADB/Kadb bez Shizuku; APK sama nie ma UID 2000. Po restarcie host może wymagać ponownego uruchomienia.

Moduły: `app` — Android UI, kompozycja i orkiestracja; `conversation-core` — czysty JVM/stany/Lab; `conversation-tools` — kontrakty; `audio-bridge` — PCM; `privileged-helper` — system; `realtime-client` — transport i `audio-bridge`. Zależności pilnuje `module_boundary_gate.py`.

Część orkiestracji GSM i adapterów modelu nadal w `app`; pełna separacja backend/frontend OPEN. [Plan](../PROJECT.md).
