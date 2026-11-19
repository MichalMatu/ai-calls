# AI Calls — start kolejnego czatu

Repo `MichalMatu/ai-calls`; kod po PR #23 na `main`. S22+ `.s8lab`: lokalna Gemma, host Binder `shell` UID 2000 przez autoryzowane Mac ADB. Kanoniczne cele i PASS: [PROJECT](../PROJECT.md); granice [SYSTEM](../architecture/SYSTEM.md) i [SECURITY](../architecture/SECURITY.md).

Na starcie sprawdź HEAD `main`, czysty worktree i ostatni receipt [Mac Local Agenta](LOCAL_AGENT.md). Nie powtarzaj zaliczonego E2E.

S1 TAKE OVER → RESUME → STOP: PASS użytkownika 19.11, bez nowego receipt. S2 po reboocie: HOLD, stock Android 16 wymaga ADB/UID 2000; lab pokazuje read-only stan hosta. Wariant 1: Kadb+NSD; kod w powiadomieniu (bez przełączania okna), porty auto. Fizyczny test OPEN; nie autonomia. Dalej S3 modularność, S4 jakość. Nie powtarzać E2E. Bez Codex/Work/Actions/schedulerów/SIP/PJSUA2. Fizyczny Call tylko za zgodą.
