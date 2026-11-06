# Odbieranie AI, manual i przejęcie

## Aktualizacja: end_call i ostrzeżenie przy bezczynności (2026-11-06)

Dwa rzeczywiste połączenia A8→S22 potwierdziły skuteczne end_call (REQUESTED, VALIDATED, DISPATCHED, CONFIRMED) po odtworzeniu pożegnania oraz kompletny prywatny recorder. Po jawnej decyzji właściciela przychodząca sekretarka otrzymała dodatkową politykę 30 s ciszy w aktywnym RX, następnie komunikat „Czy chcą Państwo kontynuować rozmowę? Jeśli nie usłyszę odpowiedzi, zakończę połączenie.”. Brak głosu przez dalsze 15 s rzeczywistego nasłuchu uruchamia zatwierdzone pożegnanie TTS i ten sam end_call po pełnym TX. Nie liczy czasu generacji modelu ani odtwarzania, reaguje na głos, zachowuje upoważnienie/STOP/przejęcie, nie dotyka zamrożonego toru PCM/Samsung. Testy host i S22 native TTS PASS; rzeczywisty timeout na połączeniu QA_PENDING. [Checkpoint](checkpoints/2026-11-06-incoming-idle30.md).

## Zakres wdrożenia

Nowy moduł obsługuje przychodzący Call otrzymany przez istniejący AndroidOwnedInCallService. Użytkownik wybiera manual albo AI dla konkretnego konta telefonicznego/SIM. Tryb domyślny jest manualny. Zapisanie trybu AI przyjmuje odwoływalną politykę sekretarki dla wybranego konta; nie wymaga kolejnego potwierdzenia każdego telefonu ani whitelisty numerów.

Pierwszy scenariusz AI przedstawia się jako wirtualna asystentka i przyjmuje wiadomość w dialogu bez skryptu, z modelem wybranym we wspólnych ustawieniach. Nie ma jeszcze narzędzi kalendarza, wysyłania powiadomień ani wykonywania zobowiązań. Instrukcja nie deklaruje tych działań. Dane sejfu nie są przekazywane do modelu. Użytkownik nadal nadaje rzeczywiste uprawnienia Androida i wybiera dialer; dotychczasowa integracja Shizuku pozostaje wymagana w laboratorium.

Kod jest zaimplementowany; pierwszy [kontrolowany dialog A8→S22](checkpoints/2026-11-06-controlled-incoming.md) ma rzeczywisty zapis INCOMING i potwierdzoną przez rozmówcę słyszalność. Przywrócenie toru użytkownika, powrót AI i STOP na S22 nadal wymagają osobnej weryfikacji. [Host i native receipts](checkpoints/2026-11-05-incoming-assistant.md). Nie utożsamiać jednego dialogu z akceptacją wszystkich trybów i profili.

## Moduły

| Warstwa | Implementacja |
|---|---|
| Niezależny rdzeń JVM | `:conversation-core`, IncomingAssistantCoordinator i IncomingAssistantPolicy; bez Androida, CLIR, silników modeli i mediów |
| Polityka | IncomingAssistantPolicyStore: tryb, rzeczywiste konto, identyfikator przyjęcia i opcja zapisu; osobno od promptów i wychodzącego zadania CLIR |
| Adapter Androida | AndroidIncomingAssistant: rzeczywisty Call, stan, gotowość, snapshot, przekazanie do wspólnego runtime |
| Scenariusz | IncomingSecretaryScenario: rola odbierającej asystentki, powitanie i wspólna aplikacyjna polityka wypowiedzi, bez skutków zewnętrznych |
| Wspólny silnik | AndroidConversationRuntime, istniejące STT/model/TTS, lokalny czat i prywatny recorder |
| UI | IncomingAssistantSettingsActivity oraz istniejący AndroidCallControlsActivity |

Przyjęcie polityki odbierania nie nadaje modelowi prawa wykonywania skutków ani ujawniania danych. Przyszłe narzędzia i zadania nadal korzystają ze wspólnych polityk danych/efektów i jednego modelu commitment, opisanego w [planie produktu](PRODUCT_ASSEMBLY_PLAN.md).

## Ustawienia i działanie

Ekran główny → „Uprawnienia i konfiguracja” → „Odbieranie: AI / ręcznie”. Wybrać tryb, konto telefoniczne i zapis dostępnych fragmentów audio/transkryptu. Silniki pozostają w obecnych ustawieniach STT/model/TTS, a skills/notatki w profilu przychodzącym. Samo otwarcie ekranu lub edycja profilu nie włącza odbierania.

Gotowość sprawdzana jest przed answer: rola dialera, rzeczywiste uprawnienia i Shizuku, dostępność wybranego STT/modelu oraz zgodność konta. Konfiguracja i instrukcje są zamrażane przed answer. Niepoprawna konfiguracja zostawia telefon do obsługi manualnej; nie podmienia modelu ani głosu. Rzeczywista gotowość TTS ujawnia się także podczas jego inicjalizacji/syntezy, a błąd jest raportowany bez rozłączania Call.

AI odpowiada tylko raz na konkretny RINGING Call. Jeśli Telecom publikuje konto z opóźnieniem, wiązanie czeka na prawdziwe szczegóły. Audio uruchamia się po rzeczywistym ACTIVE i MODE_IN_CALL. Samo włączenie AI nie przejmuje wcześniej aktywnej rozmowy. Inny aktywny Call, inna karta, utrata uprawnień/źródła albo nieobsługiwany tor VoWiFi nie nadają prawa użycia innej sesji.

Stan jest widoczny w panelu rozmowy i przez istniejący USB debug status (`incoming_mode`, `incoming_status`), bez numerów i prywatnego kontekstu. Nie wprowadzono nowej kolejki agenta lub osobnego debug bridge.

## Naturalne zakończenie rozmowy — jeszcze do wdrożenia

Obecne takeOutcome sekretarki nie zgłasza zakończenia, a adapter nie rozłącza Call po pożegnaniu. Rozmówca może zakończyć bieżący dialog telefonem; sam prompt lub powiedzenie „do widzenia” nie implementuje disconnect. F-091 OPEN opisuje brakujący kontrakt. Docelowo model proponuje zakończenie po przyjęciu i potwierdzeniu wiadomości, aplikacja sprawdza własny Call i aktualną generację, odtwarza zatwierdzone pożegnanie do końca, a następnie selektywnie rozłącza tę rozmowę. Nie jest to obecnie przetestowana funkcja.

## Manual, przejęcie, ponowne uruchomienie AI i STOP

- „Odbierz ręcznie” tłumi automatyczny start dla tego konkretnego Call i odbiera go użytkownikowi.
- „Przejmij rozmowę — zatrzymaj AI” odwołuje generację przed zatrzymaniem audio. Połączenie pozostaje aktywne. Stare callbacki nie mają prawa wznowić TTS/TX.
- „Włącz AI w rozmowie przychodzącej” jest jawnym żądaniem dla wyświetlanego Call. Wymaga aktualnej polityki AI dla jego konta i gotowości. Rozpoczyna nową generację/sesję modelu ze snapshotem ustawień; nie deklarujemy zachowania pełnej pamięci poprzedniego segmentu AI.
- Przejęcie wychodzącego Call przekazuje tylko dokładny uchwyt należący do kampanii, bez disconnect. Stara ścieżka cleanup nie może go później rozłączyć; kontynuacje kampanii są wstrzymane.
- Globalny STOP zatrzymuje także sekretarkę i wyłącza jej automatyczne odbieranie. Dotychczasowy selektywny STOP kampanii wychodzącej zachowuje swój sens. Cofnięcie trybu AI nie rozłącza przychodzącego Call oddanego użytkownikowi.

Po ponownym związaniu InCallService z już aktywną rozmową AI nie startuje samoczynnie. Nowe rzeczywiście dzwoniące połączenia mogą podlegać zachowanej polityce. Trwała kontynuacja ogólnego zadania, pochodzenie faktów i niezależne wyniki operacji pozostają dalszym rozwojem istniejącego workflow; [zaakceptowany plan](PRODUCT_ASSEMBLY_PLAN.md).

## Czat, powitanie i nagrania

Powitanie aplikacji przechodzi przez ten sam output approval, TTS, chroniony TX i anulowanie co odpowiedzi. Nie tworzy sztucznego transkryptu rozmówcy i nie wywołuje modelu. W czacie ma osobny wpis „AI / POWITANIE” i status TX. Numer 0 jest zarezerwowany dla otwarcia; pełne tury STT→model→TX zaczynają się od 1, więc powitanie nie zawyża ich licznika.

Źródło odpowiedzi jest zapisywane per wiadomość: powitanie skryptowe, dalsze odpowiedzi wybranego modelu. Istniejące archiwa sprzed tego pola zachowują swój odczyt źródła sesji. Zapis RX/TX nadal obejmuje dostępne okna/fragmenty obecnego recordera. Zatrzymanie runtime przy przejęciu kończy ten segment archiwum; pełny zapis części manualnej, wspólna ciągła oś audio i player całości pozostają F-080. Nie nazywać tego pełnym nagraniem połączenia.

Transport Samsung/Shizuku, atrybucja, PFD, format PCM, parametry VAD i pacing TX nie były zmieniane. Adapter korzysta z istniejącego przekazania audio i mechanizmu takeover. Pierwszy fizyczny dialog jest zapisany w powyższym checkpoint; mechanizm przejęcia wymaga oddzielnego dowodu urządzenia.
