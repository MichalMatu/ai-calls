# AI Calls — MVP

**Cel:** S22+ Android 16: Call → RX → STT → LLM → TTS → TX → STOP.
**Rollback:** `mvp-s22-2026-11-15`.

## Stan projektu

`main` stabilne, bez zmian; GSM PCM bez Shizuku niedostępne na Android/OEM.

## Podział pracy

S8 `feature/s8-no-shizuku`; osobny ARM64 APK `pl.michalmatu.aicallbridge.s8lab`. **S22+ PHYSICAL PASS 2026-11-17:** RTP/SDP/STOP: 6 faz, 72 RX/72 TX, również powtórka po STT. Polski STT → zatwierdzona odpowiedź deterministyczna → TTS PCM **PASS**. Raport `running` nie oznacza już `complete`. Nowy APK pozostawiono; poprzedni zachowano na rollback, stabilny SHA bez zmian. Brak rozmowy telefonicznej.

Emulator RTP/UI PASS, offline STT/TTS niedostępne. W sandboxie S8 brak pliku LiteRT; inferencja **OPEN**. Mac testy/API APK PASS.

## Następny sprint — S8: bez Shizuku

Dostarczyć model LiteRT, zweryfikować 4–6 tur STT→LLM→TTS, następnie SIP INVITE/SDP/SRTP.

## Otwarte

Brak realnego VoIP, GSM PCM, SRTP i LLM. REGISTER tylko na koncie testowym; Call tylko za zgodą na numer/koszt. Bez PII/audio w Git.
