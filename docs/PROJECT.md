# AI Calls — MVP 0.2.1

22.11.2026: testowy APK na S22+ / Android 16, GSM via Binder/ADB UID 2000. **Preview, nie produkcja.** Rollback: `s22-gsm-mvp-golden-2026-11-19` (`f0940d51`).

## Odbiór

- Incoming PL GSM: PASS (3 STT, 4 TX, END_CALL, 0 przerwanych RX).
- Notatki: PASS — 3 zapisy potwierdzone, 0 błędów, 1 zaszyfrowana notatka. Domyślnie włączone, można wyłączyć.
- Nagrania WAV: odsłuch potwierdzony przez użytkownika.
- EN: `en-US` i syntetyczne STT/TTS PASS, fizyczne GSM EN nieodebrane.
- Outgoing: wcześniejszy test 5 tur i END_CALL PASS, bez kwalifikacji bieżącego APK.
- QA: `454f766` — PL rozmowa/notatki; `c7eff94` — analiza GSM; `4ea08c8` — pełne QA (JVM/lint/501 Python), build oraz instalacja 0.2.1 na S22+ PASS.

- Refaktor Front/Back PR #77: squash merge `8c4517dc` (23.11), QA na `edb32655`: 528 Python, JVM/app/lint i oba APK PASS. S22+ debug `.s8lab`: SHA instalacji zgodne, 9/9 bezpiecznych testów UI PASS, host Binder odtworzony, IDLE; bez GSM i czyszczenia danych. [Mapa ekranów](architecture/UI_SCREEN_MAP.md).

## Separacja Front / Presentation / Domain / Data / Backend

23.11: PR #79, #80, #81 i #83 scalone; adaptery STT/TTS i probe modeli w Data, start Lab w testowalnej logice, importy warstw pilnowane przez bramki. Każdy PR miał zielony build i S22+ 9/9 bez GSM. Połączony main #79–81: 534 testy Python, oba APK, S22+ 9/9 PASS. Część pozostałych ekranów i natywny cykl TTS wymagają kolejnych QA i wydzielenia. [Handoff](operations/HANDOFF_NEXT_CHAT.md).

## Otwarte

Fizyczny EN i bieżący outgoing; podpis/IPC pakietu produkcyjnego. Strojenie Gemmy/kodów po MVP. SIP/Realtime odłożone.

**WSTRZYMANE:** Shizuku, GitHub Actions, schedulery, `WRITE_SECURE_SETTINGS`.
