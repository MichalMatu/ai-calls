# AI Calls — GSM bez Shizuku

**Jedyny cel:** Samsung S22+ (SM-S906B), GSM RX→STT→Gemma→TTS→TX bez Shizuku. Kod tylko na `feature/s8-no-shizuku`; `main` i stabilny APK nietknięte.

## Kod

S8 Lab (`.s8lab`) używa `GsmShellBinderCallMediaSessionBackend` bez fallbacku Shizuku. Własny host `GsmShellBinderMediaHost` startuje jawnie przez ADB jako UID 2000, rejestruje się w aplikacji przez Binder/`ContentProvider`. Dwa `ParcelFileDescriptor` przenoszą PCM. Wspólny `SamsungCallMediaSessionController` obsługuje RX/TX. UID, generacja, STOP, śmierć hosta i watchdog odcinają audio. Manifest S8 usuwa wejścia Shizuku.

## QA S22+ — 18.11.2026

- Android 16; APK S8 obok stabilnego dialera: **PASS**.
- Binder app↔shell, weryfikacja UID, transfer FD, rejestracja hosta: **PASS**.
- Kompilacja, testy `Gsm*Test`, odrzucenie starej generacji/STOP: **PASS**.
- `live_call_readiness=true`, `gsm_shell_ready=true`; telefon IDLE.
- Unix socket odrzucony przez SELinux (`Permission denied`) — wyłączony.
## Otwarte

- Rzeczywiste PCM RX/TX podczas rozmowy GSM: **NIEPOTWIERDZONE**.

## Następny sprint

Operator rozpoczyna testową rozmowę GSM, wraca do S8 Pilot i uruchamia START. Najpierw krótki RX/TX, potem 4–6 tur, barge-in, STOP i weryfikacja rozłączenia. Bez automatycznego dzwonienia. **WSTRZYMANE:** SIP/PJSUA2, UX, inne sprinty, merge do `main`. Bez schedulerów, Codex/Work/Actions ani danych rozmów w Git.
