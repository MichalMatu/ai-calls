# AI Calls — następny chat po SOURCE_PASS UX-01 (Sprint 6 pozostaje CLOSED)

Kontynuuj `MichalMatu/ai-calls` od świeżego `main`, przez **GitHub Connector + Mac Local Agent** (`macbook-michal`, installation `cfea4cc3-b54c-4b6d-b9a6-758d12733e98`, checkout `/Users/michal/agent-workspace/repos/ai-calls/work`). Bez Codex, ChatGPT Work, GitHub Actions, nowych gałęzi, force-push, nieautoryzowanych rzeczywistych rozmów i prywatnych danych w Git. Commity `[skip ci]`.

**Najpierw przeczytaj:** [finalny Sprint 6](checkpoints/2026-11-07-ux-sprint6-final-handoff.md), [handoff](HANDOFF_NEXT_CHAT.md), [BLOCKERS](../BLOCKERS.md), [roadmapę](ROADMAP.md), [modularizację](SAFE_MODULARIZATION_PLAN.md). Odczytaj HEAD, clean worktree i task-results zamiast polegać na starym SHA.

**Nie powtarzaj Sprintu 6.** Zakończono iterację UX na funkcjonalnym `1b359146`: 325 host PASS; fizyczny S22 28/28 native i 24/24 visual, hotfix 4/4 targeted PASS, realna Gemma offline 2 tury i STOP PASS; 46 prywatnych screenshotów. Zainstalowano APK bez utraty danych i bez prawdziwych rozmów. `F-118` (Android regex) VERIFIED; poprzedni RED zachowany wyłącznie jako historia.

**UX-01 zamknięty SOURCE_PASS** na `82d86e3`: 6 nowych JVM, 325 host Python, friction, AndroidTest compile/APK PASS, Mac clean; S22 nowej wersji **NOT_RUN**. [Checkpoint](checkpoints/2026-11-07-ux01-campaign-status-presenter-closeout.md). **Nowy zakres po odrębnej decyzji:** `UX-02 ConversationHistoryController` i ochrona asynchronicznych operacji/listy/bulk delete; bez ponawiania UX-01/Sprintu 6 i bez ingerencji w prawdziwe archiwa. Później osobno `UX-03`, `UX-04`, `UX-05`. Jedna odpowiedzialność/commit; nie zmieniaj `CampaignAgentControl`, Telecom, grantów, STOP, modelu ani transportu.

Pozostałe, osobne wymagania produktu: F-080 pełny recorder/player, F-103 PL/EN, rzeczywiste call-flows i jakość semantyczna Gemmy. Przy nich zachowaj ownership Call, grant/revoke, STOP, Samsung/Shizuku PCM/PFD, 30+15 s, pełny TX przed end_call. Nie wznowić żadnego zamkniętego testu bez wykrytej regresji.
