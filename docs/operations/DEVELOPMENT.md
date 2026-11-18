# Development

**Jedyny [plan](../PROJECT.md): własny host `shell` dla GSM bez Shizuku.** Nie rozwijać SIP/VoIP/PJSUA2 ani innych funkcji. ChatGPT → [Mac Local Agent](LOCAL_AGENT.md), bez Codex/Work/Actions/schedulerów.

Czysty S8 HEAD, Existing Code First → patch → `scripts/bin/test-profile.sh focus` → receipt. Preflight/full tylko w razie potrzeby, cache Gradle. `main` bez zmian. Fizyczny Call/instalacja po zgodzie.

[Gate](../../scripts/aicall_tools/diagnostics/docs_check.py): AGENTS ≤1 KiB, PROJECT ≤2 KiB, docs ≤8 KiB.
