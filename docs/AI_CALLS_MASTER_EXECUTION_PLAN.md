# AI Calls — jeden plan realizacji: od modelu do autonomicznej rozmowy
Data decyzji: 2026-11-09. Właściciel zaakceptował, że **model językowy jest głównym prowadzącym rozmowę**, a Agent Skills dostarczają mu kompetencje, pamięć roboczą i typowane propozycje narzędzi. Aplikacja, nie model ani Skill, egzekwuje autoryzację, ujawnianie faktów, własność Call, STOP i skutki zewnętrzne.

## Zasada nadrzędna
Jeden lokalny agent i wspólny runtime dla przychodzących i wychodzących. Silnik LLM jest wymienny; Skills nie są drugim „mózgiem” ani drugim runnerem. Laboratorium mierzy komponenty i pełne dialogi, produkt składa się wyłącznie z potwierdzonych części. Nie mnożyć modeli, ustawień ani scenariuszy zanim bieżąca bramka nie jest zamknięta.

**Kolejność bez przeskakiwania:** G0 F-158 fizyczny punkt odniesienia → G1 kompatybilność Qwen 3.5 2B → G2 jego natywny smoke → G3 uczciwe A/B z E2B → decyzja o domyślnym modelu → G4 Agent Skills dynamiczne → G5 pełny dialog → G6 telefonia end-to-end → G7 porządkowanie UX i odbiór. Po G0 następną *nową* pracą jest właśnie Gemma 4 E2B kontra Qwen 3.5 2B.

## G0 — F-158 (S22_TECHNICAL_PASS)
- DONE: Mac `aicalls-f158-contextual-skills-host-qa-20261109-v1` = done/0; celowane JVM, pełny `verify_host.sh` oraz anti-friction PASS na implementacji selektora `a3b0fba`. Nowszy AndroidTest `f6f0baf` nie był częścią tej bramki.
- DONE: `aicalls-g0-f158-s22-20261109-v1` — Mac AndroidTest compile PASS, S22 idle, 24/24 techniczne tury PASS, 12 rekordów + 4 podsumowania; bez połączeń i zmian profilu. Szersza semantyka nadal OPEN.
- Gate: osobno `TECHNICAL_PASS` (kontrakt, narzędzia, liczniki) i `SEMANTIC_REVIEW` (czy Skill rzeczywiście pomaga w rozmowie). Nie awansować selektora do produkcyjnego Call na podstawie samego AndroidTest.

## G1 — Qwen 3.5 2B: zgodność i ryzyko sprzętowe (SOURCE_METADATA_PASS)
- Wejście: potwierdzenie F-158 lub jawny blocker G0 z osobnym śledzeniem; brak powtórki już zaliczonych F-155/F-157.
- Obecny Android używa LiteRT-LM `0.19.0`, katalog obsługuje E2B/E4B oraz starsze Qwen3 1.7B/0.6B, ale **nie** Qwen3.5 2B. Istniejący backend powinien być najpierw oceniony pod kątem architektury Qwen3.5 (hybrid GatedDeltaNet), tokenizer/chat template, stan rekurrencyjny, format odpowiedzi i Android/JNI.
- Pin zweryfikowany bez pobierania: `litert-community/Qwen3.5-2B@f24013ba6844bd39be2d1f68e8d2421a77eed1f2` / `Qwen3.5-2B_int8.litertlm`, **2,119,843,840 B**, SHA-256 `c2438cb9c050220e667366b4beeca4daac56e693b226420674f53fc40f007c49`, Apache-2.0. Wymagana osobna inicjalizacja Engine dla nowej konwersacji; telefon SM-S906B s5e9925, RAM 7.44M KiB total/2.85M KiB available podczas preflight. Ze względu na Mali i ryzyko OOM: **CPU-only na G2**.
- Gate: `FEASIBLE` albo konkretny `BLOCKED` (GPU, RAM, runtime, model, magazyn). Na S22 żadnego cichego fallbacku GPU↔CPU: mierzyć rzeczywisty backend. Nie zmieniać produkcyjnego profilu, nie pobierać niezweryfikowanych plików.

## G2 — pierwsze uruchomienie Qwen 3.5 2B (LAB_ONLY)
- Włączyć przez **istniejący** `DialogueModelId` / `LanguageModelCatalog` / `DialogueEngineProvider`, bez równoległego magazynu, nowego runnera lub zmiany domyślnego E2B.
- Jedno- i wieloturowy natywny smoke na S22, 2048 kontekstu na wspólny start, poprawność PL/JSON, fakt-korekta, neutralne zapytanie, `tool=null`, nowa konwersacja bez przecieku stanu, release zasobów, cold/warm i RAM/PSS; porażka znaczy `BLOCKED`, nie promowanie modelu.
- Gate: rzeczywisty `S22_NATIVE_SMOKE_PASS`; brak fizycznego odbioru = `NOT_RUN`.

## G3 — porównanie Gemma 4 E2B vs Qwen 3.5 2B (DECISION)
- Identyczne S22, instrukcje, Skills, format, kontekst, budżet odpowiedzi i korpus. Tryb Skills zamrozić po G0; **nie mieszać jednocześnie zmiany modelu i strategii Skills**.
- Co najmniej 8 scenariuszy PL × 3 niezależne powtórzenia × 2 modele, po minimum 2 tury: intencja, pytania uzupełniające, pamięć, sprostowanie, sprzeczność, odmowa ujawnienia faktów, wybór/propozycja narzędzia, naturalne zakończenie. Zróżnicować kolejność, oddzielić cold i warm; po każdej nowej konwersacji resetować rzeczywisty stan.
- Miary: błędy schematu/narzędzi i naruszenia ograniczeń (dyskwalifikujące); ocena semantyczna i naturalność na czytanym dialogu; skuteczność pamięci i korekt; mediany i p95 cold/warm (nie mieszać), rzeczywisty GPU/CPU, RAM/PSS, błędy procesu. Wyniki szczegółowe prywatne; w Git tylko zagregowane metryki bez treści rozmów.
- Decyzja `KEEP_E2B` / `PROMOTE_QWEN` / `INSUFFICIENT_EVIDENCE`: Qwen ma sens tylko jeśli wyraźnie poprawi jakość bez utraty niezawodności i nieakceptowalnego opóźnienia. Brak automatycznej promocji na podstawie liczby parametrów lub jednego testu. E2B pozostaje domyślny do udokumentowanej decyzji.

## G4 — Agent Skills reagujące na rozmowę
- Metadata `name/description/capability` z istniejącym wersjonowaniem i edytorem; styl/zasady stałe, umiejętności wybierane na podstawie aktualnej intencji i stanu dialogu, nie wyłącznie pierwszej wypowiedzi. Najpierw protokół przejść między turami i ekonomia kontekstu LiteRT.
- Propozycja narzędzia przez model → walidacja przez aplikację → wykonanie wyłącznie w grancie → wynik do konwersacji. Żadnych dowolnych pobranych skryptów ani rozszerzania uprawnień przez prompt.
- Gate: testy aktywacji, dezaktywacji, zmiany intencji, pamięci, rollback wersji, bez regresji START/STOP i bez dostępu do niedozwolonych danych; fizyczne testy S22.

## G5 — jeden dobry agent tekstowo-głosowy
- Pełne długie rozmowy (nie tylko JSON/markery): STT → model + Skills → zatwierdzenie → TTS; przerwania, dopytanie, korekta faktów, powrót do celu, zakończenie i raport. Rozdzielić opóźnienie silnika od pełnego EOS→TX.
- Gate: ocena rozmowy jako całości, nie tylko suma testów komponentów; blokery `F-045`, `F-048`, `F-080`, `F-148` weryfikować wg aktualnego BLOCKERS, bez ich fikcyjnego zamykania.

## G6 — dwa kompletne scenariusze telefoniczne
- Najpierw INCOMING: odbiór, rozmowa, przyjęcie i potwierdzenie wiadomości, zapis, poprawne zakończenie, raport.
- Następnie OUTGOING: polecenie właściciela, autoryzowany cel i dane, samodzielne wybieranie, rozmowa, proponowany skutek → osobny dowód → raport. Tylko dokładnie autoryzowane rzeczywiste połączenia; zachować zamrożoną ścieżkę Samsung/Shizuku, STOP i takeover.
- Gate: prawdziwe audio/obserwowalny efekt i odrębna weryfikacja rezultatu. Test bez Call nie jest odbiorem Call.

## G7 — uproszczenie produktu
Jedno zrozumiałe wejście dla użytkownika i zaawansowane Lab osobno; bez mnożenia opcji, dopóki nie mają testu i jasnego działania. Wspólny APK docelowy, kompletna dokumentacja i checkpoint rzeczywiście zamkniętych bramek.

## Reguła wykonawcza każdego sprintu
1. Odczytaj aktualny `main`, ten plan, handoff i dokładny ostatni receipt. Wybierz **pierwszą niezamkniętą bramkę**; nie cofaj się do wcześniejszych PASS bez nowej przyczyny.
2. Zrób minimalną zmianę w istniejącym module → test kodu Mac → test fizyczny S22 tylko jeśli potrzebny i bez konfliktu urządzenia. Jeden kierunek zmian na sprint; bez refaktoryzacji niezwiązanej z celem.
3. Zanotuj w jednym checkpointcie: HEAD, rzeczywisty wynik, status bramki, blokery, następne jedno zadanie. Zaktualizuj handoff/roadmapę, bez rozjeżdżających się równoległych planów.
4. Bez Codex, Work i GitHub Actions. GitHub Connector + wskazany Mac Local Agent, `main`, brak nowych branchy/force-push, commity `[skip ci]`. Dane i nagrania wyłącznie prywatnie, użytkownik nie ma ręcznie przepisywać logów.
5. Statusy: `PLANNED`, `ACTIVE`, `MAC_PASS`, `S22_PASS`, `BLOCKED`, `DECIDED`. Nie uznawać `PLANNED` lub braku wyników za PASS.

## Własność decyzji
Model prowadzi rozmowę; Skills są kompetencjami i narzędziami; kod aplikacji pozostaje jedyną władzą nad połączeniami, ujawnianiem i efektami. Zmianę domyślnego modelu wolno wprowadzić dopiero po G3.
