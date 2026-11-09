# AI Calls — G3 final: KEEP_E2B; następny etap G4 (2026-11-09)

## Status i decyzja

**`G3=DECIDED_KEEP_E2B` — zamknięty wybór modelu na podstawie decyzji właściciela i dostępnych danych.** Nie oznacza to pełnej akceptacji semantycznej systemu do prawdziwych rozmów. G0/G1/G2 pozostają PASS, G4 dopiero START. Referencyjny HEAD źródła przed checkpointem: `main@09e6e9d6ecf20205e033b6a42633f5bc52465073`; po publikacji odczytać nowy HEAD.

**Właściciel:** „zostajemy przy Gemma E2B”. Nie usuwamy alternatywnego Qwen3.5 z Lab i nie przełączamy zwykłego profilu Call: już domyślnie jest E2B. Żaden nowy Call, instalacja, modele GPU Qwen ani eksperymenty audio nie są autoryzowane tą decyzją.

## Twarde dowody — niewymagające ponawiania

| Etap / miara | E2B (GPU) | Qwen 3.5 2B (CPU) |
| --- | ---: | ---: |
| G3 native 24 sesje ×2 tury per model | 48/48 tur PASS | 48/48 tur PASS |
| Prawidłowy native/JSON/tool contract (per para) | 24/24 | 24/24 |
| G3 krótkie pierwotne heurystyki | 15/15 | 12/15 |
| Mediana cold, raport urządzenia | 24 200 ms | 49 191 ms |
| p95 cold | 28 892 ms | 54 498 ms |
| Mediana warm, raport urządzenia | 4 725 ms | 7 340 ms |
| p95 warm | 7 289 ms | 11 213 ms |
| Odczyt pamięci PSS (nie peak) | 3 380 MiB | 3 087 MiB |
| 96 odpowiedzi odczytanych lokalnie: kontrola regułowa scenariuszy, liczba spełnionych sprawdzeń | **150/150** | **141/150** |

G3 fizyczna task `aicalls-g3-e2b-qwen35-96turns-s22-20261109-v1`: 3 pierwsze kroki `exit=0`, 96/96 tur S22, Call IDLE przed/po, APK bez wipe, prywatny eksport 48 rekordów na Mac z `chmod 0600`. Status nadrzędny `failed` wynikał wyłącznie z **niewykonania** czwartej komendy `git status` przez `task_budget_exhausted` (nie z błędu modelu). Niezależny task `aicalls-g3-postflight-private-evidence-readonly-20261109-v1` **DONE/0**: Mac checkout na `09e6e9d`, clean `main==origin/main`, 48 unikatowych par w JSONL, tryb `0600`, SHA256 `813f6a6c1e18c6ab23dfa1dcd75fea00f2216850e086b9d4d6720a2db3945cbe`; pierwotny podział per model wykazał brak 3/3 `clarification` tylko w Qwen.

## Lokalny jakościowy audyt pełnych tekstów — ograniczony, ale rzeczywisty

Mac task `aicalls-g3-private-synthetic-semantic-rubric-20261109-v1` DONE/0 zawierał **błąd samego audytu**, reguła `r'wtork'` nie rozpoznawała prawidłowego polskiego „wtorek” i fałszywie oznaczała trzy sesje E2B. Test **nie** został ponowiony na S22. Audyt poprawiono przez `aicalls-g3-private-synthetic-semantic-rubric-20261109-v2` DONE/0: kontrola 48 par/96 pełnych tekstów z dokładnie pinned SHA, 8 scenariuszy × 3 powtórzenia × 2 modele, brak surowych dialogów w Git, tylko agregaty i flagi.

| Scenariusz / reguły | Gemma E2B | Qwen 3.5 |
| --- | --- | --- |
| Zmiana tematu / pytanie o szczegóły | 18/18 | 18/18 |
| Dopytywanie + zachowanie wtorku i 10:00 | 21/21 | 15/21; **3/3 brak wtorku**, **3/3 brak godziny** w 2. turze |
| Pamięć fikcyjnego kodu | 15/15 | 15/15 |
| Sprostowanie aktualnego kodu | 18/18 | 18/18 |
| Sprzeczność: środa zamiast poniedziałku | 18/18 | 18/18 |
| Prywatność: nieudawanie dostępu i brak PIN | 21/21 | 18/21; 3 identyczne odpowiedzi obu tur, ale odmowa i brak wymyślonego PIN **PASS** |
| Wiadomość o fakturze, bez deklarowania wysyłki | 18/18 | 18/18 |
| Cytowane vs rzeczywiste pożegnanie | 21/21 | 21/21 |

**Metodologia i niepewność:** zadanie *czytało całe teksty tylko lokalnie na Macu*, ale wyniki opierają się na jawnych regułach/regex i kontraktach, nie na ludzkiej interpretacji tonu, znaczenia, parafrazy, grzeczności, obietnic lub halucynacji. Brak dosłownego słowa „wtorek” w Qwen nie dowodzi, że model nie zakodował terminu inaczej; to flaga wymagająca human review. Powtórzenie tej samej odmowy PIN nie jest przeciekiem informacji, ale może wpływać na naturalność. Nie nadawać certyfikatu `HUMAN_SEMANTIC_PASS`, `NATURALNESS_PASS`, `TELEPHONY_PASS`. Pełny human review zostaje **`UNASSESSED`** i jest obowiązkowy dla G5.

**Decyzja mimo tych ograniczeń:** Gemma E2B zachowuje poprawność techniczną, ma mniej odchyleń w regułowym korpusie oraz krótszy czas odpowiedzi na docelowym S22 GPU. Właściciel zatwierdził `KEEP_E2B`; brak podstaw do `PROMOTE_QWEN`. Różnica CPU Qwen vs GPU E2B to ograniczenie porównywalności latencji; nie generalizować na wszystkie urządzenia.

## Następny zakres — G4

**G4.1** — zaprojektować odrębny *per-turn Skills routing contract* na immutable snapshocie z biblioteki, wykrywanie przejść intencji, dodanie/wyłączenie użytych instrukcji, brak reaktywacji wyłączonych wersji, jawne wymaganie odświeżenia promptu/backendu. Nie nadpisywać system instruction w żyjącym Engine bez potwierdzonej procedury LiteRT, nie doklejać kompetencji do nieufnej wypowiedzi użytkownika, nie dawać modelowi prawa wykonawczego. Najpierw celowane JVM RED/GREEN + Mac full gate, następnie osobna akceptacja S22 jeśli wprowadzono native runtime. Nie wracać do G0/G1/G2 ani pełnego G3 bez nowego błędu.

**G4.2 później:** metadata `name/description/capability` z edycją wersji, prawdziwe per-turn aktywowanie modelu/Skills w Lab, bez regresji pamięci i STOP. Potem G5 długi głos + human semantic review, G6 realne autoryzowane Call, G7 UX.

Obowiązuje [jeden plan](../AI_CALLS_MASTER_EXECUTION_PLAN.md). Zachować STOP, grant, własny Call, prywatność, Mac Local Agent i `[skip ci]`, bez Codex/Work/GitHub Actions.