# AI Calls — plan GSM MVP

**Checkpoint 19.11.2026:** kod PR #29 `18f4c72b` zakwalifikowany po pełnym QA; tag golden obejmuje ten stan po scaleniu. S22+ SM-S906B, Android 16, `.s8lab`; GSM bez roota, Shizuku i Termuxa.

**PASS fizyczny:** GSM AUTO/RX/STT/Gemma/TX/TTS/notatka/END_CALL i takeover/STOP (poprzedni odbiór). Parowanie ADB przez powiadomienie, statusy, klucz po reboocie oraz start hosta z przycisku AI Calls bez Maca — PASS 19.11. APK na S22+ z kopią rollback i zgodnym SHA.

**Granica Androida:** host Binder `shell` UID 2000. Samsung wyłącza debugowanie po reboocie; włącz je ręcznie. Zachowany klucz nie wymaga kodu. Mac = fallback.

## Otwarte

- **PASS QA:** 1370 testów Gradle + 463 Python; Lint 0 errors, około 636 ostrzeżeń raportowanych przez Lint (2 sugestie UseKtx w nowym module). Preflight, APK i testy fizyczne PASS.
- **P1:** po restarcie trzeba ręcznie włączyć debugowanie bezprzewodowe. Nie dodawać `WRITE_SECURE_SETTINGS` bez audytu.
- **Dług:** pozostałe ostrzeżenia Lint i duże klasy runtime/model; bez ryzykownego refaktoru w zamrożonym GSM.

## Następny sprint

1. Utrzymać golden build S22+ i prosty ręczny start po restarcie (bez ponownego kodu).
2. Osobno zaplanować czyszczenie pozostałych ostrzeżeń i podział dużych klas.
3. Weryfikować nowe wersje Androida/OEM oraz polski TTS bez regresji GSM.

**WSTRZYMANE:** SIP/PJSUA2, schedulery, nowe uprawnienia systemowe i realne połączenia bez zgody.
