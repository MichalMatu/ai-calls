# AI Calls — MVP

**Cel:** S22+ Android 16: Call → RX → STT → LLM → TTS → TX → STOP.
**Rollback:** `mvp-s22-2026-11-15`; [checkpoint](../benchmarks/audio/FINAL_QUALITY_CHECKPOINT_2026-11-15.md).

## Stan projektu

`main`: stabilne C1–C3/G6.1/EN E2E. GSM PCM bez Shizuku nie jest publicznie dostępne na Androidzie 16.

## Podział pracy

S8: `feature/s8-no-shizuku`, bez merge. Debug APK `pl.michalmatu.aicallbridge.s8lab`, osobno od stabilnej instalacji, ARM64/S22+. W Narzędziach deweloperskich: lokalny test 6 faz RTP 8k ↔ PCM 16k ↔ Realtime 24k, 72 RX/TX i STOP, sztuczne audio na localhost (bez rozmowy/STT/LLM/TTS). Drugi ekran: ręczny SIP/TLS REGISTER → 401 → SHA-256 → 200 na koncie testowym; hasło tylko w RAM, STOP zamyka TCP. **REGISTER może zmienić Contact konta na 300 s.**

Mac: testy celowane + APK PASS; na S22+ nowe UI fizycznie niezweryfikowane. Brak sieciowego testu operatora.

## Następny sprint — S8: bez Shizuku

VoIP: interoperacyjny SIP/SDP, uwierzytelnione media SRTP i rzeczywisty RTP, następnie lokalne STT/LLM/TTS; dopiero potem 4–6 tur. `APP_PRIVATE_LITERT`: model niepotwierdzony.

## Otwarte

GSM bez uprawnień systemowych: ograniczenie Android/OEM. S8 bez INVITE, SRTP, realnego SIP operatora i działającej rozmowy. Brak auto-odnowień REGISTER, więcej niż jednego Contact i pełnej RFC 5922. Realny call tylko po zgodzie na numer/scenariusz/koszt. Bez PII/audio w Git.
