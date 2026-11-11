# Interfejs i dostępność

UI pokazuje rzeczywisty stan: manual/AI, exact Call, postęp, STOP/TAKE OVER/resume, bezpieczeństwo grantu, lokalny chat, historię, laboratorium oraz ustawienia. Żaden status UI nie zastępuje potwierdzenia z Android Telecom lub usługodawcy.

## Reguły
- Czytelne statusy `IDLE`, `RINGING`, `ACTIVE`, `ENDED`, `MANUAL`, `AI`, błąd/unknown, bez mylącego `success` po samym TX.
- Historia chroni prywatne dane; pełny playback jest nadal celem, obecny odtwarza tylko zarejestrowane segmenty.
- Wersja polska/angielska, dostępność i struktura kontrolek. Screenshoty właściciela są dozwolone; nie używać `FLAG_SECURE` ani nie publikować automatycznie obrazów prywatnych ekranów.
- Notatnik agenta ma pierwszeństwo przed ustawieniami: zwijane rozmowy, lokalny podgląd, wyszukiwanie, filtr i stronicowanie widoku; odczyt szyfrowanych rekordów poza wątkiem UI. Usuwanie wymaga potwierdzenia i braku aktywnej rozmowy AI.
- Nie dodawać dodatkowych kliknięć ani kolejnych zatwierdzeń bez zmiany materialnego zakresu grantu.

G7 to finalny odbiór UX po rzeczywistym G6, nie powód do zastępowania już testowanych ekranów. [Current](../PROJECT.md).
