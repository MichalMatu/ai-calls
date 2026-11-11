# Mac Local Agent — host operations

Kodowanie, testy i Git AI Calls odbywają się wyłącznie bezpośrednio w Codex. Poniższy protokół dotyczy tylko odrębnie zleconych operacji Local Agent i odczytu dawnych dowodów; nie jest domyślnym wykonawcą prac nad repozytorium.

**Źródło protokołu:** aktualne `MichalMatu/local-agent` `main` (`AGENTS.md`, `docs/HOST_CONTROL_PLANE.md`, `docs/CURRENT_HANDOFF.md`) oraz `agent-control` i rzeczywisty wynik tasku. Te instrukcje pobierz przed Host Ops; nie traktuj dawnego statusu `idle` jako aktualnego faktu.

## Adresowanie
- Host: `macbook-michal`, installation `cfea4cc3-b54c-4b6d-b9a6-758d12733e98`.
- Checkout: `/Users/michal/agent-workspace/repos/ai-calls/work` (przed zapisem sprawdź `main`, clean i fast-forward).
- Transport: `MichalMatu/local-agent`, branch `agent-control`; task: `.agent/hosts/macbook-michal/supervisor/cfea4cc3-b54c-4b6d-b9a6-758d12733e98/tasks/<unique-id>.json`, wynik pod tym samym prefiksem `task-results/<unique-id>.json`. Nie myl z innymi kolejkami agentów.
- Minimalny task jest adresowany dokładnym host/installation ID, `version=1`, `cwd`, ograniczonymi `commands`, `resources=["repo:ai-calls"]` i skończonymi timeoutami. Nowe zadanie ma unikalny ID. GitHub Connector publikuje task, host wykonuje, potem należy odczytać **niezmienny** wynik i kody zakończenia.

## Przed i po zadaniu
1. Sprawdź status, claims, ostatnie task-results, możliwe zasoby zajęte inną pracą. Nie uruchamiaj dwóch mutacji tego samego checkoutu ani dwóch fizycznych operacji jednocześnie.
2. Pierwsza komenda hosta: clean Git, `git fetch --no-tags origin main`, `git merge --ff-only origin/main`, dokładny oczekiwany SHA. Gdy niespójny, odmów mutacji i ponownie uzgodnij drzewo.
3. Uruchamiaj wyłącznie wąskie testy odpowiednie do zmiany. Testowanie jednostkowe nie jest autoryzacją realnego Call, ADB, instalacji ani eksportu PII.
4. Odbierz `task-results/<id>.json`. Brak pliku (404) może oznaczać oczekiwanie; **nie** zgaduj PASS i **nie** ponawiaj zadania o niepewnym skutku. Sprawdź trwały stan hosta, dopiero wtedy rozstrzygnij.
5. Zapisz w `docs/PROJECT.md` tylko najnowszy istotny QA/otwarte ryzyka. Szczegółowe wyniki są w oryginalnym Host Control, bez kopii checkpointów.

## Praca fizyczna
USB/ADB i testy S22 wymagają zweryfikowanego Maca, autoryzacji i IDLE według [DEVICE](DEVICE.md), również przy wykonaniu bezpośrednio w Codex. Nie dopisuj numerów, danych właściciela, nagrań ani transkryptów do zdalnych komend/receipts.
