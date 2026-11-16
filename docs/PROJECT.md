# AI Calls — MVP

**Cel:** S22+ Android 16: Call → RX → STT → LLM → TTS → TX → STOP.
**Rollback:** `mvp-s22-2026-11-15`; [checkpoint](../benchmarks/audio/FINAL_QUALITY_CHECKPOINT_2026-11-15.md).

## Stan projektu

`main`: stabilne C1–C3/G6.1/EN E2E. GSM PCM bez Shizuku blokuje Android/OEM.

## Podział pracy

S8: `feature/s8-no-shizuku`, bez merge. Debug APK `pl.michalmatu.aicallbridge.s8lab`, ARM64, obok stabilnej aplikacji. Narzędzia deweloperskie → VoIP Lab: 6 faz / 72 ramki RX/TX/STOP na localhost, syntetyczny RTP 8k → PCM 16k → Realtime 24k. Bez AI i rozmowy.

Ręczny SIP/TLS REGISTER: 401 → Digest SHA-256 → 200, hasło w RAM, STOP zamyka TCP. **REGISTER zmienia Contact konta do 300 s; tylko konto testowe.** Mac: unit + APK PASS, S22+ nowe UI PHYSICAL_OPEN; operator niezweryfikowany.

## Następny sprint — S8: bez Shizuku

VoIP INVITE/SDP/SRTP + realne media, następnie lokalne STT/LLM/TTS. Model `APP_PRIVATE_LITERT` nieodebrany.

## Otwarte

Bez realnej rozmowy, SIP operatora, SRTP, odnowienia REGISTER, wielu Contact i pełnej RFC 5922. GSM PCM z publicznego API niedostępny. Realny call tylko po zgodzie na numer/koszty/scenariusz. Bez PII/audio w Git.
