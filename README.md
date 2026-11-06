# AI Calls

Modułowe laboratorium Android do porównywania STT, modeli językowych, strategii dialogu, instrukcji i TTS. Docelowo wspólne sprawdzone komponenty obsłużą sekretarkę i autonomiczne, konkretnie autoryzowane zadania telefoniczne.

## Architektura

- Sześć modułów Gradle: **app**, **conversation-core**, **conversation-tools**, **audio-bridge**, **realtime-client** i **privileged-helper**. Aktualnie 195 z 220 produkcyjnych plików Kotlin pozostaje w app; pakiety są podzielone logicznie.
- Wspólny AndroidConversationRuntime obsługuje INCOMING i OUTGOING. Scenariusze oraz silniki STT/model/TTS są wymienne. Snapshot konfiguracji pojedynczej próby nie zmienia się podczas rozmowy.
- Aplikacja decyduje o upoważnieniu, własności Android Call, STOP, ujawnianiu danych i skutkach. Model nie przyznaje sobie uprawnień. Samsung/Shizuku RX/TX przez PCM/PFD pozostaje zamrożoną, sprawdzoną granicą.

## Aktualna kwalifikacja

- Sekretarka INCOMING: dialog i naturalne end_call potwierdzone kontrolowanymi połączeniami A8→S22. Fallback po 30 s aktywnej ciszy, ostrzeżeniu i kolejnych 15 s ma testy host/native PASS oraz fizyczne potwierdzenie użytkownika. Szczegółowy timeline ostatniej próby pozostaje nieodczytany.
- Modularizacja: etapy 0–2 ukończone (etap 2 = NO_CHANGE), pierwszy krok etapu 3 PASS. IncomingIdleTimeout i ConversationSessionRegistry przeniesiono bez zmiany algorytmów do conversation-core. Pełna bramka hosta i kompilacja testów Androida PASS. **Nowego APK po tym refaktorze nie instalowano**; działająca wersja na S22 jest wcześniejsza.
- OPEN / QA_PENDING: fizyczne przejęcie i oddanie rozmowy, STOP/manual, odpowiedź po ostrzeżeniu; pełne ciągłe audio i odtwarzacz (F-080), ogólne zadania, fakty i skutki (F-044/F-045/F-048). Nie traktować archiwum WAV per tura jako pełnego nagrania.
- Silniki eksperymentalne i scenariusze Orange/CLIR są nadal częścią laboratorium, nie dowodem gotowości każdej kombinacji ani wykonania zewnętrznego skutku.

## Wejście do projektu

[Bieżący handoff](docs/HANDOFF_NEXT_CHAT.md) · [Prompt do nowego czatu](docs/NEXT_CHAT_PROMPT.md) · [Plan małej modularizacji](docs/SAFE_MODULARIZATION_PLAN.md) · [Checkpoint etapu 3 / krok 1](docs/checkpoints/2026-11-06-mod-stage3-session-registry-core.md)

[Architektura](docs/ARCHITECTURE.md) · [Roadmapa](docs/ROADMAP.md) · [Zasady pracy](AGENTS.md) · [BLOCKERS — jedyny rejestr defektów](BLOCKERS.md) · [Indeks dokumentów](docs/README.md)

## Testowanie

Pełna bramka: **bash scripts/verify_host.sh**. Audyt ograniczeń: **python3 scripts/aicall_tools/diagnostics/audit_product_friction.py**. Testy fizyczne i ich prywatne dowody są osobne od host PASS; nie ponawiać zakończonych połączeń bez nowej przesłanki. W bieżącym trybie wykonania: GitHub Connector + Mac Local Agent, main, commity [skip ci], bez Codex/Work/GitHub Actions.

## Skills i Laboratorium

Edytowalne, wersjonowane instrukcje Gemmy, presety i laboratoryjne scenariusze off-call: [kontrakt](docs/SKILLS_AND_LAB.md).
