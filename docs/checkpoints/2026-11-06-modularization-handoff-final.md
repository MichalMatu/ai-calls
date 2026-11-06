# AI Calls — pełny checkpoint zamknięcia modularizacji 0–1 (2026-11-06)

## Stan repozytorium i środowiska

- **Ostatni sprawdzony kod:** main **0fb9c52d20df93a86744ae341fe5fda4d6c78498** = origin/main; lokalny worktree CLEAN na preflight. Ten checkpoint i porządkowanie dokumentacji powstaną w następnym commicie, więc przy kontynuacji sprawdzać aktualny HEAD.
- Repo: MichalMatu/ai-calls; lokalny checkout: /Users/michal/agent-workspace/repos/ai-calls/work. Wykonanie: GitHub Connector + Mac Local Agent, host macbook-michal / cfea4cc3-b54c-4b6d-b9a6-758d12733e98, task namespace w prywatnym MichalMatu/local-agent:agent-control.
- Odczyt kontrolny hosta w tej sesji: supervisor idle, brak unresolved claims; nie oznacza to stałej dostępności w nowym czacie. Bez zmian aplikacji, urządzeń i konfiguracji w etapie tego checkpointu.

## Zamknięte kroki i wyniki

- **Etap 0** — commit **5a63f8b27e16078dc337d59ed795fbbe2c6f76ec**: testy i mapa referencji, bez zmian produkcyjnych. Celowane JVM i pełny scripts/verify_host.sh PASS, 266 testów Python PASS, friction audit PASS, Git clean. [Pełny dowód](2026-11-06-mod-stage0-baseline.md).
- **Etap 1** — commit **0fb9c52d20df93a86744ae341fe5fda4d6c78498**: IncomingIdleTimeout z app przeniesiony do czystego JVM conversation-core. Zmieniono tylko package/widoczność i importy. Kontrolne odtworzenie starego nagłówka dało identyczny SHA-256 d08ff767f36a395c2a79d48f767748929a6509348b81e74864dc5649973746c2. Dwa testy zegara przeniesiono do core, dwa testy playback/tool pozostały w app, dodano dwa kontrakty core.
- Pierwszy rozszerzony test czasowy miał błędną symulację momentu wznowienia nasłuchu; jego FAIL zachowano, test poprawiono, **bez modyfikacji algorytmu produkcyjnego**. Końcowe core JVM + app playback + kompilacja AndroidTest PASS; pełne scripts/verify_host.sh: **host_quality_gate_green=true**, Gradle build/JVM/lint/APK/audyty PASS, 266 Python tests PASS; audit_product_friction.py PASS. Repo czyste po push. [Pełny dowód](2026-11-06-mod-stage1-incoming-idle-core.md).
- Brak nowej instalacji APK na Samsung S22+ po etapie 1, brak nowych połączeń i brak ingerencji w zamrożony Samsung/Shizuku RX/TX, PCM/PFD, politykę grantów, STOP/takeover i kolejność zatwierdzony tekst→TTS→pełny TX→end_call.

## Wcześniejsze dowody fizyczne — nie mieszać z obecnym host gate

- Dwie fizyczne próby A8→S22 zakończyły własny Call etapami REQUESTED→VALIDATED→DISPATCHED→CONFIRMED i END/COMPLETE, F-091/F-098 w tym zakresie VERIFIED.
- Po instalacji wcześniejszego APK z timeoutem użytkownik potwierdził rzeczywiste samorozłączenie po ciszy 30+15 s. Bezpieczny odczyt po tej próbie: 40 zapisanych sesji wobec 39, call IDLE, kampania/retry nieaktywne, 0 błędów i ostrzeżeń app, 0 FATAL na skanowanym fragmencie. **Nie odczytano szczegółowego ostatniego timeline** z warning/grace/TOOL_EVENT/COMPLETE. F-099 pozostaje OPERATOR_ACCEPTED, szczegółowe event-level QA_PENDING.
- Fizyczne manual/TAKE OVER/powrót AI/STOP oraz odezwanie się podczas 15 s grace mają osobny status QA_PENDING. Nie powtarzać bazowego dialogu ani wcześniej zaliczonych testów tylko dla kolejnego checkpointu.

## Reaudyt struktury po etapie 1

- Sześć modułów Gradle. Pliki produkcyjne Kotlin: app **196**, audio-bridge **3**, conversation-core **3**, conversation-tools **1**, realtime-client **17** = **220** (privileged-helper ma inne źródła). To jest podział kompilacji, a 196 plików app znajduje się w wielu pakietach, nie w jednym fizycznym folderze.
- AndroidConversationRuntime pozostaje jedynym aktywnym runtime, około 815 linii; CallRealtimeSessionOrchestrator około 758. To aktywny kod, nie potwierdzony balast. Repozytorium pozostaje laboratorium, silniki eksperymentalne i diagnostyczne entrypointy nie mogą być usunięte na podstawie samej nazwy.
- Typowane kontrakty etapu 2: ConversationOutcome i ConversationOutcomeBoundary po **5** referencji produkcyjnych, ConversationRunResult **3**, ConversationTermination znajduje się w conversation-core. Wynik pochodzi z read-only preflight na HEAD 0fb9c52.

## Jakość dokumentacji i porządek

Aktywne README, ROADMAP, HANDOFF i SAFE_MODULARIZATION_PLAN skondensowano do bieżącego stanu zamiast chronologicznego dopisywania kolejnych sprintów. Zaktualizowano indeks, runtime/architekturę oraz bieżące instrukcje wykonania; usunięto błędne dosłowne sekwencje znaków nowej linii i przestarzałe wskazówki Codex jako trybu tego czatu. **Nie usuwano** BLOCKERS, historii Git, poprzednich checkpointów ani prywatnych dowodów. W tej zmianie nie ma modyfikacji produkcyjnego kodu/ APK. Aktualne statusy defektów tylko w BLOCKERS.

## Następny jeden etap

Etap 2 — typowane wyniki rozmowy, bez łączenia z większym refaktorem. Na świeżym HEAD sprawdzić dokładne zależności, przenieść maksymalnie jeden rzeczywiście niezależny od Androida typ do istniejącego conversation-core albo zamknąć NO_CHANGE, jeśli migracja nie poprawia granic. Dla zmiany: celowane JVM, pełny host gate, friction audit, AndroidTest compile jeśli wymagana, pojedynczy commit main [skip ci] i checkpoint. Dopiero potem osobno etap 3 odchudzania AndroidConversationRuntime, etap 4 dużych plików, etap 5 potwierdzonego martwego kodu.

**Handoff:** [Prompt do nowego czatu](../NEXT_CHAT_PROMPT.md) · [Aktualny skrót](../HANDOFF_NEXT_CHAT.md) · [Plan](../SAFE_MODULARIZATION_PLAN.md) · [Roadmapa](../ROADMAP.md).
