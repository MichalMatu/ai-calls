# Development

**Tryby:** ChatGPT → GitHub Connector + Local Agent (komendy/testy/host), bez Codex; Codex lokalny → terminal, Git i testy bezpośrednio, bez agenta. [Protokół](LOCAL_AGENT.md).

Czysty `main`, sprawdzony SHA i brak kolizji. `bash scripts/bin/test-profile.sh preflight`, potem `auto <base-SHA>`/`focus`; `full` po większych zmianach. `device-build` przed emulatorem lub autoryzowaną instalacją. Zachowaj cache Gradle.

**Docs:** kontrakty/blokady bez historii; ostrzeżenie 6 KiB, limit 8 KiB, AGENTS ≤1 KiB, PROJECT ≤2 KiB. [Gate](../../scripts/aicall_tools/diagnostics/docs_check.py).
