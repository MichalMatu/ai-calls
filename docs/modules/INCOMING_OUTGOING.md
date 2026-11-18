# Telefonia

**INCOMING GSM:** AUTO/MANUAL, exact Call/SIM; takeover zatrzymuje AI bez rozłączania, resume wymaga nowego uprawnienia. `END_CALL` tylko po TTS.

**OUTGOING:** odrębny `PhoneTask`, grant/okno/SIM; retry tylko BUSY/NO_ANSWER, UNKNOWN bez redial po restarcie. Nie należy do odebranego incoming GSM MVP. IVR/DTMF: wysłany ton ≠ potwierdzenie.

[Zakres i bramki](../PROJECT.md).
