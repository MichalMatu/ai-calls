# Handoff — Edge Gallery Agent Skills + Orange IVR latency checkpoint

Date: 2026-09-21

Repository: `MichalMatu/android-ai-call-bridge`

Durable product branch: `main`

Local Agent control branch: `agent-control`

## Start here next time

1. read fresh `AGENTS.md`, this file, `README.md`, `docs/ROADMAP.md` and `docs/NIGHT_AUTONOMOUS_RUN_2026-09-21.md`;
2. read `docs/ARCHITECTURE.md` / `docs/SECURITY_PRIVACY.md` before changing authority, Agent Skills or speculative inference;
3. read `docs/PHASE2D_FREEZE_2026-09-18.md` before any Samsung media change;
4. fetch fresh `main` and `agent-control:.agent/status/daemon.json` before any write;
5. trust the current Local Agent / Chat Bridge identity envelope and fresh daemon binding, not a UUID copied from prose;
6. inspect the exact terminal result of the last task before creating a successor.

Do not trust a commit SHA copied from this handoff as current state. Always fetch fresh `main`.

## Frozen foundation

Target: Samsung Galaxy S22+ `SM-S906B`.

- cellular RX/TX bridge: `DONE / PROVEN_S22 / FROZEN`;
- local Polish STT/TTS: `DONE / PROVEN_S22`;
- provider-neutral text turn + application-owned approval: `DONE / PROVEN_S22`;
- Gate A readiness: `DONE`;
- original llama.cpp Gate B sweep: `DONE / PHONE-LOCAL PATH FROZEN`;
- `privileged-helper/` must remain untouched for this work.

## Edge Gallery checkpoint

The materially different `EDGE_GALLERY` route is active experimental work, not a reopening of the old local-model sweep.

Current development harness facts:

- clean official Google AI Edge Gallery upstream base used for the headless harness: `6353707057ccc524a6e513e73f0d6d5886f348a8`;
- official Play app remains separate/unmodified;
- experimental package: `com.google.aiedge.gallery`;
- loopback API: `127.0.0.1:8080`;
- model: `Gemma-4-E2B-it`;
- bridge provider requires `/health` plus exact `/v1/models` identity;
- development harness is not `PRODUCT_READY` and must remain loopback-only.

Gemma 4 E2B Gate-B-style benchmark was materially better than Qwen 1.5B but still unsafe as authority: approximately `15/24` safe with failures around commitments and sensitive disclosure. Deterministic application policy remains mandatory.

## Agent Skills decision

Official Agent Skills runtime is usable headlessly. Keep it rather than inventing ad-hoc JSON parsing.

Current phone skill tools are deliberately narrow:

```text
say(text)
listenMore()
takeOver(reason)
```

Tools only propose actions. CallBridge validates and executes. **Do not add DTMF yet.** Do not expose dialing, credentials, sensitive-data disclosure, purchases, activations, plan/tariff changes, payments or commitments to the model.

Observed latency:

- full ReAct/load-skill path: functional but too slow for IVR (representative mid-teens seconds, sometimes worse cold);
- already-warm repeated path: successful `SAY` decisions around `4.4 s` and `3.5 s`, then history contamination produced a fast `NONE`;
- explicit session reset before each decision restored deterministic `SAY`, but cost roughly `10.1-11.5 s`;
- therefore the next architecture is `prepare/reset while the counterparty speaks -> fast final decide`, not reset/load after endpoint.

## Real Orange evidence

Allowlisted test number remains `510100100` for controlled information-only validation.

Proven:

1. full cellular RX -> local STT -> Gemma -> application approval -> local TTS -> cellular TX works;
2. Gemma said an information-only prepaid/SIM request and Orange moved into the SIM-related branch, then asked for clarification;
3. Max's greeting is multi-phrase and contains long pauses. A single `onEndOfSpeech` is not final-turn evidence;
4. a recognizer-end candidate + about 4.5 s no-resume hangover captured the full greeting;
5. v12d enabled Android partial results and physically captured the greeting progressively, reaching the full semantic request before final endpoint;
6. v12d still replied too late: first TX was about `38.1 s` after recognizer end, and Orange ended the call before another bounded turn completed.

Latest v12d terminal result:

```text
.agent/results/chatgpt-orange-partial-stt-v12d-20260921.json
```

Key partial-STT observation from that run: the transcript grew continuously from `dobry` through the complete `...powiedz w jakiej sprawie dzwonisz` while Max was still speaking. This is the main latency opportunity.

## Correct endpoint direction

Do not revert to 700/1500/2500 ms silence heuristics as the final product model.

Use evidence-driven state:

```text
speech/begin -> cancel END candidate
partial/segment growth -> speech still active / invalidate stale speculation
onEndOfSpeech -> END candidate
resumed begin/growth -> cancel candidate
later END + bounded no-resume hangover -> close turn
long watchdog -> safety only
```

Measure before productizing. The current probe remains diagnostic evidence tooling, not the product orchestrator.

## Exact overnight objective

Reduce **final endpoint -> first approved TX** enough for a real IVR without weakening authority.

Preferred sequence:

1. offline/device benchmark first, no phone call;
2. split the Agent Skills path into `prepare` and `decide` so model/skill/session setup happens before final endpoint;
3. use stable partial STT for cancelable speculative inference only if it materially helps;
4. when partial text changes materially or speech resumes, cancel/invalidate stale speculation;
5. at final endpoint, require final transcript/goal match and application-owned approval before TTS/TX;
6. target a repeatable warm final-decision path around the previously observed few-second range, not 10-30+ s;
7. only after offline/device latency and safety are good, make one controlled Orange call;
8. first goal is information-only prepaid/SIM navigation and capture the **next Orange response**; no authentication, account data, purchase, activation, tariff change or commitment;
9. if the fast path cannot be made robust on Gemma 4 E2B, freeze it with evidence and return to Gate C instead of adding hacks.

## Product architecture guardrails

- frozen Samsung media stays frozen;
- do not grow `LocalPhoneLlmLiveCallProbe` or `DiagnosticProbeActivity` into product orchestration;
- do not bypass `TextCallAgentBackend`, `TextCallTurnController` or application approval;
- no speculative output may reach TTS/TX early;
- no model/tool output may widen the dial allowlist;
- Gate C / `CallPlan v1` remains the productization path after this Edge feasibility checkpoint;
- paid OpenAI work remains deferred.

## Night automation

For autonomous work with Local Agent Chat Bridge, follow `docs/NIGHT_AUTONOMOUS_RUN_2026-09-21.md` exactly. Work sequentially from terminal evidence, use one bounded successor task at a time, and pause instead of guessing when user action or a materially new product decision is required.
