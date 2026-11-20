# AI Calls — kwalifikacja golden

**Rollback:** `s22-gsm-mvp-golden-2026-11-19` (`f0940d51`), tag zachować.

## Stan MVP
- GSM S22+: Incoming i Outgoing po 7 tur, RX/TX i WAV; SAVE_NOTE i END_CALL fizycznie zweryfikowane.
- `.s8lab` z kodem `b2d0b0` na S22+: podpis/hash i Binder PASS, dane zachowane, kopia rollback; UI/Lab 9/9 PASS.
- Kod `b2d0b0`: pełne QA/build PASS; scoring, przypadki i reguły adaptacyjne Lab wydzielone do JVM core.
- S22+ offline: UI 7/7, Lab storage 2/2, Gemma manual (2 tury, STOP/reset) i adaptive PASS; bez GSM.
- Binder stabilny po zwykłym Home; po instrumentacji odtworzony automatycznie PASS. Restart procesu nadal OPEN.

## Następny sprint — golden
1. Odbiór naturalności odpowiedzi Test Lab przez człowieka, bez połączeń.
2. Utrwalić host po restarcie procesu; dalej rozdzielać adaptery Android/UI od domeny.
3. Odbiór GSM i dopiero wtedy nowy golden tag; emulator nie potwierdza audio.

## Otwarte — realne GSM za osobną zgodą
Incoming i Outgoing po 4–6 tur: polski STT/TTS, naturalność, barge-in, STOP/takeover, END_CALL, SAVE_NOTE, WAV i transkrypcje.

**WSTRZYMANE:** schedulery, Actions, Codex, Work, SIP/PJSUA2, Shizuku, WRITE_SECURE_SETTINGS. [QA](operations/DEVELOPMENT.md).
