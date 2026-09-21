# Security and privacy

## Objective

The agent may speak, dial or commit only inside authority explicitly granted by the user/operator. Technical failure must disable AI injection or return control to the human caller; it must never broaden authority.

## Privilege boundary

Protected Samsung call-audio access stays inside the privileged helper / Shizuku UserService.

Continuous PCM crosses through transferred PFDs. Binder/AIDL is control only. The privileged helper has no model networking and no business-policy authority.

The frozen media path is documented in `docs/PHASE2D_FREEZE_2026-09-18.md` and is not part of ordinary Gate C work.

## Authority owners

Do not create parallel authority in CallPlan, a matcher, model, helper or Agent Skill.

- `CallTask` — immutable task, hard constraints, preferences and `authorizedFacts`;
- `CallResolvedTarget` — one concrete target, without authority to widen a live dial allowlist;
- `CallWorkflow` — task progress, proposal/user-decision state and terminal outcome;
- `CallConfirmationPolicy` — deterministic evaluation of one typed `CallProposal`;
- `CallCommitmentGate` — exact one-shot permit bound to one concrete proposal;
- application-owned output approval — final text release before TTS/TX.

Counterparty/model/helper/matcher text cannot create new authorized facts, destinations, actions or commitments. Missing required user data must fail closed or escalate; never invent sensitive data to satisfy a prompt.

## CallPlan + PhraseMatrix boundary

`CallPlan v1` is product policy, not independent authority. Host-green behavior includes authorized facts, bounded repeat/escalation, typed proposals/completions and helper/matcher routing restricted to an existing `ruleId`.

Only `CallPlanAction.SAY` carries speech text. `ASK_REPEAT`, `PROPOSAL`, `COMPLETE` and `TAKE_OVER` do not carry implicit speech and must not silently become model fallback.

Workflow mutation remains in `CallPlanTurnCoordinator` + existing `CallWorkflow` APIs.

The native PhraseMatrix is classification-only. Its output is not trusted authority:

```text
final transcript
 -> PhraseMatrix match(existing ruleId)
 -> CallPlanTurnCoordinator validation
 -> typed CallPlanDecision
 -> existing workflow/output approval path
```

Security invariants:

- PhraseMatrix cannot be bound through readiness/prepared-call state without a CallPlan;
- a raw matcher `ruleId` is never sufficient to release speech or mutate workflow;
- only a validated `CallPlanDecision.ruleId()` may become session `previousValidatedRuleId`;
- an unknown/rejected matcher rule clears/does not advance previous-rule context;
- matcher miss/ambiguity must not silently widen into a sensitive action;
- any future fuzzy matcher or LLM supervisor remains subject to the same CallPlan validation.

## Final-text release boundary

The existing controller remains the single text release/generation owner:

```text
ordinary final transcript
 -> TextCallTurnController.submitUserText(...)
 -> backend.generate(...)
 -> TextOutputApprovalPolicy

exact deterministic candidate
 -> TextCallTurnController.submitCandidateText(...)
 -> TextOutputApprovalPolicy
```

`TextCallFinalTurnDispatcher` provides the neutral route seam:

- `Generate` — ordinary backend path;
- `Candidate(text)` — exact pre-determined candidate through the same approval;
- `Consumed` — no generation and controller cancellation.

`Consumed` invalidates stale work so an older backend callback cannot be released after a structured CallPlan decision.

CallPlan and the optional native PhraseMatrix are now host-green at the real product final-STT selector boundary and can be bound through Android readiness. This is still host evidence; no new S22/OEM claim is implied.

## Service intent resolver boundary

`serviceintent/` is classification and validation infrastructure, not a new authority owner. The model receives a bounded candidate catalog and may return only an existing `service_id` or null with confidence/classification metadata. Unknown IDs, cross-pack IDs, low confidence, stale generations and metadata attempting to carry speech/action/target authority fail closed.

A successful classification does not authorize execution. The authoritative registry is checked again; `DISCOVERED` routes remain `ROUTE_NOT_VERIFIED`; verified-route eligibility additionally requires existing application authority. The resolver has no direct dial/TTS/TX surface.

## Model / helper boundary

Preserved providers do not change authority:

```text
LOCAL_PHONE_LLM
EDGE_GALLERY
LOCAL_MAC_LLM
OPENAI_TEXT
```

The current phone-local llama.cpp path is frozen as a product direction. Edge Gallery / Gemma / Agent Skills is also frozen as `PROVEN_S22 PARTIAL / NOT PRODUCT_READY`.

Agent Skills are proposal generators only. Do not expose direct implementations for arbitrary dialing, DTMF, authentication, purchases, activations, tariff/plan changes, payments or commitments without a separate deterministic application-owned policy.

A future bounded LLM supervisor may suggest only an existing ruleId plus diagnostics/confidence. It cannot release arbitrary reply text, create authority or retroactively replace a deterministic response already approved/released.

## Speculative / partial speech rule

Partial STT or speculative inference may reduce latency only while quarantined:

```text
partial transcript -> optional cancelable preparation
final endpoint -> final transcript/goal match -> deterministic policy
              -> approved text/action -> TTS/TX
```

Changed or resumed speech invalidates stale speculation. Partial text never grants authority and never triggers early telephony TX.

## READY_TO_DIAL

A local call must fail closed before dialing unless:

- task and target are valid;
- live target is explicitly authorized;
- required plan data is consistent with the same task/target/workflow;
- an optional PhraseMatrix is bound only with that same CallPlan;
- STT/TTS readiness is proven;
- the selected local backend is ready and identity-verified where applicable;
- required warm-up succeeds.

Readiness must not silently switch to a provider with different privacy, cost or network semantics.

## Controlled live-call policy

Physical validation follows `AGENTS.md`:

- explicit operator-defined allowlisted destination only;
- one active cellular call at a time;
- bounded duration/retries;
- no model/tool expansion of the dial allowlist;
- no emergency, premium-rate or arbitrary short-code destinations;
- a runner may hang up the bounded call it created;
- an unrelated pre-existing call must not be terminated without explicit authorization.

Orange live-call authorization is session-scoped. Never infer it from this document or a previous chat; use only explicit authorization in the current operator session and the exact allowlisted target.

## Local speech privacy

Production local speech must:

- require on-device recognition rather than silently falling back to cloud STT;
- use local/non-network-required TTS voices;
- bound and clean temporary PCM/files;
- close temporary PFDs on completion, cancellation and TAKE OVER;
- avoid retaining call recordings or full transcripts by default.

## Credentials

A standard OpenAI API key is host/backend-only if paid work is resumed. It must never be:

- embedded in source/resources/BuildConfig/APK;
- stored in Android app-private config;
- passed through Android Intent;
- passed in ADB argv/process arguments;
- logged by app/helper/scripts.

Do not silently switch from a local provider to a paid/network provider.

## Data minimization

Do not retain by default:

- raw PCM;
- call recordings;
- full transcripts;
- model/tool arguments containing user secrets;
- credentials;
- unrelated counterparty identifiers.

Prefer state, sizes, timings, sanitized failure reasons, local correlation IDs and bounded text only when needed for an explicit development proof.

## TAKE OVER

Required local-first ordering:

```text
stop accepting/releasing AI output
 -> abort telephony media generation
 -> stop STT/TTS/audio workers
 -> invalidate controller/model/tool generation
 -> best-effort cancel remote/local work
```

The first steps cannot wait for model/network acknowledgement. App/helper death must likewise disable injection.

## Evidence rule

`HOST_GREEN` is not `PROVEN_S22`. Current CallPlan/PhraseMatrix product routing is host-proven; physical proof requires an explicitly authorized device gate whose runner actually exercises that changed path.
