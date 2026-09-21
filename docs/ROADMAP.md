# Roadmap

This is the authoritative execution plan. Historical experiment detail belongs in Git history and `.agent/results`, not in new status documents.

Evidence levels:

- `HOST_GREEN` — deterministic host tests/build/lint pass;
- `PROVEN_S22` — physically reproduced on the target Samsung S22+;
- `PRODUCT_READY` — proven, fail-safe and acceptable for normal use.

## Foundation

### Cellular media

Status: `DONE / PROVEN_S22 / FROZEN`

```text
RX: VOICE_DOWNLINK -> privileged helper -> transferred PFD -> app
TX: app -> transferred PFD -> CALL_ASSISTANT / TELEPHONY_TX -> cellular uplink
```

Frozen checkpoint:

```text
59b0505537a53306acdab6a2a66ca6eed2b3f1c0
```

Do not redesign this path while working on models, planning or dialogue.

### Local speech/text boundary

Status: `DONE / PROVEN_S22`

```text
telephony RX
 -> end-of-utterance detector
 -> local S22 STT
 -> TextCallAgentBackend / deterministic text decision source
 -> application-owned approval
 -> local S22 TTS
 -> telephony TX
```

`LocalSpeechTextPipeline` and `TextCallTurnController` are the established text-turn boundary. New work must reuse them rather than duplicate speech or approval logic.

### End-of-utterance / IVR turn boundary

Status: `SIGNALS PROVEN_S22 / PRODUCTION STATE MACHINE OPEN`

Real Orange testing proved that fixed capture duration and short trailing-silence thresholds are not correct IVR semantics:

- 700 ms and 1500 ms trailing silence cut Max mid-prompt;
- one complete greeting contained multiple `onEndOfSpeech` callbacks and multi-second internal pauses;
- a recognizer-end candidate with about 4.5 s hangover captured the complete prompt;
- partial STT exposed the greeting progressively and reached the complete semantic prompt before final endpoint.

Target state model:

```text
speech/begin -> cancel pending END
recognizer END -> candidate end
resumed speech/partial growth -> cancel candidate
stable later END + bounded hangover -> final turn
long watchdog -> safety only
```

Do not promote diagnostic timeout values into product semantics without physical evidence.

Representative evidence:

```text
.agent/results/chatgpt-orange-agent-skills-endpoint-v9b-20260921.json
.agent/results/chatgpt-orange-partial-stt-v12d-20260921.json
```

---

# Completed gates

## Gate A — clean product orchestration + READY_TO_DIAL

Status: `DONE / HOST_GREEN / PROVEN_S22` (off-call readiness)

Product ownership is separate from diagnostics and frozen Samsung media:

- `LocalTextCallReadinessCoordinator` owns fail-closed technical readiness;
- `AndroidLocalTextCallSpeechPreflight` proves local STT/TTS capability;
- `IdentityVerifiedLocalPhoneLlmBackend` owns local runtime + exact identity verification for the preserved local backend;
- successful preparation returns one-shot `PreparedLocalTextCall`;
- `LocalTextCallSession` consumes the prepared backend and reuses `LocalSpeechTextPipeline`;
- telephony media/endpointing remain outside that session boundary.

Evidence:

```text
.agent/results/gate-a-readiness-red-20260919-1325.json
.agent/results/gate-a-readiness-green-20260919-1329.json
.agent/results/gate-a-full-host-20260919-1332.json
.agent/results/gate-a-offcall-ready-s22-20260919-1335.json
```

## Gate B — text-brain benchmark

Status: `DONE / PROVEN_S22 / PHONE-LOCAL LLM PATH FROZEN`

Frozen benchmark infrastructure:

```text
benchmarks/text_model_suite_v1.json
scripts/text_model_benchmark.py
scripts/test_text_model_benchmark.py
```

Measured decision:

- Qwen2.5-1.5B Q4_K_M: `6/24` deterministic-safe, median request `1465.535 ms`, p95 `2690.038 ms`, warm-up `3588.515 ms`; too weak as the authority/reasoning brain;
- GPT-5.6 Sol interactive reference: `8/8` deterministic-safe on one reference pass; quality reference only, not a production backend and not latency/RAM-comparable to local llama.cpp;
- Qwen3-4B-Instruct-2507 Q4_K_M: `3/24` deterministic-safe, median `5427.353 ms`, p95 `180717.272 ms`, max `258447.910 ms`, warm-up `13936.281 ms`; severe memory/swap pressure and user-visible S22 instability/hanging.

Decision: do not spend the current phase testing nearby-size 2B/3B/4B general-purpose models on this S22. Preserve the runtime/harness only as experimental infrastructure.

Representative evidence:

```text
.agent/results/gate-b-benchmark-final-host-20260919-1440.json
.agent/results/gate-b-qwen15b-baseline-s22-retry-20260919-1424.json
.agent/results/gate-b-gpt56-reference-verify-20260919-1443.json
.agent/results/gate-b-qwen3-4b-full-benchmark-s22-retry-20260919-1500.json
.agent/results/gate-b-stop-qwen3-4b-s22-20260919-1505.json
```

### Interactive ChatGPT developer relay

Status: `DONE / HOST_GREEN / PROVEN_S22 / DEVELOPER-ONLY`

Purpose: compare strong interactive-model behavior against the same local S22 speech/media stack without turning ChatGPT into a production backend.

Proven:

- repeated 3-turn Orange flow completed end-to-end;
- cleanup deletes transient relay branches and restores call/media state;
- response delivery to Android is fast once a response exists;
- the dominant delay was waiting for the chat-side response;
- TX pacing accounts for time already spent inside a blocking pipe write while retaining a minimum 250 ms playback guard;
- final one-turn physical confirmation completed without TTS truncation.

Evidence:

```text
.agent/results/chatgpt-relay-orange-active-v4b.json
.agent/results/chatgpt-relay-full-host-tx-pacing-v2.json
.agent/results/chatgpt-relay-orange-pacing-confirm-v1.json
```

This relay is a closed benchmark checkpoint. Further relay optimization is not the default next task.

## Edge Gallery + Agent Skills feasibility checkpoint

Status: `FROZEN / PROVEN_S22 PARTIAL / NOT PRODUCT_READY`

This was deliberately separate from the frozen llama.cpp sweep. `Gemma-4-E2B-it` ran through a clean-upstream Google AI Edge Gallery/LiteRT development harness. Official Agent Skills were callable headlessly with proposal-only tools:

```text
say(text)
listenMore()
takeOver(reason)
```

No skill/tool owned dialing, DTMF, Samsung media, credentials, sensitive disclosure, purchase, activation, tariff change or commitment authority.

Positive findings:

- warm official Agent Skills `SAY` decisions repeated around `3.58-4.07 s` in v16;
- late-partial speculative inference on the real Orange greeting completed about `2.33 s` before the simulated final endpoint;
- cancellation was fast (`~8.7 ms` request, stale work released) and a clean post-cancel decision recovered;
- real Orange partial STT exposed the full semantic prompt before endpoint closure;
- the temporary v17 speculative integration built, unit-tested and installed cleanly while remaining diagnostic-only.

Final bounded live result:

- v18 made exactly one allowlisted information-only call to `510100100`;
- cellular RX and local STT succeeded and captured the complete Max prompt;
- Edge returned `backend_agent_network_IOException` before application approval, TTS or TX, so no generated reply was transmitted;
- cleanup returned the call/device state to idle.

Root cause / recovery evidence:

- v19 found the Edge process gone and port 8080 closed after v18;
- v20 historical logcat showed `com.google.aiedge.gallery` crashing while `LocalPhoneAgentRuntime.decide()` was under long monitor contention;
- after relaunch, loopback from the CallBridge UID worked and off-call inference succeeded, so basic transport was sound;
- the first post-relaunch decision took about `10.65 s`;
- Edge consumed about `2.58 GB` total PSS after inference, with swap pressure.

Decision: the current Edge Gallery / Gemma 4 E2B / Agent Skills runtime is not robust or lightweight enough for the live S22 product path. Do not add further probe hacks or repeat Orange calls by default. Reopen only after a materially improved runtime/model/hardware condition or an explicit user decision.

Evidence:

```text
.agent/results/chatgpt-edge-speculative-offcall-benchmark-v16-20260921.json
.agent/results/chatgpt-edge-speculative-live-build-v17-20260921.json
.agent/results/chatgpt-orange-speculative-live-v18-20260921.json
.agent/results/chatgpt-edge-live-transport-diagnosis-v19-20260921.json
.agent/results/chatgpt-edge-exit-reason-offcall-v20-20260921.json
```

---

# Active product gate

## Gate C — CallPlan v1: deterministic call brain with optional bounded language helper

Status: `ACTIVE / PREIMPLEMENTATION AUDIT COMPLETE / IMPLEMENTATION NEXT`

Goal: prepare structured task context and deterministic dialogue policy before the call so common bounded turns do not require a general-purpose LLM.

### Reuse existing authority; do not create a second authority model

The audit of current code and tests establishes these owners:

- `CallTask` — immutable user/operator authority: task description/action/service plus `CallConstraints`, `CallPreferences` and `authorizedFacts`;
- `CallResolvedTarget` — one concrete resolved target. Research/resolution does not itself widen the live dial allowlist;
- `CallWorkflow` — task progress, resolved-target state, concrete pending proposal, user-decision state and structured completion/failure;
- `CallConfirmationPolicy` — deterministic evaluation of one typed `CallProposal` against immutable task constraints/preferences;
- `CallCommitmentGate` — one-shot authorization bound to exactly one concrete proposal;
- application-owned output approval — final speech release remains fail-closed outside normal active negotiation and while commitment authority is pending.

These types stay authoritative. `CallPlan v1` must reference them rather than copying or mutating their authority data.

### Narrow responsibility split

Add an immutable pre-dial execution context, provisionally named `CallPlan`, whose responsibility is only deterministic dialogue planning:

```text
CallPlan
  -> existing CallTask reference
  -> existing CallResolvedTarget reference
  -> immutable known-turn rules
  -> completion criteria
  -> bounded repeat/escalation policy
```

Known-turn rules should be small typed data. For an authorized-fact answer, store the **fact key**, not a copied value; the engine resolves the value from `CallTask.authorizedFacts` at decision time and fails closed if it is absent.

A deterministic plan engine consumes a **final** transcript plus immutable plan/workflow state and may produce a proposal decision such as:

```text
SAY
ASK_REPEAT
PROPOSAL
COMPLETE
TAKE_OVER
```

Those are decision proposals, not execution authority. The plan engine must not:

- dial or widen a dial allowlist;
- mutate `CallTask`, constraints, preferences or authorized facts;
- approve a `CallProposal`;
- issue/consume commitment authorization;
- directly access Samsung media, TTS or cellular TX;
- convert partial/model output into authority.

Counterparty offers remain typed `CallProposal` data and route through the existing `CallWorkflow` -> `CallConfirmationPolicy` -> `CallCommitmentGate` path. Final speech still passes the existing application-owned output approval boundary.

An optional future language helper may only suggest a bounded rule/intent match or wording. The application validates the suggestion against the immutable plan. Unsupported intents, facts, actions or commitments are rejected/fallback; a helper can never extend the plan.

### RED/GREEN test matrix

Implementation begins host-only and TDD-first:

| Case | RED | GREEN |
| --- | --- | --- |
| known question -> authorized fact | no plan decision path | `SAY` proposal resolves exactly the existing authorized fact |
| referenced fact missing | no plan validation | fail closed; never invent a value |
| unknown/ambiguous final transcript | no bounded deterministic classifier | `ASK_REPEAT` or `TAKE_OVER` according to bounded fallback; never guess |
| known counterparty offer | no plan routing | typed `CallProposal` is produced for existing workflow policy, never accepted directly |
| offer outside task policy | preserve authority behavior | existing `CallConfirmationPolicy` returns `NEEDS_USER_DECISION`; plan cannot override |
| commitment without exact permit | preserve gate behavior | blocked; only existing exact one-shot permit can authorize commitment execution |
| speech outside ACTIVE_NEGOTIATION or while permit pending | preserve output policy | dropped/fail-closed |
| target binding | plan has no dialing authority | plan references resolved target but cannot mutate/widen runtime allowlist |
| completion criterion matched | no plan completion route | structured completion proposal/outcome; `CallWorkflow.complete` remains terminal-state owner |
| optional helper proposes unsupported item | helper untrusted | deterministic validator rejects/falls back; no authority widening |
| mutable input collections | no new immutable model yet | plan/rule/fallback collections defensively copied and immutable |
| privacy rendering | new plan can reference sensitive task/target | ordinary `toString()`/diagnostics redact facts and dial address |

### First implementation slice

1. Add only the minimal immutable `CallPlan` / known-turn rule / decision model required for authorized-fact answers and bounded fallback.
2. Write RED tests first for authorized-fact lookup, missing-fact fail-closed behavior, unknown-intent fallback, immutability and redacted rendering.
3. GREEN with the smallest deterministic engine.
4. Reuse `CallTask` and `CallResolvedTarget` by reference; do not duplicate constraints/preferences/facts.
5. Do not modify `CallWorkflow`, `CallConfirmationPolicy`, `CallCommitmentGate`, media, STT/TTS or diagnostics unless a failing test demonstrates a concrete integration gap.
6. Run `bash scripts/verify_host.sh`.
7. This first pure host/data-policy slice requires no S22 gate and no live call.

Gate C exit: common bounded turns do not require general-purpose reasoning, missing user facts are never invented, and no model/helper can grant itself authority.

---

# Later gates

## Gate D — bounded multi-turn real tasks

Status: `AFTER C`

Prove repeated turns, IVR/recovery, unknown-intent escalation, interruption/cancellation rules, structured completion/outcome and explicit user-decision surfaces. Start with non-committing tasks; add real commitments only behind existing one-shot authorization.

## Gate E — local audio-model experiments

Status: `LATER`

Only after the text/product baseline is strong, compare current mobile-feasible audio-understanding and later true speech-to-speech/full-duplex candidates. Reuse the same authority and frozen media boundaries.

---

## Preserved but deferred paths

- `LOCAL_PHONE_LLM` — experimental infrastructure only on the current S22;
- `EDGE_GALLERY` — frozen experimental Gemma 4 E2B/LiteRT path; not current product work;
- `LOCAL_MAC_LLM` — retained provider option/experiment;
- `OPENAI_TEXT` — implementation preserved, paid API proof deferred;
- `OPENAI_REALTIME_AUDIO` — preserved/frozen;
- `LOCAL_REALTIME_AUDIO` — future integration point after Gate E feasibility work.

Do not put a standard OpenAI API key on Android.

## Completion discipline

Every behavior change:

1. starts from fresh `main` plus fresh Local Agent status/binding;
2. uses TDD where deterministic behavior is testable;
3. runs `bash scripts/verify_host.sh`;
4. runs only the physical gate required by changed hardware/OEM behavior;
5. updates the existing authoritative docs instead of creating new status files;
6. leaves `main` clean and Local Agent traffic on `agent-control`;
7. deletes temporary work branches after integration.
