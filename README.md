# AI Calls

Modułowe laboratorium Android do porównywania STT, modeli językowych, strategii dialogu, instrukcji i TTS. Docelowo wspólne, zweryfikowane komponenty obsłużą sekretarkę i konkretnie autoryzowane zadania telefoniczne.

## Stan bieżący — UX-11 CLOSED, odbiór funkcjonalny offline PASS (2026-11-08)

- **Kod `main@3029034`:** UX-11 zakończony: prosty przebieg Lab, kompaktowy Sejf z polami własnymi, sam pływający dymek czatu podczas aktywnej rozmowy; 6 zakładek i STOP zachowane.
- **Mac:** 356/356 testów Python i pełny host quality gate **PASS**; AndroidTest aktualnej wersji również kompiluje się poprawnie.
- **Samsung S22:** 28/28 UI plus 2/2 testy aktywnego syntetycznego czatu **PASS**; 18/18 prywatnych screenshotów (w tym aktywny panel). Bez utraty danych podczas instalacji.
- **Funkcjonalne offline:** rzeczywiste 2 tury lokalnego modelu i STOP **PASS**; polski TTS → Parakeet STT na testowej frazie **PASS**. **Rzeczywiste CALL/RX/TX i wynik zewnętrzny NOT_RUN.**
- **Otwarte:** F-080 (pełny recorder/player), F-122/123/124 (aktywny recorder, korelacja Call, archiwum) oraz fizyczna integracja; dane custom sejfu nie są automatycznie ujawniane AI. [Handoff](docs/HANDOFF_NEXT_CHAT.md), [UX11](docs/checkpoints/2026-11-08-ux11-final-polish-functional-handoff.md), [funkcjonalne receipts](docs/checkpoints/2026-11-08-functional-real-offline-acceptance.md).

## Historia — Sprint 6 i UX-01–05 (potwierdzone wcześniej)

- **`main@1b359146` — UX ITERATION CLOSED.** 325 testów hosta, Kotlin/JVM/AndroidTest/APK/friction **PASS**. Wdrożono bezpieczne kasowanie wszystkich zakończonych rozmów, WAV tap-to-play/stop z dymków, poprawione przewijanie i STOP w Laboratorium, czytelne Skills/Silniki/Kampanie.
- **Fizyczny S22 PASS:** instalacja aplikacji/test APK bez utraty danych, **28/28 native UI + 24/24 visual**, po poprawce Android regex dodatkowo **4/4 targeted**, **2 rzeczywiste tury lokalnej Gemmy + STOP PASS**, 46 prywatnych screenshotów. Bez prawdziwych połączeń, END_CALL i usuwania danych właściciela.
- **Nowy SOURCE_PASS `main@7df2c33`:** UX-01/02/03/04 wydzieliły prezentery Kampanii, kontroler Historii, runner Laboratorium i odczytowe presentery modeli/audio. Ostatni UX-04: **13 JVM, 332 Python, friction, AndroidTest/APK PASS** na Macu; **nowy APK nieinstalowany na S22**. [Checkpoint UX-04](docs/checkpoints/2026-11-07-ux04-model-and-audio-presentation-closeout.md).
- **UX-05 / F-103 VERIFIED:** PL/EN 553 wspólne klucze, 337 host Python + 10 focused i AndroidTest/APK PASS, **fizyczny S22 2/2 native oraz 4/4 prywatne screenshoty PL/EN PASS**, zachowane dane i przywrócone `pl`. [Checkpoint UX-05](docs/checkpoints/2026-11-07-ux05-pl-en-accessibility-closeout.md). Manualnego TalkBack nie przeprowadzano.
- **Niezależne otwarte:** F-080 (pełny ciągły recorder/player), osobne autoryzowane real-call i ocena semantyki; nie wznawiać zamkniętych iteracji UX.

[Końcowy checkpoint Sprint 6](docs/checkpoints/2026-11-07-ux-sprint6-final-handoff.md) · [Bieżący handoff](docs/HANDOFF_NEXT_CHAT.md).

## Architektura

- Sześć modułów Gradle: `app`, `conversation-core`, `conversation-tools`, `audio-bridge`, `realtime-client`, `privileged-helper`. Istniejące czyste komponenty `IncomingIdleTimeout` i `ConversationSessionRegistry` są w JVM `conversation-core`.
- Jeden `AndroidConversationRuntime` obsługuje INCOMING/OUTGOING. Silniki STT/model/TTS i scenariusze są wymienne; snapshot konfiguracji nie zmienia się podczas rozmowy.
- Uprawnienia, konkretne zgody, własny Android Call, STOP, ujawnianie faktów i skutki kontroluje aplikacja, nie model. Tor Samsung/Shizuku PCM/PFD pozostaje oddzielną chronioną granicą.
- **UX-01–04 zaliczone źródłowo; UX-05 lokalizacja i automatyczna dostępność zweryfikowane na S22**, bez refaktoru Telecom. Dalsze zakresy F-122/F-123/F-124/F-080 są niezależne i OPEN. [Plan modularizacji](docs/SAFE_MODULARIZATION_PLAN.md).

## Wejście do pracy

[Aktualny handoff](docs/HANDOFF_NEXT_CHAT.md) · [Nowy czat](docs/NEXT_CHAT_PROMPT.md) · [Roadmapa](docs/ROADMAP.md) · [Architektura](docs/ARCHITECTURE.md) · [Indeks dokumentacji](docs/README.md) · [Rejestr BLOCKERS](BLOCKERS.md) · [AGENTS](AGENTS.md).

Testowanie: `bash scripts/verify_host.sh`, `python3 scripts/aicall_tools/diagnostics/audit_product_friction.py`; przy zmianach Androida odpowiednie testy instrumentacyjne z realnym **IDLE** na S22. Tryb: GitHub Connector + Mac Local Agent, `main`, commity `[skip ci]`; bez Codex, Work, GitHub Actions, wymuszonego push ani publikacji prywatnych plików.
