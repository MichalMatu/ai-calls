# AI Calls — handoff do nowego czatu (2026-11-06)

**Referencyjny HEAD pełnej kontroli:** `1d260d8424e152b8e3d072842e7bf92405b80e6e` (przed dokumentacyjnymi commitami, full host PASS). Zawsze odczytaj świeży `main`; `614b0fad` jest historycznym HEAD sprzed F-092. [Kompletny checkpoint](checkpoints/2026-11-06-ai-calls-complete-code-doc-qa-handoff.md) · [Gotowy prompt](NEXT_CHAT_PROMPT.md).

## Zamknięte

- Etap 0: zależności i ochrona flow — PASS, commit 5a63f8b, [dowód](checkpoints/2026-11-06-mod-stage0-baseline.md).
- Etap 1: IncomingIdleTimeout z app przeniesiony do conversation-core, dokładnie ten sam algorytm 30+15 s — PASS, commit 0fb9c52, [dowód](checkpoints/2026-11-06-mod-stage1-incoming-idle-core.md). Czyste testy JVM w core, testy playback w app; full host PASS i kompilacja AndroidTest PASS. Pierwszy źle skonstruowany test i poprawka zachowane w prywatnych receipts.
- Fizyczny INCOMING/end_call: dwie wcześniejsze rozmowy A8→S22 potwierdziły REQUESTED→VALIDATED→DISPATCHED→CONFIRMED i END/COMPLETE. Właściciel potwierdził też samorozłączenie po ciszy 30+15 s. Ostatni bezpieczny odczyt: 40 sesji, IDLE, zero błędów, kampania/retry nieaktywne. Szczegółowy timeline tej ostatniej rozmowy **nieodczytany**.
- **Historyczny stan etapu 1:** wtedy S22 używał wcześniejszego APK. Następnie zainstalowano `e8db8db`, a native F-091 off-call i trzy presety PASS. Nowa rzeczywista rozmowa na tym APK nadal QA_PENDING.

## Etap 2 — DONE / PASS / NO_CHANGE

Na świeżym bazowym HEAD zmapowano `ConversationOutcome` (5 produkcyjnych plików), `ConversationOutcomeBoundary` (5), `ConversationRunResult` (3) i istniejący JVM `ConversationTermination`. Żaden kolejny przenoszony typ nie zmniejsza dziś sprzężenia: wynik scenariusza jest wyłącznie w app, boundary zależy od playback, a run result obejmuje raport/evidence/chat token. Nie zmieniono produkcyjnego kodu ani testów. Celowany JVM, kompilacja AndroidTest, pełny host (266 Python), product friction i clean Git **PASS**. [Checkpoint z mapą i receipts](checkpoints/2026-11-06-mod-stage2-outcomes-nochange.md).

**Dalsza praca:** etap 3 ma już zamknięty krok 1; następna neutralna reguła wymaga nowego audytu i odrębnego gate. Nowy czat musi odczytać świeży HEAD i claims.

## Etap 3 — krok 1 DONE / PASS

`ConversationSessionRegistry` przeniesiono z app do czystego `conversation-core`: identyczny algorytm tryCreate/release, tylko package + widoczność i jeden import runtime. Cztery testy JVM przeniesione do core. Mac `aicalls-20261106-mod-stage3-preflight-v1` DONE/0 i `aicalls-20261106-mod-stage3-registry-move-gate-v1` DONE/0: core JVM, celowane app JVM, AndroidTest compile, pełny host `host_quality_gate_green=true` (266 Python) i friction PASS. Brak APK/deploy na S22. [Nowy checkpoint](checkpoints/2026-11-06-mod-stage3-session-registry-core.md).

**Następny wyłącznie osobny krok:** ponowna mapa jednej neutralnej reguły stanu tury z `AndroidConversationRuntime`, ewentualnie NO_CHANGE; nie rozpoczynać etapów 4–5 bez osobnego zakresu.

## Autonomiczny sprint F-092 — source PASS

Zamknięto wąski defekt kontraktu promptu: rola żeńskiej asystentki bez drugiego powitania i bez dosłownego wzorca zwrotu z ukośnikiem. `ConversationInstructionBuilder` + `ConversationReplyProtocolTest`, dwa pliki produkcja/test. Mac zadania `aicalls-20261106-autosprint-f092-preflight-v1` oraz `aicalls-20261106-autosprint-f092-prompt-regression-v1` oba DONE/0; 217,568 s testów, celowane JVM, AndroidTest compile, pełny host (266 Python) i product friction PASS. [Checkpoint](checkpoints/2026-11-06-autosprint-f092-language-role.md). F-092 ma status FIXED_IN_SOURCE, fizyczna jakość mowy pozostaje QA_PENDING.

**Nie zamknięto automatycznie** F-080/F-048/F-045/F-047 ani fizycznego F-091/F-099. Ten historyczny następny krok (read-only audyt F-091) już wykonano; później zaliczono fizyczną matrycę off-call. Obecny następny krok to ręczne QA edytora i naturalności bez połączeń.

## F-091 i F-089 — read-only korekta stanu

Mac taski `aicalls-20261106-autosprint-f091-evidence-readonly-v1`, `aicalls-20261106-autosprint-f091-typed-metadata-v1` i `aicalls-20261106-autosprint-f091-action-audit-v1`: DONE/0 bez treści rozmów. W prywatnych czterech plikach 8 akcji SPEAK, brak END_CALL; naturalne zakończenie F-091 pozostaje OPEN i wymaga kontrolowanej regresji off-call. F-089 jest FIXED_IN_SOURCE (typed reason CALL_ENDED), lecz bieżące fizyczne QA obecnego APK niepotwierdzone. [Checkpoint](checkpoints/2026-11-06-autosprint-f091-readonly-diagnostics.md). F-101 zamyka stare zdanie handoffu, które błędnie kazało rozpoczynać etap 3 od nowa.

## F-091 — kontekstowy wybór end_call (nowy source sprint)

Na HEAD 73702c6 preflight Mac DONE/0; pre-existing native model off-call na S22 PASS w 41 s, bez połączenia/instalacji, ale zgodność z nowym źródłem APK niepotwierdzona. Nowa instrukcja wybiera typed end_call wraz z pożegnaniem po jednoznacznym zakończeniu, a none dla cytatów, korekt i następnej sprawy. RED→GREEN, AndroidTest compile, full host i friction PASS. W chwili tego sprintu F-091 pozostawał fizycznie QA_PENDING. Później na `e8db8db` native off-call PASS; real-call po tej zmianie pozostaje QA_PENDING. [Checkpoint](checkpoints/2026-11-06-f091-semantic-closure-source.md).

## Granice wykonania i dowodów

- Repo: MichalMatu/ai-calls; branch main; commity [skip ci]. Mac checkout: /Users/michal/agent-workspace/repos/ai-calls/work.
- Pracuj w trybie **GitHub Connector + Mac Local Agent**, bez Codex/ChatGPT Work/GitHub Actions, force-push i nowych gałęzi. Przed taskiem sprawdź aktualny protokół Local Agent, claims i clean HEAD; nie wykonywać konkurencyjnych mutacji.
- Nie dotykać Samsung/Shizuku/PCM/PFD, modelu ani TTS, end_call po TX, polityk zakresu, STOP, takeover, starych prywatnych dowodów. Nie powtarzać fizycznych testów lub ukończonej bramki bez nowej przesłanki; nowa zmiana wymaga własnego GREEN.
- Etap 2 zamknięty jako NO_CHANGE; celowane JVM, pełny host, product friction i kompilacja AndroidTest PASS. W nowym etapie stosować te same bramki do nowej zmiany. Rozróżniaj host QA od fizycznego PROVEN_S22.
- Wciąż osobno QA_PENDING: manual/TAKE OVER/powrót AI/STOP, głos po ostrzeżeniu i szczegółowy timeline timeoutu. F-080 i F-044/F-045/F-048 to backlog funkcjonalny; statusy defektów tylko w [BLOCKERS](../BLOCKERS.md).

## Przeczytać

[AGENTS](../AGENTS.md), [Architektura](ARCHITECTURE.md), [Runtime](CONVERSATION_RUNTIME.md), [Bezpieczeństwo](SECURITY_PRIVACY.md), [Freeze Samsung](PHASE2D_FREEZE_2026-09-18.md), [Roadmapa](ROADMAP.md), [Plan modularizacji](SAFE_MODULARIZATION_PLAN.md), [Checkpoint zamknięcia](checkpoints/2026-11-06-modularization-handoff-final.md). Checkpointy wcześniejsze są historią, nie poleceniami ponowienia.

## Nowy sprint: wersjonowane skillsy i Laboratorium

Na bazie 7a121657: sekcje MessageCapture, Clarification, NaturalClosure i ConversationStyle, fabryczne presety, kopie i historia wersji, per-call snapshot, modelowe off-call Laboratorium wieloturowe w menu. Source/host PASS tylko po zielonej bramce. S22 APK e8db8db zainstalowane i off-call F-091 PASS; real-call F-091 nadal QA_PENDING. [Kontrakt](SKILLS_AND_LAB.md), [checkpoint](checkpoints/2026-11-06-gemma-skills-presets-lab-mvp.md). Aktualny HEAD zawsze sprawdzić; nie uruchamiać rozmów ani instalacji bez właściwego zakresu.

## Skills/presety: fizyczne testy S22 OFFCALL (2026-11-06)

Native 3/3 PASS; GEMMA_4_E2B, trzy presety, wspólny korpus 4 historie / 12 tur; izolowany zapis/rollback oraz ekran menu PASS. Naturalność nieoceniona, żadnych połączeń. [Checkpoint](checkpoints/2026-11-06-gemma-skills-three-presets-s22-offcall.md).

## Stan po pełnym audycie 2026-11-06

[Kompletny checkpoint kodu, quality gates i S22](checkpoints/2026-11-06-ai-calls-complete-code-doc-qa-handoff.md): bazowy HEAD `1d260d8`, full host PASS, 266 Python, 90/90 linków, S22 3/3 native off-call i 36 tur Gemmy 4 E2B. Naturalność, pełne UI i real-call pozostają osobno QA_PENDING. Nie uruchamiać ponownie ukończonych testów bez nowej przesłanki.
