# Rozmowa i Test Lab

GSM: PCM → STT → Gemma/Skills → walidacja → TTS/TX. STOP/takeover odcinają TX. `note` jest niezależne od `tool`: po zatwierdzonym TTS aplikacja zapisuje istotne ustalenia przed END_CALL, aktualizując **jedną zaszyfrowaną notatkę** na rozmowę. Notatki domyślnie włączone; wyłączenie respektowane. Sekrety filtrowane; cały transkrypt nie jest notatką. TASK/CALENDAR/MESSAGE to propozycje.

Lab offline: `scripts/bin/aicalls` — `lab-start`, `lab-send`, `lab-status`, `lab-stop`, `lab-save`; wejście przez stdin, `--instructions-stdin` i `lab-regression`.

Ustawienia → Model i głos: PL/EN, E2B/E4B; E4B wymaga testu zgodności. Własne instrukcje zachowują język. Po MVP: dokładność kodów, naturalność wypowiedzi, Gemma Audio/Qwen oraz SIP/Realtime — osobne eksperymenty.
