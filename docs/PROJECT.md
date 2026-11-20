# AI Calls — MVP: kwalifikacja

**Rollback:** `s22-gsm-mvp-golden-2026-11-19` → `f0940d51`; tag nietykalny.

## GSM PASS — S22+ 19.11.2026
- Incoming: 7 tur / 2:06, RX→STT→AI→TTS→TX; SAVE_NOTE, END_CALL CONFIRMED, 22 WAV.
- Outgoing: 7 tur / 2:37, 8 STT, 24 WAV, FINISHED/ANSWERED, bez retry.
- Binder UID 2000 działa. WRITE_REVOKED przy barge-in nie dowodzi awarii audio.

## Kod i QA — 20.11.2026
- PR #37–51 scalone. `main d6d69ca`; preflight, full QA, AndroidTest compile, device-build PASS (Local Agent, exit=0).
- APK SHA-256: `cf018ce04623cabcfcd415120af9a72fc62e0318e30a204293ce46106d857fa6`; 90,98 MB.
- Brak emulatora; uruchomienie testów UI pozostaje OPEN.
- S22+ przez ADB: IDLE, Binder registered. Instalacja `0.2.0-s8lab` różni się od APK kandydata. Dotychczasowa APK skopiowana na Mac; brak zmiany danych i rozmów.
- Gałęzie: `main`, `agent-control`, backup. Brak otwartych PR; M1/M2 POC zastąpione przez GSM MVP.

## Następny sprint — bramka na S22+
1. Certyfikat podpisu nowej APK = zainstalowanej; zweryfikowany rollback.
2. Po zgodzie, w IDLE: instalacja wyłącznie `.s8lab`, hash, rejestracja hosta, odczyt statusu; nie usuwać danych.
3. Smoke UI: Home, Incoming/Outgoing, Tasks, Calls/WAV, Tools/Notebook, Preparation, Skills, Vault, Settings; zapis, anulowanie, obrót.

## Otwarte — testy realne za osobną zgodą
4–6 tur Incoming i Outgoing: polski STT/TTS, dialog, barge-in, STOP/takeover, END_CALL po TTS, SAVE_NOTE, WAV, transkrypcje i trasy audio.

**WSTRZYMANE:** schedulery, Actions, Work, Codex, SIP/PJSUA2, Shizuku, WRITE_SECURE_SETTINGS. [QA](operations/DEVELOPMENT.md).
