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

## UX ustawień i historia rozmów — autonomiczny sprint
Na trzech osobnych, zielonych etapach opublikowano: `b448ace` (pięć sekcji ustawień, 266 Python), `2fca5f9` (wejście Historia/Laboratorium, 268 Python), `d59b459` (odtwarzanie zapisanych segmentów z dymków, 270 Python i celowany JVM). Wszystkie etapy: AndroidTest compile, full host i anti-friction PASS. [Mapa funkcji](SETTINGS_UX.md) · [Checkpoint z receipts](checkpoints/2026-11-06-settings-ux-autosprint.md).

**Fizyczne S22 UI PASS 7/7:** po ponownym podłączeniu zgodny podpis APK i instalacja bez czyszczenia danych, pięć sekcji, wejście do historii, osiem ekranów, niezmienione profile/presety, player 100-ms cichego syntetycznego WAV PASS. [Dokładne receipts](checkpoints/2026-11-06-settings-ux-s22-physical-acceptance.md). Po testach IDLE, bez kampanii i `pending_retry=false` odczytane przez poprawny parser. **F-080 nadal OPEN** dla ciągłego nagrania i playera całości; prawdziwe audio oraz ręczna ocena UX nie były badane. Bieżący HEAD czytać z `main`; `1d260d8` wyżej jest historycznym punktem jakości.

## S22 menu readability — F-102

Po dwóch zrzutach właściciela skorygowano kontrast i hierarchię startowego ekranu, zwijanie sekcji/czatu i bezpieczne Android insets. `2f58adf`, `312db7a`; pełny Mac gate PASS 272 Python i natywna matryca na S22 8/8 PASS bez połączeń, zachowany podpis APK i dane. F-102 VERIFIED tylko dla zakresu menu głównego. Nie powtarzać zaakceptowanych testów bez nowych zmian; następna praca UX: podstrony, nawigacja i ręczna ocena wyglądu. [Checkpoint](checkpoints/2026-11-06-menu-visual-s22-ux-acceptance.md). F-080 pozostaje OPEN. Bieżący HEAD zawsze odczytać z main.

## I18n PL/EN — foundation, S22 unavailable (2026-11-07)

Przygotowany jest wspólny `UiDesign`, zasoby PL/EN i jasny kompaktowy panel czatu; Mac gate (275 Python, AndroidTest, lint/APK, anti-friction) PASS. [Receipt](checkpoints/2026-11-07-i18n-dock-foundation-host.md). Task `aicalls-20261107-i18n-dock-s22-acceptance-v1` FAILED na preflight `exact_s22_adb_transport_unavailable` — brak instalacji i QA fizycznego. Nie powtarzać do odzyskania S22. Brak jeszcze przełącznika języka i migracji podstron, F-103 OPEN. Wznowić z faktycznego main HEAD; nie zmieniać STT/TTS, Call ani prywatnych nagrań.

## S22 i18n dock accepted — 2026-11-07

The earlier ADB failure was transient. Reconnected exact S22: read-only preflight PASS; signed APK installed without data deletion; localized dock 2/2 and prior UI matrix 8/8 — **10/10 PASS**. [Checkpoint](checkpoints/2026-11-07-i18n-dock-s22-acceptance.md). No calls, S22 IDLE after test. Do not rerun these accepted tests without changes. F-103 remains OPEN: implement user-selectable Auto/PL/EN locale independently of STT/TTS and migrate remaining screens.

## Unified UI sprint — current handoff (2026-11-07)

Start from fresh `main` (at this checkpoint `0afea79`, re-read before work). Home no longer uses 5 fake accordions; `AppNavigation` has four tabs and `SettingsHubActivity` direct destinations. `UiChromeActivity` now wraps 11 prior screens and the two new subsettings screens, all without touching phone/AI/Call runtime. Native theme, Back header, 48dp buttons, density-aware spacing and `GeneralSettingsController` are present. Prior gates 275, 276, 278, 280, 282 and final **284 Python PASS**. Exact S22 task `aicalls-20261107-ui-full-navigation-s22-acceptance-v1`: **15/15 PASS**, 2× IDLE, same APK signing, -r install without data wipe, no campaign/retry/calls. Only subjective visual acceptance remains OPEN. Read [UI checkpoint](checkpoints/2026-11-07-ui-unified-navigation-checkpoint.md). Do not repeat previously accepted host/S22 evidence without source change. F-103 remains OPEN; F-080 unaffected.

## Screenshots and Calls UX sprint (2026-11-07)

New source after `f764fec`: removed 8 `FLAG_SECURE` screen restrictions, recorded permanent no-screenshot-block owner rule in AGENTS.md and F-104; added Calls detail-back-to-list, single completed session deletion with confirmation and symlink/path safety (F-105/106), action spacing (F-107), UI-only Auto/PL/EN selector with locale config. **F-103 full legacy page translation remains OPEN; F-080 unrelated.** See [checkpoint](checkpoints/2026-11-07-calls-screenshots-ui-phase.md). Host and new S22 acceptance are not proven by previous S22 15/15; re-read exact receipt. No real call/delete in tests; only synthetic archive.

## UX/controllers & S22 hardware handoff — 2026-11-07

Current source advances from `f764fec` through `b33bdaa` (re-read latest main). No screenshot flags remain; Calls nested Back/delete + typed archived playback controller; full Calls and Incoming/AI Tools settings PL/EN; UI locale Auto/PL/EN. Host gates 286/292/293/295 Python PASS; Tools final gate DONE/0: 297 Python, AndroidTest compile/APK/lint and friction PASS. At the time of that checkpoint S22 ADB was unavailable; **superseded by physical 21/21 acceptance on 2026-11-07**, see the latest receipt below. Read [checkpoint](checkpoints/2026-11-07-ux-controller-qa-adb-handoff.md). F-103 full UI translation and full controller separation still OPEN; continue only after current host QA and exact S22 identity recovery.

## Physical S22 UX acceptance — latest verified 2026-11-07

At `main@035af45` the updated AI Calls APK and AndroidTest APK were installed on exact SM-S906B via signed `adb install -r -t`, **preserving user data**. Host gate **297 Python, JVM, lint, APK and anti-friction PASS**. S22 **21/21 actual native UI tests PASS** by exact receipts: 16 in `aicalls-20261107-s22-ux-21tests-locale-corrected-v2`, one screenshot verified from original native log by `aicalls-20261107-s22-screenshot-failure-diagnosis-v1`, and final 4 in `aicalls-20261107-s22-ux-final-four-acceptance-v1` DONE/0. Earlier wrappers recorded FAILED because of outdated PL-only title and a singular/plural parse error; preserve those failures. F-104/F-105/F-106/F-108 VERIFIED, F-107 still requires human visual review, F-103 full PL/EN and additional controller extraction OPEN, F-080 independent. Exact [physical acceptance checkpoint](checkpoints/2026-11-07-s22-ux-latest-21-21-acceptance.md). Do not claim full UX complete or rerun this accepted matrix without changes.

## Minimalistyczne UX — najnowszy odbiór S22 (2026-11-07)

Na `main@6a3aad0` zakończono sześć wskazanych przez właściciela poprawek: Home bez dubli dolnego menu, Calls jednowierszowy z kierunkiem i czerwonym koszem oraz potwierdzeniem, Tabler MIT lokalne wektory, płaskie przyciski bez elewacji, język/wygląd zwijany, wspólne `UiSelect` dla dropdownów. **Host 300 Python + APK/lint/AndroidTest/friction PASS. Exact Samsung S22: instalacja aplikacji/test APK bez utraty danych i 22/22 natywne testy PASS**, 2× IDLE, rola dialera, signer, brak kampanii/retry, zero live calls. Nie powtarzać tej wersji bez zmian. [Checkpoint](checkpoints/2026-11-07-minimal-ui-tabler-s22-22of22.md). F-110/111 VERIFIED; F-107 ręczna estetyka i F-103 kompletne PL/EN nadal OPEN, podobnie separacja Campaign/Engine/Preparation/Skills i F-080 pełny recorder. Przed następną pracą odczytać świeży HEAD.
