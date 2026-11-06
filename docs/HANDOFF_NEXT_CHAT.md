# AI Calls — handoff do nowego czatu (2026-11-06)

**Zweryfikowany bazowy HEAD etapu 2:** main 06fdd75c7db8949aa64e5a5fb17f124853f7403b = origin/main, clean przed dokumentacją; jeden nowy commit dokumentacyjny etapu 2 przesunie HEAD. W nowym czacie pobierz **świeży HEAD**, nie zakładaj SHA. [Kompletny checkpoint](checkpoints/2026-11-06-modularization-handoff-final.md) · [Gotowy prompt](NEXT_CHAT_PROMPT.md).

## Zamknięte

- Etap 0: zależności i ochrona flow — PASS, commit 5a63f8b, [dowód](checkpoints/2026-11-06-mod-stage0-baseline.md).
- Etap 1: IncomingIdleTimeout z app przeniesiony do conversation-core, dokładnie ten sam algorytm 30+15 s — PASS, commit 0fb9c52, [dowód](checkpoints/2026-11-06-mod-stage1-incoming-idle-core.md). Czyste testy JVM w core, testy playback w app; full host PASS i kompilacja AndroidTest PASS. Pierwszy źle skonstruowany test i poprawka zachowane w prywatnych receipts.
- Fizyczny INCOMING/end_call: dwie wcześniejsze rozmowy A8→S22 potwierdziły REQUESTED→VALIDATED→DISPATCHED→CONFIRMED i END/COMPLETE. Właściciel potwierdził też samorozłączenie po ciszy 30+15 s. Ostatni bezpieczny odczyt: 40 sesji, IDLE, zero błędów, kampania/retry nieaktywne. Szczegółowy timeline tej ostatniej rozmowy **nieodczytany**.
- **S22 nadal używa wcześniejszego sprawdzonego APK** (przed etapem 1). Nowego APK po przeniesieniu klasy nie instalowano ani nie wykonywano kolejnej rozmowy.

## Etap 2 — DONE / PASS / NO_CHANGE

Na świeżym bazowym HEAD zmapowano `ConversationOutcome` (5 produkcyjnych plików), `ConversationOutcomeBoundary` (5), `ConversationRunResult` (3) i istniejący JVM `ConversationTermination`. Żaden kolejny przenoszony typ nie zmniejsza dziś sprzężenia: wynik scenariusza jest wyłącznie w app, boundary zależy od playback, a run result obejmuje raport/evidence/chat token. Nie zmieniono produkcyjnego kodu ani testów. Celowany JVM, kompilacja AndroidTest, pełny host (266 Python), product friction i clean Git **PASS**. [Checkpoint z mapą i receipts](checkpoints/2026-11-06-mod-stage2-outcomes-nochange.md).

**Dalsza praca:** etap 3 jest następnym osobnym zakresem; nie uruchomiono go w tej sesji. Nowy czat musi odczytać świeży HEAD i claims.

## Granice wykonania i dowodów

- Repo: MichalMatu/ai-calls; branch main; commity [skip ci]. Mac checkout: /Users/michal/agent-workspace/repos/ai-calls/work.
- Pracuj w trybie **GitHub Connector + Mac Local Agent**, bez Codex/ChatGPT Work/GitHub Actions, force-push i nowych gałęzi. Przed taskiem sprawdź aktualny protokół Local Agent, claims i clean HEAD; nie wykonywać konkurencyjnych mutacji.
- Nie dotykać Samsung/Shizuku/PCM/PFD, modelu ani TTS, end_call po TX, polityk zakresu, STOP, takeover, starych prywatnych dowodów. Nie powtarzać fizycznych testów lub ukończonej bramki bez nowej przesłanki; nowa zmiana wymaga własnego GREEN.
- Etap 2 zamknięty jako NO_CHANGE; celowane JVM, pełny host, product friction i kompilacja AndroidTest PASS. W nowym etapie stosować te same bramki do nowej zmiany. Rozróżniaj host QA od fizycznego PROVEN_S22.
- Wciąż osobno QA_PENDING: manual/TAKE OVER/powrót AI/STOP, głos po ostrzeżeniu i szczegółowy timeline timeoutu. F-080 i F-044/F-045/F-048 to backlog funkcjonalny; statusy defektów tylko w [BLOCKERS](../BLOCKERS.md).

## Przeczytać

[AGENTS](../AGENTS.md), [Architektura](ARCHITECTURE.md), [Runtime](CONVERSATION_RUNTIME.md), [Bezpieczeństwo](SECURITY_PRIVACY.md), [Freeze Samsung](PHASE2D_FREEZE_2026-09-18.md), [Roadmapa](ROADMAP.md), [Plan modularizacji](SAFE_MODULARIZATION_PLAN.md), [Checkpoint zamknięcia](checkpoints/2026-11-06-modularization-handoff-final.md). Checkpointy wcześniejsze są historią, nie poleceniami ponowienia.
