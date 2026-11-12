# Architektura

`app`: Call, UI, scheduler, model, historia. `conversation-core`: tury/generacje, DTMF. `conversation-tools`: skutki. `audio-bridge`/`privileged-helper`: Samsung/Shizuku PCM. `realtime-client`: osobny transport.

INCOMING i OUTGOING współdzielą `AndroidConversationRuntime`. Bez drugiego toru audio/schedulera/vaultu.
