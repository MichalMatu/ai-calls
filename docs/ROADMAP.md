# AI Calls — aktualna roadmapa 2026-11-05

**Bieżący kierunek — decyzja właściciela 2026-11-05:** aplikacja testowa do selekcji modułów, z których później powstanie docelowy flow. [Zakres, ocena i instalacja](TEST_LAB_SCOPE.md). Silniki mają interfejsy, ale ogólny scenariusz i jeden preset próby wymagają uporządkowania. Orange jest jedną warstwą testów. Wspólny instalator jest propozycją do wdrożenia, nie gotową funkcją.

**Ostatni zakończony zakres:** przyjmowanie zlecenia Codexa bez osobnego grantu/TTL/kliknięcia na telefonie, audyt powtórnych zgód i jawny SCRIPT/Gemma Direct. [Stan i dowody](checkpoints/2026-11-05-task-intake-and-dialogue-ab.md); F-062/F-065/F-066/F-067. Zakres zgód VERIFIED: 768 JVM + 266 Python, lint/oba APK/audyty i 5/5 testów native PASS; rzeczywisty start Direct z Codexa bez mobilnego kliknięcia, 7 pełnych tur. Para A/B E2B zebrana; pełna macierz 6 profili nadal nieukończona. Pętla F-061 pozostaje OPEN; porównanie modeli wymaga rozdzielenia tekstu skryptu od RAW i postępu zadania.

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

## Następna kolejność

1. **Konfiguracja próby i scenariusz:** scalić istniejące wybory STT/model/strategia/instrukcje/TTS w snapshot, wydzielić CLIR jako scenariusz wspólnego silnika. Jeden działający dodatkowy scenariusz zamiast równoległego runnera i nieużywanych abstrakcji.
2. **Powtarzalna ocena komponentów:** korpus tekstu dla dialogu i modeli, wspólny korpus audio dla STT, wspólny tekst dla TTS. Porównać skrypt/Direct na kontrolowanych danych. Zachować wcześniejsze RED i fizyczne dowody; nie powtarzać historycznego odrzuconego probe.
3. **Kontrolowana integracja:** IVR/konsultant testowy oraz własna końcówka telefoniczna z odsłuchem RX/TX po drugiej stronie; potem Orange i inne konkretnie zlecone cele. Sukces zadania oceniać osobno od liczników przepływu.
4. **Jedna ścieżka przygotowania:** pakiet instalacyjny Mac/ADB dla AI Calls + rzeczywiście potrzebnych zależności, później wspólny ekran przygotowania. Gallery nie jest wymaganiem; modele pozostają we własnym katalogu. Nie zmieniać zamrożonych mediów przy pracach nad instalacją.
5. **Rozwój adapterów według wyników:** Piper F-057 wymaga poprawki kontraktu silnika; M4 pełnego backendu rozmowy audio. F-044/F-045/F-047/F-048/F-061 i pozostałe bieżące defekty odczytywać w [BLOCKERS](../BLOCKERS.md).

Wykonanie: lokalny Codex na wskazanym Macu; Git/Gradle/USB/ADB bezpośrednio. Local Agent wyłącznie dla ChatGPT, bez zleceń z Codexa. `main`, `[skip ci]`; bez Work/Actions/nowych branchy/force-push i konkurencyjnych mutacji. No outgoing calls without exact app-recorded revocable authorization.
