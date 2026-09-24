# Campaign authorization grant

Status: design contract for the application-owned authorization layer.

## Purpose

AI Calls needs a durable way to represent a user's bounded authorization for repeated attempts of the same campaign. Without it, autonomous retry loops regress into asking the operator to confirm the same target/task/effect repeatedly.

The grant is an application-owned authority object. Chat prose, markdown, ServicePacks, model output, connected hardware and Local Agent artifacts are not substitutes for it.

## Minimum data model

A grant should bind at least:

```text
grant_id
version
created_at
revoked_at?
subject/account_scope
allowed_targets[]
allowed_tasks[]
allowed_effects[]
allowed_effect_values
allowed_disclosure_fields[]
retry_policy
expires_at?
max_attempts?
issuer_evidence_ref
```

For service-setting work, the effect binding must include the desired state, for example:

```text
service=CLIR
enabled=true
```

or

```text
service=CLIR
enabled=false
```

A grant for one value must not silently authorize the inverse value unless both values are explicitly inside the grant scope.

## Scope matching

Before dialing or committing an external effect, application policy must compare the requested action against the grant:

```text
exact target match
AND exact task/effect type match
AND requested value inside allowed value set
AND account/SIM scope match when relevant
AND disclosure request inside allowed disclosure set
AND not revoked
AND not expired
AND retry/attempt bound not exceeded
```

Any mismatch fails closed.

## Retry semantics

A retry inside the same exact valid scope should reuse the campaign grant rather than request another redundant product confirmation.

Retries must still run normal operational gates:

- live-call readiness;
- `IDLE` before dial;
- one owned active call;
- exact target binding;
- disclosure policy;
- output approval;
- commitment gate for account-changing effects;
- factual external-success evidence;
- cleanup to `IDLE`.

The grant replaces repeated authorization prompts; it does not replace these runtime controls.

## Commitment semantics

A durable campaign grant is not a reusable commitment permit.

For every concrete account-changing action:

```text
valid campaign grant
 -> exact CallExternalEffect
 -> deterministic effect validation
 -> exactly one fresh one-shot CallCommitmentGate permit
 -> permit consumed once
 -> separate factual external-success evidence
```

Do not persist or reuse `CallCommitmentGate` permits across actions.

## Revocation

Revocation must be application-owned and immediate for future actions.

A revoked grant:

- cannot start a new call;
- cannot authorize a new effect;
- cannot be resurrected by chat history, ServicePack data or model output.

If revocation occurs while a call is already active, policy should prevent any new commitment that has not already consumed its exact one-shot permit and should cleanly terminate/take over according to session policy.

## Persistence and privacy

Persist only the minimum scope metadata required to enforce authority.

Do not persist plaintext identity secrets inside the grant. Store typed field IDs / references and resolve plaintext through `IdentityVault -> FactDisclosurePolicy` late in the live state where disclosure is allowed.

Grant diagnostics should expose IDs, scope hashes, booleans and reason codes rather than secrets.

## Cross-chat continuity

Chat memory is not the authority mechanism. A new chat/session may discover that an application-owned grant exists and is valid, then continue work inside that scope without recreating the user's decision as chat prose.

The new session must not widen the grant merely because previous messages or handoff text describe a broader intention.

## External platform controls

Repository/application authorization cannot override external platform or tool enforcement. If an external layer requires an additional confirmation or blocks an action, the application must not bypass that control or claim execution.

## Orange CLIR acceptance target

For the current CLIR development campaign, the intended application grant design should be able to represent a bounded scope equivalent to:

```text
account_scope = current test SIM/account
allowed service = CLIR
allowed desired states = {true, false} only when explicitly included
allowed Orange campaign targets = exact reviewed targets only
allowed read-only verification = CLIR state interrogation
purpose = repeated physical acceptance iterations
```

The grant must not authorize unrelated carrier services, purchases, payments, contracts, premium/emergency calls or another account/SIM.

## Acceptance criteria for this feature

The durable grant mechanism is complete when:

1. the user can create/approve a scoped grant through application-owned policy;
2. the grant persists across app/session restarts according to product policy;
3. unchanged retries inside scope do not prompt again;
4. material widening fails closed;
5. revocation blocks future actions;
6. `CallCommitmentGate` remains one-shot per concrete effect;
7. identity stays late-bound and redacted;
8. tests and physical acceptance demonstrate that the grant cannot widen target/task/effect/account scope.
