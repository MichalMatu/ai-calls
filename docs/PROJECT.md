# AI Calls — GSM MVP bez Shizuku

S22+ GSM: RX→STT→Gemma→TTS→TX; `GsmShellBinderCallMediaSessionBackend` + autoryzowany ADB host UID 2000. Binder z UID, generacją, STOP, watchdogiem; bez fallbacku Shizuku.

## Odbiór — 18.11.2026

- AUTO, lokalna Gemma, szyfrowana notatka, zakończenie przez AI: fizyczne E2E PASS.
- Poprawka „do widzenia”: potwierdzona przez użytkownika. Ostatnia sesja 3 tury, RX/TX, `conversation_terminal_category=completed`.
- S22+: 1200 testów jednostkowych PASS, 22 Python PASS, UI 2/2 PASS, build/debug Binder/default dialer PASS. Zachowany APK do rollback.
- Mac automatycznie wznowił host po rzeczywistym restarcie telefonu: PASS.

## Otwarte

- **Brak autonomicznego startu bez MacBooka.** Po reboocie host potrzebuje autoryzowanego ADB; `s8_gsm_reconnect.py` czeka na zdarzenie USB. Bez schedulera.
- Ręczne przejęcie/wznowienie AI oraz STOP wymagają osobnego potwierdzenia fizycznego; testy stanów UI i autoryzacji PASS.

## Następny sprint

- Jakość głosu/odpowiedzi: osobny sprint. **WSTRZYMANE:** SIP/PJSUA2 i zmiany poza GSM MVP.
- Deploy: `install-s8-gsm-lab.sh --install` tylko IDLE, z rollback. Bez Codex/Work/Actions, PII/WAV w Git.
