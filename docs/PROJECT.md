# AI Calls — MVP / bramka testów

**Rollback:** `s22-gsm-mvp-golden-2026-11-19` → `f0940d51`; nie zmieniać tagu.

## GSM PASS — S22+ (19.11.2026)
- Incoming bez Wi-Fi/USB: 7 tur / 2:06, RX→STT→AI→TTS→TX, SAVE_NOTE i END_CALL CONFIRMED, 22 WAV.
- Outgoing: 7 tur / 2:37, 8 STT, 24 WAV, jedna próba bez retry, FINISHED/ANSWERED.
- Host Binder UID 2000 działa bez Wi-Fi/ADB wireless. WRITE_REVOKED przy barge-in nie dowodzi awarii.
- APK `2a617bb0` wgrana i host odzyskany w IDLE; **nie** jest to dowód instalacji nowego UI.

## Kod / QA (20.11.2026)
- PR #37–49: Home, Incoming, Outgoing, Tasks, Calls i Scenarios scalone.
- PR #50: AI Tools, zwijany Notebook i Preparation — merge `7afb3886`.
- Mac Local Agent: QA v2 `5e38431`: Python, preflight, kompilacja AndroidTest, pełne QA hosta i APK build PASS (exit 0). **Nie uruchomiono** testów UI na emulatorze ani GSM tej wersji.

## P0 — do wydania na telefon
1. Aktualny `main`: czyste drzewo, preflight/full/device-build, raport z SHA i APK; audit debug/log/sekretów.
2. Archiwizacja/porządkowanie gałęzi: tylko po weryfikacji scalonego PR. Zachować `main`, `agent-control`, backup i golden tag.
3. S22+ wyłącznie IDLE: backup APK, sprawdzenie hosta/ADB, instalacja `.s8lab`, hash i rollback. Bez połączeń.
4. Smoke UI: Home, Incoming, Outgoing, Tasks, Calls/WAV, AI Tools, Notebook, Preparation, Skills, Vault, Settings; Save/Cancel, obrót, nawigacja.

## Otwarte — testy realne, osobna zgoda
Incoming i Outgoing po 4–6 tur: polski STT/TTS, jakość rozmowy, barge-in, STOP/takeover, END_CALL po TTS, SAVE_NOTE, WAV i transkrypcje. Błędy zgłaszać z dowodami.

**WSTRZYMANE:** schedulery, Actions, Work, Codex, SIP/PJSUA2, Shizuku, WRITE_SECURE_SETTINGS. [QA](operations/DEVELOPMENT.md).
