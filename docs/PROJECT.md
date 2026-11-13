# AI Calls — MVP

**Cel:** INCOMING/OUTGOING, kampanie na S22, wspólny STT/Gemma/TTS i STOP. Kreator, kolejka, alarm, retry oraz porównanie źródłowych odpowiedzi działają w testach. Pojedyncze zadanie uruchomione z alarmu w tle S22 zaliczone.

## Otwarte
- **P0 G6.1 — kampanie:** integracja 3 odbiorców, alarm/BOOT, retry, STOP, UNKNOWN, exact SIM i PL/EN w testach; pełny fizyczny odbiór wieloodbiorcowy PHYSICAL_OPEN.
- **P0 — S22:** supervised OUTGOING oraz pojedyncze zadanie z kreatora → alarm przy zgaszonym ekranie → exact SIM/Call → PCM RX+TX/STT/Gemma/TTS → STOP → IDLE **PASS (13.11.2026)**. Nadal otwarte INCOMING, barge-in/echo, takeover/resume i playback historii. Próba sekretarki bez zaobserwowanego połączenia nie stanowi odbioru.
- **P0 — wizyta:** wynik/propozycja terminu; bez potwierdzonej rezerwacji.
- **P1 — kampania:** opcjonalne zakończenie po pierwszym potwierdzonym sukcesie.

## Następny sprint
Fizyczny INCOMING na S22, następnie barge-in i takeover/resume; bez powtarzania zaliczonego pojedynczego zadania wychodzącego. **MVP BLOCKED / PHYSICAL_OPEN** dla pozostałych ścieżek. [SECURITY](architecture/SECURITY.md).
