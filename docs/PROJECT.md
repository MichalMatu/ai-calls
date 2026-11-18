# AI Calls — GSM bez Shizuku

**Cel:** S22+ GSM RX→STT→Gemma→TTS→TX. Kod na `feature/s8-no-shizuku`, bez zmian `main`.

## Kod

S8 Lab (`.s8lab`) używa `GsmShellBinderCallMediaSessionBackend`, bez fallbacku Shizuku. Po autoryzowanym ADB proces `GsmShellBinderMediaHost` działa jako UID 2000 i rejestruje Binder w aplikacji. Dwa `ParcelFileDescriptor` przenoszą PCM. UID, generacja, STOP, watchdog i śmierć hosta odcinają audio.

## QA S22+ — 18.11.2026

- Android 16; realna rozmowa przychodząca, 6 tur Gemma: **PASS funkcjonalny**.
- PCM RX 2 088 320 B / TX 2 325 986 B. Jakość sieci niezmierzona.
- UID, Binder, FD, STOP, testy, build, ekran rozmowy: **PASS**.
- Po zmianie instalacji host zniknął: `gsm_shell_host_unavailable`. Odtworzony przez ADB **bez Shizuku**; Binder/UID PASS. Stabilny APK usunięty przez użytkownika.

## Otwarte

- Host **nie przetrwa aktualizacji/rebootu**; bez ADB nie jest to samodzielne GSM. Aplikacja słusznie przechodzi w tryb MANUAL, nie używa Shizuku.
- Dalsze testy: ponowny Call, przejęcie AI, STOP, rozłączenie, opóźnienia.

## Następny sprint

Każdy deploy: `ANDROID_SERIAL=... bash scripts/bin/install-s8-gsm-lab.sh --install` (instalacja → host → Binder gate). Po restarcie bez instalacji: `start-s8-gsm-shell.sh --start`. Nie zaliczać deploymentu przy nieaktywnym hoście. **WSTRZYMANE:** SIP/PJSUA2, merge do `main` przed fizycznym gate. Bez schedulerów, Codex/Work/Actions i PII/WAV w Git.
