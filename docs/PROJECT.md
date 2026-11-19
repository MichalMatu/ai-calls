# AI Calls — roadmapa MVP

**Golden rollback:** s22-gsm-mvp-golden-2026-11-19 → f0940d51 (S22+, Android 16, .s8lab). Nie ruszać tagu.

## PASS fizyczny — 19.11.2026

- Incoming bez Wi-Fi/USB: 7 tur / 2:06, RX→STT→AI→TTS→TX, SAVE_NOTE i END_CALL CONFIRMED, 22 WAV.
- Outgoing Orange 510 100 100: 1 próba bez retry, 7 tur / 2:37, 8 STT, RX/TX, 24 WAV, FINISHED/ANSWERED, zakończony Call.
- Host Binder działa bez Wi-Fi/ADB wireless. 3 WRITE_REVOKED odpowiadają barge-in, nie dowodzą awarii audio.

## Otwarte

- Outgoing AI END_CALL: 7 SPEAK, zero END_CALL; koniec owned_call_ended, bez dowodu że rozłączyło AI. Poprawiono prompt i liczniki; konieczny odbiór fizyczny po wdrożeniu.
- UX, konfiguracja STT/TTS/modelu i subiektywna jakość odpowiedzi — osobny etap po GSM.

## Następny sprint

1. QA (JVM/Python/Lint/security/build), PR→main, backup i instalacja na S22+ tylko IDLE.
2. Za osobną zgodą Outgoing: audio/WAV, END_CALL DISPATCHED po TTS, koniec exact Call, brak retry i ujawniania danych. Dopiero wtedy PASS rozłączenia przez AI.
3. Osobno UX i konfiguracja profili/modelu.

**WSTRZYMANE:** schedulery, Actions, Work, Codex, SIP/PJSUA2, Shizuku i WRITE_SECURE_SETTINGS. [QA](operations/DEVELOPMENT.md).
