# AI Calls — handoff do nowego czatu (2026-11-06)

**Zweryfikowany bazowy HEAD aktualnego sprintu:** main 614b0fadbd5df806127008cb0b01891f4a0bdc26 = origin/main, clean przed F-092. Commit tej poprawki przesuwa HEAD; zawsze pobierz świeży main/origin. W nowym czacie pobierz **świeży HEAD**, nie zakładaj SHA. [Kompletny checkpoint](checkpoints/2026-11-06-modularization-handoff-final.md) · [Gotowy prompt](NEXT_CHAT_PROMPT.md).

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

## Autonomiczny sprint F-092 — source PASS

Zamknięto wąski defekt kontraktu promptu: rola żeńskiej asystentki bez drugiego powitania i bez dosłownego wzorca zwrotu z ukośnikiem. `ConversationInstructionBuilder` + `ConversationReplyProtocolTest`, dwa pliki produkcja/test. Mac zadania `aicalls-20261106-autosprint-f092-preflight-v1` oraz `aicalls-20261106-autosprint-f092-prompt-regression-v1` oba DONE/0; 217,568 s testów, celowane JVM, AndroidTest compile, pełny host (266 Python) i product friction PASS. [Checkpoint](checkpoints/2026-11-06-autosprint-f092-language-role.md). F-092 ma status FIXED_IN_SOURCE, fizyczna jakość mowy pozostaje QA_PENDING.

**Nie zamknięto automatycznie** F-080/F-048/F-045/F-047 ani fizycznego F-091/F-099. Następna jedna bezpieczna czynność: read-only, bez-PII audyt przyczyny niewysłania typed end_call w zachowanym ostatnim wyniku F-091; żadnych połączeń bez konkretnego zakresu.

## Granice wykonania i dowodów

- Repo: MichalMatu/ai-calls; branch main; commity [skip ci]. Mac checkout: /Users/michal/agent-workspace/repos/ai-calls/work.
- Pracuj w trybie **GitHub Connector + Mac Local Agent**, bez Codex/ChatGPT Work/GitHub Actions, force-push i nowych gałęzi. Przed taskiem sprawdź aktualny protokół Local Agent, claims i clean HEAD; nie wykonywać konkurencyjnych mutacji.
- Nie dotykać Samsung/Shizuku/PCM/PFD, modelu ani TTS, end_call po TX, polityk zakresu, STOP, takeover, starych prywatnych dowodów. Nie powtarzać fizycznych testów lub ukończonej bramki bez nowej przesłanki; nowa zmiana wymaga własnego GREEN.
- Etap 2 zamknięty jako NO_CHANGE; celowane JVM, pełny host, product friction i kompilacja AndroidTest PASS. W nowym etapie stosować te same bramki do nowej zmiany. Rozróżniaj host QA od fizycznego PROVEN_S22.
- Wciąż osobno QA_PENDING: manual/TAKE OVER/powrót AI/STOP, głos po ostrzeżeniu i szczegółowy timeline timeoutu. F-080 i F-044/F-045/F-048 to backlog funkcjonalny; statusy defektów tylko w [BLOCKERS](../BLOCKERS.md).

## Przeczytać

[AGENTS](../AGENTS.md), [Architektura](ARCHITECTURE.md), [Runtime](CONVERSATION_RUNTIME.md), [Bezpieczeństwo](SECURITY_PRIVACY.md), [Freeze Samsung](PHASE2D_FREEZE_2026-09-18.md), [Roadmapa](ROADMAP.md), [Plan modularizacji](SAFE_MODULARIZATION_PLAN.md), [Checkpoint zamknięcia](checkpoints/2026-11-06-modularization-handoff-final.md). Checkpointy wcześniejsze są historią, nie poleceniami ponowienia.
