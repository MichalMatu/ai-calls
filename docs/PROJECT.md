# AI Calls — MVP

**Cel:** S22+ Android 16: Call → RX → STT → LLM → TTS → TX → STOP.
**Rollback:** `mvp-s22-2026-11-15`; [checkpoint](../benchmarks/audio/FINAL_QUALITY_CHECKPOINT_2026-11-15.md).

## Stan projektu

`main`: stabilne C1–C3/G6.1/EN E2E. S2–S7: wcześniejsze odbiory 60/60 torów, 18/18 tur i 5/5 STOP. GSM RX/TX nadal wymaga Shizuku.

## Podział pracy

`feature/s8-no-shizuku`: eksperyment bez merge do `main`. Mac: testy celowane i APK PASS. S22+: syntetyczny RTP/PCMU localhost 8 kHz — RX, TX i STOP PASS (bez Shizuku, ADB wymagane tylko do uruchomienia testu). Stabilny APK przywrócony, SHA-256 PASS; bez realnych połączeń.

## Następny sprint — S8: bez Shizuku

Wymienne backendy: Shizuku zachowany, publiczny GSM fail-closed. `AppOwnedVoipCallMediaSessionBackend` pilnuje exact-call i STOP; `AppOwnedLoopbackRtpMediaPort` realizuje fizycznie potwierdzone PCM/RTP. `APP_PRIVATE_LITERT`: lokalny LLM bez Shizuku, test jakości na S22+ otwarty.

## Otwarte

Publiczny GSM RX/TX zablokowany przez Android/OEM. Lokalny RTP nie jest usługą SIP: brak SIP/SDP, SRTP, uwierzytelnienia, PSTN i przekierowania. Dalej: bezpieczny zewnętrzny VoIP, STT/LLM/TTS oraz realny exact Call/STOP/4–6 tur po osobnej zgodzie. Bez PII/audio w Git.
