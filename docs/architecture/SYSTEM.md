# AI Calls — architektura

- `app/`: Android UI, exact Call, autoryzacja, STT/Gemma/TTS, historia i harmonogram.
- `conversation-core/`: czyste kontrakty stanów, generacji i IVR/DTMF; `conversation-tools/`: typowane akcje.
- `audio-bridge/` i `privileged-helper/`: zweryfikowany Samsung/Shizuku PCM RX/TX.
- `realtime-client/`: opcjonalny osobny transport; `service-packs/`: scenariusze; `scripts/`: QA.

INCOMING i OUTGOING używają **jednego** `AndroidConversationRuntime`: app-owned Call + grant → RX → STT → model/Skills → zatwierdzenie tekstu/skutków → TTS → TX. Model proponuje, aplikacja decyduje. Nie dodawaj drugiego schedulera, vaultu ani toru audio. [SECURITY](SECURITY.md).
