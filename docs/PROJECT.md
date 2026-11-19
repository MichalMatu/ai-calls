# AI Calls — plan GSM MVP

**Checkpoint 19.11.2026:** `main` `6910caba`, PR #29 `541b12f6` (kandydat, bez merge). S22+ SM-S906B, Android 16, `.s8lab`; GSM bez roota, Shizuku i Termuxa.

**PASS fizyczny:** AUTO → RX/STT → Gemma → TX/TTS → notatka → END_CALL, takeover/STOP; parowanie ADB z powiadomienia (19.11). Klucz przetrwał restart, status ADB/GSM działa. APK z rollbackiem, build i testy aplikacji PASS.

**Granica Androida:** host Binder `shell` UID 2000. Samsung wyłącza debugowanie po reboocie; włącz je ręcznie. Zachowany klucz nie wymaga kodu. Mac = fallback.

## Otwarte

- **P0:** po reboocie przy ADB ON wykryto `host_registered=false`. Start hosta **tylko przyciskiem z APK** nadal wymaga fizycznego receipt PASS. Bez niego nie nazywać wersji golden.
- **P0 QA:** pełny gate wszystkich modułów/lint/Python/bezpieczeństwo; pierwsze uruchomienie Lint ujawniło 89 błędów i 636 ostrzeżeń (do naprawy). Nie maskować baseline.
- **P1:** automatyczne włączenie debugowania niepotwierdzone; nie dodawać `WRITE_SECURE_SETTINGS` bez odrębnego audytu. Duże klasy runtime/model: refaktor później.

## Następny sprint

1. Zweryfikować app-only przycisk przy IDLE, bez polecenia GSM z Maca.
2. Naprawić blokujące Lint; pełny QA, preflight, checkpoint/rollback.
3. Dopiero po fizycznym PASS: stabilny tag i merge PR #29. Potem modularność i polski TTS.

**WSTRZYMANE:** SIP/PJSUA2, schedulery, nowe uprawnienia systemowe i realne połączenia bez zgody.
