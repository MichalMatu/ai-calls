# Architektura — granice systemu

## Jeden runtime
- `app/` — Android UI, kontrola realnego połączenia, adaptery modelu/mowy, archiwum i infrastruktura.
- `conversation-core/` — Kotlin/JVM: koordynacja stanów i generacji rozmowy oraz kontrakty IVR/DTMF; bez Android/MediaPlayer.
- `conversation-tools/` — typowane narzędzia i wykonanie przez aplikacyjne porty, bez uprawnień modelu.
- `audio-bridge/`, `privileged-helper/` — istniejący izolowany PCM RX/TX Samsung/Shizuku, jedno app-owned Call; nie duplikować ani nie przepisywać bez fizycznego QA.
- `realtime-client/` — opcjonalny Realtime transport, inny niż lokalny Gemma; jego barge-in PASS nie dowodzi lokalnego barge-in.
- `service-packs/` — deklaratywne materiały scenariuszy i Skills; `scripts/` — Host QA, testy, offline/USB diagnostyka; `benchmarks/` — testowe dane.

**Własność:** aplikacja posiada Call, grant, STT/TTS, kontrolę wyjścia oraz recorder; model/Skill proponują tekst i typed action, nie wykonują skutków. INCOMING i OUTGOING prowadzą do jednego `AndroidConversationRuntime`, bez drugiego runnera.

## Domyślny przepływ
`Exact Call + scoped grant` → `PCM RX` → `STT` → `Gemma E2B + contextual Skills` → `TextOutputApprovalPolicy/FactDisclosurePolicy/CallCommitmentGate` → `TTS` → `app-owned TX`, z rejestracją prywatnych zdarzeń. STOP/takeover unieważniają generację zanim spóźniony callback opublikuje TX.

## Reguły zmian
Wyraźne interfejsy, testy na granicach, brak alternatywnego schedulera, replay tool efektów i drugiego vault. W plikach modułów utrzymuj tylko bieżący kontrakt, nie pomiary kolejnych sprintów. Frozen Samsung PCM ma dedykowany gate fizyczny. Szczegóły: [SECURITY](SECURITY.md), [runtime](../modules/CONVERSATION.md), [telephony](../modules/INCOMING_OUTGOING.md).
