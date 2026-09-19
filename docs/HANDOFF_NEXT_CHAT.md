# Handoff — ChatGPT relay checkpoint closed; Gate C next

Date: 2026-09-19

Repository: `MichalMatu/android-ai-call-bridge`

Durable product branch: `main`

Local Agent control branch: `agent-control`

## Start here next time

1. read fresh `AGENTS.md`, this file, `README.md` and `docs/ROADMAP.md`;
2. read `docs/ARCHITECTURE.md` / `docs/SECURITY_PRIVACY.md` when touching authority or privacy;
3. read `docs/PHASE2D_FREEZE_2026-09-18.md` before any Samsung media change;
4. fetch fresh `main` and `agent-control:.agent/status/daemon.json` before any write;
5. use only the current Local Agent binding from that daemon status.

Do not trust a commit SHA copied from this handoff. Always trust fresh `main`.

## Stable foundation

Target: Samsung Galaxy S22+ `SM-S906B`.

- cellular RX/TX bridge: `DONE / PROVEN_S22 / FROZEN`;
- local Polish STT/TTS: `DONE / PROVEN_S22`;
- provider-neutral text turn + application-owned approval: `DONE / PROVEN_S22`;
- end-of-utterance detection: `DONE / PROVEN_S22`;
- product-owned pre-dial `READY_TO_DIAL`: `DONE / HOST_GREEN / PROVEN_S22` off-call;
- frozen media checkpoint: `59b0505537a53306acdab6a2a66ca6eed2b3f1c0`.

Do not redesign the Samsung media path while working on planning/dialogue.

## Phone-local LLM decision

Gate B is closed. The general-purpose phone-local LLM route on this S22 is frozen.

- Qwen2.5-1.5B Q4_K_M: operationally light enough, but only `6/24` deterministic-safe in the frozen benchmark and unsafe as the authority/reasoning brain;
- Qwen3-4B-Instruct-2507 Q4_K_M: only `3/24` deterministic-safe, extreme latency tail, severe memory/swap pressure and user-visible S22 instability/hanging;
- GPT-5.6 Sol interactive reference: `8/8` deterministic-safe on one reference pass, but not a production backend and not latency/RAM-comparable to local llama.cpp.

Do not resume nearby-size 2B/3B/4B hunting on the S22. Keep the local runtime/harness only as experimental infrastructure.

Detailed numbers remain in `docs/ROADMAP.md` and `.agent/results`; they are intentionally not duplicated here.

## Interactive ChatGPT developer relay — closed checkpoint

A developer-only text relay is now implemented and physically proven:

```text
live S22 call
 -> local STT
 -> transient GitHub request branch
 -> interactive ChatGPT response
 -> ADB delivery
 -> local S22 TTS
 -> cellular TX
```

It is benchmark/evidence tooling only. It requires this interactive ChatGPT session and must not become a production/background backend or an authority layer.

Key evidence:

```text
.agent/results/chatgpt-relay-orange-active-v4b.json
.agent/results/chatgpt-relay-full-host-tx-pacing-v2.json
.agent/results/chatgpt-relay-orange-pacing-confirm-v1.json
```

What is proven:

- 3/3 repeated Orange turns completed end-to-end;
- transient relay branches are deleted during cleanup;
- hangup, unmute cleanup and Bluetooth restoration complete cleanly;
- relay responses reach Android over ADB in roughly hundreds of milliseconds once available;
- in the 3-turn run, the dominant 17–27 s wait was the interactive ChatGPT/human relay wait, not local STT/TTS/media;
- TX pacing now subtracts time already spent inside a blocking pipe write from the remaining playback hold while retaining a minimum 250 ms guard;
- the final 1-turn physical confirmation completed without truncation. Its TX write happened to block only 1 ms, so the optimization correctly produced essentially no artificial latency reduction in that sample.

No further relay optimization is the default next task.

## Current product direction

Next is **Gate C — `CallPlan v1` preimplementation audit**.

The goal is to make useful calls independent of a general-purpose local LLM by preparing structured authority/context before the call and keeping live authority application-owned.

Reuse the existing model instead of creating a parallel system:

- `CallTask`;
- `CallConstraints`;
- `CallPreferences`;
- `authorizedFacts`;
- `CallWorkflow`;
- confirmation and commitment semantics.

The plan should eventually carry only what the call needs: resolved target, explicit goal, authorized facts, constraints/preferences, preset answers/actions, confirmation boundaries, fallback/escalation rules and completion criteria.

Dialogue rule:

```text
known question + authorized fact -> deterministic answer
known choice + rule -> deterministic action
unknown / low confidence -> repeat or escalate
new commitment / sensitive disclosure -> existing application-owned authority gate
optional future model -> language/reasoning helper only, never authority
```

## Exact next task

> Fetch fresh `main` and `agent-control:.agent/status/daemon.json`. Read the current authority/workflow domain classes and tests. Perform a **preimplementation audit only** for `CallPlan v1`: identify the narrowest responsibility split, what existing types are reused, what new immutable data/state (if any) is actually needed, and the TDD test matrix. Do not change frozen Samsung media. Do not resume phone-local model hunting or paid OpenAI API work. Do not implement until the responsibility split and tests are clear.

After that audit, Gate C implementation can begin in TDD if the design is clean.
