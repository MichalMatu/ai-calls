# AI Calls — MVP

**Cel:** S22+ Android 16: Call → RX → STT → LLM → TTS → TX → STOP.
**Rollback:** `mvp-s22-2026-11-15`; [checkpoint](../benchmarks/audio/FINAL_QUALITY_CHECKPOINT_2026-11-15.md).

## Stan projektu

`main`: C1–C3/G6.1/EN E2E; GSM PCM nadal zależy od Shizuku.

## Podział pracy

`feature/s8-no-shizuku`: bez merge, backend domyślny bez zmian. S22+: syntetyczny SDP ↔ RTP 8k ↔ PCM 16k ↔ Realtime 24k, RX/TX/STOP PASS; tylko localhost, bez rozmowy. Stabilny APK przywrócony (SHA PASS).

Mac: SIPS/TLS OPTIONS bez dial/REGISTER PASS. Offline REGISTER + SHA-256 Digest RFC 8760: 401 korelowane przez Via/Call-ID/CSeq, CSeq 1→2, bez MD5 i wielokrotnych retry. Testy/preflight/APK PASS; **bez konta, sieci i rzeczywistej rejestracji SIP**.

## Następny sprint — S8: bez Shizuku

Obsługa sesji TLS i bezpiecznego REGISTER z kontem, następnie SIP INVITE/SDP/SRTP oraz lokalne STT/LLM/TTS. `APP_PRIVATE_LITERT`: odbiór modelu otwarty.

## Otwarte

Brak realnego REGISTER, INVITE, SRTP, operatora i PSTN. Obsługa pełnego RFC 5922 poza DNS SAN nadal otwarta; GSM PCM blokuje Android/OEM. Test 4–6 tur tylko po zgodzie na numer/koszty/scenariusz. Bez PII/audio w Git.
