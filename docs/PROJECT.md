# AI Calls — MVP

**Cel:** S22+ Android 16: Call → RX → STT → LLM → TTS → TX → STOP.
**Rollback:** `mvp-s22-2026-11-15`.

## Stan projektu

`main`: stabilne C1–C3/G6.1. GSM PCM blokuje Android/OEM bez Shizuku.

## Podział pracy

S8 na `feature/s8-no-shizuku`, bez merge. Oddzielny ARM64 APK `pl.michalmatu.aicallbridge.s8lab`. **S22 PHYSICAL PASS**: 6 faz RTP 72 RX/72 TX, SDP/STOP, stabilna aplikacja SHA bez zmian.

Nowy lokalny STT/TTS lab (syntetyczne wejście, deterministyczny tekst, zgoda na odpowiedź, STOP). DUMP raport: naprawiona ścisła lista nazw. Emulator: raport kompletny, STT/TTS FAIL (brak dostępnego polskiego głosu/rozpoznawania); UI RTP 2 testy PASS. S22 test STT/TTS PHYSICAL_OPEN: wcześniejsza próba przywrócona SHA, później brak ADB. Panel pokazuje, czy plik LiteRT istnieje **w osobnej aplikacji S8**; istnienie ≠ inferencja. Unit/testy APK PASS.

## Następny sprint — S8: bez Shizuku

Potwierdzić polskie STT/TTS na S22, model LiteRT i 4–6 tur, potem uwierzytelnione SIP INVITE/SDP/SRTP.

## Otwarte

Bez realnej rozmowy/operacyjnego SIP/SRTP i potwierdzonego LLM. REGISTER wyłącznie ręczny na koncie testowym (może zmienić Contact). Realny call tylko po zgodzie na numer/koszt/scenariusz. Bez PII/audio w Git.
