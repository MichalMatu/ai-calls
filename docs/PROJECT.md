# AI Calls — jedyny aktywny plan

**TYLKO GSM bez Shizuku na Samsungu S22+.** Użytkownik potwierdził, że GSM działa z Shizuku. Kodować na `feature/s8-no-shizuku`; `main` i działającego APK nie zmieniać.

## Stan projektu

Gotowy własny tor Samsung: `SamsungVoiceDownlinkCapture` (RX), `SamsungCallAssistantTrack` (TX), `SamsungCallMediaSessionController`, PCM → STT → Gemma → TTS. Shizuku dostarcza UID `shell` (2000); samo kopiowanie kodu nie daje tego uprawnienia.

## Otwarte

Host `GsmShellMediaHost` i backend S8 działają w kodzie: kontrola UID, generacji, STOP, lokalny RX/TX PCM i ręczny ADB bootstrap. S8 nie wraca do Shizuku, także przy wyborze modelu (Gemma LiteRT); produkcja zachowuje fallback. Kompilacja i testy jednostkowe PASS; rozmowa GSM **bez Shizuku niepotwierdzona**.

## Następny sprint

1. Zweryfikować bootstrap hosta, przekazanie FD, STOP i brak wycieków na urządzeniu; utrzymać celowane testy.
2. Dopiero po zgodzie: izolowany APK S8, IDLE, sprawdzenie ADB, RX → STT → Gemma → TTS → TX, 4–6 tur, barge-in, `END_CALL`, nagranie, rollback.
3. Po dowodach fizycznych wyłączyć zależność od Shizuku; nie wcześniej.

**WSTRZYMANE:** SIP/VoIP/PJSUA2, badania modeli, UX, pozostałe audyty i nowe funkcje. Nie scalać z `main` bez odbioru. Existing Code First → patch → test celowany → receipt Local Agenta. Bez Codex/Work/Actions, nowych schedulerów, nieautoryzowanych połączeń/instalacji i PII/WAV w Git.
