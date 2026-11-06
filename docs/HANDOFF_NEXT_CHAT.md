# AI Calls — handoff do nowego czatu (2026-11-06)

**Bazowy HEAD etapu 3, krok 1:** main b7818fb00d138b7e50a1fabe86def02738ddf434 = origin/main, clean przed zmianą. Końcowy commit tego kroku przesuwa HEAD; nowy czat musi go odczytać świeżo. W nowym czacie pobierz **świeży HEAD**, nie zakładaj SHA. [Kompletny checkpoint](checkpoints/2026-11-06-modularization-handoff-final.md) · [Gotowy prompt](NEXT_CHAT_PROMPT.md).

## Zamknięte

- Etap 0: zależności i ochrona flow — PASS, commit 5a63f8b, [dowód](checkpoints/2026-11-06-mod-stage0-baseline.md).
- Etap 1: IncomingIdleTimeout z app przeniesiony do conversation-core, dokładnie ten sam algorytm 30+15 s — PASS, commit 0fb9c52, [dowód](checkpoints/2026-11-06-mod-stage1-incoming-idle-core.md). Czyste testy JVM w core, testy playback w app; full host PASS i kompilacja AndroidTest PASS. Pierwszy źle skonstruowany test i poprawka zachowane w prywatnych receipts.
- Fizyczny INCOMING/end_call: dwie wcześniejsze rozmowy A8→S22 potwierdziły REQUESTED→VALIDATED→DISPATCHED→CONFIRMED i END/COMPLETE. Właściciel potwierdził też samorozłączenie po ciszy 30+15 s. Ostatni bezpieczny odczyt: 40 sesji, IDLE, zero błędów, kampania/retry nieaktywne. Szczegółowy timeline tej ostatniej rozmowy **nieodczytany**.
- **S22 nadal używa wcześniejszego sprawdzonego APK** (przed etapem 1). Nowego APK po przeniesieniu klasy nie instalowano ani nie wykonywano kolejnej rozmowy.

## Etap 2 — DONE / PASS / NO_CHANGE

Na świeżym bazowym HEAD zmapowano `ConversationOutcome` (5 produkcyjnych plików), `ConversationOutcomeBoundary` (5), `ConversationRunResult` (3) i istniejący JVM `ConversationTermination`. Żaden kolejny przenoszony typ nie zmniejsza dziś sprzężenia: wynik scenariusza jest wyłącznie w app, boundary zależy od playback, a run result obejmuje raport/evidence/chat token. Nie zmieniono produkcyjnego kodu ani testów. Celowany JVM, kompilacja AndroidTest, pełny host (266 Python), product friction i clean Git **PASS**. [Checkpoint z mapą i receipts](checkpoints/2026-11-06-mod-stage2-outcomes-nochange.md).

**Dalsza praca:** etap 3 jest następnym osobnym zakresem; nie uruchomiono go w tej sesji. Nowy czat musi odczytać świeży HEAD i claims.

## Etap 3 — krok 1 DONE / PASS

`ConversationSessionRegistry` przeniesiono z app do czystego `conversation-core`: identyczny algorytm tryCreate/release, tylko package + widoczność i jeden import runtime. Cztery testy JVM przeniesione do core. Mac `aicalls-20261106-mod-stage3-preflight-v1` DONE/0 i `aicalls-20261106-mod-stage3-registry-move-gate-v1` DONE/0: core JVM, celowane app JVM, AndroidTest compile, pełny host `host_quality_gate_green=true` (266 Python) i friction PASS. Brak APK/deploy na S22. [Nowy checkpoint](checkpoints/2026-11-06-mod-stage3-session-registry-core.md).

**Następny wyłącznie osobny krok:** ponowna mapa jednej neutralnej reguły stanu tury z `AndroidConversationRuntime`, ewentualnie NO_CHANGE; nie rozpoczynać etapów 4–5 bez osobnego zakresu.

## Granice wykonania i dowodów

- Repo: MichalMatu/ai-calls; branch main; commity [skip ci]. Mac checkout: /Users/michal/agent-workspace/repos/ai-calls/work.
- Pracuj w trybie **GitHub Connector + Mac Local Agent**, bez Codex/ChatGPT Work/GitHub Actions, force-push i nowych gałęzi. Przed taskiem sprawdź aktualny protokół Local Agent, claims i clean HEAD; nie wykonywać konkurencyjnych mutacji.
- Nie dotykać Samsung/Shizuku/PCM/PFD, modelu ani TTS, end_call po TX, polityk zakresu, STOP, takeover, starych prywatnych dowodów. Nie powtarzać fizycznych testów lub ukończonej bramki bez nowej przesłanki; nowa zmiana wymaga własnego GREEN.
- Etap 2 zamknięty jako NO_CHANGE; celowane JVM, pełny host, product friction i kompilacja AndroidTest PASS. W nowym etapie stosować te same bramki do nowej zmiany. Rozróżniaj host QA od fizycznego PROVEN_S22.
- Wciąż osobno QA_PENDING: manual/TAKE OVER/powrót AI/STOP, głos po ostrzeżeniu i szczegółowy timeline timeoutu. F-080 i F-044/F-045/F-048 to backlog funkcjonalny; statusy defektów tylko w [BLOCKERS](../BLOCKERS.md).

## Przeczytać

[AGENTS](../AGENTS.md), [Architektura](ARCHITECTURE.md), [Runtime](CONVERSATION_RUNTIME.md), [Bezpieczeństwo](SECURITY_PRIVACY.md), [Freeze Samsung](PHASE2D_FREEZE_2026-09-18.md), [Roadmapa](ROADMAP.md), [Plan modularizacji](SAFE_MODULARIZATION_PLAN.md), [Checkpoint zamknięcia](checkpoints/2026-11-06-modularization-handoff-final.md). Checkpointy wcześniejsze są historią, nie poleceniami ponowienia.
