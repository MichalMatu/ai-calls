# AI Calls — zakres laboratorium, moduły i instalacja

## Decyzja właściciela — 2026-11-05

Bieżąca aplikacja jest **modułowym laboratorium testowym**. Ma porównywać wejścia audio, rozpoznawanie mowy, modele, sterowanie dialogiem, instrukcje i wyjścia mowy. Docelowy flow powstanie później z elementów, których jakość i współpracę potwierdzą testy. CLIR i Orange są jednym scenariuszem integracyjnym. Ani jeden operator, ani jedna udana rozmowa nie definiują jakości całej aplikacji.

Zachowujemy wspólny silnik wykonywania zadania, STOP, zakres danych/skutków i własność Call. Laboratorium ma również automatycznie wykonywać zlecone próby. Nie przenosimy rutynowej obsługi IVR, startu ani rozłączania na operatora. Skrypt jest wymienną strategią dialogu. Testy eksperymentalnych komponentów nie oznaczają ich akceptacji do docelowego flow.

## Co już można wybrać

W ekranie narzędzi kampanii istnieją dwa przyciski:

- „Rozpocznij zadanie: włącz CLIR — skrypt + model”: `SCRIPT_GEMMA`, PhraseMatrix i klasyfikator akcji, następnie zatwierdzona wypowiedź akcji/recovery.
- „Rozpocznij zadanie: włącz CLIR — Gemma Direct”: `GEMMA_DIRECT`, swobodny tekst wybranego modelu bez klasyfikatora akcji JSON i stałych fraz skryptu.

Direct nadal dostaje zadanie/instrukcje i podlega aplikacyjnym zasadom wypowiedzi, danych i efektów. „Bez skryptu” nie oznacza pustego promptu. Nazwa UI „Gemma Direct” jest historyczna: `DialogueEngineProviders` korzysta z modelu wybranego w profilu, również Qwen. Docelowa etykieta laboratorium powinna mówić „Model bez skryptu”.

Ustawienia silników mają niezależne wejście, model, wyjście i głos (`AudioEngineProfile`). Tryb skrypt/Direct jest dziś osobnym parametrem START i jest zapamiętywany dla zadania na czas jego kontynuacji (`CampaignDialogueModeStore`), nie stanowi pola zapisanego presetu silników. Instrukcje Direct mają własny istniejący magazyn. Runtime otrzymuje teraz jeden typowany `ConversationRunConfiguration` z profilem i strategią; Direct zawiera też zamrożoną instrukcję. Konfiguracja powstaje przed prewarm i jest przekazywana przez executor do tego samego potoku. Zapisywalny preset całego eksperymentu, ogólny scenariusz i adaptery kanałów nadal wymagają integracji.

## Ocena bieżącego kodu

| Obszar | Istniejąca granica | Co wymaga uporządkowania |
|---|---|---|
| STT | `SpeechInputProvider`: start, PCM, final, cancel | Wspólny kontrakt wejść z pliku i mikrofonu do powtarzalnych prób poza telefonem |
| Model | `DialogueEngineProvider`: Direct albo klasyfikator, wspólny runtime LiteRT-LM | Neutralne nazwy trybów/raportów i jednoznaczna identyfikacja wybranego artefaktu w wyniku eksperymentu |
| TTS | `SpeechOutputProvider`: tekst → PCM16, cancel | Ten sam test jakości dla każdego adaptera; Piper ma znany błąd zewnętrznego API, sam instalator go nie naprawi |
| Konfiguracja | `ConversationRunConfiguration` składa istniejące wybory w jeden niezmienny snapshot próby: ScriptAssisted albo ModelDirect | Rozwinąć zapisywalny preset eksperymentu bez kopiowania magazynów preferencji |
| Zadanie/scenariusz | Istnieją `CallTask`, `CallResolvedTarget`, typed effects i TaskBrief | `CampaignAgentControl`, `AppOwnedCampaignService` i `AppOwnedClirCampaignExecutor` nadal wiążą start ze specyfikacją CLIR; ogólny intake zadań nie jest gotowy; wspólny runtime otrzymuje już osobny scenariusz |
| Orkiestracja | Wspólny tor używany przez `AndroidConversationRuntime` i owned-call executor | Scenariusz CLIR i tryb nadzorowany wydzielone; dalej adapter przychodzącego Call i kanały korpusu, bez drugiego runnera lub drugiej polityki STOP/danych/skutków |
| Audio modelu | Eksperymentalny PCM→AudioBytes→transkrypcja | `SpeechInputProvider` oddaje tekst. Pełny dialog przyjmujący audio wymaga rzeczywistego kontraktu multimodalnego; zmiana samej etykiety/pola nie wystarczy |

Modułowość silników jest już użyteczna, ale cała aplikacja nie jest jeszcze ogólnym laboratorium scenariuszy. Początkowy przegląd dotyczył `83baf50`; implementacja snapshotu jest opisana w [checkpoint konfiguracji próby](checkpoints/2026-11-05-lab-run-configuration.md). Nie jest to nowy wynik rozmowy fizycznej. Bieżące defekty i zakresy dowodów pozostają wyłącznie w [BLOCKERS](../BLOCKERS.md), m.in. F-044 (generic tasks/executors), F-045, F-048, F-057 i F-061.

Szczegóły aktualnego podziału, przygotowania obu kierunków i historii: [CONVERSATION_RUNTIME](CONVERSATION_RUNTIME.md).

## Kontrakt dalszego kodowania

Jedna konfiguracja próby ma składać: scenariusz/zadanie + kanał testu + STT + model + strategię dialogu + instrukcje + TTS/głos. Domyślne preferencje pozostają w obecnych magazynach; konfiguracja próby jest ich snapshotem z jawnymi wyborami eksperymentu. Zmiany ustawień nie przestawiają aktywnej rozmowy.

```text
scenariusz + snapshot konfiguracji próby
  → adapter kanału: tekst / plik audio / mikrofon / własna rozmowa
  → istniejące STT → strategia dialogu + model → polityka wypowiedzi → TTS
  → wynik + pomiar + prywatne dowody
```

Scenariusz dostarcza cel, materiał wejściowy/rozmówcę testowego i kryterium sukcesu. Silnik STT/TTS ani model nie znają numeru Orange, nie wybierają SIM i nie sterują Telecom. Wspólna polityka rozstrzyga zakres skutków i danych. Adapter rzeczywistej rozmowy obsługuje Call i zamrożony tor Samsung/Shizuku. Adapter offline nie powinien wymagać roli dialera ani instalowania dodatków potrzebnych wyłącznie do telefonii.

Zmiany robić pionowo: jeden dodatkowy scenariusz z tym samym silnikiem, działającym UI/runnerem i wynikiem, potem następny. Bez nieużywanych abstrakcji, drugich magazynów tych samych preferencji, kopii potoku i wielkiej przebudowy mediów. Nie wprowadzać globalnych limitów tur/prób ani dodatkowych zgód.

## Testy szersze niż Orange

| Warstwa | Materiał / rozmówca | Co rozstrzyga |
|---|---|---|
| Tekst | Ten sam korpus transkrypcji dla modeli i obu strategii | Rozumienie celu, poprawność akcji, odpowiedź na pytanie, trzymanie tematu, brak zapętleń; bez wpływu STT/TTS |
| Audio z pliku | Ten sam polski korpus z referencją: różne głosy, tempo, hałas i przerwy | WER/CER STT, jakość TTS i opóźnienia komponentów; bez zmiennej infolinii |
| Rozmówca kontrolowany | Jawna maszyna stanów IVR/konsultanta oraz osobne warianty rozmówcy oparte na modelu | Hold, prośba o powtórzenie, oczekiwanie na konkret, powtarzane pytanie, odmowa, zmiana tematu, dane i zakończenie; brak uzależnienia oceny od jednego drugiego AI |
| Telefonia kontrolowana | Własna końcówka testowa / własny numer z IVR i możliwością odsłuchu drugiej strony | Rzeczywiste RX/TX, słyszalność, DTMF, przerwania, teardown i STOP, niezależny wynik scenariusza |
| Środowisko naturalne | Orange oraz później inne jawnie zlecone cele | Integracja z niekontrolowanym rozmówcą; oddzielny wynik od testów porównawczych |

Korpus powinien zawierać również proste zapytanie informacyjne, rezerwację bez realnego skutku w symulatorze oraz konflikt/prośbę o efekt spoza zadania. Nagrania syntetyczne i naturalne raportować osobno. Rozmówca oparty na modelu nie jest jedynym sędzią własnego wyniku.

Dla A/B zachować ten sam materiał, zadanie, instrukcje wspólne i urządzenie; zmieniać jedną oś. Najpierw porównać skrypt/Direct i modele na tekście, STT na korpusie audio, TTS na wspólnym tekście, potem połączenia wybranych modułów. Nie wykonywać ślepo iloczynu wszystkich opcji. Nie opierać wyboru na pojedynczej rozmowie z Orange.

Raport próby powinien identyfikować konfigurację faktyczną, wersję APK, hash modelu, korpusu i instrukcji, źródło odpowiedzi oraz wynik zadania niezależny od technicznego przepływu. Mierzyć inicjalizację/prewarm, STT final, generację modelu, TTS i EOS→TX, powtórzenia bez postępu, pamięć/temperaturę oraz reakcję STOP. Statystyki rozkładu opóźnień podawać z wielkością próbki; nie ogłaszać zwycięzcy z jednej tury. Prywatne audio i treści pozostają poza Git.

## Jedna aplikacja i instalacja zależności

| Element | Obecny sposób użycia | Kierunek |
|---|---|---|
| LiteRT-LM / LLM | Biblioteka `litertlm-android:0.19.0` bezpośrednio w AI Calls | Pozostaje w naszym APK |
| Google AI Edge Gallery | Osobna aplikacja demonstracyjna; brak wymaganej zależności wykonawczej w naszym torze | Nie instalować jako wymagania AI Calls; może zostać pomocniczym narzędziem porównawczym |
| Parakeet | Biblioteka MediaPipe w APK, osobny plik modelu | Zarządzanie artefaktem/gotowością w AI Calls |
| Modele językowe | Własny katalog, pobranie/import/hash w AI Calls | Zachować jeden mechanizm; modele jako dane, niezależne od aktualizacji APK |
| Privileged helper | Moduł `com.android.library` dołączony do AI Calls; UserService pakowany z aplikacją | Nie wymaga osobnego APK helpera |
| Shizuku | API/provider są w APK, manager/serwer uprzywilejowany pozostają zewnętrzne | Na obecnym etapie instalować razem z AI Calls, zachowując sprawdzoną granicę mediów |
| Android STT/TTS | Silniki/głosy systemu dostępne na urządzeniu | Sprawdzać rzeczywistą dostępność wybranego silnika/głosu |
| Piper | Zewnętrzny silnik Android TTS + polski głos | Opcjonalna zależność profilu; osadzenie natywnego TTS to osobny adapter i pomiar, nie sklejanie APK |

LiteRT-LM udostępnia Android SDK do dołączenia przez Gradle; Gallery jest wskazane jako przykład aplikacji, nie wymagany proces serwera. Potwierdzają to [dokumentacja SDK](https://github.com/google-ai-edge/LiteRT-LM/blob/main/docs/api/kotlin/getting_started.md) oraz lokalne `app/build.gradle.kts` i `DialogueEngineProviders.kt`.

**Doprecyzowanie właściciela:** docelowo jeden pakiet instalacyjny. W bieżącym laboratorium kontynuujemy moduły i testy; instalator jest odłożony do etapu dystrybucji.

`PackageInstaller` to część systemu Android i Android SDK, nie osobna aplikacja ani paczka do zainstalowania. Korzystamy z API; nie kopiujemy jego implementacji do AI Calls.

Shizuku składa się z klienta API/provider (już w naszym APK), serwera/brokera działającego jako shell/root oraz managera odpowiedzialnego za uruchomienie i obsługę. Docelowo można oprzeć własną dystrybucję na potrzebnych częściach API, startera, serwera i obsługi UserService/Binder oraz zastąpić UI managera własnym. Same ustawienia lub skopiowany ekran nie zastępują działającego brokera. Obecne API/provider są gotowymi bibliotekami, więc nie ma potrzeby przepisywać ich jako fragmentów. API ma [licencję MIT](https://github.com/RikkaApps/Shizuku-API/blob/master/LICENSE), serwer/manager [Apache 2.0](https://github.com/RikkaApps/Shizuku/blob/master/LICENSE); przy wykorzystaniu kodu zachowujemy odpowiednie licencje i informacje autorów. W laboratorium pozostawiamy dotychczasowy zewnętrzny Shizuku, bez zmiany fizycznie zweryfikowanego transportu.

Możliwe warianty realizacji docelowego pakietu:

1. **Jeden pakiet uruchamiany na Macu przez USB/ADB:** dostarcza/aktualizuje AI Calls i potrzebne APK zależności, rozpoznaje już zainstalowane wersje, zachowuje dane, uruchamia istniejące readiness. Shizuku i opcjonalny Piper pozostają oddzielnymi pakietami Androida. Wykorzystać istniejące narzędzia urządzenia i instalacji; brak potrzeby Gallery. Model wybrany w aplikacji jest pobierany/importowany przez obecny katalog, bez wrzucania wszystkich modeli do APK.
2. **Jeden ekran „Przygotowanie” w AI Calls:** status zależności, instalacja brakującego komponentu przez Android PackageInstaller, przejście do rzeczywistej aktywacji Shizuku i wybór modelu/głosu. Odtwarzać stan po powrocie z systemowego ekranu, nie powielać własnych potwierdzeń ani już spełnionych kroków.
3. **Opcjonalnie pełne scalenie źródeł:** wbudowany TTS i własna dystrybucja/start brokera uprzywilejowanego są możliwym osobnym projektem. Włączenie biblioteki Shizuku API nie zastępuje serwera uruchomionego z prawami shell/root; nie ma prostego przełącznika Gradle dającego takie prawa zwykłej aplikacji. Taka zmiana wymaga osobnej kwalifikacji granicy mediów, nie jest potrzebna do modularności laboratorium.

Android obsługuje wspólną sesję instalacji wielu pakietów (`setMultiPackage`, od API 29). Pakiety nadal mają odrębne identyfikatory i podpisy. Przy pierwszej instalacji przez zwykłą aplikację trzeba obsłużyć systemową zgodę instalacji; `setRequireUserAction` nie daje dowolnemu instalatorowi prawa cichej pierwszej instalacji. To wymaganie Androida, nie własny grant AI Calls. [Oficjalny kontrakt PackageInstaller](https://developer.android.com/reference/android/content/pm/PackageInstaller.SessionParams).

Dla Shizuku na telefonie bez roota potrzebny jest start z ADB lub parowania bezprzewodowego; oficjalna instrukcja zaznacza ponowne uruchomienie po reboocie. Wspólny pakiet może automatyzować dostępną ścieżkę na przygotowanym urządzeniu, ale sam fakt instalacji APK nie oznacza aktywnego serwera. [Instrukcja Shizuku](https://shizuku.rikka.app/guide/setup/) i [API/UserService](https://github.com/RikkaApps/Shizuku-API).

Wspólny instalator i ekran przygotowania nie są zaimplementowane. Kontynuacja laboratorium wdraża snapshot konfiguracji próby oraz usuwa podmianę modelu przy braku cache; nie przeprowadzono nowej rozmowy telefonicznej w tym zakresie. Nie ma nowej deklaracji gotowości Piper, pełnego Gemma Audio ani ogólnych skutków zewnętrznych.

## Weryfikacja pierwszego przeglądu — historyczne `bf6eccd`

Zakres zmiany: wyłącznie dokumentacja i instrukcje rozwoju, na bazie `83baf50`; runtime pozostaje z `c611830`. Przejrzano deklaracje providerów i profilu, wybór trybu w UI/START/retry, związek executorów z CLIR oraz zależności Gradle. Zweryfikowano bieżące oficjalne kontrakty SDK, PackageInstaller i Shizuku.

`audit_product_friction` PASS, `git diff --check` PASS; 86 lokalnych odnośników w zmienianych dokumentach istnieje. F-070 koryguje nieaktualny wymóg kolejnej decyzji o TTL. Odczyt S22 przez istniejący most USB: 2× IDLE, kampania nieaktywna, pending_retry=false; profil ANDROID_STT / GEMMA_4_E2B / ANDROID_TTS_PL. Bez instalacji, modyfikacji profilu, prób telefonicznych i nowych testów modelu. Nie powtarzano Gradle/native dla zmiany samych dokumentów; poprzednie wyniki i hashe APK są w handoff/checkpoint.
