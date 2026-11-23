# Architektura MVP

Telecom / exact Call → `AndroidIncomingAssistant` → `AndroidConversationRuntime` → STT / Gemma / TTS → `ToolCallingConversationSession`. Po zatwierdzonym TTS: zaszyfrowana notatka, a opcjonalnie autoryzowany END_CALL.

`app` integruje Android UI, `presentation`, `domain` oraz `data/adapters`. `conversation-core` i `conversation-tools` są czystymi modułami JVM; `audio-bridge` obsługuje PCM, `privileged-helper` system, a `realtime-client` transport. Kontrola importów: `scripts/aicall_tools/diagnostics/module_boundary_gate.py`.

Natywny TTS/probe i projekcje terminalne rozmowy są wydzielone z ekranów/runtime; w istniejących Activity wciąż znajduje się część koordynacji. **To granica na MVP, nie deklaracja pełnego ukończenia migracji Compose.** Zachowano wygląd, nawigację, ustawienia i dane.

Host Binder UID 2000 działa poza APK przez lokalne ADB; po reboocie może wymagać ponownego uruchomienia. Bez automatycznych połączeń. [Mapa 34 Activity](UI_SCREEN_MAP.md) jest kontraktem przyszłej migracji, nie potwierdzeniem fizycznego QA.
