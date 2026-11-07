# AI Calls — następny chat po zamknięciu Sprintu 6

Kontynuuj `MichalMatu/ai-calls` od świeżego `main`, przez **GitHub Connector + Mac Local Agent** (`macbook-michal`, installation `cfea4cc3-b54c-4b6d-b9a6-758d12733e98`, checkout `/Users/michal/agent-workspace/repos/ai-calls/work`). Bez Codex, ChatGPT Work, GitHub Actions, nowych gałęzi, force-push, nieautoryzowanych rzeczywistych rozmów i prywatnych danych w Git. Commity `[skip ci]`.

**Najpierw przeczytaj:** [finalny Sprint 6](checkpoints/2026-11-07-ux-sprint6-final-handoff.md), [handoff](HANDOFF_NEXT_CHAT.md), [BLOCKERS](../BLOCKERS.md), [roadmapę](ROADMAP.md), [modularizację](SAFE_MODULARIZATION_PLAN.md). Odczytaj HEAD, clean worktree i task-results zamiast polegać na starym SHA.

**Nie powtarzaj Sprintu 6.** Zakończono iterację UX na funkcjonalnym `1b359146`: 325 host PASS; fizyczny S22 28/28 native i 24/24 visual, hotfix 4/4 targeted PASS, realna Gemma offline 2 tury i STOP PASS; 46 prywatnych screenshotów. Zainstalowano APK bez utraty danych i bez prawdziwych rozmów. `F-118` (Android regex) VERIFIED; poprzedni RED zachowany wyłącznie jako historia.

**Cel ewentualnej nowej iteracji po osobnej decyzji użytkownika:** zacznij **UX-01** — tylko read-only `CampaignExecutionStatusPresenter` (historyczny status → typowany, lokalizowalny opis); nie zmieniaj `CampaignAgentControl`, Telecom, grantów, STOP, modelu ani transportu. Po osobnym checkpoint/gate: `UX-02` kontroler historii, `UX-03` runner Laboratorium, `UX-04` ustawienia i `UX-05` i18n. Jeden mały kontrakt/test na commit; nie refaktoruj wszystkiego naraz.

Pozostałe, osobne wymagania produktu: F-080 pełny recorder/player, F-103 PL/EN, rzeczywiste call-flows i jakość semantyczna Gemmy. Przy nich zachowaj ownership Call, grant/revoke, STOP, Samsung/Shizuku PCM/PFD, 30+15 s, pełny TX przed end_call. Nie wznowić żadnego zamkniętego testu bez wykrytej regresji.
