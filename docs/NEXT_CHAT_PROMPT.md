# AI Calls — następny czat: G4 po G3 KEEP_E2B

Wznów `MichalMatu/ai-calls` w trybie **GitHub Connector + Mac Local Agent + ChatGPT Sandbox**, bez Codex, Work i GitHub Actions (także odczytu). Nie twórz branchy ani force-push; commity `[skip ci]`. Sprawdź świeży `main` HEAD i czystość Mac checkoutu.

**Obowiązkowe:** `AGENTS.md`, `README.md`, `docs/AI_CALLS_MASTER_EXECUTION_PLAN.md`, `docs/HANDOFF_NEXT_CHAT.md`, `docs/checkpoints/2026-11-09-g3-keep-e2b-final-decision.md`, `docs/ROADMAP.md`, `docs/ARCHITECTURE.md`, `docs/SECURITY_PRIVACY.md`, `BLOCKERS.md`. Przy Mac Host Ops także `MichalMatu/local-agent` `AGENTS.md`, `docs/HOST_CONTROL_PLANE.md`, `docs/CURRENT_HANDOFF.md`.

**G0/G1/G2/G3 nie powtarzać:** G3 `KEEP_E2B` decyzja właściciela. Fizycznie S22 96/96 native tur, private 48-pair JSONL, Mac clean postflight DONE/0, local 96-reply bounded semantic rubric v2 DONE/0 (E2B 150/150; Qwen 141/150). Pełny human naturalness review **UNASSESSED**, do G5; nie udawać `HUMAN_PASS`. Qwen pozostaje Lab-only CPU; model Call E2B niezmieniony.

**Jeden etap:** G4.1 per-turn dynamic Skill routing contract. Analizuj `ConversationSkillRouter`, `AndroidSkillLabSessionFactory`, `SkillLabRunner`, `ConversationSkillLibrary`, `ConversationInstructionBuilder` i testy. Dodaj minimalny typowany mechanizm śledzenia intencji z odświeżeniem wyłącznie *propozycji* snapshotu, bez wstrzykiwania dynamicznych system instructions do żywego silnika LiteRT i bez uruchamiania narzędzi/call. Zabezpiecz aktywne wersje, disabled, unknown custom, zmianę tematu, pamięć/korektę, STOP. Wykonaj JVM RED/GREEN, Mac full `verify_host.sh` i anti-friction, checkpoint. Dopiero po potwierdzeniu lifecycle w Lab zacznij G4.2 native S22. Nie zmieniaj default E2B, zaufania do wejścia STT, ani Samsung RX/TX.
