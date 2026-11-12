# AI Calls — szybka praca

1. Ustal aktualny clean `main`/base SHA i brak aktywnej kolizji hosta; kod przez GitHub Connector, testy przez Mac Local Agent. Bez Codex/Work/Actions, nowych branchy i force-push.
2. Existing Code First; zmieniaj najmniejszy istniejący moduł. Preflight: `bash scripts/bin/test-profile.sh preflight`; regresje: `auto <base-SHA>` albo `focus`; jeden `full` po dużej zmianie. `device-build` tylko bezpośrednio przed autoryzowaną instalacją.
3. Zachowuj cache Gradle, nie czyść SDK ani lokalnych danych; nie uruchamiaj AndroidTest na prywatnym S22. Po zmianie sprawdź czysty checkout.

## Dokumentacja — twarda dyscyplina
- `docs/PROJECT.md`: tylko **aktualne** otwarte zadania, decyzje i fizyczne blokady. Pozostałe pliki tylko stabilne, nieoczywiste kontrakty. Nie dodawaj historii, wyników testów, datowanych sprintów, list bibliotek ani opisu kodu.
- **Aktualizuj tylko gdy zmienia się kontrakt lub stan blokady.** Drobne poprawki bez aktualizacji dokumentów. Najpierw usuwaj nieaktualny wpis; nie duplikuj informacji.
- Budżet wszystkich Markdown (wraz z root README i AGENTS): **ostrzegaj po 18 KiB, blokuj powyżej 24 KiB**; pojedynczy plik 4 KiB. Egzekwuje [docs_check.py](../../scripts/aicall_tools/diagnostics/docs_check.py) i `preflight`. Nie podnoś limitu w celu dopisania treści; testy: `python3 -m unittest scripts.tests.test_docs_check`.
- Dowody testów w Git/Host Control receipts, nie w dokumentach. [PROJECT](../PROJECT.md) · [LOCAL_AGENT](LOCAL_AGENT.md).
