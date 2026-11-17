# AI Calls — MVP

**Cel:** S22+ Android 16: Call → RX → STT → LLM → TTS → TX → STOP.
**Rollback:** `mvp-s22-2026-11-15`.

## Stan projektu

`main`: stabilne C1–C3/G6.1; GSM PCM bez Shizuku blokuje Android/OEM.

## Podział pracy

S8 `feature/s8-no-shizuku`, osobny ARM64 APK `pl.michalmatu.aicallbridge.s8lab`. **S22+ PHYSICAL PASS 2026-11-17:** RTP/SDP/STOP 6 faz 72 RX/72 TX; polskie STT → zatwierdzony tekst → TTS PCM. E2B 2,6 GB pobrany w sandboxie S8, natywna próba zgodności tekstu **PASS**; znacznik trwały i status „Model compatibility verified” widoczny także bez przyznanego READ_PHONE_STATE. „Check compatibility” teraz żąda uprawnienia z jasnym komunikatem i wymaga ponownego kliknięcia; nie włącza sam inferencji po zgodzie. Stabilny APK SHA bez zmian, S8 ma backup.

Kod 4 tur LiteRT/STOP i prywatny raport gotowy; **cztery tury native jeszcze OPEN**. Mac unit/APK PASS; emulator UI PASS.

## Następny sprint — S8 bez Shizuku

Sprawdzić 4–6 lokalnych tur z E2B i STT→LLM→TTS; następnie SIP INVITE/SDP/SRTP.

## Otwarte

Brak realnego VoIP, GSM PCM i SRTP. Bez realnego Call bez zgody na numer/koszt. Bez PII/audio w Git.
