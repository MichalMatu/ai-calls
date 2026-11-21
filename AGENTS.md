# AI Calls — agenci

- Tylko GSM bez Shizuku w MVP. [Plan](docs/PROJECT.md) · [QA](docs/operations/DEVELOPMENT.md).
- `app` — Android/UI/orkiestracja. `conversation-core` — stany i Lab JVM. `conversation-tools` — kontrakty. `audio-bridge` — PCM. `privileged-helper` — system. `realtime-client` — transport. Core i tools bez Androida.
- Praca bezpośrednio w Codex i GitHub. `main` → gałąź → testy → PR. Automatyzacje Local Agenta i Actions są poza tym obiegiem.
- Test Lab korzysta z danych syntetycznych. `lab-ui` uruchamiaj wyłącznie na emulatorze.
- S22+ wyłącznie IDLE przy instalacji lub przywracaniu hosta, za zgodą i z rollbackiem. Połączenia wymagają osobnej zgody. Zachowaj exact Call/SIM, STOP/takeover i END_CALL po TTS.
- PII, WAV i klucze pozostają poza Git i logami. Zrzuty ekranu dozwolone. Bez `FLAG_SECURE` i nieudowodnionych PASS.
