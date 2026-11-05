# Kontrolowany rozmówca A8 → S22 — 2026-11-06

Kod aplikacji `ef50a1b5e9fab85bfe4654169ffeec9d644ff6e9`; dokumentacja startowa `0b47081`. Lokalny Codex sterował wyłącznie S22; właściciel wykonywał połączenie ręcznie z A8, przygotowywanego osobno w ChatGPT. Bez zleceń Local Agentowi, Work i Actions. [Moduł odbierania](../INCOMING_ASSISTANT.md).

## Przygotowanie i obserwacja właściciela

Świeży preflight S22 SM-S906B: IDLE, AI Calls jako dialer, kampania nieaktywna, retry false, uprawnienia Androida i uruchomiony Shizuku. Profil ANDROID_STT → GEMMA_4_E2B → ANDROID_TTS_PL, głos auto. Aplikacyjny profile_check PASS, sześć dostępnych polskich głosów. Właściwe konto wybrano przez istniejący ekran: jego etykieta odpowiadała numerowi przekazanemu przez właściciela. Zapisano AI oraz lokalny zapis transkryptu i dostępnych fragmentów audio; readback polityki PASS. Fingerprints wychodzącego zadania, skills i profilu audio bez zmian. Nie zmieniano kodu ani APK do tej próby.

Właściciel potwierdził zakończony test, słyszalność AI oraz płynne, sensowne odpowiedzi. To pozytywna obserwacja rzeczywistego kontrolowanego rozmówcy dla tej konfiguracji. Nie było przygotowanego scenariusza ani niezależnego pomiaru jakości. Nie jest to odbiór całej macierzy silników.

## Odczyt urządzenia i wyniki

Obserwator Maca uruchomiony przed próbą odczytał IDLE i 33 wcześniejsze sesje. Następnie urządzenia zniknęły z ADB; obserwator nie przechwycił zdarzeń połączenia na żywo. Po ponownym podłączeniu S22 odczytano jedną nową sesję incoming-secretary / INCOMING / gemma_direct. Zapis urządzenia umożliwił ocenę bez powtarzania udanej rozmowy.

Sesja recordera trwała **84 213 ms**. Timeline zawiera powitanie oraz **3 COUNTERPART_STT, 3 INCOMING_MODEL_DIRECT i 4 AGENT_FULL_TX / WRITE_COMPLETE**. Każda główna wypowiedź ma pcm_bytes_accepted równe pcm_bytes_requested. Trzy decyzje modelu mają local_error_present=false. Zapisano też trzy krótkie potwierdzenia ACK; nie są to dodatkowe odpowiedzi modelu. Recorder ma END i COMPLETE, bez storage_error. Prywatny eksport obejmuje 10 poprawnych WAV mono PCM16 16 kHz oraz timeline; archiwum 2 045 952 B, SHA-256 `423f3113c94a2d647489839eb66f789fbb226161f2108cde3e9f9235c494442a`.

| Pomiar | Wynik |
|---|---|
| Start runtime → pierwszy TX powitania | 852 ms |
| EOS → krótki ACK, tury 1–3 | 1804 / 1800 / 1800 ms |
| EOS → pierwszy TX pełnej odpowiedzi, tury 1–3 | 15 406 / 5586 / 5773 ms |
| Czas decyzji modelu, tury 1–3 | 12 161 / 2693 / 2833 ms |

Pierwsza odpowiedź jest wyraźnie wolniejsza; nie rozdzielono jeszcze w tym pomiarze inicjalizacji od generacji. Subiektywna płynność potwierdzona przez rozmówcę nie oznacza braku opóźnień. Licznik runtime turns_completed wynosi 2: trzecia odpowiedź ma pełny zapis TX, ale brak końcowego turn_3_complete przed zakończeniem sesji. Nie zawyżamy tego licznika do 3. Końcowy raport ma operator_stopped i success=false z ogólnego sygnału zatrzymania mediów — F-089 OPEN; to nie dowód użycia STOP ani błędu modelu.

Postcheck po eksporcie: **2× IDLE**, kampania nieaktywna, retry false, 34 sesje. Profil Android STT/E2B/Android TTS oraz polityka AI zachowane. W połączeniu z obserwacją rzeczywistego rozmówcy jest to pozytywny dowód fizycznego dialogu INCOMING w tym profilu. Brakuje niezależnej osi czasu RINGING→answer→ACTIVE, więc nie podajemy czasu automatycznego odebrania.

## Pozostałe QA_PENDING

Przejęcie manualne, brak późnego TX, powrót AI i STOP podczas rzeczywistej rozmowy pozostają nieprzetestowane. Pełny zapis sesji, głos po przejęciu i player całości wymagają implementacji (F-080). Nie sprawdzano kalendarza, rezerwacji, ujawniania danych ani innego skutku zewnętrznego.

## Domknięcie zaległego testu model→TTS bez telefonii — PASS

Po eksporcie rozmowy i świeżym preflight off-call uruchomiono jedyny rozszerzony kontrakt selectedModelAndVoiceRunSecretaryGreetingAndDialogWithoutTelephony. App na urządzeniu ma SHA-256 `646f88d9a2e466621d659d807e1e93e7e26da8dc1c33b5e3de84b13da7a5d37d`, bez zmiany produkcyjnego APK. Nowy AndroidTest `ce40fb5455a3578076571c84e7a5270951235a749aa373c0d1a244c7b1242862` zainstalowano po kontroli zgodności certyfikatów. Wcześniejszy build AndroidTest i lint PASS; F-088 zachowuje pierwszy compile FAIL przed poprawką enum.

AndroidJUnitRunner **OK (1 test)**, jeden status 0, brak ujemnych statusów testu. Rzeczywista Gemma E2B, aplikacyjny approval i wybrany Android TTS: powitanie oraz dwie odpowiedzi na syntetyczny tekst, bez mikrofonu i połączenia. Cofnięcie upoważnienia odrzuca kolejne otwarcie bez PCM. Trzy WAV mono PCM16 16 kHz mają poprawne rozmiary i niezerowy RMS; długości 5896 / 10 373 / 6262 ms. To nie jest test rzeczywistego STT, telefonii lub słyszalności. Fingerprints zadania/skills/profilu oraz przyjętej polityki AI bez zmian; postcheck IDLE, kampania/retry nieaktywne.

Receipts extended-native-speech.txt, extended-native-speech-summary.json i prywatny katalog synthetic-speech. F-090: wrapper początkowo oczekiwał println w stdout runnera i zgłosił fałszywe FAIL; zachowano je w extended-native-speech-wrapper-first-report.json. Wynik zweryfikowano statusami JUnit i unikalnym katalogiem artefaktów bez powtarzania testu. Łącznie **18 natywnych kontraktów PASS**: wcześniejsze 6 + 11 oraz to rozszerzenie. Rzeczywista rozmowa opisana wyżej jest osobnym dowodem.

Prywatne receipts: `~/Documents/AI-Calls/local-codex/2026-11-06-controlled-incoming/`, katalog 0700, pliki 0600: preparation.json, owner-observation.json, observer-start.json, observer-events.jsonl, observer-status.json, observer-stopped.json, post-call-status.json, call-runtime-report.txt, call-technical-summary.json oraz prywatne archiwum sesji. owner-observation.json zachowuje wcześniejszy stan sprzed odczytu; aktualne wyniki techniczne są w osobnych receipts. Bez numerów, transkryptów lub WAV w repozytorium. Po eksporcie zatrzymano wyłącznie obserwator Maca. Polityka AI pozostaje przyjęta; nie wyłączano jej automatycznie po informacji właściciela o zakończeniu rozmowy.

## Scenariusze kolejnych prób

Każdy scenariusz jest osobną oceną; nie wymaga powtarzania poprzedniego testu ani ograniczenia liczby tur.

Do pełnego dialogu wiadomości: rozmówca prosi o przyjęcie wiadomości; na pytanie o sprawę podaje fikcyjny serwis rowerowy i gotowy kosztorys; prosi o kontakt jutro po szesnastej, następnie zmienia termin na piątek po osiemnastej; prosi o powtórzenie; potwierdza poprawne podsumowanie i mówi „To wszystko, dziękuję, do widzenia”. Odpowiedzi modelu są swobodne, a nie zapisanym skryptem. Obecny APK nie rozłącza sam po takim zakończeniu (F-091); tę ostatnią czynność trzeba dopiero zaimplementować i sprawdzić. Samo pożegnanie w transkrypcie nie dowodzi hangup.

| Próba | Co mówi kontrolowany rozmówca | Co sprawdzamy |
|---|---|---|
| Wiadomość | „Chcę zostawić wiadomość: proszę oddzwonić jutro po szesnastej.” Następnie poprosić o powtórzenie. | Zachowanie treści i czasu w dialogu oraz archiwum; bez obietnicy wykonania telefonu lub narzędzia. |
| Korekta | „Pomyliłem się: w piątek, nie jutro. Najlepiej po osiemnastej.” | Zastąpienie poprzedniej informacji i poprawne podsumowanie. |
| Oczekiwanie | „Chwileczkę, sprawdzę szczegóły.” Po chwili kontynuować. | Zachowanie kontekstu, brak pętli ACK i niezamówionego zakończenia. |
| Przejęcie | W trakcie dialogu właściciel używa „Przejmij rozmowę — zatrzymaj AI” na S22 i mówi sam. | To samo aktywne połączenie, głos użytkownika, brak późnego AI/TX; osobno jawny powrót AI. |

To scenariusze sekretarki przyjmującej wiadomości, bez rzeczywistych zobowiązań. Po odczycie danych ocena jakości powinna połączyć słyszalność drugiej strony, transkrypt, czasy i rzeczywisty stan połączenia.

## Dwie kolejne rozmowy — odczyt i ocena transkryptu

Po zgłoszeniu kolejnych prób i ponownym podłączeniu S22 wyeksportowano dwie sesje nowsze od pierwszego testu, bez ponawiania rozmów i bez zmiany APK lub ustawień. Krótsza próba trwała 63 151 ms, miała 2 tury modelu oraz powitanie; model dopytywał o temat niepełnej wiadomości. Najnowsza próba trwała **143 414 ms**, miała **6 rozpoznań STT, 6 decyzji INCOMING_MODEL_DIRECT i 7 pełnych WRITE_COMPLETE** wraz z powitaniem. Wszystkie decyzje mają local_error_present=false; wszystkie główne TX mają accepted=requested. Recorder COMPLETE, bez błędów zapisu, 20 poprawnych WAV mono PCM16 16 kHz. Archiwum SHA-256 `93d8ded72cf1d1362c595ef3c486f1bcb3b2214b9f5ee149e4f6868203f68e5f`.

Ocena treści najnowszej próby: zachowany temat wiadomości i prośba o kontakt, prawidłowe zastąpienie poprzedniego terminu nowym, właściwe podsumowanie i reakcja pożegnaniem po potwierdzeniu. W STT zmieniony termin jest dokładną godziną w piątek, więc model powtarza dokładną godzinę; to odczyt transkryptu, bez niezależnego potwierdzenia rozpoznania względem WAV. Do oceny stylu: powtórzone przedstawienie się, męska forma po żeńskim powitaniu i literalne Pan/Pani. Krótkie ACK to stałe teksty aplikacji, odrębne od generacji Gemmy.

F-091 ma teraz także dowód fizyczny: po pożegnaniu oraz jawnym żądaniu zakończenia model potwierdza koniec, ale po pełnym TX nadal uruchamiany jest RX kolejnej tury 7. RX_INTERRUPTED następuje dopiero przy zakończeniu Call. Nie jest to skuteczne automatyczne rozłączenie. F-089 nadal dotyczy etykiety operator_stopped/OPERATOR_STOP w końcowej diagnostyce. Nie deklarować wykonania zewnętrznego zadania, narzędzia lub powiadomienia na podstawie wypowiedzi modelu.

EOS→pełny TX tur 1–6: 14 258 / 6926 / 4781 / 5848 / 5321 / 3576 ms. Postcheck: 2× IDLE, kampania nieaktywna, retry false, niezmieniony Android STT/E2B/Android TTS, AI nadal włączone, 36 sesji. Przejęcia, powrotu AI, STOP ani pełnego nagrywania F-080 nie testowano w tym zakresie.

Prywatne pliki: `~/Documents/AI-Calls/local-codex/2026-11-06-transcript-review/` (0700, pliki 0600): oba archiwa i timeline, czytelne transkrypty Markdown, latest-runtime-report.txt, latest-technical-summary.json oraz review-manifest.json. Treść rozmowy i audio pozostają poza Git. To odczyt istniejącego testu, bez nowej instrumentacji lub powtarzania host QA.
