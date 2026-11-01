# AI Calls — Local Agent 4.20.16 Mac Host Control flow

Effective 2026-11-01. Source contract: `MichalMatu/local-agent` current `AGENTS.md`, `docs/HOST_CONTROL_PLANE.md`, `docs/CURRENT_HANDOFF.md`. Mac installed 4.20.16 accepted 2026-10-31; source changes do not prove current runtime state.

## Which queue

For Mac operations (ADB, Android SDK, local device access, builds), select **exactly**:
- Transport repository: private `MichalMatu/local-agent`
- Branch: `agent-control`
- Host ID: `macbook-michal`
- Installation ID: `cfea4cc3-b54c-4b6d-b9a6-758d12733e98`
- Task: `.agent/hosts/macbook-michal/supervisor/cfea4cc3-b54c-4b6d-b9a6-758d12733e98/tasks/<unique-task-id>.json`
- Output: same prefix `task-results/<unique-task-id>.json`
- Status: same prefix `status.json`

Minimum machine task (within the **private** transport repository):

```json
{
  "version": 1,
  "id": "aicalls-example-readonly-unique-v1",
  "target_host_id": "macbook-michal",
  "target_installation_id": "cfea4cc3-b54c-4b6d-b9a6-758d12733e98",
  "cwd": "/Users/michal",
  "commands": ["adb devices -l"],
  "resources": ["adb:rfct70l7e8j"],
  "command_timeout": 60,
  "idle_timeout": 50,
  "task_timeout": 180,
  "memory_limit_mb": 512,
  "output_policy": "summary"
}
```

Never replay the example ID verbatim. Use new immutable ID for each actual request; no project `work_branch`/`agent_binding` in a host task. The Mac already has owner-authorized `github_writers` intake; exact host/installation identity, STOP, resource locks, command limits, durable no-replay and result publication remain mandatory.

For repository-specific project worker actions, `MichalMatu/ai-calls:agent-control/.agent/tasks` is **a different compatible queue**, requiring `agent_binding=c25f88c0-4682-414c-8062-c47fa4034cb0` and a registered target. Do not conflate project heartbeat with host liveness.

## Acceptance order

1. Check current Mac `status.json` and immutable task results in `local-agent:agent-control`.
2. Run read-only ADB enumeration/mDNS; verify connected S22 by `ro.product.model=SM-S906B` and `ro.serialno=RFCT70L7E8J`. A Wi-Fi `IP:port` is transport identity, not proof of hardware identity. Prefer repository `s22_adb_discovery.py` after code/host test acceptance.
3. Confirm cellular `IDLE`, Shizuku readiness without dialing, then execute Python + Gradle unit/lint/build in a clean checkout. Do not install/alter phone state without a specific warranted test step.
4. Resolve verified failures. Never invent call ownership: `CampaignCallControllerRegistry` still lacks a real call-specific Android backend. No global hangup, autonomous `*100` or CLIR operation.
5. Read the published result/status with exact task ID, durable outcome, actual tested source SHA and device evidence before claiming `HOST_GREEN` or `PROVEN_S22`.

Earlier `docs/SPRINT_AGENT_CONTROL_CALL_LIFECYCLE_2026-11-01.md` described the legacy project mailbox and mistakenly treated its stale heartbeat as Mac unavailability. Keep it as historical evidence; **this document supersedes its transport routing and liveness conclusions**.
