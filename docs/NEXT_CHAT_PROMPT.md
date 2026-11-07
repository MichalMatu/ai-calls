# AI Calls — następny chat po SOURCE_PASS UX-02 (Sprint 6 pozostaje CLOSED)

Kontynuuj `MichalMatu/ai-calls` od **świeżego `main`** przez GitHub Connector + **Mac Local Agent** (`macbook-michal`, installation `cfea4cc3-b54c-4b6d-b9a6-758d12733e98`, checkout `/Users/michal/agent-workspace/repos/ai-calls/work`). Bez Codex, Work, GitHub Actions (także odczytu statusu), nowych gałęzi, force-push i nieautoryzowanych rzeczywistych Call. Commity `[skip ci]`.

Najpierw przeczytaj [AGENTS](../AGENTS.md), [handoff](HANDOFF_NEXT_CHAT.md), [ROADMAP](ROADMAP.md), [modularizację](SAFE_MODULARIZATION_PLAN.md), [BLOCKERS](../BLOCKERS.md), [Sprint 6](checkpoints/2026-11-07-ux-sprint6-final-handoff.md), [UX-01](checkpoints/2026-11-07-ux01-campaign-status-presenter-closeout.md) i [UX-02](checkpoints/2026-11-07-ux02-conversation-history-controller-closeout.md). Zweryfikuj prawdziwy HEAD, clean Mac i task-results.

**Nie powtarzaj zamkniętych etapów:**
- Sprint 6 funkcjonalny `1b359146`: 325 host tests, S22 28/28 native + 24/24 visual, targeted 4/4, realna Gemma 2 tury + STOP, 46 prywatnych screenshotów.
- UX-01 source `82d86e3`: typed read-only CampaignExecutionStatusPresenter; 6 JVM, 325 Python, AndroidTest/APK + friction PASS; fizyczny S22 nowego SHA NOT_RUN.
- **UX-02 source `109c4467`**: controller read/list/completed-only delete, immutable state + generation callbacks, Activity potwierdza i renderuje, `RecordedConversationHistory` nadal jest wyłącznym magazynem; 7 JVM, 325 Python, friction i AndroidTest/APK PASS; **S22 nowego SHA NOT_RUN**. Final receipt `aicalls-ux02-controller-final-green-host-20261107-v2` DONE/0. Nie wznawiać zaakceptowanych testów fizycznych.

**Następny autonomiczny, oddzielny sprint: UX-03** — wydziel `SkillLabRunner`, immutable `SkillLabUiState`, worker/model callback cancellation, 120s timeout i ochrona stale callbacks bez zmiany działania prawdziwych rozmów. RED→GREEN Kotlin JVM, pełny appropriate host/friction/AndroidTest gate, checkpoint. W testach wyłącznie syntetyczne dane i off-call, nie wykonywać rzeczywistej rozmowy ani nie instalować nowego APK bez właściwej decyzji o fizycznym QA.

Później osobno UX-04/05, F-080 continuous recorder/player i niezależna weryfikacja F-091/Call oraz jakości semantycznej Gemmy. Nie zmieniaj Samsung/Shizuku, PCM/PFD RX/TX, ownership Call, grant/revoke, STOP, 30+15 s lub pełnego TX przed end_call.
