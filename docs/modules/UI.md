# Interfejs i dostępność

UI pokazuje rzeczywisty stan: manual/AI, exact Call, postęp, STOP/TAKE OVER/resume, bezpieczeństwo grantu, lokalny chat, historię, laboratorium oraz ustawienia. Żaden status UI nie zastępuje potwierdzenia z Android Telecom lub usługodawcy.

## Reguły
- Czytelne statusy `IDLE`, `RINGING`, `ACTIVE`, `ENDED`, `MANUAL`, `AI`, błąd/unknown, bez mylącego `success` po samym TX.
- Historia chroni prywatne dane; pełny playback jest nadal celem, obecny odtwarza tylko zarejestrowane segmenty.
- Wersja polska/angielska, dostępność i struktura kontrolek. Screenshoty właściciela są dozwolone; nie używać `FLAG_SECURE` ani nie publikować automatycznie obrazów prywatnych ekranów.
- Agent AI: pięć kart bez powielonego tytułu, zwięzłe podsumowania bez wartości danych, osobne komunikaty błędów, PL/EN i opis karty ze stanem dla czytnika ekranu. Odczyt Keystore/Vault poza głównym wątkiem; tylko najnowszy wynik aktualizuje widok.
- Notatnik ma pierwszeństwo przed ustawieniami: zwijane rozmowy, wyszukiwanie, filtr, stronicowanie; szyfrowany odczyt poza UI. Usuwanie wymaga potwierdzenia i braku aktywnego AI Call; wynik usuwania jest obsługiwany mimo równoległego odświeżenia.
- Przełączniki narzędzi: opisy odróżniają zapis lokalny, propozycje i zakończenie własnego Call. Niezapisane zmiany są chronione przy Back i zmianie zakładki, a odrzucenie przywraca widoczne ustawienia.
- Nie dodawać dodatkowych kliknięć ani kolejnych zatwierdzeń bez zmiany materialnego zakresu grantu.

G7 to finalny odbiór UX po rzeczywistym G6, nie powód do zastępowania już testowanych ekranów. [Current](../PROJECT.md).
