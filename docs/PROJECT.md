# AI Calls — plan GSM MVP

**Checkpoint 19.11.2026:** `main` `6910caba`, PR #29 `541b12f6` (kandydat, bez merge). S22+ SM-S906B, Android 16, `.s8lab`; GSM bez roota, Shizuku i Termuxa.

**Potwierdzone:** fizyczne AUTO → RX/STT → lokalna Gemma → TX/TTS → zaszyfrowana notatka → END_CALL (wcześniejsze 3 tury, takeover/STOP). Parowanie ADB kodem z powiadomienia na tym samym telefonie: PASS 19.11. Klucz zachowany po restarcie; stan ADB/GSM widoczny. APK zabezpieczone rollbackiem; build i testy jednostkowe aplikacji PASS.

**Granica Androida:** host Binder `shell` UID 2000. Samsung wyłącza debugowanie bezprzewodowe po reboocie; trzeba je ręcznie włączyć, bez ponownego kodu, jeśli klucz nadal jest autoryzowany. Mac ADB = fallback.

## Otwarte

- **P0:** po reboocie przy ADB ON wykryto `host_registered=false`. Start hosta **tylko przyciskiem z APK** nadal wymaga fizycznego receipt PASS. Bez niego nie nazywać wersji golden.
- **P0 QA:** pełny gate wszystkich modułów/lint/Python/bezpieczeństwo; pierwsze uruchomienie Lint ujawniło 89 błędów i 636 ostrzeżeń (do naprawy). Nie maskować baseline.
- **P1:** automatyczne włączenie debugowania niepotwierdzone; nie dodawać `WRITE_SECURE_SETTINGS` bez odrębnego audytu. Duże klasy runtime/model: refaktor później.

## Następny sprint

1. Zweryfikować app-only przycisk przy IDLE, bez polecenia GSM z Maca.
2. Naprawić blokujące Lint; pełny QA, preflight, checkpoint/rollback.
3. Dopiero po fizycznym PASS: stabilny tag i merge PR #29. Potem modularność i polski TTS.

**Wstrzymane:** SIP/PJSUA2, schedulery, nowe uprawnienia systemowe i realne połączenia bez zgody.
