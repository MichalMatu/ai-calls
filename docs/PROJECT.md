# AI Calls — kwalifikacja golden

**Rollback:** `s22-gsm-mvp-golden-2026-11-19` (`f0940d51`), tag zachować.

## Stan MVP
- GSM S22+: Incoming i Outgoing po 7 tur, RX/TX i WAV; SAVE_NOTE i END_CALL fizycznie zweryfikowane.
- `.s8lab` z kodem `3fb299e` na S22+: podpis/hash APK i Binder PASS, dane zachowane, rollback APK na Macu.
- `main 62c35e9`: pełne QA/build PASS; Lab-scoring i przypadki w JVM core, granice sześciu modułów chronione.
- S22+ offline: UI 7/7, Lab storage 2/2, Gemma manual (2 tury, STOP/reset) i adaptive PASS; bez GSM.
- Binder wymagał przywrócenia po instrumentacji; poprawna regeneracja PASS, trwałość hosta nadal OPEN.

## Następny sprint — golden
1. Ocenić naturalność odpowiedzi Test Lab przez człowieka, bez połączeń.
2. Wyjaśnić host po restarcie procesu; kontynuować separację Android/UI i orkiestracji w małych PR.
3. Odbiór GSM i dopiero wtedy nowy golden tag; emulator nie potwierdza audio.

## Otwarte — realne GSM za osobną zgodą
Incoming i Outgoing po 4–6 tur: polski STT/TTS, naturalność, barge-in, STOP/takeover, END_CALL, SAVE_NOTE, WAV i transkrypcje.

**WSTRZYMANE:** schedulery, Actions, Codex, Work, SIP/PJSUA2, Shizuku, WRITE_SECURE_SETTINGS. [QA](operations/DEVELOPMENT.md).
