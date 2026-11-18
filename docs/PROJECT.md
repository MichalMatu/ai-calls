# AI Calls — jedyny aktywny cel

**GSM bez Shizuku na Samsungu S22+ (SM-S906B).** Rozwój tylko na `feature/s8-no-shizuku`. `main` i stabilny dialer pozostają nietknięte. SIP/PJSUA2, UX i inne prace są wstrzymane.

## Implementacja

Samsung RX `VOICE_DOWNLINK` + TX `CALL_ASSISTANT` pozostają we wspólnym `SamsungCallMediaSessionController`. Izolowany APK S8 (`.s8lab`) wybiera `GsmShellBinderCallMediaSessionBackend` bez fallbacku do Shizuku. Własny host `GsmShellBinderMediaHost` działa jako UID 2000 po **jawnej autoryzacji ADB** i rejestruje `IGsmShellMediaService` przez systemowy Binder/`ContentProvider`; manifest S8 usuwa wejścia Shizuku. PCM przenoszą dwa `ParcelFileDescriptor`, a STOP jest jednokierunkowym wywołaniem Binder. UID, generacja, śmierć hosta i STOP są sprawdzane.

## Dowody z S22+, 18.11.2026

- Android 16; izolowany S8 instalowany obok stabilnego APK, bez zmiany roli dialera.
- Systemowy Binder app↔shell, rejestracja hosta, FD, UID 2000: **PASS**.
- APK, testy `Gsm*Test`, koordynator, diagnostyka: **PASS**.
- Odrzucenie heartbeat nieaktywnej generacji i spóźnionego STOP bez utraty hosta: **PASS**.
- Unix `LocalSocket` blokowany przez SELinux/`Permission denied`; **nie używać**.
- Rzeczywiste GSM RX→STT→Gemma→TTS→TX i rozłączenie: **NIEPOTWIERDZONE**.

## Następna bramka

Po fizycznym QA i przy obecności operatora: aktywna **testowa rozmowa GSM**, `SupervisedCallPilotActivity` w S8, START/STOP, najpierw krótki test RX/TX, potem 4–6 tur; barge-in i zakończenie. Telefon i rozmowa muszą być jawnie zatwierdzone, z kontrolą IDLE przed działaniami ADB, bez automatycznego wybierania numeru.

Nie scalać z `main` bez dowodów fizycznych. Nie włączać schedulerów, Codex/Work/Actions; nie publikować numerów, WAV ani transkryptów w Git. [Zasady](../AGENTS.md).
