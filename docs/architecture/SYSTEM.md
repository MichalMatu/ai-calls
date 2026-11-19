# Architektura

S22+ GSM: Telecom → `AndroidIncomingAssistant` (exact Call) → `AndroidConversationRuntime` (RX/TX) → STT/Gemma/TTS → `ToolCallingConversationSession` → SAVE_NOTE/END_CALL po TTS.

`GsmShellBinderCallMediaSessionBackend` wymaga hosta UID 2000. Start: lokalny Kadb + NSD (`127.0.0.1`, klucz ADB, przycisk AI Calls) albo awaryjnie Mac ADB. Sam APK nie ma UID 2000. Jednorazowy kod w prywatnym powiadomieniu pozwala parować bez opuszczania okna.

Moduły: `app` = Android/UI/GSM; `conversation-core` = stany; `conversation-tools` = akcje; pozostałe adaptory nie są głównym GSM RX/TX. [Plan](../PROJECT.md).
