# Interfejs i dostępność

UI pokazuje rzeczywisty stan: manual/AI, exact Call, postęp, STOP/TAKE OVER/resume, bezpieczeństwo grantu, lokalny chat, historię, laboratorium oraz ustawienia. Żaden status UI nie zastępuje potwierdzenia z Android Telecom lub usługodawcy.

## Reguły
- Czytelne statusy `IDLE`, `RINGING`, `ACTIVE`, `ENDED`, `MANUAL`, `AI`, błąd/unknown, bez mylącego `success` po samym TX.
- Historia chroni prywatne dane; pełny playback jest nadal celem, obecny odtwarza tylko zarejestrowane segmenty.
- Wersja polska/angielska, dostępność i struktura kontrolek. Screenshoty właściciela są dozwolone; nie używać `FLAG_SECURE` ani nie publikować automatycznie obrazów prywatnych ekranów.
- Agent AI: pięć kart bez powielonego tytułu, zwięzłe podsumowania bez wartości danych, osobne komunikaty błędów, PL/EN i opis karty ze stanem dla czytnika ekranu. Odczyt Keystore/Vault poza głównym wątkiem; tylko najnowszy wynik aktualizuje widok.
- Opcje: podstrony mają Back; szkic sekretarki przeżywa obrót, reset odświeża formularz i zapis. Dostęp SIM wymaga zgody Androida, a brak poprzedniej SIM nigdy nie wybiera innej automatycznie. Błędna polityka blokuje zapis; opisy MANUAL dotyczą tylko przyszłych rozmów.
- Notatnik ma pierwszeństwo przed ustawieniami: zwijane rozmowy, wyszukiwanie, filtr, stronicowanie; szyfrowany odczyt poza UI. Usuwanie wymaga potwierdzenia i braku aktywnego AI Call; wynik usuwania jest obsługiwany mimo równoległego odświeżenia.
- Przełączniki narzędzi: rozróżniają lokalny zapis, propozycje i zakończenie własnego Call. Back i zmiana zakładki chronią niezapisane zmiany; po obrocie zachowywane są tylko identyfikatory przełączników, nie treść notatek ani wyszukiwania.
- Nie dodawać dodatkowych kliknięć ani kolejnych zatwierdzeń bez zmiany materialnego zakresu grantu.
- Model i głos: trzy karty bieżących wyborów; model, rozpoznawanie i głos wybiera się w modalach z natychmiastowym zapisem. Parakeet jest w grupie Dodatkowe tego samego wyboru STT. Silnik TTS wybiera się przy głosach, pliki mają osobny widok, postęp/błędy należą do odpowiedniej karty. Alias dawnego ekranu silników zachowany. Wynik sprawdzania pozostaje w karcie, przycisk jest blokowany zamiast chowany. Dostawca głosu dotyczy tylko TTS; wariant systemowy pokazuje rozwiązaną nazwę aplikacji. Gotowość i wybór pozostają osobne; usuwanie jednego pliku ponownie sprawdza IDLE/wybór i chroni bazę.
- Rola aplikacji Telefon ma status i jawny przycisk w Ustawieniach; Start pokazuje brak roli. Otwarcie ekranów nie żąda roli. Żądanie używa Activity Result, aby Android znał caller; wynik odświeża status. Systemowy wybór i powrót nie uruchamiają Call ani grantu.

G7 to finalny odbiór UX po rzeczywistym G6, nie powód do zastępowania już testowanych ekranów. [Current](../PROJECT.md).
