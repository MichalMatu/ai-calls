# Skills, modele i laboratorium

Produkt ma wspólną konfigurację „Model i głos”: Gemma E2B lub zgodny import LiteRT-LM, Android STT/TTS i opcjonalny Parakeet w grupie Dodatkowe wyboru STT. Laboratorium pozostaje narzędziem developerskim; E4B/Qwen nie są osobnymi ofertami normalnego UI.

## Skills
Skills są wersjonowane (nazwa/opis/capability/instrukcja), aktywowane kontekstowo dla każdej tury, mogą proponować narzędzie; **nie** nabywają uprawnień. `ConversationToolExecutor` i aplikacja autoryzują efekty, `CallCommitmentGate` jest jedyną bramką skutków. Model nie dostaje całego sejfu.

## Dowody do ponownego użycia
G3 96 tur A/B → KEEP_E2B, G4 dynamiczne przeładowanie Skills z pamięcią i STOP native off-call PASS, G5.3 18 native tur i 36 WAV, aktywny STOP STT/E2B/TTS PASS. 0/6 indywidualnych ocen naturalności/semantyki pozostaje UNASSESSED. Nie restartować tych benchmarków bez zmiany badanego kontraktu.

## Zasada QA

Inwentarz modeli pochodzi z plików, nie z katalogu kandydatów. Parakeet ma osobny wybór STT; SHA-256 jest sprawdzany poza UI, a runtime weryfikuje plik przed inicjalizacją. Zapis wyboru jest oddzielony od gotowości. Głosy offline wczytują się automatycznie. Import wymaga nagłówka kontenera i natywnej odpowiedzi zgodnej z protokołem, zanim zostanie oznaczony jako zgodny. Poprawność pliku nie dowodzi jakości mowy.
Nowy model/Skill/adapter najpierw w off-call Lab ze spójnym promptem i jawnie rozdzielonym cold/warm. Brak automatycznej promocji wariantu do realnych rozmów. [Current](../PROJECT.md), [dev flow](../operations/DEVELOPMENT.md).
