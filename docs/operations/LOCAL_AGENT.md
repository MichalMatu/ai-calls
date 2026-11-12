# Mac Local Agent — wykonanie QA

Host `macbook-michal`; installation `cfea4cc3-b54c-4b6d-b9a6-758d12733e98`; checkout `/Users/michal/agent-workspace/repos/ai-calls/work`. Kanał: `MichalMatu/local-agent` branch `agent-control`, `.agent/hosts/macbook-michal/supervisor/<installation-id>/tasks/<id>.json` i `task-results/<id>.json`.

Przed zadaniem sprawdź status/kolizje. Task: dokładny host, installation, `version=1`, `cwd`, `resources=["repo:ai-calls"]`, skończone timeouts. Pierwszy krok hosta: clean `main`, fetch i fast-forward, porównanie SHA; w konflikcie nie mutuj. Odczytaj **terminalny** wynik, nigdy nie zakładaj PASS z braku błędu lub z 404. Nie ponawiaj niepewnego zadania bez rozstrzygnięcia.

Aktualny protokół i zasady intake znajdują się w `MichalMatu/local-agent` `AGENTS.md` i `docs/HOST_CONTROL_PLANE.md`; bieżący stan odczytaj z transportu, nie z dawnych receipts. Testy hosta nie dają prawa do ADB, instalacji, realnego Call ani eksportu danych. [DEVICE](DEVICE.md).
