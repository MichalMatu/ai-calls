# Telefonia — połączenia i kampanie

**INCOMING:** polityka AI/MANUAL + exact Call/SIM → wspólny runtime; takeover nie rozłącza człowieka, resume wymaga tej samej sesji. STOP i zmiana zgody odcinają spóźnione audio.

**OUTGOING:** cel/odbiorcy/okno/SIM zatwierdzone w kreatorze → trwały `PhoneTask` → `AlarmManager` → pojedynczy exact grant i app-owned dial. Próby rezerwuje się trwale przed dial; każdy odbiorca ma własny stan i cytaty STT. Retry wyłącznie potwierdzone BUSY/NO_ANSWER przed końcem okna; UNKNOWN po restarcie nie jest automatycznie ponawiany. TIME/BOOT odtwarza alarm bez nadawania zgody. Brak roli dialera, SIM, permission lub możliwości startu w tle: pauza/komunikat, nie pozorny sukces.

**IVR/DTMF:** istniejący `OutgoingIvrDtmfGate` wiąże cyfrę, sesję, grant i exact Call; adapter `play/stop` bez pełnego podłączenia AI. UNKNOWN po możliwym DTMF nie podlega replay. Nie udawaj potwierdzenia sieciowego. [SECURITY](../architecture/SECURITY.md).
