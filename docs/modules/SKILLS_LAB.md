# Skills, modele i laboratorium

Obecny produkt to laboratorium modułowych konfiguracji STT / model / strategia dialogu / aktywne Skills / TTS / typ zadania, z wymiennymi backendami i powtarzalnymi próbami. Produkcja obecnie KEEP_GEMMA_E2B_GPU; Qwen3.5 2B, E4B i ALL_ACTIVE pozostają porównaniami Lab, nie automatyczną zmianą profilu.

## Skills
Skills są wersjonowane (nazwa/opis/capability/instrukcja), aktywowane kontekstowo dla każdej tury, mogą proponować narzędzie; **nie** nabywają uprawnień. `ConversationToolExecutor` i aplikacja autoryzują efekty, `CallCommitmentGate` jest jedyną bramką skutków. Model nie dostaje całego sejfu.

## Dowody do ponownego użycia
G3 96 tur A/B → KEEP_E2B, G4 dynamiczne przeładowanie Skills z pamięcią i STOP native off-call PASS, G5.3 18 native tur i 36 WAV, aktywny STOP STT/E2B/TTS PASS. 0/6 indywidualnych ocen naturalności/semantyki pozostaje UNASSESSED. Nie restartować tych benchmarków bez zmiany badanego kontraktu.

## Zasada QA
Nowy model/Skill/adapter najpierw w off-call Lab ze spójnym promptem i jawnie rozdzielonym cold/warm. Brak automatycznej promocji wariantu do realnych rozmów. [Current](../PROJECT.md), [dev flow](../operations/DEVELOPMENT.md).
