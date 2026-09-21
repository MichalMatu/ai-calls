# Handoff — Orange service pack + bounded service intent resolver

Date: 2026-09-22

Repository: `MichalMatu/android-ai-call-bridge`

Durable branch: `main`

Local Agent control/evidence branch: `agent-control`

## Start here

1. Read fresh `AGENTS.md`, `README.md`, this file, `docs/ROADMAP.md`, `docs/ARCHITECTURE.md`, `docs/SECURITY_PRIVACY.md`, `docs/PHASE2D_FREEZE_2026-09-18.md` and `service-packs/orange/service_tree.v1.json`.
2. Fetch fresh `main`, fresh `agent-control:.agent/status/daemon.json`, latest Local Agent terminal result, `git status`, worktrees and branches.
3. Use only the fresh `agent_binding`; never copy a binding from this handoff.
4. Keep `privileged-helper/` and the frozen Samsung media path untouched unless a direct bug requires a separately justified physical regression gate.
5. Do not infer live-call authorization from this document or an earlier chat. A future session must explicitly authorize the exact target again.

## Current product state

The frozen Samsung media foundation remains `DONE / PROVEN_S22 / FROZEN`. Gate C deterministic Orange speech has already been physically proven. The current work is service-pack mapping plus a generic natural-intent classifier that cannot gain authority.

Code/data checkpoint before this documentation cleanup:

```text
6860f2ad3a7c80a1927ce03a3462aaa99fbd3ee4
```

Always use fresh `origin/main` rather than assuming that hash is still HEAD.

## Orange service tree

Source of truth: `service-packs/orange/service_tree.v1.json`.

Verified physical nodes: `orange.root`, `orange.root.reprompt`.

Verified observed root edges include `orange.root.list_capabilities`, `orange.root.invoice_status`, `orange.root.invoice_topic`, `orange.root.internet_problem`, and `orange.root.roaming_info`.

The edge status means the transition itself was physically observed. It does not mean a complete target service route was reached.

Current service seeds remain `DISCOVERED`:

```text
orange.invoice.status
orange.internet.problem
orange.roaming.info
```

Do not promote a service route to `VERIFIED` without physical evidence reaching the intended node/terminal/barrier.

## Latest physical roaming evidence

Evidence task: `chatgpt-orange-roaming-live-v151-20260922`.

```text
verified Max root
 -> reviewed speech: Roaming.
 -> backend_generate_calls=0
 -> OBSERVE_ONLY
 -> known Max root reprompt
 -> hangup
 -> FINAL_CALL_STATE=0
```

The corresponding `orange.root.roaming_info -> orange.root.reprompt` edge is `VERIFIED`; `orange.roaming.info` is still only `DISCOVERED`.

## Service intent resolver

Generic package: `app/src/main/kotlin/pl/michalmatu/aicallbridge/serviceintent/`.

```text
UserIntentResolutionRequest
 -> UserIntentClassificationModel(bounded candidates only)
 -> ModelBackedUserIntentResolver
 -> existing service_id or null
 -> ServiceRegistry revalidation
 -> ServiceIntentExecutionValidator
```

Fail-closed rules already tested:

- nonexistent/hallucinated service ID -> reject;
- service from another pack -> reject;
- low confidence -> reject;
- unrelated request -> null/unknown;
- stale generation -> reject;
- model metadata attempting speech/action/target authority -> reject;
- `DISCOVERED` route -> classification may succeed, execution `ROUTE_NOT_VERIFIED`;
- `VERIFIED` route -> may become eligible only if existing application authority also allows it.

Demonstration:

```text
Mam problem z internetem w Orange
 -> orange.internet.problem
 -> ROUTE_NOT_VERIFIED
```

There is intentionally no telephony call or speech released by this resolver demo.

## Architecture checkpoint

The 2026-09-22 pre-refactor audit found no Orange-specific leakage in `serviceintent`, no session changes to frozen media, and no `TODO/FIXME/HACK` markers in active Kotlin/Python sources. `OrangeLiveAction.kt` is still small, so replacing it with a data-driven action catalog now would add churn without solving a current bottleneck.

Do not turn diagnostic runners or probe activities into product orchestrators. Discovery and product execution remain separate.

Authority remains with `CallTask`, `CallResolvedTarget`, `CallWorkflow`, `CallConfirmationPolicy`, `CallCommitmentGate`, and application-owned output approval.

## Branch checkpoint

Local Agent cleanup task `chatgpt-branch-cleanup-v159-20260922` removed only branches proven merged with zero unique commits and no worktree:

```text
agent/edge-gallery-live-flow
agent/gate-a-ready-to-dial
agent/gate-b-freeze-local-llm
agent/gate-b-text-benchmark
work/phase1-live-call-probes
```

Preserved intentionally: `agent-work`, `agent/chatgpt-relay-developer-path`, all `chat-relay/*` branches with unique evidence/commits, `main`, and remote `agent-control`.

## Next large task

A ready-to-paste prompt for a later maximum-10-hour autonomous Orange mapping session is stored at `docs/ORANGE_OVERNIGHT_MAPPING_PROMPT.md`.

It has **not** been started in this checkpoint. The future session must explicitly authorize repeated calls to exact target `510100100`; then it may iterate low-risk branches without asking before each individual bounded test, while stopping each branch at auth/payment/commitment barriers and continuing elsewhere.

## Final invariants

- exact Orange target only: `510100100`;
- one active cellular call at a time;
- deterministic route speech only, no model-generated runtime IVR speech;
- `OBSERVE_ONLY` never speaks;
- `backend_generate_calls=0` on deterministic route tests;
- no invented PESEL/customer/SMS/payment credentials;
- no purchase, activation, tariff/contract or other commitment merely for discovery;
- every call cleans up to `IDLE`;
- observed edge `VERIFIED` is distinct from service route `VERIFIED`;
- `HOST_GREEN` is distinct from `PROVEN_S22`.
