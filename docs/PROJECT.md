# AI Calls — plan GSM MVP

**Stan 19.11.2026:** PR #23 scalony do `main`. S22+ Android 16, debug `.s8lab`. AUTO → STT → lokalna Gemma → TTS → zaszyfrowana notatka → END_CALL: fizyczny PASS; „do widzenia” potwierdzone. Ostatni Call: 3 tury, RX/TX, `conversation_terminal_category=completed`. QA: 1200 jednostkowych, 22 Python, UI 2/2 PASS. APK do rollback zachowany.

**Zależność:** host Binder UID 2000 jako `shell`; Mac z autoryzowanym ADB wznowił go po reboocie (PASS). **Bez Maca brak samodzielnego startu hosta po reboocie.**

## Otwarte

- **P0 GSM:** TAKE OVER → RESUME AI → STOP: fizyczny PASS użytkownika 19.11 (bez nowego receipt); poprzednie RX/TX, notatka i END_CALL PASS.
- **P1 autonomia:** zwykła aplikacja nie uzyskuje UID 2000 ani chronionego toru audio GSM. Każda alternatywa wymaga testu na Android 16.
- **Dług:** `app` 375 plików (~2,2 MiB kodu); `Gemma4LiteRtTextBackend` 1140 linii, `AndroidConversationRuntime` 1028. Za duże granice odpowiedzialności.

## Następny sprint

1. **S1 GSM (PASS 19.11):** TAKE OVER → RESUME AI → STOP działa. Nie powtarzać E2E bez regresji.
2. **S2 autonomia (HOLD):** APK na Android 16 nie ma UID 2000 ani CAPTURE_AUDIO_OUTPUT. Reboot wymaga ADB lub dostępu OEM/system. Lab pokazuje stan hosta; bez hosta nie odbiera automatycznie.
3. **S3 modularność:** małe ekstrakcje stanu tury/audio z `AndroidConversationRuntime`, adaptera modelu z `Gemma4LiteRtTextBackend`. Test każdej granicy; zero regresji GSM.
4. **S4 jakość:** oddzielny sprint naturalności odpowiedzi, polskiego głosu i opóźnień.

**WSTRZYMANE:** SIP/PJSUA2, nowe schedulery i refaktor podczas odbioru. Gate: fokus → preflight → build → fizyczne QA tylko przy zmianie działania.
