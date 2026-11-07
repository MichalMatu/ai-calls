# AI Calls — następny chat po SOURCE_PASS UX-03 (Sprint 6 CLOSED)

Kontynuuj `MichalMatu/ai-calls` od świeżego `main` przez **GitHub Connector + Mac Local Agent** (`macbook-michal`, installation `cfea4cc3-b54c-4b6d-b9a6-758d12733e98`, checkout `/Users/michal/agent-workspace/repos/ai-calls/work`). Bez Codex, Work, GitHub Actions (również bez odczytu ich statusu), nowych gałęzi, force-push i nieautoryzowanych rzeczywistych Call. Commity `[skip ci]`.

Najpierw czytaj [AGENTS](../AGENTS.md), [handoff](HANDOFF_NEXT_CHAT.md), [ROADMAP](ROADMAP.md), [modularizację](SAFE_MODULARIZATION_PLAN.md), [BLOCKERS](../BLOCKERS.md), [Sprint 6](checkpoints/2026-11-07-ux-sprint6-final-handoff.md), [UX-01](checkpoints/2026-11-07-ux01-campaign-status-presenter-closeout.md), [UX-02](checkpoints/2026-11-07-ux02-conversation-history-controller-closeout.md), [UX-03](checkpoints/2026-11-07-ux03-skill-lab-runner-closeout.md). Sprawdź prawdziwy HEAD, czystość Mac i najnowsze task-results.

**Zamknięte — nie powtarzaj bez nowej regresji:**
- Sprint 6 funkcjonalny `1b359146`: 325 Python, S22 28/28 native + 24/24 visual, targeted 4/4, realna Gemma 2 tury + STOP, 46 prywatnych screenshotów. To **nie** test nowego kodu.
- UX-01 source `82d86e3`: read-only CampaignExecutionStatusPresenter, 6 JVM, 325 Python, AndroidTest/APK/friction PASS; S22 nowego SHA NOT_RUN.
- UX-02 source `109c4467`: ConversationHistoryController, completed-only delete, 7 JVM, 325 Python, AndroidTest/APK/friction PASS; S22 nowego SHA NOT_RUN.
- **UX-03 source `b53cb820`**: jeden `SkillLabRunner`, `SkillLabUiState`, 120s timeout, STOP/race/late callback; Mac RED→GREEN 9 JVM + 328 Python, friction, `verify_host.sh`, AndroidTest i APK PASS. Receipt `aicalls-ux03-runner-final-green-host-20261107-v2` DONE/0; **S22 nowego SHA NOT_RUN**. Zmodyfikowane jedynie odpowiedzialności Skills Lab, nie telefonia.

**Następny autonomiczny, oddzielny sprint: UX-04** — bezpiecznie wydziel odczyt/prezentację w `AudioEngineSettingsActivity` i `LanguageModelActivity`, użyj istniejących `AudioEngineProfileStore`, readiness providers i `LanguageModelTransferService`; nie dubluj magazynów ani serwisów. Jeden zakres na raz: mapa referencji, RED→GREEN Kotlin JVM, pełny Mac host/friction/AndroidTest APK, checkpoint. Nie zmieniaj pobierania, model-transportu ani prawdziwych Call przy refaktoryzacji widoków.

Później oddzielnie UX-05/F-103, F-080 continuous recorder/player, F-091 real Call i semantyczna jakość Gemmy. Zachowaj Samsung/Shizuku, PCM/PFD RX/TX, grant/STOP/Call ownership, 30+15 s oraz pełny zatwierdzony TX przed end_call. Dane osobowe, WAV i screenshoty wyłącznie prywatnie.
