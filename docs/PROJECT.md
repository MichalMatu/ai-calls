# AI Calls — jedyny aktywny plan

**TYLKO GSM bez Shizuku na Samsungu S22+.** Użytkownik potwierdził, że GSM działa z Shizuku. Kodować na `feature/s8-no-shizuku`; `main` i działającego APK nie zmieniać.

## Stan projektu

Gotowy własny tor Samsung: `SamsungVoiceDownlinkCapture` (RX), `SamsungCallAssistantTrack` (TX), `SamsungCallMediaSessionController`, PCM → STT → Gemma → TTS. Shizuku dostarcza UID `shell` (2000); samo kopiowanie kodu nie daje tego uprawnienia.

## Otwarte

Własny host `app_process` uruchamiany po zgodzie ADB; lokalny kanał z kontrolą UID aplikacji i `shell`, generacji oraz RX/TX PCM. STOP lub utrata połączenia musi zamknąć oba tory. Brak publicznego serwera/komend. Zachować produkcyjny Shizuku fallback do odbioru na S22.

## Następny sprint

1. Dokończyć backend host ↔ aplikacja, bez duplikacji Samsung audio; kompilacja i testy UID/STOP/generacji/FD.
2. Dopiero po zgodzie: izolowany APK S8, IDLE, sprawdzenie ADB, RX → STT → Gemma → TTS → TX, 4–6 tur, barge-in, `END_CALL`, nagranie, rollback.
3. Po dowodach fizycznych wyłączyć zależność od Shizuku; nie wcześniej.

**WSTRZYMANE:** SIP/VoIP/PJSUA2, modele, UX, pozostałe audyty i nowe funkcje. Nie scalać z `main` bez odbioru. Existing Code First → patch → test celowany → receipt Local Agenta. Bez Codex/Work/Actions, nowych schedulerów, nieautoryzowanych połączeń/instalacji i PII/WAV w Git.
