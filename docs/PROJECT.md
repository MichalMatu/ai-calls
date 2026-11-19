# AI Calls — jedyna roadmapa

**Golden:** tag `s22-gsm-mvp-golden-2026-11-19` → `f0940d51`; S22+ SM-S906B, Android 16, `.s8lab` (nie wydanie uniwersalne).
**PASS:** parowanie ADB z powiadomienia, zachowany klucz, stany ADB/GSM, start hosta z APK bez Maca, rollback/SHA. Wcześniejszy fizyczny incoming: RX/STT/Gemma/TTS/TX/notatka/END_CALL, STOP/takeover. QA: 1370 JVM, 463 Python, Lint 0 błędów.
**Po reboocie:** ręcznie włącz debugowanie bezprzewodowe; przy zachowanym kluczu bez nowego kodu. Nie dodawać `WRITE_SECURE_SETTINGS`.

## Otwarte

- **P0 incoming E2E:** na golden 4–6 tur, realne RX/TX, cisza, STOP/takeover, pożegnanie i hangup; brak testu rozmowy po ostatnim APK.
- **P0 outgoing GSM:** `PhoneTask` i UI istnieją, ale AI/PCM outgoing nie ma fizycznego PASS; SIM, zgoda, anulowanie, END_CALL.
- **P1 nagrania:** opt-in, zapis i odtwarzanie obu kierunków; bez pozornych statusów.
- **P1 audio/UX:** polskie STT/TTS, odsłuch; 633 ostrzeżenia Lint (bez zbiorczego wyciszania).
- **P2 utrzymanie:** małe refaktory dużych klas runtime/model i zgodność Android/OEM.

## Następny sprint

1. Celowane QA golden + fizyczny incoming 4–6 tur za zgodą.
2. Outgoing E2E i nagrania; po poprawkach pełny gate i odbiór fizyczny.
3. Jakość/modularność; nowy golden wyłącznie po PASS — [bramki](operations/DEVELOPMENT.md).

**WSTRZYMANE:** SIP/PJSUA2, schedulery, root, nowe uprawnienia, autonomiczne dzwonienie.
