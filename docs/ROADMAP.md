# AI Calls — bieżąca roadmapa 2026-11-06

**Bieżący kierunek — decyzja właściciela 2026-11-05:** aplikacja testowa do selekcji modułów, z których później powstanie docelowy flow. [Zakres, ocena i instalacja](TEST_LAB_SCOPE.md). Silniki mają interfejsy, wspólny snapshot i wydzielone scenariusze oraz profile skills/danych i historię; zapisywalny preset eksperymentu i ogólny scenariusz nadal wymagają uporządkowania. Orange jest jedną warstwą testów. Docelowo jeden pakiet instalacyjny; bieżące prace dotyczą laboratorium, a nie wdrożenia instalatora.

**Uzupełniony plan produktu:** [PRODUCT_ASSEMBLY_PLAN](PRODUCT_ASSEMBLY_PLAN.md) określa składanie wspólnych bibliotek dla zadań telefonicznych i sekretarki, manual/AI/przejęcie, osobne dane profilu/zadania i rozszerzalne skillsy oraz pełne archiwum z jednym playerem. Istniejące pakiety są granicami logicznymi; kolejne funkcje mają utrwalać je jako moduły kompilacji. F-080 OPEN: pełny zapis i player nie są jeszcze zaimplementowane.

Właściciel zaakceptował zadanie obejmujące wiele rozmów, koordynację według postępu, pamięć z pochodzeniem faktów, warunki samodzielności, naturalne przekazywanie głosu i trwały stan z oddzielnymi wynikami skutków. Rozwijać istniejący model/workflow bez duplikacji; nie uzależniać pierwszego modułu INCOMING od całej przyszłej platformy call center. Szczegóły w planie produktu.

**Najnowsze QA:** [reaudyt hosta i modułów](checkpoints/2026-11-06-quality-modularity-reaudit.md): pełny host gate PASS, 266 testów Python, 6 modułów, 197/220 produkcyjnych plików Kotlin w app, 0 zerwanych linków. S22 dostępny: APK timeoutu zainstalowany, native TTS/end_call PASS, fizyczne rozłączenie po ciszy potwierdzone przez operatora, 40 sesji, IDLE, 0 błędów. Szczegółowy timeline timeoutu QA_PENDING. Manual/przejęcie/STOP osobne QA_PENDING.

**Poprzedni fizycznie sprawdzony zakres runtime:** [wspólny silnik, przygotowanie i historia](checkpoints/2026-11-05-conversation-modules.md), `e72bb15`: 785 JVM + 266 Python, lint/oba APK/audyty, 8/8 native oraz 7 pełnych tur OUTGOING i kontrolowany STOP PASS. Starsze [intake i A/B](checkpoints/2026-11-05-task-intake-and-dialogue-ab.md) zachowują swój zakres; pełna macierz profili nadal nieukończona. Pętla F-061 pozostaje OPEN. Samo techniczne RX/TX nie dowodzi jakości dialogu ani wykonania CLIR.

Obowiązujące dowody: [audio + Orange](checkpoints/2026-11-05-audio-quality-orange-acceptance.md), [handoff](HANDOFF_NEXT_CHAT.md), [BLOCKERS](../BLOCKERS.md). Wcześniejsze checkpointy są historią pomiarów.

| Etap | Stan | Kolejne kryterium |
|---|---|---|
| USB debug bridge, panel, lokalne ADB, diagnostyka błędów, profile i prywatny eksport | **DONE — Mac + S22 off-call PASS; właściciel potwierdził otwarcie panelu** | Kontynuacja lokalnym Codexem; osobne rozwijanie bez duplikatów |
| M1 — Android STT → Gemma E2B → Android TTS | **Orange: 5 pełnych tur**; powrót IDLE | Pierwsza odpowiedź 22,4 s; F-042 i efekt zadania OPEN |
| M2 — Parakeet TDT 0.6B v3 PL | **3 próbki syntetyczne: 0 błędów słów/znaków; Orange: 1 pełna tura** | Naturalny korpus, final segments, pierwsze EOS→TX 35,0 s, wspólne zasoby |
| M3 — Piper PL | **APK 1.1 + Gosia zainstalowane; native TTS FAIL** | F-057: poprawna walidacja nazw głosów w zewnętrznym silniku |
| M4 — Gemma Audio Direct | **F-056 naprawiony; 3 próbki PL: 0 błędów słów/znaków; profil disabled** | Pełny backend rozmowy audio, współpraca modeli, RAM i STOP |
| Alternatywny LLM | **LiteRT-LM 0.19.0; katalog/pobieranie/import; E2B/E4B/Qwen3 1.7B native PL+JSON PASS** | Qwen3 0.6B: błąd odpowiedzi PL (F-059), nadal eksperymentalny; joint RAM/thermal i live dla nowych modeli |
| Live effects/CLIR | **Nieudowodnione zewnętrzne wykonanie** | Dokładny grant, własny Call, ścisłe ujawnienia, niezależny dowód efektu |

## Nowy zakres wspólny dla obu kierunków

[Runtime i warstwy danych/czatu](CONVERSATION_RUNTIME.md) są wydzielone w kodzie; [QA](checkpoints/2026-11-05-conversation-modules.md). [Moduł odbierania](INCOMING_ASSISTANT.md) dostarcza adapter konkretnego przychodzącego Call i przyjętą politykę, używając tego samego runtime, przygotowania i czatu. Niezależne conversation-core i conversation-tools mają granice kompilacji JVM; podstawowy INCOMING i end_call fizycznie zaakceptowane, ale manual/przejęcie/STOP wymagają odrębnego testu. Same profile nie odbierają telefonu.

**Refaktoryzacja bez zmiany zachowania:** [SAFE_MODULARIZATION_PLAN](SAFE_MODULARIZATION_PLAN.md): etap 0 mapa i kontrakty, etap 1 przeniesienie czystego IncomingIdleTimeout do conversation-core, później małe migracje wyników i odpowiedzialności, każda z testami/rollback. Bez zmian zamrożonego Samsung/Shizuku.

## Następna kolejność

1. **Domknięcie manual/przejęcia/STOP:** podstawowy INCOMING i automatyczne end_call działają; osobno sprawdzić przekazanie Call człowiekowi, powrót AI, STOP i wznowienie dialogu po ostrzeżeniu zamiast rozłączenia. Nie ponawiać bazowego dialogu bez nowej przesłanki.
2. **Pełny transkrypt/audio i Play:** niezależny recorder całej sesji, wspólna oś czasu źródeł i wiadomości, pokrycie również podczas inferencji/TTS i po przejęciu, player całości i fragmentów czatu. F-080; dowód źródła głosu użytkownika i zachowania zamrożonej granicy mediów na S22. Stare segmenty nie stanowią dowodu pełnego pokrycia.
3. **Ogólne zadania, skillsy i narzędzia:** zapisywalne presety, własne instrukcje/skillsy i kontekst konkretnego zadania; działający scenariusz wizyty/rezerwacji oraz sekretarki na tych samych kontraktach, jednoznaczne warunki i wynik. Osobny wspólny moduł narzędzi z katalogiem/wykonawcą, ekranem konfiguracji i obserwacją wywołań; pierwszy adapter kalendarza: dostępność i zapis. Rozwijać istniejący model zadania i polityki, bez drugich magazynów i gate'ów.
4. **Powtarzalna ocena i kontrolowana telefonia:** korpus tekstu dla modeli/strategii, audio dla STT i wspólny tekst dla TTS; kontrolowany IVR/rozmówca i własna końcówka z odsłuchem drugiej strony, potem konkretnie zlecone cele. Pomiary wykonywać także przy wdrażaniu powyższych funkcji. Sukces zadania osobno od przepływu; zachować wcześniejsze RED i nie powtarzać odrzuconego probe.
5. **Składanie docelowej aplikacji:** wybrane, kwalifikowane biblioteki i jeden APK bez wymaganych aplikacji towarzyszących. Wbudowanie wybranych zależności oraz test samodzielności następują po selekcji; instalator wielu pakietów jest tylko możliwym etapem przejściowym. Rzeczywiste uprawnienia Androida pozostają wymagane.

Rozwój adapterów według wyników: Piper F-057 wymaga poprawki kontraktu silnika; M4 pełnego backendu rozmowy audio. F-044/F-045/F-047/F-048/F-061/F-080 i pozostałe bieżące defekty odczytywać w [BLOCKERS](../BLOCKERS.md). Pełne kryteria i granice nowych modułów w [planie składania produktu](PRODUCT_ASSEMBLY_PLAN.md).

Wykonanie: lokalny Codex na wskazanym Macu; Git/Gradle/USB/ADB bezpośrednio. Local Agent wyłącznie dla ChatGPT, bez zleceń z Codexa. `main`, `[skip ci]`; bez Work/Actions/nowych branchy/force-push i konkurencyjnych mutacji. No outgoing calls without exact app-recorded revocable authorization.
