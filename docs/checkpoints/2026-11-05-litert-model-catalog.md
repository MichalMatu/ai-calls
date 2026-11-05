# LiteRT-LM 0.19.0 i katalog modeli — 2026-11-05

Wykonanie: lokalny Codex, bezpośrednio Git/Gradle/USB/ADB. Bez Local Agent tasków, Work i GitHub Actions. Zakres: runtime tekstowy, instalacja modeli i UI; bez zmian Samsung/Shizuku, autoryzacji, STOP, ujawnień ani skutków zewnętrznych.

## Zmiana

- LiteRT-LM Android 0.17.1 → stabilne 0.19.0, [oficjalne wydanie](https://github.com/google-ai-edge/LiteRT-LM/releases/tag/v0.19.0).
- Jeden `LanguageModelCatalog`: Gemma 4 E2B, Gemma 4 E4B, Qwen3 1.7B i Qwen3 0.6B. Identyfikatory, przypięte rewizje, rozmiary i SHA-256 są własnością aplikacji; metadane pobranych plików opisuje [cache](2026-11-05-model-candidate-cache.md).
- `LiteRtDialogueEngineProvider` przekazuje wybrany model do wspólnego istniejącego runtime; brak zamiany niedostępnego modelu na E2B. Qwen wyłącza myślenie przez parametr szablonu; aplikacyjna walidacja/zgoda na wypowiedź i schemat JSON pozostają obowiązujące.
- Preferencja modelu pozostaje w `AudioEngineProfileStore`; wejście, wyjście i konkretny głos są niezależne. Aktywna rozmowa zachowuje swój snapshot; zmiana modelu dotyczy następnej rozmowy.
- Gemma Audio wskazuje jawnie E2B, niezależnie od wybranego modelu tekstowego. M4 nadal nie jest pełnym backendem rozmowy audio.

## Ekran i instalacja

`Modele językowe` na ekranie głównym albo `Ustawienia silników audio AI → Modele: pobierz, importuj, wybierz`.

Każdy wiersz pokazuje rozmiar, źródło/licencję, obecność i wybór. „Pobierz” pobiera przypiętą wersję z Hugging Face; „Importuj” otwiera systemowy picker plików. Obie ścieżki sprawdzają rozmiar i SHA-256 przed atomowym przeniesieniem. „Użyj” zapisuje model na następną rozmowę, zachowując STT/TTS/głos. E2B z dotychczasowej instalacji pozostaje ważny i nie jest pobierany ponownie.

Jeden nieeksportowany serwis foreground `dataSync` obsługuje transfer, powiadomienie, postęp i anulowanie. Operacje są serializowane; obrót/zamknięcie ekranu nie przerywa transferu. Nie pozostawiono starego, równoległego interfejsu pobierania E2B na ekranie głównym. Runtime native jest częścią APK — aktualizacja biblioteki wymaga aktualizacji aplikacji, pliki modeli instaluje się z katalogu.

## Dowody

- RED: poprzedni provider odrzucał E4B; nowy test wszystkich czterech wyborów FAIL przed implementacją.
- Host: 765 JVM (704 app + 51 realtime + 10 helper), 264 Python, lint wszystkich modułów, build obu APK i audyty PASS. F-058 dokumentuje pierwszy przerwany lint; powtórka bez równoległych edycji PASS.
- S22: instalacja APK z identycznym podpisem i zachowaniem danych; grant/skills/audio fingerprints bez zmian.
- S22: E4B i Qwen3 1.7B zaimportowane przez aplikacyjny serwis, pełne sprawdzenie SHA-256 PASS (15,2 / 5,4 s z narzutem instrumentacji).
- S22: Qwen3 0.6B pobrany rzeczywiście przyciskiem katalogu, SHA-256 PASS, odtworzenie Activity w trakcie transferu PASS (14,4 s z narzutem instrumentacji).
- S22: E2B / E4B / Qwen3 1.7B — osobno polska odpowiedź i ograniczona akcja JSON PASS (31,4 / 65,6 / 33,5 s łącznie z inicjalizacją obu backendów i instrumentacją). Nie są to czasy odpowiedzi w rozgrzanej rozmowie.
- S22: wybór wszystkich zainstalowanych modeli z zachowaniem STT/TTS/głosu i po odtworzeniu ekranu PASS; osobna przestrzeń preferencji i menu 11 wyborów PASS; accordion ekranu głównego PASS.
- F-060: RED powrotu z katalogu ujawnił nieodświeżony model w ustawieniach. Minimalna poprawka odświeża tylko zmieniony model, zachowując niezapisane STT/TTS. Ten sam test S22 GREEN; opis na ekranie głównym również odświeżony.
- Regresja Gemma Audio i Parakeet na polskich próbkach syntetycznych S22 PASS (38,0 / 7,8 s z instrumentacją).
- Starsza paczka Qwen3 0.6B `e6bb3e4a` nie zdała polskiej odpowiedzi (tautologia zamiast Warszawy) ani kompletności JSON. FAIL zachowany. Po podniesieniu runtime katalog wskazuje aktualny eksport dla GPU 0.19.0: `f3f204cecab53892b096ebd8b3b931f8612c6bd3`, 343490560 B, SHA-256 `bf1aba9ce8d963124407353c6aa4cbf72eba78db21ae133de983eac672ce83e0`; [karta upstream](https://huggingface.co/litert-community/Qwen3-0.6B). Nowa paczka pobrana w aplikacji i SHA-256 PASS (13,6 s); GPU generuje, lecz ten sam błąd polskiej odpowiedzi i niedomknięty JSON nadal FAIL. F-059 pozostaje OPEN dla tego wariantu; mały model pozostaje eksperymentalny, bez cichej zamiany na E2B i bez obchodzenia walidacji JSON. Dla trzech pozostałych modeli testy są PASS.

Prywatne receipts, APK i wyniki instrumentacji: `~/Documents/AI-Calls/local-codex/2026-11-05-model-manager/`. Modelowe pliki importowe nie trafiają do Git. Ten zakres nie stanowi dowodu wykonania CLIR ani rozwiązania Piper F-057.


## Końcowy stan

Telefon 2× IDLE, kampania nieaktywna, retry false. Profil Android STT/E2B/Android TTS; grant/skills/audio fingerprints identyczne z początkiem. Bez nowych połączeń w tym zakresie. Własne kopie źródłowe importów z katalogu `model-import/` usunięte po weryfikacji; aktywne modele i cache GPU pozostawione. Prywatne receipts mają katalog 0700 / pliki 0600. Gałęzie main + agent-control.

Zainstalowane końcowe APK (SHA-256):

- `app/build/outputs/apk/debug/app-debug.apk`: `7b7ecec354ebf48d401d7380031750803bb51492a24ee40e7e48f50b9d72f58c`
- `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk`: `1bf3837608aa759d6ef999dec0dbb53f881b4102aedc26036e6aff53324b3c27`
