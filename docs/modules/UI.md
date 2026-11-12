# Interfejs i dostępność

UI pokazuje rzeczywisty stan: manual/AI, exact Call, postęp, STOP/TAKE OVER/resume, bezpieczeństwo grantu, lokalny chat, historię, laboratorium oraz ustawienia. Żaden status UI nie zastępuje potwierdzenia z Android Telecom lub usługodawcy.

## Układ panelu
- Start pokazuje stan zadania wychodzącego; ten wskaźnik nie jest odczytem stanu wszystkich połączeń Androida.
- Jedna nazwa **Ustawienia** w pasku, menu i dokumentacji. Cztery zakładki nie prowadzą do diagnostyki ani nie wykonują działań telefonicznych.
- `UiDesign.content` ustala marginesy stron: 20 dp poziomo, 16 dp u góry, 24 dp na dole. `navigationRow` jest jednym klikalnym wierszem z etykietą i strzałką; drugi wiersz służy bieżącemu stanowi, bez ogólnych opisów menu.
- Agent i Ustawienia używają tych samych wierszy. Nazwa wejścia odpowiada podstronie: „Kontekst rozmów”, „Notatki i narzędzia”, „Odbieranie połączeń”. Start prowadzi przez „Nowe zadanie” do wspólnego kreatora pojedynczego telefonu i kampanii; nie obiecuje rezerwacji.
- Jedna paleta, trzy poziomy tekstu, kontrast i kontrolki co najmniej 48 dp. Przyciski zachowują rozróżnienie głównej akcji, pomocniczej i STOP; nieaktywne mają osobny stan. Treść rośnie przy większej czcionce.
- Odstępy kart należą do ich układu. Chrome uzupełnia wyłącznie brakującą przerwę między sąsiednimi widocznymi przyciskami, zachowuje większy jawny odstęp i nie dodaje marginesu za ostatnią akcją.
- Opis pozostaje przy decyzji użytkownika: skutki grantu, zakres danych, WAV, zapis szkicu lub wynik. Nie dodawaj instrukcji obsługi nawigacji ani kolejnych stopek.

## Zadania telefoniczne
Kreator używa istniejących szablonów i wersji osobistych: cel → lista numerów z kodem kraju → SIM i okno czasowe → jedno zatwierdzenie zakresu. Szkic jest szyfrowany; obrót i powrót nie uruchamiają zadania. Powtarzające się numery są scalane po normalizacji, błędne wiersze blokują zatwierdzenie. Czas dotyczy początku kolejki; termin wizyty pozostaje w scenariuszu. Godziny niejednoznaczne przy zmianie czasu nie są zgadywane.

Zadanie pokazuje odbiorców, próby, kolejny termin, problem i wynik. Cena/dostępność/pozostałe odpowiedzi są cytatami z STT rozmówcy, z zachowaniem „od”, zakresu, waluty i negacji; brak danych nie jest zerem. Nie ma automatycznej oceny najtańszej oferty ani potwierdzenia rezerwacji. Anulowanie dotyczy zadania; globalny STOP anuluje przyszłe zadania i wyłącza sekretarkę. TAKE OVER wstrzymuje kolejkę. Wznowienie pomija niepewną próbę, której powtórzenie wymaga jawnego wyboru odbiorcy.

## Laboratorium i diagnostyka
- Konfiguracja i czat zajmują osobne widoki; runner pozostaje jeden. STOP i wyjście kończą sesję. Wspólne dymki, etap i czas oczekiwania są widoczne; metryki i wyniki są w modalu. Odtworzenie ekranu nie uruchamia testu.
- Wynik Shizuku jest modalny, z zakresem off-call i surowymi szczegółami na żądanie. Możliwości urządzenia dotyczą inicjalizacji w procesie aplikacji; wejścia i wyjścia są osobno. Błąd lub brak danych nie potwierdza audio.

## Reguły
- Czytelne statusy `IDLE`, `RINGING`, `ACTIVE`, `ENDED`, `MANUAL`, `AI`, błąd/unknown, bez mylącego `success` po samym TX.
- Historia chroni prywatne dane; pełny playback jest nadal celem, obecny odtwarza tylko zarejestrowane segmenty.
- Wersja polska/angielska, dostępność i struktura kontrolek. Screenshoty właściciela są dozwolone; nie używać `FLAG_SECURE` ani nie publikować automatycznie obrazów prywatnych ekranów.
- Agent AI: pięć wierszy bez powielonego tytułu, zwięzłe podsumowania bez wartości danych, osobne komunikaty błędów, PL/EN i opis karty ze stanem dla czytnika ekranu. Odczyt Keystore/Vault poza głównym wątkiem; tylko najnowszy wynik aktualizuje widok.
- Ustawienia: podstrony mają Back; szkic sekretarki przeżywa obrót, reset odświeża formularz i zapis. Dostęp SIM wymaga zgody Androida, a brak poprzedniej SIM nigdy nie wybiera innej automatycznie. Błędna polityka blokuje zapis; opisy MANUAL dotyczą tylko przyszłych rozmów.
- Notatnik ma pierwszeństwo przed ustawieniami: zwijane rozmowy, wyszukiwanie, filtr, stronicowanie; szyfrowany odczyt poza UI. Usuwanie wymaga potwierdzenia i braku aktywnego AI Call; wynik usuwania jest obsługiwany mimo równoległego odświeżenia.
- Przełączniki narzędzi: rozróżniają lokalny zapis, propozycje i zakończenie własnego Call. Back i zmiana zakładki chronią niezapisane zmiany; po obrocie zachowywane są tylko identyfikatory przełączników, nie treść notatek ani wyszukiwania.
- Nie dodawać dodatkowych kliknięć ani kolejnych zatwierdzeń bez zmiany materialnego zakresu grantu.
- Model i głos: trzy karty, modale wyboru, osobne pliki. Wybór i gotowość są oddzielne; usuwanie ponownie sprawdza IDLE/wybór i chroni bazę. Provider dotyczy tylko TTS; systemowy wybór pokazuje nazwę aplikacji. Wynik sprawdzania pozostaje w karcie, przycisk jest blokowany zamiast chowany.
- Rola aplikacji Telefon ma status i jawny przycisk w Ustawieniach; Start pokazuje brak roli. Otwarcie ekranów nie żąda roli. Żądanie używa Activity Result, aby Android znał caller; wynik odświeża status. Systemowy wybór i powrót nie uruchamiają Call ani grantu.

G7 to finalny odbiór UX po rzeczywistym G6, nie powód do zastępowania już testowanych ekranów. [Current](../PROJECT.md).
