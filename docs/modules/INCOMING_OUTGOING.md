# Telefonia

INCOMING: AI/MANUAL z exact Call/SIM; takeover nie kończy rozmowy.

OUTGOING: zatwierdzony `PhoneTask` → trwała kolejka/AlarmManager → exact dial/grant. Każdy odbiorca ma osobny wynik. Retry tylko potwierdzone BUSY/NO_ANSWER w oknie; UNKNOWN po restarcie bez redial. BOOT odtwarza alarm, nie zgodę.

IVR/DTMF: grant-bound gate bez pełnego podłączenia AI. Dispatch tonu ≠ potwierdzenie.
