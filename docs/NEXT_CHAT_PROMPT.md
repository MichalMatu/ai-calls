# AI Calls — kolejny osobny krok etapu 3

Kontynuuj `MichalMatu/ai-calls` na świeżym `main` **tylko GitHub Connector + Mac Local Agent**: macbook-michal / cfea4cc3-b54c-4b6d-b9a6-758d12733e98, checkout `/Users/michal/agent-workspace/repos/ai-calls/work`. Bez Codex, ChatGPT Work, GitHub Actions (również odczytów), nowych gałęzi, force-push i powtarzania zaakceptowanych testów fizycznych.

**Zamknięte:** etapy 0–1 PASS, etap 2 NO_CHANGE/PASS, etap 3 krok 1 PASS — `ConversationSessionRegistry` i 4 testy JVM w conversation-core bez zmiany algorytmu. [Handoff](HANDOFF_NEXT_CHAT.md), [checkpoint kroku](checkpoints/2026-11-06-mod-stage3-session-registry-core.md), [plan](SAFE_MODULARIZATION_PLAN.md). Bazowy HEAD poprzedniego kroku: `b7818fb00d138b7e50a1fabe86def02738ddf434`; nie zakładać go jako bieżącego.

**Cel jednego kolejnego chatu:** po fresh HEAD, clean Git i aktualnym host/claims/recent task-result przeanalizować pozostałą neutralną logikę stanu tury w `AndroidConversationRuntime`. Wydzielić co najwyżej jedną czystą regułę, jeśli istotnie zmniejsza zależności, albo udokumentować NO_CHANGE. Zachować jeden runtime INCOMING/OUTGOING, 30+15 s, STOP, takeover, grant i Call ownership, brak stale TX, zatwierdzony TTS → pełny TX → end_call. Nie przenosić Android Telecom, Handler, audio, Shizuku PCM/PFD ani recorder. Nie zaczynać etapów 4–5.

**Gate:** celowane JVM, `bash scripts/verify_host.sh`, friction audit, odpowiednia kompilacja AndroidTest, git diff --check, checkpoint/handoff, jeden commit `[skip ci]`, clean main/origin; bez fizycznych rozmów bez nowej przesłanki.

**QA_PENDING osobno:** szczegółowy timeline ostatniego timeoutu, manual/TAKE OVER/powrót AI/STOP, głos podczas 15 s grace. S22 ma wcześniejsze APK sprzed etapu 1. Nie utożsamiać HOST_GREEN z PROVEN_S22, nie publikować WAV, transkryptów ani PII; blokery tylko w BLOCKERS.
