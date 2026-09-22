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

The frozen Samsung media foundation remains `DONE / PROVEN_S22 / FROZEN`. Gate C deterministic Orange speech is physically proven. Current work is service-pack mapping plus a generic natural-intent classifier that cannot gain authority.

Current durable code/data checkpoint before this handoff refresh:

```text
61ea6470320e574dda37cd9bc11905462dc9305c
```

Always use fresh `origin/main` rather than assuming that hash is still HEAD.

## Orange service tree

Source of truth: `service-packs/orange/service_tree.v1.json`.

Verified physical nodes: `orange.root`, `orange.root.reprompt`.

Verified observed root edges include `orange.root.list_capabilities`, `orange.root.invoice_status`, `orange.root.invoice_topic`, `orange.root.internet_problem`, `orange.root.roaming_info`, and `orange.root.roaming_prices`. Edge `VERIFIED` means only that the physical transition was observed; it does not mean a complete service route was reached.

Current service seeds remain `DISCOVERED`:

```text
orange.invoice.status
orange.internet.problem
orange.roaming.info
```

No complete service route is `VERIFIED`. Do not promote one without physical evidence reaching the intended node, terminal, or barrier.

## Latest physical roaming evidence

The 2026-09-22 overnight session added reviewed action `roaming_prices` with exact speech `Chcę sprawdzić ceny w roamingu.`. Kotlin changed and the resulting debug APK was installed before live testing.

Three bounded physical calls were made, all to exact target `510100100`. The first two stopped fail-closed during root acquisition and produced exact observed pre-roll fragments `wielkości orange` and `g jakości orange`. Both are stored as `VERIFIED` `IGNORABLE_PREROLL_FRAGMENT` evidence and authorize only exact bounded root-acquisition retry.

Final evidence task: `chatgpt-orange-roaming-prices-live-final-v176-20260922`.

```text
known exact pre-roll: orange
 -> bounded root-acquisition retry
 -> verified Max root
 -> reviewed speech: Chcę sprawdzić ceny w roamingu.
 -> backend_generate_calls=0
 -> exact reviewed speech transmitted
 -> OBSERVE_ONLY
 -> known Max root reprompt
 -> hangup
 -> FINAL_CALL_STATE=0
```

The corresponding `orange.root.roaming_prices -> orange.root.reprompt` edge is `VERIFIED` with `service_route_verified=false`. `orange.roaming.info` remains `DISCOVERED`. Further physical calls were stopped because this second reviewed roaming wording again returned the same known root reprompt, so repetition no longer added useful route evidence.

A later queued experiment attempted to start an `outage_topic` RED/green cycle after that stop condition. The green task failed before tests/call/install and left only checkpointed workspace edits; those edits were discarded and the RED commit was explicitly reverted. There is therefore no durable outage seed/action from that attempt.

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

Fail-closed rules remain: hallucinated or cross-pack IDs reject; low confidence/unrelated input rejects; stale generation rejects; model metadata cannot gain speech/action/target authority; `DISCOVERED` routes stop at `ROUTE_NOT_VERIFIED`; only an existing `VERIFIED` route plus application authority can become eligible. No Orange-specific logic belongs in `serviceintent`.

## Architecture and branch checkpoint

Do not turn diagnostic runners or probe activities into product orchestrators. Discovery and product execution remain separate. Authority remains with `CallTask`, `CallResolvedTarget`, `CallWorkflow`, `CallConfirmationPolicy`, `CallCommitmentGate`, and application-owned output approval.

Historical relay/worktree branches containing unique evidence remain intentionally preserved. Do not perform aggressive branch cleanup.

## Next work

Do not repeat `roaming_info` / `roaming_prices` against the same root prompt just to obtain another identical reprompt. Start a later session from fresh state and choose another low-risk informational seed only with fresh live-call authorization. A future live session must stop at auth/payment/purchase/activation/contract barriers and must never invent credentials.

## Final invariants

- one active cellular call at a time;
- deterministic reviewed route speech only, no model-generated runtime IVR speech;
- `OBSERVE_ONLY` never speaks;
- `backend_generate_calls=0` on deterministic route tests;
- no invented PESEL/customer/SMS/payment credentials;
- no purchase, activation, tariff/contract or other commitment merely for discovery;
- every call cleans up to `IDLE`;
- observed edge `VERIFIED` is distinct from service route `VERIFIED`;
- `HOST_GREEN` is distinct from `PROVEN_S22`.
