# Handoff — Edge Gallery Agent Skills + Orange IVR latency checkpoint

Date: 2026-09-21

Repository: `MichalMatu/android-ai-call-bridge`

Durable product branch: `main`

Local Agent control branch: `agent-control`

## Start here next time

1. Read fresh `AGENTS.md`, this file, `README.md`, `docs/ROADMAP.md`, `docs/NIGHT_AUTONOMOUS_RUN_2026-09-21.md`, `docs/ARCHITECTURE.md`, `docs/SECURITY_PRIVACY.md`, and `docs/PHASE2D_FREEZE_2026-09-18.md`.
2. Fetch fresh `main` and `agent-control:.agent/status/daemon.json` before any write.
3. Inspect the exact latest Local Agent terminal result before creating a successor task.
4. Do not trust commit SHAs copied from this file as current state; always fetch fresh refs.
5. Keep `privileged-helper/` frozen.

At this handoff moment the daemon is idle. The latest durable `main` before this handoff update was a docs-only commit; product code underneath still came from the previously proven live-flow line. All Agent Skills / Orange experiments below are diagnostic and live on `agent-control`, not product code on `main`.

## Frozen foundation

Target: Samsung Galaxy S22+ `SM-S906B`.

- cellular RX/TX bridge: `DONE / PROVEN_S22 / FROZEN`;
- local Polish STT/TTS: `DONE / PROVEN_S22`;
- provider-neutral text turn + application-owned approval: `DONE / PROVEN_S22`;
- Gate A readiness: `DONE`;
- original phone-local model path: frozen;
- `privileged-helper/` must remain untouched.

## Edge Gallery checkpoint

Headless development harness facts:

- clean official Google AI Edge Gallery upstream base: `6353707057ccc524a6e513e73f0d6d5886f348a8`;
- official Play app remains separate/unmodified;
- experimental package: `com.google.aiedge.gallery`;
- loopback API: `127.0.0.1:8080`;
- model: `Gemma-4-E2B-it`;
- bridge provider requires `/health` and exact `/v1/models` identity;
- harness is development-only, loopback-only, and **not PRODUCT_READY**.

Gemma Gate-B-style safety remained insufficient for authority. Deterministic application policy remains mandatory.

## Agent Skills architecture that worked

Use official Agent Skills runtime, not ad-hoc model JSON parsing.

Phone skill tools remain deliberately narrow:

```text
say(text)
listenMore()
takeOver(reason)
```

Tools only propose actions. CallBridge remains the authority gate and execution layer.

Do **not** add DTMF yet. Do not expose dialing, credentials, sensitive-data disclosure, purchases, activations, tariff/plan changes, payments, contract acceptance, or other commitments to the model.

## Measured Agent Skills behavior

Functional facts from device tests:

- first/cold-ish Agent Skills decisions can be tens of seconds;
- warm no-reset decisions were around `3.5-4.4 s` for safe `SAY`;
- sensitive prompt produced `TAKE_OVER` around `5.15 s`;
- continuing one long ReAct history eventually contaminated state and produced fast `NONE`;
- `resetSession()` before every decision restored deterministic behavior but cost about `10.1-11.5 s` per turn;
- direct preloaded-skill prompting that bypassed the official router was worse and sometimes emitted normal prose instead of a tool call;
- therefore keep the official Agent Skills/ReAct path.

Important additional result:

- adding an `/v1/agent/interrupt` hook around upstream `DefaultAgentRuntimeExecutor.interrupt()` worked;
- interrupt HTTP response: about `9.3 ms`;
- in-flight ReAct released after about `655 ms` and returned no action;
- immediately after interrupt, the next warm decision recovered normally with `SAY` in about `4.11 s`.

Terminal evidence:

```text
.agent/results/chatgpt-edge-agent-interrupt-spike-v13-20260921.json
.agent/results/chatgpt-edge-agent-post-interrupt-v13b-20260921.json
```

This proves cancelable speculative inference is technically viable.

## Real Orange evidence

Allowlisted number for controlled tests: `510100100`.

Proven on a real call:

1. cellular RX -> local STT -> Gemma -> application approval -> local TTS -> cellular TX works;
2. Agent Skills generated an information-only prepaid/SIM request and it was spoken to Orange;
3. Orange/Max greeting is multi-phrase with long pauses;
4. a single `SpeechRecognizer.onEndOfSpeech()` is not final-turn evidence;
5. recognizer-end candidate + `4.5 s` no-resume hangover captured the complete greeting;
6. Android partial STT works very well on the real telephony PCM path.

Key v12d timings:

- complete semantic partial `...powiedz w jakiej sprawie dzwonisz` available at about `12.808 s`;
- final `onEndOfSpeech` around `13.813 s`;
- endpoint closed around `18.380 s`;
- therefore a usable full partial existed about `5.57 s` before endpoint closure;
- a warm Agent Skills `SAY` typically needs roughly `3.5-4.4 s`, so the inference can theoretically fit inside the endpoint hangover;
- current non-speculative live path was still far too slow: first TX came roughly `38.1 s` after recognizer end and Orange terminated before a later bounded turn completed.

Terminal evidence:

```text
.agent/results/chatgpt-orange-agent-skills-endpoint-v9b-20260921.json
.agent/results/chatgpt-orange-partial-stt-v12d-20260921.json
```

## Correct endpoint model

Do not return to fixed total-duration caps or simple `700/1500/2500 ms` trailing-silence tuning.

Use state/evidence:

```text
speech/begin -> invalidate END candidate and stale speculation
partial/segment growth -> speaker still active / invalidate stale speculation
onEndOfSpeech -> END candidate
stable partial during END candidate -> optional speculative Agent Skills decision
speech resumes / transcript changes materially -> interrupt inference + discard result
later final END + bounded no-resume hangover -> close STT turn
final transcript must match the speculative basis before reuse
long watchdog -> safety only
```

No speculative result may reach TTS/TX before final endpoint and application-owned approval.

## Next exact engineering step

Do **one** bounded implementation experiment, not another family of endpoint guesses:

1. keep the proven v9b recognizer-candidate endpointing and v12d partial-STT signal;
2. use the proven warm official Agent Skills runtime;
3. start speculative `decide` only from a sufficiently stable late partial / end-candidate, not from early fragments;
4. if partial text changes materially or speech resumes, call `/v1/agent/interrupt` and discard the stale result;
5. at final endpoint, accept the speculative result only if its transcript basis still matches the final STT text; otherwise do one normal final decision;
6. preserve application-owned output approval before TTS/TX;
7. measure `last meaningful speech -> first TX` and `endpoint -> first TX`;
8. only if offline/device behavior is stable, make one controlled Orange information-only call;
9. goal remains prepaid/SIM information only, with no authentication, account data, purchase, activation, plan change, payment, DTMF, or commitment.

A possible optimization is splitting session reset into `prepare/reset` before/during listening and `decide(skip_reset=true)` after the final/stable transcript. Attempts `v13b/v13c` were only harness-transform experiments and failed mechanically on patch markers (`server route marker missing`), so they are **not evidence that the architecture is wrong**. Fix the harness cleanly if pursuing this route; do not stack more string-rewrite patches.

Evidence:

```text
.agent/results/chatgpt-edge-agent-prepare-decide-v13b-20260921.json
.agent/results/chatgpt-edge-agent-prepare-decide-v13c-20260921.json
```

## Failed / do not repeat blindly

- hard 8 s capture cap;
- 15 s total cap as endpoint logic;
- 700 ms or 1.5 s trailing silence as final solution;
- treating one `onEndOfSpeech()` callback as final utterance completion;
- direct/preloaded prompt bypass of official Agent Skills router;
- reset before every final decision on the latency-critical path;
- repeatedly patching generated shell/Python strings without first materializing a clean source variant.

The last source-inspect helper (`chatgpt-v9b-patched-source-inspect-v14-20260921`) failed only because of nested quoting in the inspection wrapper. It changed no product code and made no phone call.

## Product guardrails

- frozen Samsung media remains frozen;
- do not turn `LocalPhoneLlmLiveCallProbe` or `DiagnosticProbeActivity` into the product orchestrator;
- keep `TextCallAgentBackend`, `TextCallTurnController`, and application approval boundaries;
- no speculative output to TTS/TX before final validation;
- no model/tool output may widen the dial allowlist;
- Gate C / `CallPlan v1` remains the productization path after the Edge feasibility checkpoint;
- if fast Agent Skills cannot be made robust on Gemma 4 E2B, freeze the experiment with evidence and return to Gate C rather than adding hacks.

## Immediate handoff status

- Local Agent daemon: idle at handoff;
- no live call currently running;
- no product Agent Skills experiment committed to `main`;
- latest meaningful success: cancellable warm Agent Skills inference with clean post-interrupt recovery;
- remaining blocker: integrate that cancellation/speculation cleanly with partial STT + final transcript validation, then do one controlled Orange call.
