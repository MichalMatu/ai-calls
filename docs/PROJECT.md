# AI Calls — MVP

**Cel:** INCOMING/OUTGOING, kampanie na S22, wspólny STT/Gemma/TTS i STOP. Kreator, kolejka, alarm, retry oraz porównanie źródłowych odpowiedzi per odbiorca działają w testach; bez fizycznego odbioru kampanii.

## Otwarte
- **P0 G6.1 — kampanie:** integracja 3 odbiorców, alarm/BOOT, retry, STOP, UNKNOWN, exact SIM i PL/EN; start w tle S22 PHYSICAL_OPEN.
- **P0 — S22:** supervised OUTGOING do Orange: Call/PCM RX+TX, STT/Gemma/TTS, 2 tury modelu i STOP na realnym S22 **PASS (13.11.2026)**. Nadal otwarte INCOMING, barge-in, takeover/resume, historia i autonomiczna kampania.
- **P0 — wizyta:** wynik/propozycja terminu; bez potwierdzonej rezerwacji.
- **P1 — kampania:** opcjonalne zakończenie po pierwszym potwierdzonym sukcesie.

## Następny sprint
Zweryfikować jednokrotny autonomiczny dial z kolejki na S22 (grant/SIM/STOP/UNKNOWN), potem INCOMING i barge-in; bez powtarzania zaliczonego supervised audio. **MVP BLOCKED / PHYSICAL_OPEN** dla tych ścieżek. [SECURITY](architecture/SECURITY.md).
