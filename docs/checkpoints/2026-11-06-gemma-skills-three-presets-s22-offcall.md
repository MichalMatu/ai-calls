# AI Calls — fizyczna matryca trzech presetów na S22 (2026-11-06)

Źródło APK: e8db8db765a75990f7c60da3145ddbcf4d6e17b5.
S22 SM-S906B / Android API 36, po teście IDLE.
Model rzeczywiście wybrany w profilu: **GEMMA_4_E2B**.
Identyczny korpus SkillLabCorpus: 4 syntetyczne historie i 12 tur na preset.
Nowy kontekst modelu dla każdej historii, zachowana wieloturowość wewnątrz historii.

## Pomiary natywne na S22

| Preset | END_CALL poprawny | Za wcześnie | Brak końca | Pytania: heur. | Generacja średnia | Inicjalizacja |
|---|---:|---:|---:|---:|---:|---:|
| natural | 12/12 | 0 | 0 | 3 | 7029 ms | 8 ms |
| formal | 12/12 | 0 | 0 | 3 | 11012 ms | 8 ms |
| message | 12/12 | 0 | 0 | 3 | 12361 ms | 18 ms |

Trafność dotyczy propozycji END_CALL wobec oznaczonych oczekiwań;
nie wykonano żadnego narzędzia ani połączenia. Liczba pytań jest tylko
heurystyką znaku zapytania. Średni czas obejmuje generację, a czas inicjalizacji
jest mierzony osobno. **Naturalność: NIEOCENIONA ręcznie** — bez udawanej
automatycznej oceny subiektywnej. Wynik jednej rundy nie dowodzi
statystycznej przewagi presetu ani jakości rozmowy na żywo.

## Zapis, UI i rollback

Trzy testy AndroidSkillPresetMatrixTest zakończone OK (3 tests):
1. Oddzielny szyfrowany magazyn na telefonie — kopiowanie fabrycznego
   presetu, edycja nowej wersji, trwały odczyt, wyłączenie skilla,
   przywrócenie i historia wersji.
2. Uruchamianie ekranów Skills i Laboratorium bez modyfikacji
   aktywnego presetu użytkownika.
3. Wszystkie trzy presety na tym samym lokalnym modelu/korpusie.

Test nie wykonywał pełnej sekwencji dotykowych interakcji edytora.
Prywatny plik wynikowy w cache aplikacji zawiera wyłącznie metadane
syntetyczne; nie opublikowano transkryptów ani danych użytkownika.

## Pochodzenie i ograniczenia

Mac Local Agent: aicalls-20261106-skills-matrix-test-source-v1
(kompilacja PASS), aicalls-20261106-skills-matrix-s22-run-v1
(Android instrument OK 3/3). Pierwszy wrapper zwrócił FAILED wyłącznie
dlatego, że błędnie potraktował INSTRUMENTATION_CODE: -1 jako porażkę.
Kod ten oznacza RESULT_OK; niniejszy sprint zweryfikował prywatny plik
z metrykami bez ponowienia 36 inferencji.

Bez rzeczywistych połączeń, bez STT/TTS, bez wykonania END_CALL.
F-091 live Call i manual/TAKE OVER/STOP nadal QA_PENDING.
Nie zmieniono zamrożonego toru Samsung RX/TX.
