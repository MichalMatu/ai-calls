# AI Calls — MVP

**Cel:** S22+ Android 16: Call → RX → STT → LLM → TTS → TX → STOP.
**Rollback:** `mvp-s22-2026-11-15`.

## Stan projektu

`main`: stabilne C1–C3/G6.1; GSM PCM bez Shizuku blokuje Android/OEM.

## S8 — fizyczna kwalifikacja S22+ (2026-11-17)

Branch `feature/s8-no-shizuku`, odrębny APK `pl.michalmatu.aicallbridge.s8lab`, bez merge. **PASS:** RTP/SDP/STOP 6 faz 72 RX/72 TX; Android polskie STT i TTS; Gemma E2B 2,6 GB w sandboxie S8, natywna zgodność tekstu + trwały status VERIFIED. Check compatibility zgłasza brak READ_PHONE_STATE, wymaga ponownego kliknięcia; uprawnienie testowe przywrócone.

**Nowe PASS:** 4/4 natywne tury tekstowe Gemma E2B na S22+ (~9 s), bez zapisu wypowiedzi. Osobny DUMP-gated test całej lokalnej ścieżki **syntetyczne audio → Android STT → prawdziwa Gemma E2B → approval → lokalne TTS PCM** (~14 s), jeden turn, bez treści w raporcie. Mac unit/preflight/APK PASS. Stabilny APK SHA bez zmian, kopie S8 do rollbacku.

## Następny sprint — S8 bez Shizuku

Rozbudować lokalną ścieżkę głosową do 4–6 tur, dodać ręczny flow w UI; później SIP INVITE/SDP/SRTP.

## Otwarte

Brak prawdziwej rozmowy VoIP/GSM i SRTP. Realny Call tylko po zgodzie na numer/koszt. Bez PII/audio w Git.
