# Development

[Jedyny plan](../PROJECT.md): GSM przed SIP. ChatGPT: GitHub Connector + [Mac Local Agent](LOCAL_AGENT.md), bez Codex/Work/Actions/schedulerów.

Czysty HEAD/SHA, Existing Code First → patch → test celowany `scripts/bin/test-profile.sh focus` → runtime. Preflight/full tylko w razie potrzeby. Zachowaj cache; fizyczny Call/instalacja po zgodzie.

Gate: `scripts/aicall_tools/diagnostics/docs_check.py`; AGENTS ≤1 KiB, PROJECT ≤2 KiB, docs razem ≤8 KiB.
