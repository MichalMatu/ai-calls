# AI Calls — kwalifikacja golden

**Rollback:** `s22-gsm-mvp-golden-2026-11-19` (`f0940d51`), tag zachować.

## Stan MVP
- GSM S22+: Incoming i Outgoing po 7 tur, RX/TX i WAV; SAVE_NOTE oraz END_CALL fizycznie zweryfikowane.
- `.s8lab` wgrana na S22+: IDLE, hash APK i Binder PASS, dane zachowane; poprzednia APK zabezpieczona na Macu.
- Host full QA i build PASS. Kontrola granic sześciu modułów w preflight/full. AndroidTest kompiluje się; wykonanie UI wciąż OPEN.

## Następny sprint — golden
1. Stopniowo wydzielać czystą logikę Test Lab do JVM core; UI/Android/model adaptery pozostają w `app`.
2. Utrzymać `preflight`, `full`, `device-build` i `lab-ui` (tylko emulator). Smoke UI S22+ w IDLE, bez rozmów.
3. Zmiany wyłącznie po QA i PR; nie oznaczać nowego golden przed fizycznym odbiorem.

## Otwarte — realne GSM za osobną zgodą
Incoming i Outgoing po 4–6 tur: polski STT/TTS, dialog, barge-in, STOP/takeover, END_CALL, SAVE_NOTE, WAV, transkrypcje.

**WSTRZYMANE:** schedulery, Actions, Codex, Work, SIP/PJSUA2, Shizuku, WRITE_SECURE_SETTINGS. [QA](operations/DEVELOPMENT.md).
