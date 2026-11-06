# AI Calls — handoff do nowego czatu (2026-11-06)

**Stan źródła przed checkpointem:** main 0fb9c52d20df93a86744ae341fe5fda4d6c78498, zgodny z origin i czysty; końcowy commit dokumentacji może go przesunąć. W nowym czacie pobierz **świeży HEAD**, nie zakładaj SHA. [Kompletny checkpoint](checkpoints/2026-11-06-modularization-handoff-final.md) · [Gotowy prompt](NEXT_CHAT_PROMPT.md).

## Zamknięte

- Etap 0: zależności i ochrona flow — PASS, commit 5a63f8b, [dowód](checkpoints/2026-11-06-mod-stage0-baseline.md).
- Etap 1: IncomingIdleTimeout z app przeniesiony do conversation-core, dokładnie ten sam algorytm 30+15 s — PASS, commit 0fb9c52, [dowód](checkpoints/2026-11-06-mod-stage1-incoming-idle-core.md). Czyste testy JVM w core, testy playback w app; full host PASS i kompilacja AndroidTest PASS. Pierwszy źle skonstruowany test i poprawka zachowane w prywatnych receipts.
- Fizyczny INCOMING/end_call: dwie wcześniejsze rozmowy A8→S22 potwierdziły REQUESTED→VALIDATED→DISPATCHED→CONFIRMED i END/COMPLETE. Właściciel potwierdził też samorozłączenie po ciszy 30+15 s. Ostatni bezpieczny odczyt: 40 sesji, IDLE, zero błędów, kampania/retry nieaktywne. Szczegółowy timeline tej ostatniej rozmowy **nieodczytany**.
- **S22 nadal używa wcześniejszego sprawdzonego APK** (przed etapem 1). Nowego APK po przeniesieniu klasy nie instalowano ani nie wykonywano kolejnej rozmowy.

## Kolejny i jedyny punkt rozwojowy

**Etap 2: typowane wyniki rozmowy.** Na początek sprawdź referencje ConversationOutcome i ConversationOutcomeBoundary (po 5 plików produkcyjnych), ConversationRunResult (3) i ConversationTermination w core. Po potwierdzeniu opłacalności przenieś **najwyżej jeden** czysty typ/kontrakt do conversation-core; zmigruj wszystkich konsumentów, testy i importy, bez zmiany zachowania. Jeśli brak korzyści, zapisz NO_CHANGE z uzasadnieniem. [Plan i gates](SAFE_MODULARIZATION_PLAN.md).

## Granice wykonania i dowodów

- Repo: MichalMatu/ai-calls; branch main; commity [skip ci]. Mac checkout: /Users/michal/agent-workspace/repos/ai-calls/work.
- Pracuj w trybie **GitHub Connector + Mac Local Agent**, bez Codex/ChatGPT Work/GitHub Actions, force-push i nowych gałęzi. Przed taskiem sprawdź aktualny protokół Local Agent, claims i clean HEAD; nie wykonywać konkurencyjnych mutacji.
- Nie dotykać Samsung/Shizuku/PCM/PFD, modelu ani TTS, end_call po TX, polityk zakresu, STOP, takeover, starych prywatnych dowodów. Nie powtarzać fizycznych testów lub ukończonej bramki bez nowej przesłanki; nowa zmiana wymaga własnego GREEN.
- Etap 2: celowane JVM + scripts/verify_host.sh i audit_product_friction, w razie zmiany Android importów kompilacja AndroidTest; dokumentacja i czysty commit. Rozróżniaj host QA od fizycznego PROVEN_S22.
- Wciąż osobno QA_PENDING: manual/TAKE OVER/powrót AI/STOP, głos po ostrzeżeniu i szczegółowy timeline timeoutu. F-080 i F-044/F-045/F-048 to backlog funkcjonalny; statusy defektów tylko w [BLOCKERS](../BLOCKERS.md).

## Przeczytać

[AGENTS](../AGENTS.md), [Architektura](ARCHITECTURE.md), [Runtime](CONVERSATION_RUNTIME.md), [Bezpieczeństwo](SECURITY_PRIVACY.md), [Freeze Samsung](PHASE2D_FREEZE_2026-09-18.md), [Roadmapa](ROADMAP.md), [Plan modularizacji](SAFE_MODULARIZATION_PLAN.md), [Checkpoint zamknięcia](checkpoints/2026-11-06-modularization-handoff-final.md). Checkpointy wcześniejsze są historią, nie poleceniami ponowienia.
