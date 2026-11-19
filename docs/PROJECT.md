# AI Calls — roadmapa MVP

**Golden rollback:** s22-gsm-mvp-golden-2026-11-19 → f0940d51 (S22+, Android 16, .s8lab). Nie ruszać tagu.

## PASS fizyczny — 19.11.2026

- Incoming bez Wi-Fi/USB: 7 tur / 2:06, RX→STT→AI→TTS→TX, SAVE_NOTE i END_CALL CONFIRMED, 22 WAV.
- Outgoing Orange 510 100 100: 1 próba bez retry, 7 tur / 2:37, 8 STT, RX/TX, 24 WAV, FINISHED/ANSWERED, zakończony Call.
- Host Binder działa bez Wi-Fi/ADB wireless. 3 WRITE_REVOKED odpowiadają barge-in, nie dowodzą awarii audio.

## Otwarte

- Outgoing AI END_CALL: 7 SPEAK, zero END_CALL; koniec owned_call_ended, bez dowodu że rozłączyło AI. Poprawiono prompt i liczniki; konieczny odbiór fizyczny po wdrożeniu.
- UX, konfiguracja STT/TTS/modelu i subiektywna jakość odpowiedzi — osobny etap po GSM.

## Wdrożenie — 19.11.2026

- main 2a617bb0: QA/build PASS, APK .s8lab na S22+ z rollbackiem i zgodnym SHA.
- Po instalacji Mac Agent przywrócił host Binder w IDLE: host_registered=true; bez Call.

## Następny sprint

1. Za osobną zgodą fizyczny Outgoing: po TTS END_CALL DISPATCHED → exact Call ended, zapis i brak retry/ujawnień. Dopiero wtedy PASS zakończenia przez AI.
2. Następnie UX, STT/TTS i model.

**WSTRZYMANE:** schedulery, Actions, Work, Codex, SIP/PJSUA2, Shizuku i WRITE_SECURE_SETTINGS. [QA](operations/DEVELOPMENT.md).
