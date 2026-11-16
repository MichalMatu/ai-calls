# AI Calls — MVP

**Cel:** S22+ Android 16: Call → RX → STT → LLM → TTS → TX → STOP.
**Rollback:** `mvp-s22-2026-11-15`; [checkpoint](../benchmarks/audio/FINAL_QUALITY_CHECKPOINT_2026-11-15.md).

## Stan projektu

`main`: stabilne C1–C3/G6.1/EN E2E; GSM RX/TX wymaga Shizuku.

## Podział pracy

`feature/s8-no-shizuku`: eksperyment bez merge i bez zmiany domyślnego backendu. Mac: testy celowane i APK PASS. S22+ Android 16: RTP/PCMU 8 kHz ↔ aplikacyjny PCM 16 kHz ↔ Realtime PCM 24 kHz — RX, TX, STOP **PASS**. Tylko syntetyczna cisza na `127.0.0.1`; bez Shizuku. ADB wyłącznie do QA. Stabilny APK przywrócony (SHA-256 PASS), bez rzeczywistych połączeń.

## Następny sprint — S8: bez Shizuku

Zaimplementować bezpieczne SIP/SDP/SRTP i lokalny tor STT/LLM/TTS z tym transportem. `APP_PRIVATE_LITERT` jawnie opcjonalne; model na S22+ czeka na odbiór.

## Otwarte

GSM RX/TX: blokada Android/OEM. Loopback nie zapewnia SIP, uwierzytelnienia ani PSTN. Test rozmowy 4–6 tur wymaga osobnej zgody na numer, koszt i scenariusz. Bez PII/audio w Git.
