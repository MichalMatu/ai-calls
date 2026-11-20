# AI Calls — MVP przed testami

**Rollback:** `s22-gsm-mvp-golden-2026-11-19` → `f0940d51`; tag nietykalny.

## GSM PASS — S22+ 19.11.2026
- Incoming bez Wi-Fi/USB: 7 tur / 2:06, RX→STT→AI→TTS→TX; SAVE_NOTE, END_CALL CONFIRMED, 22 WAV.
- Outgoing: 7 tur / 2:37, 8 STT, 24 WAV, FINISHED/ANSWERED, bez retry.
- Binder UID 2000 bez Wi-Fi/ADB wireless; WRITE_REVOKED podczas barge-in to nie dowód awarii.
- APK `2a617bb0` zainstalowana, host IDLE; **nie** potwierdza wdrożenia nowego UI.

## Kod / QA 20.11.2026
- PR #37–49: Home, Incoming, Outgoing, Tasks, Calls, Scenarios — merged.
- PR #50: AI Tools, Notebook, Preparation — merged `7afb3886`.
- Mac Local Agent QA v2 `5e38431`: Python, preflight, AndroidTest **compile**, full QA i build PASS (exit 0). Brak uruchomionych testów UI/emulatora i GSM dla nowego UI.

## Następny sprint — P0
1. `main`: czyste drzewo, preflight/full/device-build, SHA/APK; przegląd debug/log/sekretów.
2. Usunąć tylko gałęzie z potwierdzonym merge; zachować `main`, `agent-control`, backup i golden tag.
3. S22+ w IDLE: backup APK, ADB/host, instalacja `.s8lab`, SHA, rollback. Bez połączeń.
4. Smoke UI: Home, Incoming/Outgoing, Tasks, Calls/WAV, AI Tools/Notebook, Preparation, Skills, Vault, Settings; zapis, anulowanie, obrót.

## Otwarte — testy realne, osobna zgoda
Incoming i Outgoing po 4–6 tur: polski STT/TTS, dialog, barge-in, STOP/takeover, END_CALL po TTS, SAVE_NOTE, WAV, transkrypcja.

**WSTRZYMANE:** schedulery, Actions, Work, Codex, SIP/PJSUA2, Shizuku, WRITE_SECURE_SETTINGS. [QA](operations/DEVELOPMENT.md).
