# AI Calls — MVP

**Cel:** S22+ Android 16: Call → RX → STT → LLM → TTS → TX → STOP.
**Rollback:** `mvp-s22-2026-11-15`; [checkpoint](../benchmarks/audio/FINAL_QUALITY_CHECKPOINT_2026-11-15.md).

## Stan projektu

`main`: C1–C3/G6.1/EN E2E; GSM PCM nadal zależy od Shizuku.

## Podział pracy

`feature/s8-no-shizuku`: eksperyment bez merge. S22+: SDP/RTP 8k ↔ PCM 16k ↔ Realtime 24k, RX/TX/STOP PASS na localhost; bez rozmowy. Stabilny APK przywrócony (SHA PASS).

Mac: SIPS/TLS OPTIONS i offline REGISTER 401 → SHA-256 Digest → 200 PASS. Sprawdzane Via/Call-ID/CSeq, To/From i własny Contact z okresem ważności; obce odpowiedzi odrzucane. Preflight, testy celowane, APK PASS. **Bez rzeczywistej rejestracji, konta SIP lub sieci.**

## Następny sprint — S8: bez Shizuku

Po zgodzie: sprawdzony kanał TLS, pełna obsługa odpowiedzi registrarów i kontaktów, potem SIP INVITE/SDP/SRTP oraz lokalne STT/LLM/TTS. `APP_PRIVATE_LITERT`: odbiór modelu otwarty.

## Otwarte

Brak realnego REGISTER, INVITE, SRTP, operatora i PSTN. Obsługa certyfikatów RFC 5922 poza DNS SAN otwarta. GSM PCM blokuje Android/OEM. Test 4–6 tur tylko po zgodzie na numer/koszty/scenariusz. Bez PII/audio w Git.
