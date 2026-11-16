# AI Calls — MVP

**Cel:** S22+ Android 16: Call → RX → STT → LLM → TTS → TX → STOP.
**Rollback:** `mvp-s22-2026-11-15`; [checkpoint](../benchmarks/audio/FINAL_QUALITY_CHECKPOINT_2026-11-15.md).

## Stan projektu

`main`: stabilne C1–C3/G6.1/EN E2E; GSM RX/TX zależy od Shizuku.

## Podział pracy

`feature/s8-no-shizuku`: bez merge, domyślny backend niezmieniony. S22+: syntetyczny SDP ↔ RTP/PCMU 8 kHz ↔ PCM 16 kHz ↔ Realtime 24 kHz: RX/TX/STOP PASS, obcy SDP odrzucony. Tylko `127.0.0.1`; ADB do QA. Stabilny APK przywrócony (SHA PASS), bez rozmowy.

Mac: osobny klient SIPS/TLS OPTIONS (bez REGISTER/INVITE/media), ścisła korelacja odpowiedzi, DNS SAN i zaufane CA, blokada sieci bez zgody. Testy z fałszywym kanałem TLS, preflight i APK PASS; **bez rzeczywistego połączenia TLS z operatorem**.

## Następny sprint — S8: bez Shizuku

Uwierzytelnione SIP/SDP i sprawdzony SRTP, potem lokalne STT/LLM/TTS; `APP_PRIVATE_LITERT` wymaga odbioru modelu.

## Otwarte

Brak SIP REGISTER/INVITE, SRTP, konta operatora i PSTN. Pełne sprawdzenie certyfikatów SIP RFC 5922 poza podzbiorem DNS SAN otwarte. GSM PCM blokuje Android/OEM. Test 4–6 tur tylko za osobną zgodą na numer/koszty/scenariusz. Bez PII/audio w Git.
