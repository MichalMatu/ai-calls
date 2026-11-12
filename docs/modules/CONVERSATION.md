# Rozmowa — STT, model, TTS

Wspólny `AndroidConversationRuntime` dla INCOMING/OUTGOING; domyślnie lokalna Gemma E2B, Android STT/TTS. Skills i narzędzia dostarczają propozycje, nie uprawnienia.

- Każdy wynik RX/STT/model/TTS/TX jest przypisany do session, turn i generation. STOP, Call-ended i barge-in odrzucają spóźnione TX, nieznany zapis PCM nie staje się sukcesem.
- `ConversationToolExecutor`: lokalne SAVE_NOTE i kontrolowane END_CALL. TASK/CALENDAR/MESSAGE to propozycje; nie zewnętrzne integracje.
- Prywatna historia tekstowa powstaje w autoryzowanej rozmowie; WAV tylko przy opt-in. Niepewny lub brakujący segment musi być oznaczony.
- Fizyczne echo/double-talk, local barge-in i jakość polskiej rozmowy S22 nadal wymagają próby. Nie zastępuj endpointingu/VAD bez własnego porównania. [SECURITY](../architecture/SECURITY.md).
