# AI Calls — MVP

**Cel:** S22+ Android 16: Call → RX → STT → LLM → TTS → TX → STOP.
**Rollback:** `mvp-s22-2026-11-15`.

## Stan projektu

`main`: stabilne C1–C3/G6.1; GSM PCM bez Shizuku blokuje Android/OEM.

## S8 — fizyczna kwalifikacja S22+ (2026-11-17)

Branch `feature/s8-no-shizuku`, odrębny APK `pl.michalmatu.aicallbridge.s8lab`, bez merge. **PASS:** RTP/SDP/STOP 6 faz 72 RX/72 TX; Android polskie STT i TTS; Gemma E2B 2,6 GB w sandboxie S8, natywna zgodność tekstu + trwały status VERIFIED. Check compatibility zgłasza brak READ_PHONE_STATE, wymaga ponownego kliknięcia; uprawnienie testowe przywrócone.

**Nowe PASS:** 4/4 natywne tury tekstowe Gemma E2B na S22+ (~9 s), bez zapisu wypowiedzi. Osobny DUMP-gated test całej lokalnej ścieżki **syntetyczne audio → Android STT → prawdziwa Gemma E2B → approval → lokalne TTS PCM** (~14 s), jeden turn, bez treści w raporcie. Mac unit/preflight/APK PASS. Stabilny APK SHA bez zmian, kopie S8 do rollbacku.

## Sprint S8 — integracja lokalna i SIP/SRTP

Rozbudowano tor syntetyczny RTP → systemowe STT → Gemma E2B → approval → TTS → RTP do 2–6 tur oraz ekran ręcznej próby; STOP w fazach RX/model/TX i brak starych ramek RTP są objęte testami celowanymi Mac (PASS). **Fizyczny E2E 2/2 na S22+ — PASS:** rzeczywisty lokalny STT → Gemma E2B → approval → TTS na syntetycznym RTP (393 RX / 205 TX, 26,1 s łącznie; najdłuższy czas odpowiedzi 12,8 s). Po naprawie raportu i przeniesieniu otwarcia UDP poza wątek UI stan mediów ACTIVE. Rollback po poprzednich nieudanych próbach oraz stabilny APK zweryfikowane SHA PASS. **Fizyczne 4/6 tur również PASS**: 4/4 (46,9 s, 790 RX/404 TX, najdłuższa odpowiedź 13,7 s) i 6/6 (61,5 s, 1184 RX/603 TX, najdłuższa odpowiedź 12,4 s). Testy STOP i 5% strat pakietów na urządzeniu pozostają otwarte; kod i celowane testy Mac PASS.

Protokół SIP ma jedynie ręczny REGISTER i testowaną bramkę stanów wymagającą TLS/SRTP; brak natywnego SIP/SDP/SRTP, a Mac nie ma NDK, SWIG ani PJSIP. Szczegóły, dowody i bramki: [S8 secure VoIP handoff](architecture/S8_SECURE_VOIP_HANDOFF.md).

## Otwarte

Brak prawdziwej rozmowy VoIP/GSM i SRTP. Realny Call tylko po zgodzie na numer/koszt. Bez PII/audio w Git.
