# AI Calls — MVP

**Cel:** INCOMING/OUTGOING, kampanie na S22, wspólny STT/Gemma/TTS i STOP. Kreator, kolejka, alarm, retry, wyniki per odbiorca istnieją, lecz bez odbioru realnej kampanii.

## Otwarte
- **P0 G6.1 — kampanie:** integracja 3 odbiorców, alarm/BOOT, retry, STOP, UNKNOWN, exact SIM i PL/EN; start w tle S22 PHYSICAL_OPEN.
- **P0 — S22:** INCOMING/OUTGOING, dwukierunkowe audio, barge-in, takeover/resume i historia PHYSICAL_OPEN.
- **P0 — wizyta:** wynik/propozycja terminu; bez potwierdzonej rezerwacji.
- **P1 — wyniki:** porównanie ofert (obecnie osobne wyniki).
- **P1 — kampania:** opcjonalne zakończenie po pierwszym potwierdzonym sukcesie.

## Następny sprint
Integracja kolejki i regresje, potem autoryzowany test S22; bez powtarzania wcześniej zaliczonych testów. **MVP BLOCKED / PHYSICAL_OPEN** do potwierdzenia Call/audio. [SECURITY](architecture/SECURITY.md).
