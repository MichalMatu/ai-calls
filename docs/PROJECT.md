# AI Calls — roadmapa MVP

**Golden rollback:** s22-gsm-mvp-golden-2026-11-19 → f0940d51 (S22+, Android 16, .s8lab). Nie ruszać tagu.

## PASS fizyczny — 19.11.2026

- Incoming bez Wi-Fi/USB: 7 tur / 2:06, RX→STT→AI→TTS→TX, SAVE_NOTE i END_CALL CONFIRMED, 22 WAV.
- Outgoing Orange 510 100 100: 1 próba bez retry, 7 tur / 2:37, 8 STT, RX/TX, 24 WAV, FINISHED/ANSWERED, zakończony Call.
- Host Binder działa bez Wi-Fi/ADB wireless. 3 WRITE_REVOKED odpowiadają barge-in, nie dowodzą awarii audio.

## Otwarte

- UX, konfiguracja STT/TTS/modelu i subiektywna jakość dialogu — nowy priorytet.
- Rozłączanie exact Call działa (Incoming END_CALL CONFIRMED po TTS); Outgoing kończy i zapisuje połączenia. GSM przyjęte do MVP.
- Ostatni Outgoing: FINISHED, 2 tury, 3 STT, 10 WAV, bez retry. Model wybrał 3× SPEAK, więc brak nowego dowodu wywołania END_CALL, ale też brak dowodu regresji rozłączania. Dalsze scenariusze w zwykłym QA, nie blokują UX.

## Wdrożenie — 19.11.2026

- main 2a617bb0: QA/build PASS, APK .s8lab na S22+ z rollbackiem i zgodnym SHA.
- Po instalacji Mac Agent przywrócił host Binder w IDLE: host_registered=true; bez Call.

## Następny sprint

1. UX: ekran zadania: menu akcji, Skip/Restore, szczegóły w kartach; zrzuty na koniec sprintu.
2. Konfiguracja STT/TTS/modelu oraz testy jakości odpowiedzi; regresje GSM tylko przy dotknięciu flow. Bez kolejnych połączeń bez osobnej zgody.

**WSTRZYMANE:** schedulery, Actions, Work, Codex, SIP/PJSUA2, Shizuku i WRITE_SECURE_SETTINGS. [QA](operations/DEVELOPMENT.md).
