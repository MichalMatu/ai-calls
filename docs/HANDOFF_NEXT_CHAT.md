# Handoff — Orange Service Pack Explorer / deterministic Gate C

Date: 2026-09-21

Repository: `MichalMatu/android-ai-call-bridge`

Durable branch: `main`

Local Agent control/evidence branch: `agent-control`

## Start here in the next chat

1. Read fresh `AGENTS.md`, `README.md`, this file, `docs/ROADMAP.md` and `service-packs/orange/service_tree.v1.json`.
2. Read `docs/ARCHITECTURE.md` / `docs/SECURITY_PRIVACY.md` before changing ownership or authority boundaries.
3. Read `docs/PHASE2D_FREEZE_2026-09-18.md` before touching Samsung media behavior.
4. Fetch fresh `main` and fresh `agent-control:.agent/status/daemon.json`.
5. Trust only the fresh Bridge envelope + fresh daemon binding. Never copy an old binding from this handoff.
6. Inspect the latest terminal Local Agent result before creating a successor task.
7. Keep `privileged-helper/` and the frozen Samsung media path untouched.
8. Do not assume live-call authorization from this chat. The new chat must explicitly authorize physical Orange calls again.

Current Local Agent daemon contract requires explicit `resources` on tasks. For physical S22 work the canonical resource used successfully is:

```text
device:rfct70l7e8j
```

## Frozen foundation

Target phone: Samsung Galaxy S22+ `SM-S906B`, Android 16 / API 36 / One UI 8, direct USB ADB serial `RFCT70L7E8J`.

- cellular RX/TX and fail-safe media lifecycle: `DONE / PROVEN_S22 / FROZEN`;
- local Polish STT/TTS: `DONE / PROVEN_S22`;
- provider-neutral text pipeline + application-owned output approval: `DONE / PROVEN_S22`;
- product readiness/prepared-call boundary: `DONE`;
- deterministic `CallPlan + PhraseMatrix` final-STT path: `HOST_GREEN`;
- controlled cellular deterministic RX -> STT -> route -> approved TTS -> TX: `PROVEN_S22`;
- general-purpose phone-local llama.cpp route: frozen;
- Edge Gallery / Gemma 4 E2B / official Agent Skills: `FROZEN / PROVEN_S22 PARTIAL / NOT PRODUCT_READY`;
- interactive ChatGPT relay: developer benchmark only.

Authority remains unchanged:

- `CallTask` owns immutable task/constraints/preferences/authorized facts;
- `CallResolvedTarget` owns the concrete resolved target but never widens allowlists;
- `CallWorkflow` owns progress/proposals/user decisions/outcomes;
- `CallConfirmationPolicy` evaluates typed proposals;
- `CallCommitmentGate` owns one exact commitment permit;
- application-owned output approval is mandatory before TTS/TX.

Models, helpers, matchers, service-pack discovery logic and Agent Skills remain proposal/classification-only.

## Gate C status now

Gate C is no longer waiting for its first live proof.

Status:

```text
ACTIVE / HOST_GREEN / LIVE DETERMINISTIC PATH PROVEN_S22
CURRENT SLICE: ORANGE SERVICE PACK EXPLORER
```

The current product goal is to make Orange the first fully decoded deterministic service pack so that eventually:

```text
natural user request
 -> validated Orange service_id
 -> verified deterministic route
 -> automatic traversal
 -> auth/confirmation only where the verified route requires it
```

The model must not improvise each IVR turn.

## Key physical live evidence

### v101 — first real Gate C call / safe fail-closed

```text
.agent/results/chatgpt-gate-c-orange-live-v101-20260921.json
```

Orange answered; real downlink audio reached STT; the first final transcript was only `orange`; CallPlan failed closed to `TAKE_OVER`; `backend_generate_calls=0`; no TTS/TX reply was sent; cleanup returned the phone to idle and restored Bluetooth.

This proved the need for explicit root-acquisition handling rather than treating branding fragments as intent.

### v115 — first complete deterministic live RX -> TX + observation

```text
.agent/results/chatgpt-orange-explorer-live-v115-20260921.json
```

This is the main physical Gate C success checkpoint.

Observed sequence:

```text
Orange pre-roll `orange`
 -> bounded root-acquisition retry
 -> STT full Max root prompt
 -> PhraseMatrix / CallPlan validated `list_capabilities`
 -> approved_text_source=call_plan_candidate
 -> approved_text=Jakie sprawy możesz załatwić?
 -> local TTS
 -> telephony TX
 -> OBSERVE_ONLY
 -> Max reprompt transcript
 -> hangup / IDLE cleanup
```

Important evidence:

```text
gate_c_fast_path=true
gate_c_call_plan_bound=true
backend_generate_calls=0
approved_text=Jakie sprawy możesz załatwić?
output_tts_pcm_bytes=59966
telephony_tx_pcm_bytes=59966
end_of_speech_to_first_tx_ms=2333
```

Observed Max root:

```text
dobry wieczór jestem max twój wirtualny asystent orange nasza rozmowa jest nagrywana chętnie pomogę powiedz w jakiej sprawie dzwonisz
```

Observed response after `Jakie sprawy możesz załatwić?`:

```text
abym mógł ci pomóc muszę mieć pewność w jakiej sprawie dzwonisz dlatego powiedz proszę czego dotyczy twoja sprawa
```

Conclusion: Max is a voice-first intent router. Asking for a capability list produces a reprompt, not a menu listing.

### v123 — invoice seed physically attempted

```text
.agent/results/chatgpt-orange-invoice-live-v123-20260921.json
```

Observed sequence:

```text
pre-roll `orange`
 -> bounded retry
 -> full verified root prompt
 -> reviewed invoice_status action
 -> approved_text=Chcę sprawdzić fakturę.
 -> local TTS / telephony TX
 -> OBSERVE_ONLY
 -> same Max root reprompt
 -> cleanup to IDLE
```

Important evidence:

```text
gate_c_fast_path=true
gate_c_call_plan_bound=true
backend_generate_calls=0
approved_text=Chcę sprawdzić fakturę.
output_tts_pcm_bytes=49612
telephony_tx_pcm_bytes=49612
end_of_speech_to_first_tx_ms=2297
ORANGE_INVOICE_V123_GREEN=true
```

Max again answered:

```text
abym mógł ci pomóc muszę mieć pewność w jakiej sprawie dzwonisz dlatego powiedz proszę czego dotyczy twoja sprawa
```

Interpretation: the exact reviewed utterance `Chcę sprawdzić fakturę.` was physically transmitted successfully but did **not** reach a verified invoice service route. Do not claim otherwise. The service seed is `DISCOVERED`, not `VERIFIED`.

## Final host checkpoint before this handoff

Product/data checkpoint validated by Local Agent:

```text
5b0f599b98aefcef2279490cb14824f70e63ea17
```

Evidence:

```text
.agent/results/chatgpt-orange-service-tree-final-green-v125-20260921.json
```

Result:

```text
12 Python tests OK
bash scripts/verify_host.sh -> host_quality_gate_green=true
ORANGE_SERVICE_TREE_HANDOFF_HOST_GREEN=true
```

Later commits in this handoff update only authoritative documentation and do not change runtime behavior.

## Orange Explorer implementation

### Exact live target

```text
510100100
```

No other target is authorized by the current Orange diagnostic implementation.

### Named reviewed live actions

`OrangeLiveAction.kt` currently contains:

```text
GREETING          -> Dzień dobry.
LIST_CAPABILITIES -> Jakie sprawy możesz załatwić?
INVOICE_STATUS    -> Chcę sprawdzić fakturę.
OBSERVE_ONLY      -> no speech
```

This is intentionally not a free-text surface.

`GateCLiveCallScenarioFactory` binds every speech-producing action to a reviewed root phrase and authorized fact. `OBSERVE_ONLY` builds no `SAY` rules at all.

### Root acquisition

Current exact physically observed ignorable pre-roll fragments in `scripts/local_phone_llm_live_call.py`:

```text
orange
jakości orange
5g jakości orange
```

They may be ignored only when all root-acquisition safety conditions pass. Do not generalize them into fuzzy branding logic without new physical evidence.

Root acquisition also permits a bounded retry for Android `SpeechRecognizer ERROR_NO_MATCH(7)` only when the tested report proves:

- Gate C fast path;
- bound CallPlan;
- root-acquisition action;
- zero backend generation;
- actual detected speech;
- trailing-silence endpointing.

`OBSERVE_ONLY` never uses pre-roll/no-match retry policy.

Current retry bound remains small (`MAX_GATE_C_PREROLL_RETRIES = 2`).

### Authority invariant

The live explorer must continue to use:

```text
final STT
 -> PhraseMatrix
 -> existing CallPlan ruleId validation
 -> application-owned output approval
 -> TTS/TX
```

No arbitrary matcher/model output becomes speech.

Do not reinterpret `TAKE_OVER` globally as `continue listening`. Root-acquisition continuation is an explicit runner policy with strict evidence conditions.

## Durable service tree

Source of truth:

```text
service-packs/orange/service_tree.v1.json
```

Current verified nodes:

```text
orange.root
orange.root.reprompt
```

Current verified observed edges:

```text
orange.root.list_capabilities
  orange.root -> orange.root.reprompt
  speech: Jakie sprawy możesz załatwić?
  outcome: REPROMPT
  evidence: v115

orange.root.invoice_status
  orange.root -> orange.root.reprompt
  speech: Chcę sprawdzić fakturę.
  outcome: REPROMPT
  evidence: v123
```

Current service seed:

```text
orange.invoice.status
status: DISCOVERED
last_outcome: REPROMPT
service route: NOT VERIFIED
```

Important distinction: an observed edge can be `VERIFIED` because its actual transition was physically reproduced while the desired service seed remains only `DISCOVERED` because the target service node was not reached.

State discipline for service seeds:

```text
SEED -> DISCOVERED -> VERIFIED
```

Use explicit barrier/risk metadata when applicable, e.g.:

```text
READ_ONLY
AUTH_REQUIRED
STATE_CHANGE
PAID_COMMITMENT
HUMAN_HANDOFF
```

Do not promote from public documentation alone.

## Discovery policy for the next chat

The user wants iterative Orange mapping, not one isolated call.

Within a chat that explicitly authorizes repeated calls to exact target `510100100`, continue automatically across safe discovery branches instead of asking for confirmation before every single call.

For each branch:

1. choose one unverified capability seed;
2. define one exact reviewed, non-committing utterance;
3. RED test the action/route boundary;
4. minimal GREEN through existing CallPlan/PhraseMatrix;
5. targeted tests + `bash scripts/verify_host.sh`;
6. one bounded physical Orange call with `--observe-next`;
7. persist observed node/edge/transcript outcome;
8. if it reaches an auth/customer-data/payment/state-change/commitment barrier, record that node/barrier and move to another branch instead of finalizing the action;
9. repeat.

The user specifically said not to stop the overall exploration at the first auth barrier. That means **continue with another branch**, not bypass the barrier or invent credentials.

Never guess or send PESEL, customer number, SMS codes, payment data or other sensitive credentials. Never confirm a purchase, paid option, activation, tariff/contract change or other irreversible commitment merely to map the route.

## Seed backlog direction

Orange public capability material may be used only to seed hypotheses such as:

- invoice / payment information;
- outage / technical support;
- PUK;
- Neostrada / Wi-Fi;
- roaming information;
- voicemail;
- SIM / eSIM;
- data usage / internet packages;
- prepaid / top-up information;
- call forwarding;
- My Orange;
- human consultant / handoff.

These are not verified routes until physically reproduced.

Prefer informational/non-committing phrasing first. State-changing variants become barriers rather than automatic commitments.

## Exact next engineering step

Do **not** redo the first live Gate C proof.

Start from the current tree and expand discovery.

The invoice seed already demonstrated that `Chcę sprawdzić fakturę.` yields a reprompt. Preserve that evidence. For invoice, the next experiment should use a separately reviewed, simpler/operator-native phrasing selected deliberately (for example from concrete Orange wording/corpus evidence), with its own RED/GREEN path; do not overwrite the v123 edge.

In parallel, add at least one new low-risk seed from another capability so exploration does not stall on invoice phrasing alone.

Once several service intents are proven, consider replacing the growing hard-coded explorer enum with a **data-driven reviewed action catalog** sourced from service-pack data, but only if it preserves all current authority properties:

- no arbitrary runtime speech;
- exact allowlisted target;
- CallPlan validation;
- application-owned approval;
- zero backend generation on deterministic routes;
- tested fail-closed behavior.

Do not refactor into a generic free-text IVR bot just to speed discovery.

## Final Orange acceptance target

Orange can be called the first fully decoded service only when the service pack supports a meaningful catalog of verified routes plus recovery/back/repeat/barrier behavior and passes a final acceptance test like:

```text
user prompt: natural language request for one catalog service
 -> resolver proposes existing service_id only
 -> service_id validated against verified Orange service pack
 -> exact target 510100100
 -> deterministic route executes without model improvisation
 -> expected verified terminal/barrier reached
 -> cleanup to IDLE
```

The final resolver/model may propose a known `service_id`; it must not invent new route actions or release arbitrary speech.

## Do not restart these paths by default

- Edge Gallery live-call experiments;
- old llama.cpp model sweep;
- paid OpenAI gates;
- Samsung media refactors;
- `privileged-helper/` work;
- broad arbitrary diagnostic speech surfaces.

Historical detail remains in Git history and `.agent/results`; continue from this checkpoint rather than reconstructing old phases.
