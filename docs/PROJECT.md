# AI Calls — plan GSM MVP

**Stan 18.11.2026:** PR #23 scalony do `main`. S22+ Android 16, debug `.s8lab`. AUTO → STT → lokalna Gemma → TTS → zaszyfrowana notatka → END_CALL: fizyczny PASS; „do widzenia” potwierdzone. Ostatni Call: 3 tury, RX/TX, `conversation_terminal_category=completed`. QA: 1200 jednostkowych, 22 Python, UI 2/2 PASS. APK do rollback zachowany.

**Zależność:** host Binder UID 2000 jako `shell`; Mac z autoryzowanym ADB wznowił go po reboocie (PASS). **Bez Maca brak samodzielnego startu hosta po reboocie.**

## Otwarte

- **P0 GSM:** przejęcie człowieka → wznowienie AI i STOP bez osobnego pełnego fizycznego receipt. Pożegnanie, notatka, audio RX/TX: PASS.
- **P1 autonomia:** zwykła aplikacja nie uzyskuje UID 2000 ani chronionego toru audio GSM. Każda alternatywa wymaga testu na Android 16.
- **Dług:** `app` 375 plików (~2,2 MiB kodu); `Gemma4LiteRtTextBackend` 1140 linii, `AndroidConversationRuntime` 1028. Za duże granice odpowiedzialności.

## Następny sprint

1. **S1 odbiór GSM:** jeden Call: AUTO → manual takeover → resume AI → STOP; potwierdź exact Call/generację, brak starego TX i prywatny raport (bez PII). Nie powtarzaj zaliczonego E2E.
2. **S2 niezależność:** porównaj publiczne Android API/rolę dialera, podpis systemowy/OEM, root/ADB. Wynik: sprawdzona architektura albo jawny HOLD; nie psuj Binder MVP.
3. **S3 modularność:** małe ekstrakcje stanu tury/audio z `AndroidConversationRuntime`, adaptera modelu z `Gemma4LiteRtTextBackend`. Test każdej granicy; zero regresji GSM.
4. **S4 jakość:** oddzielny sprint naturalności odpowiedzi, polskiego głosu i opóźnień.

**WSTRZYMANE:** SIP/PJSUA2, nowe schedulery i refaktor podczas odbioru. Gate: fokus → preflight → build → fizyczne QA tylko przy zmianie działania.
