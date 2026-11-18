# Architektura

S22+ GSM: Telecom → `AndroidIncomingAssistant` (exact Call) → `AndroidConversationRuntime` (tury/RX/TX) → `LocalSpeechTextPipeline` (STT/Gemma/TTS) → `ToolCallingConversationSession` → `conversation-tools` (SAVE_NOTE/END_CALL po TTS). Host `GsmShellBinderCallMediaSessionBackend` działa z ADB UID 2000, nie z uprawnień APK.

Moduły: `app` integruje Android/UI/telefon/model; `conversation-core` trzyma stany/generacje; `conversation-tools` kontrakty akcji; `audio-bridge`, `privileged-helper`, `realtime-client` to osobne/starsze adaptory, nie aktualny RX/TX S22.

Docelowe podziały i kryteria: [PROJECT](../PROJECT.md). Nie przełączać toru na Shizuku/SIP.
