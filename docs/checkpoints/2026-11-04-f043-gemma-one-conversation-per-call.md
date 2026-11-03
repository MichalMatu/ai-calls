# F-043 — one persistent Gemma Conversation for an entire authorized phone call (2026-11-04)

## Problem

Owner requests **one chat per real conversation**, rather than constructing a new model chat for each speaker turn. This matches the Google AI Edge LiteRT-LM native `Conversation` lifecycle: it owns a stateful chat history and incremental prompt prefill. The prior `LiteRtGemma4Runtime.runGeneration` called `Engine.createConversation(...)` for **every** `generate` and closed it in each `finally`. Keeping the `Engine` warm was not the same as keeping a conversation alive. Three earlier utterances had to be reinserted manually via `DialogueActionTurnMemory.classifierInput`, increasing tokens/time and producing repetitive task introductions. See canonical [F-043](../../BLOCKERS.md).

The official native C++ API explicitly warns **not to reuse a Conversation after `CancelProcess()`**, since its underlying session becomes invalid. Production `DialogueActionBackend` and `TextCallTurnController` issue `cancel()` even after ordinary successful turns; the fix must preserve native context when idle, while still aborting and disposing a genuinely canceled active generation.

## Changes in source

- `Gemma4LiteRtTextBackend.LiteRtGemma4Runtime`: hold one native `Conversation` for **all healthy turns within one backend/call lifetime**. Reuse the same instance on subsequent `sendMessage`, with system instruction and JSON response format configured once. Keep a separate `Engine` (heavy weights) from conversation (per-call state).
- Treat `cancel()` after an already-completed turn as **callback generation invalidation only**. Call native `cancelProcess()` and discard the poisoned conversation **only if a `sendMessage` is still in flight**. On STOP/revoke/backend.close, cancel active work, drain/close conversation and then engine; never reuse an interrupted native context. Prevent an undelivered canceled response from silently entering the next prompt.
- `Gemma4LiteRtTextBackend.resetConversationContext`: explicit reset used for synthetic model prewarming only. `CampaignGemmaPreflight` keeps the already-initialized engine (F-034 optimization) but clears the synthetic `W jakiej sprawie Pan dzwoni?` turn before transferring the classifier to the real call. The next real user message thus starts the actual chat, and subsequent real turns extend it.
- `DialogueActionBackend`: native persistent classifier receives **only the current finalized utterance**; the built-in Conversation now carries previous model/user messages. `DialogueActionTurnMemory` remains as the app-owned anti-repetition safeguard and still supplies bounded context to *stateless* alternative providers. No change to grants, allowed actions, caller identity checks, output approval, or STOP.
- Added privacy-safe `AiCallBridgePerf` markers: `conversation_reused=true/false`, `conversation_turn=N`, and existing engine/conversation/inference durations and GPU/CPU backend. No spoken words/JSON outputs/phone numbers/account data sent to GitHub.
- Unit tests: backend reuse/prewarm reset, native vs stateless history behavior, existing generation cancellation/error/closed state. Android synthetic no-call Gemma 2-turn benchmark can validate that native reuse is real and measure first vs next turn.

## Out of scope and limits

- This creates **one native chat per phone call**, not an unlimited hardware context. Android local model context remains subject to the engine's actual token capacity; do not silently drop/clear history each turn, invent new retry limits, or pretend arbitrary multi-hour conversations fit in 2048 tokens. Future long-call memory compaction requires a separately designed app-owned summary/state mechanism.
- The current production action catalogue is predominantly CLIR-specific. The same per-call model session is a prerequisite for general tasks (appointments, mechanic booking, etc.), **not proof those effects and dynamic per-task policies are implemented yet**.
- Current `LocalTtsSpeechOutput` still initializes local TTS per full response (a separate performance opportunity). F-042 neutral quick ACK and monitoring remain in source, but an actual user-audible fast response must be shown on real S22 call evidence.
- No external call is made by the host QA/benchmark, and there is no workaround around previous host call safety refusal. Private RX/TX recordings remain only on the owner's Mac.

## Validation

**Mac full host QA PASS**: Local Agent `aicalls-20261104-f043-one-chat-per-call-full-host-qa-v1` **DONE/0** on exact source `bcbe0a2a84781bf79033ccbef7293ce5f29dcc47`; `PRODUCT_FRICTION_AUDIT=PASS`, `FULL_HOST_GATE=PASS`, three successful Gradle build/test/lint phases, **248 Python tests PASS**, `host_quality_gate_green=true`, `F043_PERSISTENT_CONVERSATION_SOURCE_INVARIANTS=PASS`; no ADB action, audio upload or call. Native Kotlin unit tests cover synthetic-prewarm reset, same backend across multiple calls to generate, and selective native-vs-stateless history input. Latest F-042 source gate on source `0be652d4` was also `DONE/0` but it has **not** been deployed to the S22.

**First physical attempt deferred — exact phone unavailable.** Mac Local Agent task `aicalls-20261104-f043-s22-one-conversation-synthetic-benchmark-v1` ended rc=81 at the *first read-only precheck*: `EXACT_S22=false`, `PRECHECK=DEFERRED_EXACT_DEVICE_MISSING`. No APK was built or installed, no test instrumented, no grant was modified and no call started. The previously installed S22 APK is still from F-040. On reconnect, recheck exact model/ADB serial, IDLE/default dialer/no active campaign, preference hash baseline; only then build + `adb install -r`, run `AndroidGemmaActionLatencyProfileTest` synthetic 2-turn text, and report JSON PASS plus first/warm durations and whether `conversation_reused=false` on first, `true` on second. Never infer remote acoustic behavior from a no-call test.

## Physical Samsung S22 acceptance after owner STOP AI — PASS (synthetic only)

- Mac Agent read-only `aicalls-20261104-s22-post-stop-no-call-readiness-v1` **DONE/0**: exact `SM-S906B` connected; `PHONE_IDLE=true`, AI Calls default dialer, `CAMPAIGN_SERVICE_RECORD=false`, `CAMPAIGN_CONTINUATION_SAVED=false` and original grant preference file present. Owner explicitly stopped the prior auto-retry campaign; no service/campaign was force-stopped by our tools.
- Mac Agent `aicalls-20261104-f043-s22-post-stop-deploy-two-turn-v3` **DONE/0**, source pin `bcbe0a2a84781bf79033ccbef7293ce5f29dcc47`, `F043_DEVICE_APKS_BUILD=PASS`, app `adb install -r` **PASS**, AndroidTest APK install **PASS**, persisted grant and continuation SHA fingerprints unchanged.
- **Real S22 instrumented 2-turn synthetic Gemma test PASS**; both completions logged as `complete`, acceleration mode **GPU**. Text and model answers were not exported, and the test did not dial, use mic or change the grant.
- Direct, sanitized `AiCallBridgePerf` logs confirm **one actual native conversation**:
  - Synthetic turn 1: `conversation_reused=false`, `conversation_turn=1`, `engine_ready_ms=6709`, `conversation_create_ms=3191`, `generate_ms=6233`.
  - Synthetic turn 2: `conversation_reused=true`, `conversation_turn=2`, `engine_ready_ms=0`, `conversation_create_ms=0`, `generate_ms=3998`.
  - `F043_NATIVE_CONVERSATION_REUSE_PROVEN=true`. The synthetic benchmark's standalone `System.out` turn-duration markers were not surfaced by Android instrumentation (`NOT_REPORTED`), but two independently logged native runtime timings were collected. Do **not** present these as actual telephone EOS-to-TX or far-end audible voice durations.
- `POST_IDLE=true`, `POST_ROLE=true`, `AUTHORIZATION_PRESERVED=true`, `CONTINUATION_PRESERVED=true`. No spontaneous campaign start or carrier call.

This supersedes the earlier `EXACT_S22=false` and later `APP_OWNED_CAMPAIGN_SERVICE_ACTIVE=true` deferments. Those were real, temporary preconditions, not test passes. **F-043 source and physical two-turn chat reuse are now VERIFIED.** F-042 subsecond audible filler, external CLIR outcome, and general appointment/mechanic skills remain separately open.

## Status

**F-043 VERIFIED — one native LiteRT-LM Conversation reused across two actual on-device synthetic turns on S22**, source and Mac QA PASS, installed source `bcbe0a2a`. Physical live-call dialogue timing, quick-ack remote audibility and CLIR result remain **OPEN**. Do not repeat the completed host or two-turn synthetic test without changes.
