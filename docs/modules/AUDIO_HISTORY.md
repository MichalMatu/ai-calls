# Prywatne audio i historia

`CallSessionEvidenceRecorder` oraz `RecordedConversationHistory` zapisują/odczytują lokalne, dostępne fragmenty RX/TX/STT i oś czasu; `ArchivedConversationPlayer` odtwarza **zapisane** fragmenty, nie gwarantuje pełnej ciągłej rozmowy.

WAV wymaga opt-in. Sprawdzaj bezpieczne ścieżki, sesję zakończoną, uprawnienia i stan IDLE; brakujące odcinki oznaczaj jawnie. Pełny playback, luka po takeover i pokrycie S22 pozostają otwarte. [SECURITY](../architecture/SECURITY.md).
