# Development

Jedyny [plan](../PROJECT.md): GSM P0 przed SIP. ChatGPT używa GitHub Connector i [Mac Local Agent](LOCAL_AGENT.md), bez Codex/Work/Actions/schedulerów.

Sprawdź SHA i czysty checkout; Existing Code First → mała poprawka → `bash scripts/bin/test-profile.sh focus` → runtime. Większy zakres: `preflight` / `full` tylko gdy potrzebne; użyj cache Gradle. Test fizyczny po zgodzie.

Dokumentację sprawdza `scripts/aicall_tools/diagnostics/docs_check.py`; AGENTS ≤1 KiB, PROJECT ≤2 KiB, łącznie ≤8 KiB.
