# AI Calls — MVP

**Cel:** S22+ Android 16: Call → RX → STT → LLM → TTS → TX → STOP.
**Rollback:** `mvp-s22-2026-11-15`.

## Stan projektu

`main`: stabilne C1–C3/G6.1; GSM PCM bez Shizuku blokuje Android/OEM.

## S8 — fizyczna kwalifikacja S22+ (2026-11-17)

Branch `feature/s8-no-shizuku`, odrębny APK `pl.michalmatu.aicallbridge.s8lab`, bez merge. **PASS:** RTP/SDP/STOP 6 faz 72 RX/72 TX; Android polskie STT i TTS; Gemma E2B 2,6 GB w sandboxie S8, natywna zgodność tekstu + trwały status VERIFIED. Check compatibility zgłasza brak READ_PHONE_STATE, wymaga ponownego kliknięcia; uprawnienie testowe przywrócone.

**Nowe PASS:** 4/4 natywne tury tekstowe Gemma E2B na S22+ (~9 s), bez zapisu wypowiedzi. Osobny DUMP-gated test całej lokalnej ścieżki **syntetyczne audio → Android STT → prawdziwa Gemma E2B → approval → lokalne TTS PCM** (~14 s), jeden turn, bez treści w raporcie. Mac unit/preflight/APK PASS. Stabilny APK SHA bez zmian, kopie S8 do rollbacku.

## Sprint S8 — integracja lokalna i SIP/SRTP

Rozbudowano tor syntetyczny RTP → systemowe STT → Gemma E2B → approval → TTS → RTP do 2–6 tur oraz ekran ręcznej próby; STOP w fazach RX/model/TX i brak starych ramek RTP są objęte testami celowanymi Mac (PASS). **Fizyczny E2E 2/2 na S22+ — PASS:** rzeczywisty lokalny STT → Gemma E2B → approval → TTS na syntetycznym RTP (393 RX / 205 TX, 26,1 s łącznie; najdłuższy czas odpowiedzi 12,8 s). Po naprawie raportu i przeniesieniu otwarcia UDP poza wątek UI stan mediów ACTIVE. Rollback po poprzednich nieudanych próbach oraz stabilny APK zweryfikowane SHA PASS. **Fizyczne 4/6 tur również PASS**: 4/4 (46,9 s, 790 RX/404 TX, najdłuższa odpowiedź 13,7 s) i 6/6 (61,5 s, 1184 RX/603 TX, najdłuższa odpowiedź 12,4 s). **Fizyczne STOP PASS**: nasłuchiwanie 1 ms, przetwarzanie 2 ms, nadawanie 5 ms (czas lokalnej operacji STOP, nie pełnego wygaszenia natywnej inferencji). **Fizyczny test 5% strat PASS**: 4/4 tury, 39/790 syntetycznych pakietów wejściowych utraconych i dopełnionych ciszą. Kod i testy Mac PASS.

SIP: Mac zbudował natywny PJSIP/PJSUA2 2.17 + OpenSSL 3.5.9 dla Android ARM64 (16 KB ELF) oraz osobny JNI do odczytu aktywnego SRTP. Kompilacje prototypów Java: przychodzące INVITE/RING/accept/reject/BYE, port PCM16k i ścisła bramka SRTP — **PASS**. Testy kontrolera SIP → STT/Gemma/TTS, ochrony PCM i UI — **PASS**. Natywny stos pozostaje w izolowanym prototypie, nie w APK; fizyczne połączenie SIP/SRTP, wiarygodna weryfikacja TLS na połączeniu i licencja do dystrybucji nadal **OPEN**. Szczegóły, dowody i bramki: [S8 secure VoIP handoff](architecture/S8_SECURE_VOIP_HANDOFF.md).

**Nowe S22+ JNI OFFLINE PASS (2026-11-17):** po ponownym podłączeniu telefonu instalacja odizolowanego debug APK z PJSUA2/JNI, test ładowania obu bibliotek, negatywne testy tożsamości TLS/SRTP i cykl `Endpoint.libCreate/libDestroy` — PASS. Przywrócenie poprzedniego APK S8 i zgodność stabilnego APK (SHA-256) — PASS; bez połączeń, konta SIP lub rejestracji. Domyślny debug APK nie zawiera PJSIP (osobny test Mac PASS). Dowód: `aicalls-s8-s22-reconnected-jni-offline-rollback-20261117-v3`.

## Otwarte

Brak realnego połączenia VoIP/GSM na telefonie; nie testowano negocjacji SIP/SDP/SRTP z dostawcą ani podpięcia JNI do eksperymentalnego APK. Dalsze testy natywne wymagają osobnego odbioru bezpieczeństwa transportu i decyzji licencyjnej; połączenia na prawdziwy numer wyłącznie po zgodzie na numer/koszt. Bez PII/audio w Git.
