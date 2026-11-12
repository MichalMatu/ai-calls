# S22 — fizyczne testy

Docelowy Samsung Galaxy S22+ `SM-S906B`, Android 16; zweryfikuj urządzenie na Macu, rolę dialera, Shizuku, SIM, permission i Telecom IDLE. IP ADB nie dowodzi tożsamości.

Transport `VOICE_DOWNLINK` RX / `CALL_ASSISTANT` TX, PCM, watchdog i selective cleanup są zamrożone; zmiana wymaga fizycznej regresji na S22. Testy instrumentacyjne tylko na emulatorze; **bez** `adb uninstall` na prywatnym telefonie. Instalacja wyłącznie po autoryzacji i weryfikacji IDLE (`adb install -r`, zachowanie danych). Prawdziwy Call wymaga osobnej zgody na cel, numer i SIM. Żadne prywatne WAV, numery, transkrypty w wynikach hosta. [SECURITY](../architecture/SECURITY.md).
