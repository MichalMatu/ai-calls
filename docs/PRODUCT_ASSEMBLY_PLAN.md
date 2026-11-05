# Plan składania AI Calls — zadania, sekretarka i pełne archiwum

## Cel i stan — decyzja właściciela 2026-11-05

Docelowa aplikacja wykonuje zlecone zadania telefoniczne, np. umawia wizytę u lekarza lub mechanika, i działa jako sekretarka dla połączeń przychodzących. Użytkownik wybiera tryb manualny albo AI i może przejąć trwającą rozmowę. Każda rozmowa obsługiwana przez AI Calls ma mieć transkrypt i pełną ścieżkę audio, odsłuch całej sesji oraz odsłuch fragmentu z czatu. To wymaganie obejmuje także dalszą część przejętej sesji, jeżeli zapis pozostaje włączony.

Bieżący produkt pozostaje [laboratorium](TEST_LAB_SCOPE.md): porównujemy komponenty, a docelową aplikację składamy z wybranych, sprawdzonych modułów. Docelowo jeden APK bez wymaganych aplikacji towarzyszących; pliki modeli/głosów mogą być zarządzane jako osobne dane przez aplikację. Uprawnienia systemowe nadal nadaje użytkownik. Scalenie zależności następuje po wyborze i kwalifikacji komponentów.

Stan początkowy źródła przy ustalaniu planu: `b5d60ea`, runtime `e72bb15`. Wydzielone są logiczne granice silnika, scenariuszy, przygotowania i historii. Większość tych elementów pozostaje w module Gradle `app`; nie są jeszcze zestawem niezależnych bibliotek do dołączenia do nowej aplikacji. Aktualizacja wykonania: [odbieranie/manual/przejęcie i conversation-core](INCOMING_ASSISTANT.md) są w kodzie, z QA_PENDING fizycznym. Pełny zapis sesji i player całości wymagają implementacji. Ten dokument nie zmienia APK ani ustawień urządzenia. Bieżące statusy defektów tylko w [BLOCKERS](../BLOCKERS.md).

## Dane i zachowanie agenta

**Zaakceptowane uzupełnienie 2026-11-05:** głównym bytem jest zadanie, które może obejmować kilka rozmów, porównanie propozycji i oddzwonienie. Rozmowa jest sesją zadania. Koordynator wybiera kolejny krok na podstawie rzeczywistego stanu i postępu: rozmowa, narzędzie, oczekiwanie, dopytanie lub zakończenie. Powtarzanie bez postępu prowadzi do rozpoznania przyczyny i zmiany strategii, bez arbitralnego limitu tur lub prób.

Fakty zachowują pochodzenie i status: dane właściciela, informacja rozmówcy, propozycja modelu i potwierdzone ustalenie są odrębne. Zadanie określa zakres samodzielności i warunki, np. terminy/cenę/lokalizację, albo polecenie zebrania propozycji do późniejszej decyzji. Model nie zmienia tych warunków na podstawie słów rozmówcy.

Stan zadania i wykonanych operacji ma być trwały. Po restarcie koordynator weryfikuje stan Androida oraz faktyczne skutki przed kontynuacją; nie obiecuje samoczynnego wznowienia każdego Call. Rezerwacja, wpis kalendarza i przypomnienie mają osobne wyniki, a ponowienie nie tworzy duplikatów. To rozwój istniejących modeli zadania/workflow i narzędzi, bez drugiego koordynatora autoryzacji.

Naturalność rozmowy jest osobnym kryterium: opóźnienia, przekazywanie głosu, reakcja na przerwanie przez rozmówcę, hold i natychmiastowe przejęcie przez właściciela. Weryfikacja obejmuje kontrolowaną telefonię i rzeczywisty odsłuch. Osobista asystentka jest bieżącym celem produktu; wielu użytkowników, równoczesne linie i kolejki call center są późniejszym zakresem z odrębną izolacją danych i adapterem kanału. Nie wprowadzać ich jako wymogu startu obecnych funkcji.

| Warstwa | Zawartość | Własność i użycie |
|---|---|---|
| Profil użytkownika | Dane kontaktowe/tożsamości, preferencje, dostępność | Sejf i istniejące referencje pól; scenariusz otrzymuje tylko potrzebny, dozwolony zakres |
| Konkretne zadanie | Cel, adresat/kryteria wyboru, terminy, lokalizacja, warunki, dopuszczalne zobowiązania, kryterium sukcesu | Jawne zlecenie przyjęte raz do wspólnego modelu zadania; wymagane dane zależą od scenariusza |
| Skills i instrukcje | Rola, sposób rozmowy, umiejętności zadania, wiedza dziedzinowa, dodatkowe instrukcje właściciela | Edytowalne, wielokrotnego użytku, wersjonowane; snapshot zamrożony dla próby |
| Stan bieżącej rozmowy | Rozmówca, ustalone fakty, propozycje, brakujące informacje, etap zadania | Pamięć sesji; nie nadpisuje automatycznie profilu ani zakresu zadania na podstawie słów rozmówcy |
| Archiwum | Transkrypt, źródłowe audio, wspólna oś czasu, konfiguracja próby, wynik i dowody | Oddzielne od przygotowania i pamięci modelu; istniejący prywatny zapis, bez drugiej bazy tych samych transkryptów |

Dla wizyty zadanie opisuje np. rodzaj wizyty, akceptowane terminy, placówkę/lokalizację i warunki cenowe, jeżeli mają zastosowanie. Dla mechanika dochodzą potrzebne dane pojazdu i opis usługi. Nie tworzymy jednego obowiązkowego formularza z polami wszystkich branż. Brak istotnej informacji prowadzi do konkretnego pytania lub zebrania jej w rozmowie; nie do powtarzania już udzielonej zgody.

Użytkownik może wyrazić zlecenie zwykłym językiem. Aplikacja przekłada je na wspólny model zadania, dołącza pasujący profil i skillsy oraz uzupełnia dane z dozwolonych źródeł. Dopytuje o rzeczywiście brakujące warunki, bez wymagania od użytkownika napisania prepromptu lub ręcznej obsługi telefonu.

Wynik zawiera ustalony termin i warunki albo rzeczowy opis niepowodzenia/brakujących danych. Sam tekst modelu „umówione” nie stanowi dowodu rezerwacji. Wykorzystujemy istniejące typowane efekty i jeden CallCommitmentGate, a nie osobny system autoryzacji dla każdej branży.

## Preprompt i skills

Użytkownik może wybrać gotowe skillsy, dodać własne instrukcje i zapisać zestaw do ponownego użycia. Interfejs powinien pokazywać te wybory zwykłym językiem; nie wymaga ręcznego składania całego promptu.

Kompilator instrukcji składa: stałe zasady aplikacji → rolę kierunku rozmowy → opis zadania i warunki → wybrane skillsy i wiedzę scenariusza → dodatkowe instrukcje właściciela → aktualny stan dialogu. Przekazuje modelowi potrzebny kontekst, zachowując wartości sejfu w istniejącej kontrolowanej ścieżce ujawnienia. Nie wkleja całego sejfu i archiwum do każdej inferencji.

Skill instrukcyjny opisuje zachowanie, np. zebranie dogodnych terminów, dopytanie o niejasność lub przyjęcie wiadomości. Skill wykonujący czynność wymaga również jawnego kontraktu danych, rzeczywistego adaptera narzędzia i rozpoznawalnego wyniku. Sam preprompt nie implementuje kalendarza, rezerwacji ani wysłania wiadomości. Wiedza domenowa należy do wymiennego scenariusza/ServicePack. Treść rozmówcy jest wejściem dialogu, a nie instrukcją zmieniającą zasady aplikacji lub prawo dostępu do danych.

Obecny ConversationSkill jest zamkniętym katalogiem instrukcji; notatki profilu już są edytowalne. Dodawanie własnych, nazwanych i wersjonowanych skillsów oraz wspólny preset całej próby to dalszy rozwój istniejącego przygotowania, nie drugi magazyn preferencji.

## Osobny moduł narzędzi — konfiguracja i obserwacja

**Decyzja właściciela 2026-11-05:** wykonywanie narzędzi jest osobnym, konfigurowalnym i obserwowalnym modułem, wspólnym dla Gemmy i pozostałych modeli. Nie należy do implementacji konkretnego STT, TTS ani scenariusza CLIR. Składa się z katalogu typowanych narzędzi, adaptera żądań modelu, wykonawcy i adapterów usług. Skill opisuje, kiedy i po co użyć narzędzia; wykonawca obsługuje rzeczywistą operację i wynik.

Ekran **„Narzędzia”** ma pokazywać dostępne adaptery, gotowość/uprawnienia i ustawienia. Dla kalendarza: wybór kalendarza, odczyt dostępności albo również zapis, strefa czasowa i domyślne parametry wydarzenia. Preset eksperymentu wybiera zestaw narzędzi i konfigurację; model otrzymuje opis tylko dostępnych mu funkcji. Zwykła edycja dotyczy następnej próby; jawne wyłączenie/cofnięcie dostępu zatrzymuje nowe wykonania także w aktywnej sesji. Włączenie narzędzia w katalogu nie rozszerza zakresu danych lub skutków przyjętego zadania. Rzeczywiste uprawnienia systemowe pozostają wymagane, bez dodatkowych potwierdzeń każdej czynności w już przyjętym zakresie.

Pierwszy adapter kalendarza ma realizować sprawdzenie dostępności i utworzenie wydarzenia. Zmiana/usunięcie wydarzenia to kolejne jawnie wdrażane operacje. Umówienie wizyty u usługodawcy i dodanie wpisu do kalendarza mają oddzielne wyniki. Operacje korzystają ze wspólnej polityki zakresu/danych i typowanych skutków; nie tworzymy drugiego systemu zgód ani nie wykorzystujemy permitu jednego efektu do innej czynności.

Historia wykonania jest powiązana z zadaniem, sesją i turą rozmowy: identyfikator/wersja narzędzia, żądanie i argumenty, wynik walidacji, rozpoczęcie, czas trwania, rzeczywisty wynik lub błąd. Rozróżniamy żądanie modelu, dopuszczenie, wykonanie i potwierdzenie rezultatu. Nieznany rezultat po przerwaniu nie jest oznaczany jako sukces lub bezpieczne anulowanie. Powtórzenie tego samego żądania nie może tworzyć duplikatów wydarzenia; idempotencja i odczyt rezultatu należą do adaptera, bez arbitralnych limitów wywołań.

Widok rozmowy pokazuje osobne wpisy narzędzi, np. „Sprawdzono dostępność” i „Zapisano wizytę”, z możliwością rozwinięcia lokalnych szczegółów. Wpis narzędzia nie jest wypowiedzią rozmówcy ani tekstem automatycznie kierowanym do TTS. Diagnostyka umożliwia filtrowanie po sesji/narzędziu, podgląd czasów i przyczyny błędu; prywatne argumenty/wyniki pozostają w chronionym lokalnym archiwum, poza logcat/Git/raportem publicznym. Zdarzenia rozszerzają istniejącą oś czasu, bez drugiej kopii transkryptu.

Laboratorium ma pozwalać uruchomić kontrolowany test pojedynczego narzędzia i porównać jego użycie przez różne modele. Symulację i rzeczywisty adapter oznaczamy jednoznacznie; test zapisu kalendarza weryfikuje rzeczywisty wpis oraz brak duplikatu po ponowieniu. STOP/cofnięcie ma pierwszeństwo przed kolejnymi wywołaniami, a trwające operacje raportują faktyczny stan. Nie dodajemy automatycznego ponawiania operacji o nieznanym skutku.

**Stan implementacji:** w lokalnym Gemma4LiteRtTextBackend nie ma jeszcze podłączonego katalogu/wykonawcy narzędzi; automaticToolCalling jest false. W app nie ma adaptera kalendarza ani READ_CALENDAR/WRITE_CALENDAR. Istniejące handlery Realtime nie są dowodem podłączenia tej funkcji do lokalnej Gemmy. Powyższy moduł wymaga wdrożenia i testów; sama zmiana flagi nie implementuje narzędzi.

## Klocki i granice kompilacji

| Klocek | Kontrakt / odpowiedzialność |
|---|---|
| Wspólny model zadania i sesji | Zadanie, kierunek, warunki, zdarzenia, stan i wynik; domena testowalna bez telefonu |
| Przygotowanie i skills | Profil, kontekst zadania, kompilacja instrukcji, referencje danych |
| Narzędzia | Katalog, konfiguracja, wykonawca i adaptery usług niezależne od modelu; typowane żądania i obserwowalne wyniki |
| Scenariusze | Rezerwacja, sekretarka, CLIR i testy kontrolowane; wymienne w jednym runtime |
| Wejście / model / wyjście | Istniejące porty STT, dialogu i TTS; adaptery konkretnych silników i artefaktów |
| Adapter telefonii | Wychodzący lub przychodzący własny Call, stan Androida, manual/AI i przejęcie |
| Media i zapis sesji | Istniejący transport, niezależny recorder, indeks źródeł i pokrycia audio |
| Archiwum i odtwarzanie | Jedna historia, transkrypt z odnośnikami czasu, player całości/fragmentu i eksport |
| Aplikacja składająca | Laboratorium: wszystkie kwalifikowane wybory i pomiary; produkt: wybrane moduły i uproszczony interfejs |

Wydzielamy rzeczywiste moduły kompilacji wraz z pionowym wdrożeniem funkcji i testami ich zależności. Rdzeń nie importuje Activity, CLIR ani konkretnego silnika. Androidowe adaptery zależą od wspólnych kontraktów. Nie dodajemy pustych bibliotek ani drugich runnerów na zapas. Aktualnych callerów migrujemy bez utrzymywania starych aliasów i fallbacków.

Preset eksperymentu wiąże scenariusz/kanał, STT, model, strategię skrypt/Direct, wersje instrukcji/skillsów, zestaw narzędzi i ich konfigurację, TTS/głos i politykę zapisu. Zmiana presetu nie przestawia trwającej rozmowy. Gotowość kombinacji wynika z możliwości i pomiarów na urządzeniu. Raport identyfikuje faktycznie użyte elementy; nie zastępuje brakującego komponentu innym po cichu.

## Sekretarka, manual i przejęcie

Zaimplementowany adapter przychodzący korzysta z tego samego runtime, ustawień silników, przygotowania, czatu i archiwum. Włączenie sekretarki zapisuje odwoływalną politykę obsługi wskazanego SIM/konta, dopuszczalnych czynności i danych. Konkretna sesja wiąże się z rzeczywistym przychodzącym Call. Nie wymaga whitelisty numerów ani potwierdzenia każdego telefonu; profil instrukcji sam nie aktywuje odbierania.

- **Manual:** użytkownik odbiera i prowadzi rozmowę; agent nie rozpoczyna dialogu.
- **AI:** aplikacja odbiera według przyjętej polityki, a po rzeczywistym ACTIVE rozpoczyna wspólny flow. Pierwszy scenariusz sekretarki przedstawia rolę, ustala sprawę i przyjmuje wiadomość; dalsze zobowiązania wymagają implementacji i zakresu zadania.
- **Przejmij:** natychmiast unieważnia generację agentowego STT/inferencji/TTS i dalszego TX, oddaje tor rozmowy użytkownikowi i pozostawia połączenie aktywne. Spóźniony callback nie może wznowić mówienia. Przywrócenie AI wymaga wyraźnego polecenia użytkownika i sprawdzenia tej samej sesji.

Przejęcie nie jest rozłączeniem ani samoistnym cofnięciem osobno przyjętego zapisu rozmowy. Recorder ma niezależny cykl życia, jeżeli nagrywanie pozostaje aktywne. Transkrypt części manualnej może zostać uzupełniony lokalnie z nagrania po rozmowie; nie wymaga dalszego działania dialogowego agenta. STOP, cofnięcie właściwego zakresu i zakończenie Call zachowują pierwszeństwo. Nie tworzymy konkurencyjnych właścicieli PFD ani drugiego przechwytywania zamrożonego źródła mediów.

## Pełne audio i transkrypt

Właściciel wskazał pełne archiwum jako standard każdej rozmowy obsługiwanej przez AI Calls. Implementacja ma wykorzystać istniejącą konfigurację zapisu bez dodatkowego pytania przy każdej sesji. Bieżący kod nadal zapisuje według dotychczasowego wyboru lokalnego RX/TX/transkryptu; plan nie zmienia tego ustawienia ani zakresu obsługiwanych Call. Uprawnienia Androida i prywatność rozmówców obowiązują; nie rozszerzamy zapisu na obce rozmowy.

Pełne audio oznacza pokrycie dostępnych mediów od początku do końca obsługiwanej sesji, w tym IVR/oczekiwania, słuchania, czasu inferencji, TTS, nakładania wypowiedzi i części manualnej. Zakończenie tury VAD nie kończy nagrania. Zdarzenia zestawienia połączenia i okresy bez dostępnego źródła muszą być widoczne na osi czasu.

Obecne pliki per tura pozostają przydatne do diagnostyki i odsłuchu konkretnej wiadomości. Jednak RX jest dziś zapisywany w oknach przechwytywania STT, a TX odpowiada bajtom przyjętym przez writer dla AI/ACK. Samo sklejenie tych WAV-ów nie odtworzy niezarejestrowanej mowy. Pokrycie w czasie inferencji/TTS i źródło faktycznego głosu użytkownika po przejęciu wymagają implementacji oraz celowanej weryfikacji na S22. [F-080](../BLOCKERS.md) pozostaje OPEN.

Źródłem prawdy mają być ścieżki RX i lokalnego TX powiązane jednym monotonicznym czasem sesji oraz indeksem próbek/segmentów, zawierającym rolę, format, początek, długość, pokrycie i błędy. Fizyczne pliki mogą pozostać segmentowane. Wszystkie zdarzenia, transkrypt i źródła audio należą do tej samej sesji; indeks rozszerza istniejące archiwum. Nie trzymamy drugiej niezależnej kopii transkryptu. Ciągły plik miksu może być tworzony na eksport/odsłuch jako materiał pochodny.

Transkrypt obejmuje rozmówcę, wypowiedzi AI z ACK oraz użytkownika po przejęciu. Rozróżnia tekst rozpoznany, tekst zatwierdzony i dźwięk faktycznie wysłany lokalnie; pokazuje przerwane/niepełne fragmenty. Późniejsza korekta ASR zachowuje powiązanie ze źródłowym audio. Nagranie jest podstawą sprawdzenia błędu STT, a nie gwarancją bezbłędnej transkrypcji. Status WRITE_COMPLETE nie staje się dowodem słyszalności po drugiej stronie.

Luki, utrata źródła, zapis częściowy i brak miejsca są jawne. Brakujących próbek nie opisujemy jako nagranej ciszy. Problem archiwum nie blokuje samej rozmowy; wynik wskazuje zakres braków. Retencja/eksport i stan miejsca mają być zrozumiałe dla użytkownika, bez arbitralnego limitu liczby nagrań lub ukrywania utraty wcześniejszej sesji. Prywatne audio i treści pozostają poza Git i zwykłymi logami.

## Jeden player, dwa sposoby odsłuchu

- **Odtwórz całość:** jeden przebieg od początku do końca, Play/Pause, przewijanie, czas i prędkość; bez ręcznego otwierania kolejnych plików.
- **Odtwórz wiadomość:** kliknięcie przy wypowiedzi przechodzi do odpowiadającego jej fragmentu tej samej osi czasu. Bieżąca wiadomość jest podświetlana podczas odsłuchu.

Player zachowuje oryginalne odstępy i nakładanie RX/TX. Opcjonalne pomijanie zarejestrowanej ciszy może być osobnym trybem, domyślny odsłuch zachowuje rzeczywisty przebieg. Braki nagrania są oznaczone. Historyczne segmenty pozwalają odtworzyć tylko dostępny materiał, z uczciwym statusem niepełnego pokrycia.

Nagranie nie musi być jednym wielkim plikiem na dysku: indeksowana oś czasu może zapewniać płynny odczyt segmentów i miksowanie strumieni. Odtwarzanie historycznego audio kierujemy do lokalnego odsłuchu, bez podłączenia do uplinku trwającej rozmowy. Obsługa wyjścia audio podczas aktywnego Call wymaga sprawdzenia rzeczywistego stanu Androida i trasy.

## Kolejność i kryteria odbioru

1. **Odbieranie / manual / przejęcie — kod wdrożony, kwalifikacja fizyczna pending.** Wspólny preset i kontekst INCOMING, zapis polityki, adapter własnego Call, ten sam runtime. W tym zakresie ustalić źródła i cykl życia niezależnego zapisu, żeby przejęcie nie odcinało archiwum przez konstrukcję silnika.
2. **Pełne archiwum i player jako osobny moduł.** Najpierw sprawdzić pokrycie źródeł i wspólny czas, następnie pełny zapis, odnośniki czatu i płynny odsłuch. Odsłuch dostępnych starych segmentów nie zamyka wymagania pełnego zapisu nowych sesji.
3. **Ogólny intake zadań, rozszerzalne skillsy i moduł narzędzi.** Jeden pełny scenariusz wizyty/rezerwacji na kontrolowanym rozmówcy, z warunkami i wynikiem; następnie kolejna branża. Katalog/wykonawca narzędzi z ekranem konfiguracji i historią, pierwszy rzeczywisty adapter kalendarza: dostępność i zapis wydarzenia. Zachować wspólne polityki danych i skutków. F-044/F-045/F-048 oraz jakość dialogu pozostają w kanonicznym rejestrze.
4. **Selekcja i składanie produktu.** Porównania korpusu i kontrolowanej telefonii, wybór zgodnych komponentów, wydzielanie bibliotek przy rzeczywistych integracjach. Następnie wbudowanie wybranych zależności i próba samodzielności jednego APK bez aplikacji towarzyszących.

Odbiór wymaga zarówno testów hosta, jak i właściwych prób fizycznych. Dla przejęcia: brak późnego TX, połączenie pozostaje aktywne, rozmowa użytkownika działa, stan manualny utrzymuje się do polecenia. Dla archiwum: początek/koniec, hold, mowa w czasie inferencji/TTS, nakładanie, manualny głos, partial TX, awaria zapisu i poprawny eksport. Dla playera: ciągłość przez granice plików, seek, pauza/wznowienie, zgodność czasu i czatu oraz jawne luki. Dla zadania: uzgodnione warunki i rzeczywisty wynik niezależny od deklaracji modelu.

## Weryfikacja tej aktualizacji planu

Zakres: dokumentacja i rejestr brakującej funkcji, bez zmiany zachowania runtime, instalacji, ustawień lub połączeń. Przejrzano powyższe kontrakty i zapis źródłowy na `b5d60ea`. Nowe funkcje mają status QA_PENDING implementacji; wcześniejsze [QA runtime](checkpoints/2026-11-05-conversation-modules.md) zachowuje swój zakres. Stan S22 nie był ponownie odczytywany dla tej zmiany dokumentacji. Lokalny transport Local Agent sprawdzony read-only: idle, bez aktywnego zadania; praca wykonana bezpośrednio w Codexie.

Pierwsza aktualizacja planu: audit_product_friction PASS, git diff --check PASS, 125 lokalnych odnośników bez braków. Doprecyzowanie modułu narzędzi na bazie `b97d957`: audyt i diff-check ponownie PASS, 32 lokalne odnośniki w trzech zmienianych dokumentach bez braków. Bez ponawiania Gradle/native/live dla zmiany samego planu.
