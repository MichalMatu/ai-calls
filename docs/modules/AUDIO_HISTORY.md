# Audio, prywatne archiwum i odtwarzanie

`CallSessionEvidenceRecorder` zapisuje dostępne prywatne per-turn WAV RX/TX/TX-ACK oraz `timeline.jsonl`. `RecordedConversationHistory` odczytuje teksty, identyfikuje realnie zapisane pliki, bezpieczne kody `END`/`RX_INTERRUPTED`; `ArchivedConversationPlayer` pozwala na fragment lub sekwencję zapisanych fragmentów w kolejności `elapsed_ms`.

## Aktualny stan
- W G6 dodano jeden przycisk odtwarzania **dostępnych fragmentów** oraz podstawowe statusy zakończenia. Wybór chroni lokalne canonical path, telefon `IDLE`, `READ_PHONE_STATE`, lifecycle pause/back i prywatne pliki; nie eksportuje danych.
- **Nie twierdź**, że playlist to pełne nagranie. Cisza między segmentami, treść rozmowy człowieka po przejęciu, brakujące RX/STT lub odmowa zapisu mogą pozostawić dziury.

## Docelowy kontrakt
Jedna oś czasu RX/TX/STT/AI/manual, zapis wyłącznie za dozwoloną zgodą i uprawnieniami, czas rozpoczęcia, brakujące odcinki jawnie oznaczone, pełne playback/pause/seek, synchronizacja z pojedynczymi wypowiedziami, retencja i bezpieczne kasowanie tylko zakończonej sesji. Nie przepisywać recordera ani nie dodawać drugiej bazy; pracować z istniejącym prywatnym timeline.

**OPEN:** realne pokrycie audio całego autoryzowanego Call i przerwanie/manual na S22. [Status](../PROJECT.md), [policy](../architecture/SECURITY.md).
