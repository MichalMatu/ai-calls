# AI Calls — MVP

**Cel:** bezpieczna sekretarka S22:
Call → PCM → STT → LLM → TTS → PCM → STOP/END_CALL.

## Stan 15.11.2026 — milestone

S22 off-call: **60/60** prób sześciu torów audio,
**18/18** syntetycznych tur dialogu i **5/5** testów STOP.
Gemma E2B: cold-first 15,74 s, po prewarm 8,12 s,
hot mediana 1,50 s, prewarm 5,96 s (dwa przebiegi).
Profil użytkownika bez zmian: Parakeet → Gemma E2B →
Piper Gosia. To stabilny **rdzeń syntetyczny**,
nie pełna kwalifikacja Telecom.
Dowody: [macierz](../benchmarks/audio/AUDIO_ROUTE_QUALITY.md),
[checkpoint](../benchmarks/audio/CHECKPOINT_HANDOFF_2026-11-15.md).

## Następny sprint

Prawdziwy Call 4–6 tur z powitaniem podczas prewarm,
end-of-speech → TX, barge-in i STOP; END_CALL po
„do widzenia” oraz odsłuch. Osobno zdiagnozować
REPAIRS=1 pierwszej odpowiedzi. Kandydaci: [lista](../benchmarks/MODEL_CANDIDATES.md).

## Otwarte

C1–C3 realne połączenie/hałas/pożegnanie;
G6.1 kampanie, boot/retry/takeover: PHYSICAL_OPEN.
Bez schedulerów, automatycznych telefonów i WAV w Git.
