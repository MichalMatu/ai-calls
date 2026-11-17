# S22 — zasady testów fizycznych

Użytkownik potwierdza działanie aplikacji z GSM. **Najpierw testuj istniejący tor Samsung/Shizuku**, nie wymieniaj go na VoIP. Sprawdź model S22+, SIM, rolę dialera, grant, RX/TX i IDLE; odróżniaj testy syntetyczne od rozmów rzeczywistych.

Rzeczywisty Call i instalacja wyłącznie za zgodą, z weryfikacją APK oraz rollbackiem. Bez AndroidTest i `adb uninstall` na prywatnym S22, bez PII/WAV w receipts. Aktualne zadania: [PROJECT](../PROJECT.md).
