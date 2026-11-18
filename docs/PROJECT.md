# AI Calls — GSM bez Shizuku

**Jedyny cel:** Samsung S22+ (SM-S906B), GSM RX→STT→Gemma→TTS→TX bez Shizuku. Kod tylko na `feature/s8-no-shizuku`; `main` i stabilny APK nietknięte.

## Kod

S8 Lab (`.s8lab`) używa `GsmShellBinderCallMediaSessionBackend` bez fallbacku Shizuku. Własny host `GsmShellBinderMediaHost` startuje jawnie przez ADB jako UID 2000, rejestruje się w aplikacji przez Binder/`ContentProvider`. Dwa `ParcelFileDescriptor` przenoszą PCM. Wspólny `SamsungCallMediaSessionController` obsługuje RX/TX. UID, generacja, STOP, śmierć hosta i watchdog odcinają audio. Manifest S8 usuwa wejścia Shizuku.

## QA S22+ — 18.11.2026

- Android 16; APK S8 obok stabilnego dialera: **PASS**.
- Binder app↔shell, weryfikacja UID, transfer FD, rejestracja hosta: **PASS**.
- Kompilacja, testy `Gsm*Test`, odrzucenie starej generacji/STOP: **PASS**.
- GSM Binder gotowy; realny S22+ test rozmowy przychodzącej 18.11.2026: 6 tur.
- Unix socket odrzucony przez SELinux (`Permission denied`) — wyłączony.
## Otwarte

- PCM: RX 2 088 320, TX 2 325 986 B — **PASS funkcjonalny**; jakość sieci niezmierzona.

## Następny sprint

QA zwykłego dialera S8: ręczne odbieranie, AI takeover/resume, STOP, rozłączenie, latencja. `media_rx_tx_observed` jest niezależne od `conversation_terminal_category` i `chat_relay_live_call_success`. Przed merge sprawdzić 3 zaległe commity `main`. **WSTRZYMANE:** SIP/PJSUA2, merge do `main` przed QA. Bez schedulerów, Codex/Work/Actions ani danych rozmów w Git.
