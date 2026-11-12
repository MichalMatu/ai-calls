# AI Calls — kontrakt UI

Cztery zakładki: Start / Rozmowy / Agent AI / Ustawienia. Start: „Nowe zadanie”, lista zadań, STOP; Agent: scenariusze, Skills, sejf, notatki; ustawienia: model/głos, role, uprawnienia. Bez podwójnych przycisków i zbędnych potwierdzeń.

Kreator: scenariusz/cel → wiele numerów (walidacja, usunięcie duplikatów) → SIM, czas i retry → jedno zatwierdzenie. Szkic prywatny/szyfrowany; otwarcie ekranu, obrót i Back **nigdy** nie dzwonią. Wyniki oddzielne per odbiorca, cytaty STT zachowują „od”, walutę i negację; bez wymyślonej ceny/rezerwacji. STOP globalny anuluje przyszłe próby, takeover pauzuje, UNKNOWN wymaga jawnego retry.

Stan UI odróżnia IDLE/RINGING/ACTIVE/MANUAL/AI/ENDED oraz UNKNOWN od sukcesu. Obsłuż PL/EN, czytnik, skalowanie tekstu i minimum 48 dp dla akcji. Prawdziwy Telecom i potwierdzony skutek zewnętrzny mają pierwszeństwo przed opisem AI. Nie włączaj `FLAG_SECURE`. [PROJECT](../PROJECT.md).
