# AI Calls — MVP

**Cel:** S22+ Android 16: Call → RX → STT → LLM → TTS → TX → STOP.
**Rollback:** `mvp-s22-2026-11-15`; [checkpoint](../benchmarks/audio/FINAL_QUALITY_CHECKPOINT_2026-11-15.md).

## Stan projektu

`main`: stabilne C1–C3/G6.1/EN E2E; GSM PCM nadal zależy od Shizuku.

## Podział pracy

`feature/s8-no-shizuku`: bez merge. S22+: syntetyczny SDP/RTP 8k ↔ PCM 16k ↔ Realtime 24k, RX/TX/STOP PASS na localhost; stabilny APK przywrócony (SHA PASS).

Mac: klient SIPS/TLS OPTIONS i **opcjonalny** `registerOnce` (jawna zgoda na konkretną próbę). TLS CA + DNS, REGISTER → 401 → SHA-256 → 200; korelacja Via/Call-ID/CSeq, To/From i Contact. Testy na fałszywym TLS PASS, APK PASS. **Brak połączenia z operatorem i testu rzeczywistej rejestracji.**

## Następny sprint — S8: bez Shizuku

Sprawdzenie realnego SIP registrar po osobnej zgodzie; obsługa wielu Contact / URI SAN, SIP INVITE/SDP/SRTP i lokalne STT/LLM/TTS. `APP_PRIVATE_LITERT`: model nieodebrany.

## Otwarte

Realny REGISTER, INVITE, SRTP i PSTN niezweryfikowane. Obecny REGISTER: jeden Contact, jedna próba Digest, bez odnowień. GSM PCM blokuje Android/OEM. Test 4–6 tur wymaga zgody na numer/koszty/scenariusz. Bez PII/audio w Git.
