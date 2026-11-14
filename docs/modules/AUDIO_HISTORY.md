# Audio i historia — aktywny sprint I/O

Tor: Call RX PCM → Android STT / Parakeet PL / Gemma Audio Direct → Gemma → Android TTS PL / Piper → Call TX PCM. Porównuj osobno etapy i zimny/ciepły start; instrumentuj czasy oraz błędy, nie treść rozmów.

Bazowy S22: Android STT + Gemma + Android TTS, lokalne WAV RX/TX opt-in. Gemma Audio Direct wymaga stabilności, zgodności i redukcji opóźnień. Piper wymaga polskiego głosu offline. Echo/barge-in i takeover są PHYSICAL_OPEN. Jakość dialogu to osobny sprint.
