# AI Calls — MVP

**Cel:** S22+ Android 16: Call → RX → STT → LLM → TTS → TX → STOP.
**Rollback:** `mvp-s22-2026-11-15`.

## Stan projektu

`main`: stabilne C1–C3/G6.1, bez zmian. GSM PCM bez Shizuku blokuje Android/OEM.

## Podział pracy

S8: `feature/s8-no-shizuku`, bez merge. Oddzielny ARM64 APK `pl.michalmatu.aicallbridge.s8lab` z poprawką znacznika `report_complete`. **S22+ Android 16 PHYSICAL PASS (2026-11-17):** lokalny RTP/SDP/STOP 6 faz, 72 RX/72 TX; po STT/TTS powtórzony test 72/72 PASS. Polski Android STT → deterministyczny tekst → zatwierdzenie → lokalny TTS PCM **PASS**. Bez mikrofonu, realnego SIP i połączeń. Bieżący APK S8 pozostawiony na urządzeniu, stabilna aplikacja SHA bez zmian, pierwotny APK S8 zachowany do rollbacku.

Emulator: UI/RTP PASS; offline STT/TTS niedostępne w obrazie systemowym. Oddzielny sandbox S8: brak pliku wbudowanego LiteRT (odczyt dwóch lokalizacji); inferencja lokalnego LLM **OPEN**. Testy celowane i APK na Macu PASS.

## Następny sprint — S8: bez Shizuku

Dostarczyć/zweryfikować model LiteRT w sandboxie S8, przeprowadzić 4–6 rzeczywistych lokalnych tur STT→LLM→TTS, następnie uwierzytelnione SIP INVITE/SDP/SRTP.

## Otwarte

Brak prawdziwej rozmowy VoIP i GSM PCM, SRTP oraz fizycznej inferencji LLM. REGISTER wyłącznie ręczny na autoryzowanym koncie testowym (zmienia Contact). Realny Call tylko po zgodzie na numer/koszt/scenariusz. Bez PII/audio w Git.
