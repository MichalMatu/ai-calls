# AI Calls — GSM bez Shizuku

**Cel:** S22+ GSM RX→STT→Gemma→TTS→TX bez Shizuku; kod tylko na `feature/s8-no-shizuku`.

## Kod

S8 Lab korzysta z `GsmShellBinderCallMediaSessionBackend`; host ADB UID 2000 rejestruje Binder i przesyła PCM przez dwa FD. Kontrole: UID, generacja, STOP, watchdog, zanik hosta. Bez fallbacku Shizuku.

## QA S22+ — 18.11.2026

- GSM 6 tur: RX 2 088 320 / TX 2 325 986 B; PASS.
- E2E 4 tury: RX 753 920 / TX 1 128 844 B, `conversation_terminal_category=completed`, `chat_relay_live_call_success=true`. Użytkownik potwierdził notatkę i rozłączenie AI; szyfrowany indeks notatek istnieje.
- Binder/UID/build/UI: PASS. Po aktualizacji APK host odtworzono; po restarcie adbd USB nasłuch Mac sam wznowił host: PASS.

## Otwarte

- Android po reboocie nie uruchomi sam hosta UID 2000. Nasłuch ADB na Macu wznowi host po podłączeniu autoryzowanego S22+, lecz nie zapewni niezależnej pracy telefonu.
- Do odbioru: prawdziwy reboot z podłączonym Maciem, ręczne przejęcie i STOP.

## Następny sprint

Na Macu `s8_gsm_reconnect.py --install` włącza zdarzenia `adb wait-for-device/disconnect` bez schedulera; `--uninstall` wyłącza. Tylko zatwierdzony S22+, IDLE, sprawdzony Binder. Aktualizacja APK: `install-s8-gsm-lab.sh --install`. **WSTRZYMANE:** SIP/PJSUA2 i merge do `main` przed odbiorem. Bez Codex/Work/Actions, schedulerów i PII/WAV w Git.
