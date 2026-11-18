# AI Calls — GSM bez Shizuku

**Cel:** S22+ GSM RX→STT→Gemma→TTS→TX. Kod na `feature/s8-no-shizuku`, bez zmian `main`.

## Kod

S8 Lab (`.s8lab`) używa `GsmShellBinderCallMediaSessionBackend`, bez fallbacku Shizuku. Po autoryzowanym ADB proces `GsmShellBinderMediaHost` działa jako UID 2000 i rejestruje Binder w aplikacji. Dwa `ParcelFileDescriptor` przenoszą PCM. UID, generacja, STOP, watchdog i śmierć hosta odcinają audio.

## QA S22+ — 18.11.2026

- Android 16; realna rozmowa przychodząca, 6 tur Gemma: **PASS funkcjonalny**.
- PCM RX 2 088 320 B / TX 2 325 986 B. Jakość sieci niezmierzona.
- **E2E PASS**: ponowny Call z AI, 4 tury, RX 753 920 B, TX 1 128 844 B, `media_rx_tx_observed=true`, `conversation_terminal_category=completed`, `chat_relay_live_call_success=true`. Użytkownik potwierdził zapis notatki i rozłączenie przez AI; zaszyfrowany indeks notatek istnieje.
- UID, Binder, FD, STOP, testy, build, ekran rozmowy: **PASS**.
- Po zmianie instalacji host zniknął: `gsm_shell_host_unavailable`. Odtworzony przez ADB **bez Shizuku**; Binder/UID PASS. Stabilny APK usunięty przez użytkownika.

## Otwarte

- Host **nie przetrwa aktualizacji/rebootu**; bez ADB nie jest to samodzielne GSM. Aplikacja słusznie przechodzi w tryb MANUAL, nie używa Shizuku.
- Pozostało: manualne przejęcie, STOP, restart, opóźnienia oraz trwałość uruchomienia hosta; sama ścieżka AI→notatka→koniec rozmowy zaliczona.

## Następny sprint

Każdy deploy: `ANDROID_SERIAL=... bash scripts/bin/install-s8-gsm-lab.sh --install` (instalacja → host → Binder gate). Po restarcie bez instalacji: `start-s8-gsm-shell.sh --start`. Nie zaliczać deploymentu przy nieaktywnym hoście. **WSTRZYMANE:** SIP/PJSUA2, merge do `main` przed trwałym gate hosta. Bez schedulerów, Codex/Work/Actions i PII/WAV w Git.
