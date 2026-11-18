# AI Calls — GSM bez Shizuku

**Cel:** S22+ GSM RX→STT→Gemma→TTS→TX bez Shizuku. Gałąź `feature/s8-no-shizuku`; bez zmian `main`.

## Kod

S8 Lab (`.s8lab`) używa `GsmShellBinderCallMediaSessionBackend` bez fallbacku Shizuku. Własny host `GsmShellBinderMediaHost` startuje jawnie przez ADB jako UID 2000, rejestruje się w aplikacji przez Binder/`ContentProvider`. Dwa `ParcelFileDescriptor` przenoszą PCM. Wspólny `SamsungCallMediaSessionController` obsługuje RX/TX. UID, generacja, STOP, śmierć hosta i watchdog odcinają audio. Manifest S8 usuwa wejścia Shizuku.

## QA S22+ — 18.11.2026

- Android 16, S8 Lab: **PASS**; stabilny APK usunięty przez użytkownika.
- Binder app↔shell, weryfikacja UID, transfer FD, rejestracja hosta: **PASS**.
- Kompilacja, testy `Gsm*Test`, odrzucenie starej generacji/STOP: **PASS**.
- S22+ GSM 18.11: 6 tur, RX/TX PASS; host zniknął po zmianie instalacji i został przywrócony przez ADB bez Shizuku (UID/Binder PASS).
- Unix socket odrzucony przez SELinux (`Permission denied`) — wyłączony.
## Otwarte

- PCM RX 2 088 320 i TX 2 325 986 B — PASS; jakość niezmierzona.
- Host ADB nie jest trwały po restarcie/aktualizacji. Nie jest to samodzielny tryb bez ADB. Zanik hosta bezpiecznie wyłącza AI; bez Shizuku.

## Następny sprint

Każdy deploy S8: `install-s8-gsm-lab.sh --install` (APK→ADB host→Binder gate); po restarcie bez instalacji: `start-s8-gsm-shell.sh --start`. QA: ponowny rzeczywisty Call, takeover/STOP, restart. **WSTRZYMANE:** SIP/PJSUA2 i merge do `main` bez odbioru. Bez schedulerów, Codex/Work/Actions i danych rozmów w Git.
