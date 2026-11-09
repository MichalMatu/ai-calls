# AI Calls — jeden plan realizacji: od modelu do autonomicznej rozmowy
Data decyzji: 2026-11-09. Właściciel zaakceptował, że **model językowy jest głównym prowadzącym rozmowę**, a Agent Skills dostarczają mu kompetencje, pamięć roboczą i typowane propozycje narzędzi. Aplikacja, nie model ani Skill, egzekwuje autoryzację, ujawnianie faktów, własność Call, STOP i skutki zewnętrzne.

## Zasada nadrzędna
Jeden lokalny agent i wspólny runtime dla przychodzących i wychodzących. Silnik LLM jest wymienny; Skills nie są drugim „mózgiem” ani drugim runnerem. Laboratorium mierzy komponenty i pełne dialogi, produkt składa się wyłącznie z potwierdzonych części. Nie mnożyć modeli, ustawień ani scenariuszy zanim bieżąca bramka nie jest zamknięta.

**Kolejność bez przeskakiwania:** G0 F-158 fizyczny punkt odniesienia → G1 kompatybilność Qwen 3.5 2B → G2 jego natywny smoke → G3 uczciwe A/B z E2B → decyzja o domyślnym modelu → G4 Agent Skills dynamiczne → G5 pełny dialog → G6 telefonia end-to-end → G7 porządkowanie UX i odbiór. Po G0 następną *nową* pracą jest właśnie Gemma 4 E2B kontra Qwen 3.5 2B.

## G0 — F-158 (S22_TECHNICAL_PASS)
- DONE: Mac `aicalls-f158-contextual-skills-host-qa-20261109-v1` = done/0; celowane JVM, pełny `verify_host.sh` oraz anti-friction PASS na implementacji selektora `a3b0fba`. Nowszy AndroidTest `f6f0baf` nie był częścią tej bramki.
- DONE: `aicalls-g0-f158-s22-20261109-v1` — Mac AndroidTest compile PASS, S22 idle, 24/24 techniczne tury PASS, 12 rekordów + 4 podsumowania; bez połączeń i zmian profilu. Szersza semantyka nadal OPEN.
- Gate: osobno `TECHNICAL_PASS` (kontrakt, narzędzia, liczniki) i `SEMANTIC_REVIEW` (czy Skill rzeczywiście pomaga w rozmowie). Nie awansować selektora do produkcyjnego Call na podstawie samego AndroidTest.

## G1 — Qwen 3.5 2B: zgodność i ryzyko sprzętowe (SOURCE_METADATA_PASS + verified pinned download)
- Wejście: potwierdzenie F-158 lub jawny blocker G0 z osobnym śledzeniem; brak powtórki już zaliczonych F-155/F-157.
- Obecny Android używa LiteRT-LM `0.19.0`. Przed G2 katalog zawierał E2B/E4B i Qwen3 1.7B/0.6B; teraz zawiera również przypiętego Qwen3.5 2B tylko dla Lab. Istniejący backend został oceniony pod kątem architektury Qwen3.5 (hybrid GatedDeltaNet), tokenizer/chat template, stan rekurrencyjny, format odpowiedzi i Android/JNI.
- Pin zweryfikowany bez pobierania: `litert-community/Qwen3.5-2B@f24013ba6844bd39be2d1f68e8d2421a77eed1f2` / `Qwen3.5-2B_int8.litertlm`, **2,119,843,840 B**, SHA-256 `c2438cb9c050220e667366b4beeca4daac56e693b226420674f53fc40f007c49`, Apache-2.0. Wymagana osobna inicjalizacja Engine dla nowej konwersacji; telefon SM-S906B s5e9925, RAM 7.44M KiB total/2.85M KiB available podczas preflight. Ze względu na Mali i ryzyko OOM: **CPU-only na G2**.
- Gate: `FEASIBLE` albo konkretny `BLOCKED` (GPU, RAM, runtime, model, magazyn). Na S22 żadnego cichego fallbacku GPU↔CPU: mierzyć rzeczywisty backend. Nie zmieniać produkcyjnego profilu, nie pobierać niezweryfikowanych plików.

## G2 — pierwsze uruchomienie Qwen 3.5 2B (S22_CPU_SMOKE_PASS; LAB_ONLY)
- Włączyć przez **istniejący** `DialogueModelId` / `LanguageModelCatalog` / `DialogueEngineProvider`, bez równoległego magazynu, nowego runnera lub zmiany domyślnego E2B.
- Jedno- i wieloturowy natywny smoke na S22, 2048 kontekstu na wspólny start, poprawność PL/JSON, fakt-korekta, neutralne zapytanie, `tool=null`, nowa konwersacja bez przecieku stanu, release zasobów, cold/warm i RAM/PSS; porażka znaczy `BLOCKED`, nie promowanie modelu.
- Gate: rzeczywisty `S22_NATIVE_SMOKE_PASS`; brak fizycznego odbioru = `NOT_RUN`.

## G3 — porównanie Gemma 4 E2B vs Qwen 3.5 2B (DECIDED_KEEP_E2B; MODEL_SELECTION_CLOSED)
- Identyczne S22, instrukcje, Skills, format, kontekst, budżet odpowiedzi i korpus. Tryb Skills zamrozić po G0; **nie mieszać jednocześnie zmiany modelu i strategii Skills**.
- Co najmniej 8 scenariuszy PL × 3 niezależne powtórzenia × 2 modele, po minimum 2 tury: intencja, pytania uzupełniające, pamięć, sprostowanie, sprzeczność, odmowa ujawnienia faktów, wybór/propozycja narzędzia, naturalne zakończenie. Zróżnicować kolejność, oddzielić cold i warm; po każdej nowej konwersacji resetować rzeczywisty stan.
- Miary: błędy schematu/narzędzi i naruszenia ograniczeń (dyskwalifikujące); ocena semantyczna i naturalność na czytanym dialogu; skuteczność pamięci i korekt; mediany i p95 cold/warm (nie mieszać), rzeczywisty GPU/CPU, RAM/PSS, błędy procesu. Wyniki szczegółowe prywatne; w Git tylko zagregowane metryki bez treści rozmów.
- Decyzja `KEEP_E2B` / `PROMOTE_QWEN` / `INSUFFICIENT_EVIDENCE`: Qwen ma sens tylko jeśli wyraźnie poprawi jakość bez utraty niezawodności i nieakceptowalnego opóźnienia. Brak automatycznej promocji na podstawie liczby parametrów lub jednego testu. E2B pozostaje domyślny do udokumentowanej decyzji.


### Aktualizacja wyników G2/G3 — 2026-11-09

- **G2 DONE/0:** Qwen3.5 CPU 3/3 syntetyczne tury S22, SHA import i 2 niezależne backendy PASS, Mac full gate PASS. Nie zmieniać zwykłego Call/E2B.
- **G3 S22 TECH_PASS:** `aicalls-g3-e2b-qwen35-96turns-s22-20261109-v1` komendy 1–3 PASS (latest build, 96 tur, 48 par, 2 summary, prywatny eksport na Mac); E2B/GPU 24/24 native/JSON/tools, content hints 15/15, cold/warm mediany 24,20/4,73 s; Qwen3.5/CPU 24/24, 12/15, 49,19/7,34 s. `low_memory=false` po każdej grupie. Skrót nie stanowi human semantic review ani porównania tego samego akceleratora.
- **Formalny task `failed` = tylko postflight `task_budget_exhausted`:** po udanym 96-turn AndroidTest etap 4 `git status` nie wystartował; końcowa czystość Mac i analiza prywatnej semanty **OPEN**, nie powtarzać 96 tur.
- **Dalsze działania:** [kompletny checkpoint](checkpoints/2026-11-09-g3-e2b-qwen35-pending-host-receipt-handoff.md) → read-only Mac postflight/JSONL 48 → human review → decyzja `KEEP_E2B`/`PROMOTE_QWEN`/`INSUFFICIENT_EVIDENCE` → **dopiero G4**. E2B default do decyzji.

### Decyzja G3 — 2026-11-09

**G3 CLOSED — `DECIDED_KEEP_E2B` (decyzja właściciela 2026-11-09).** Mac+S22: 96/96 natywnych tur, 48/48 prywatnych par, backend E2B GPU/Qwen CPU, 24/24 JSON/tools na model; ostateczny Mac clean postflight i integralność prywatnego pliku **DONE/0**. Lokalny przegląd **wszystkich 96 syntetycznych odpowiedzi** regułowym audytem `aicalls-g3-private-synthetic-semantic-rubric-20261109-v2` **DONE/0**: E2B **150/150**, Qwen **141/150** punktowych kontroli. Qwen: 3× brak tekstowej wzmianki o wtorku i 3× godziny 10 w drugiej turze wyjaśnienia oraz 3× identyczna odpowiedź w obu turach prywatności. Ten ostatni sygnał nie dowodzi naruszenia prywatności. Pominięta reguła z v1 (`wtork`) została naprawiona; v2 jest obowiązującym wynikiem. **Naturalność oceniona przez człowieka nadal UNASSESSED**, przeniesiona do G5. E2B pozostaje głównym modelem; Qwen lab-only, bez zmiany kodu telefonii. [Finalny checkpoint](checkpoints/2026-11-09-g3-keep-e2b-final-decision.md).

**Nie cofać wyboru modelu z powodu niezakończonej ludzkiej oceny naturalności**: ten wymóg pozostaje odrębną bramką G5, a nie otwartą decyzją E2B/Qwen. Żadnego automatycznego awansu Qwen. Pierwsza niezamknięta bramka modelowego planu to **G4.1**.

## G4 — Agent Skills reagujące na rozmowę (G4.1 MAC_PASS, G4.2 S22_E2E_SCOPED_PASS; G4.3 OPEN)

**G4.2 fizyczny E2E na S22: PASS (2026-11-09).** `G4.1` kontrakt JVM i Mac full PASS; `G4.2` rzeczywisty `SkillLabRunner` → `AndroidSkillLabSessionFactory` → `DynamicSkillLabBackend` → E2B/GPU, **4/4 natywne tury, 3 przełączenia aktywnego zestawu Skills, fikcyjny kod zachowany przed/po zmianie tematu, STOP PASS, profil i Call IDLE bez zmian**. Ostateczny task `aicalls-f157-g4p2-e2b-memory-s22-e2e-retest-20261109-v4` **DONE/0**: Mac 371 Python, Gradle/JVM/lint/APK/AndroidTest/audio i anti-friction PASS, S22 instrument 1/1 PASS, oba debug APK bez wipe i końcowe Git clean. Poprzedni fizyczny RED pamięci `F-157` naprawiony, historyczne FAIL retained. Tylko syntetyczne **off-call tekstowe E2E** — nie pełne STT/TTS, human naturalness ani rzeczywisty Call. Gemma E2B pozostaje głównym modelem. [Kompletny checkpoint](checkpoints/2026-11-09-g4-dynamic-skills-e2e-final.md).

**Następny pojedynczy zakres G4.3:** wersjonowane metadane/capability, widoczny per-turn dziennik wyboru Skills i typowane propozycje narzędzi z wyraźnym zatwierdzaniem przez aplikację. Bez Call effects i bez nieuwierzytelnionego wykonania skryptu; osobne targeted JVM + Mac + S22 przy realnej zmianie ścieżki modelu.

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
