# F-155 — Gemma E2B, zimny start i diagnostyka kontekstu Laboratorium (2026-11-09)

## Zweryfikowana przyczyna pozornego spowolnienia

Na rzeczywistym Samsungu S22 `SM-S906B`, przez prywatny USB/ADB, zadanie `aicalls-f155-latency-model-context-baseline-s22-20261109-v1` **DONE/0** potwierdziło aktywny profil `GEMMA_4_E2B` i cztery natywne odpowiedzi (2 tury × 2 niezależne sesje) z backendem `GPU`. Kod `AudioEngineProfileStore` → `DialogueEngineProviders.resolve` → `Gemma4LiteRtTextBackendFactory.modelFile(..., modelId)` wybiera plik modelu zgodny z tym identyfikatorem; nie ma cichego wyboru E4B. Nie obliczano ponownie SHA pliku modelu na S22. Nie wykonano realnego Call.

| Context | Tura | Łączny SkillLabRunner | Ładowanie silnika | Utworzenie konwersacji | Generacja | GPU | Znaki |
| --- | --- | ---: | ---: | ---: | ---: | --- | ---: |
| 2048 | 1 | 28570 ms | 12559 ms | 3716 ms | 12285 ms | tak | 62 |
| 2048 | 2 | 3318 ms | 0 ms | 0 ms | 3316 ms | tak | 45 |
| 4096 | 1 | 27228 ms | 10885 ms | 3798 ms | 12536 ms | tak | 63 |
| 4096 | 2 | 3627 ms | 0 ms | 0 ms | 3623 ms | tak | 46 |

W tej krótkiej porównywalnej próbie **różnica 2048–4096 nie tłumaczy wzrostu czasu**. Wcześniejsze 16–23 s były średnią z krótkich sesji obejmującą pierwszą zimną turę. Następna tura na zachowanym kontekście trwała około 3–4 s, zgodnie z dawnymi wynikami 4–6 s. Dla wyjść dłuższych, nasycenia kontekstu, p95 i pamięci potrzeba osobnego porównania. Inicjalizacja jest *lazy* i występuje przy pierwszym `sendMessage`, nie podczas natychmiastowego wejścia Lab w `INPUT_READY`.

## Implementacja

- `Gemma4LiteRtTextBackend.kt`: typowane `NativeModelTiming`, opcjonalny observer; czas: engine, conversation, native generate, suma, reused/cold, GPU/CPU, status i liczba znaków; bez promptu, odpowiedzi, telefonu czy ścieżki pliku. Zachowany dotychczasowy log `AiCallBridgePerf`.
- `DialogueEngineProviders.kt` i `AndroidSkillLabSessionFactory.kt`: diagnostyka tylko Laboratorium. Budżet tokenów kontekstu ustawiany dla kolejnej sesji, snapshot trwa przez cały run. Normalna ścieżka telefoniczna pozostaje 2048.
- `ConversationSkillLabActivity.kt`: edytowalne dodatnie pole `lab-context-tokens`, domyślnie 4096, zapamiętanie ustawienia po faktycznym starcie; disabled podczas aktywnej sesji, live panel `lab-model-diagnostics` z konfiguracją, backendem i rozbiciem czasu, średnią generowania, PSS aplikacji i wolną RAM systemu. Bez fikcyjnego `tokens/s` — backend podaje tylko znaki.
- PL/EN: `lab_runtime_diagnostics.xml`. `AndroidLabModelDiagnosticsNativeTest` obejmuje zmianę kontekstu na 2048 i rzeczywisty callback inferencji oraz przywrócenie poprzedniej lokalnej preferencji. Bez wipe, połączeń i eksportu danych.

## QA i granice

Stan przy utworzeniu checkpointu: źródło `main@1a30d0aa48d6f58cab682cd265bb916988f8eb99`; **Mac gate i instalacja S22 w przygotowanym tasku `aicalls-f155-lab-model-diagnostics-mac-gate-20261109-v1` — oczekują na dokładny receipt**. F-155 **FIXED_IN_SOURCE/DEVICE_QA_PENDING**, nie nazywać zmian UI VERIFIED bez pełnego Mac i prawdziwego S22 po instalacji. Baseline 4/4 realnych tur GPU PASS, osobne dowody już zamkniętych F-150–154 pozostają ważne.

Nie podnoszono domyślnego budżetu Call, nie używano 8192 i E4B w tej próbie; przy 8192 należy najpierw sprawdzić pamięć. W następnym kroku funkcjonalnym wrócić do `SKILL-01/02/03` i oceny rzeczywistego wpływu edytowanych instrukcji.
