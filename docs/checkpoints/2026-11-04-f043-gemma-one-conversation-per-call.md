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

Mac Local Agent task `aicalls-20261104-f043-one-chat-per-call-full-host-qa-v1` submitted against source `bcbe0a2a84781bf79033ccbef7293ce5f29dcc47`, covering full `scripts/verify_host.sh`, all source/test modules, Python, friction audit and F-043 exact wiring invariants. **Check task result before declaring PASS.**

When host QA is green, validate on *exact IDLE S22* with `adb install -r` (no app data clear or unauthorized call) preserving grant and continuation fingerprints and default dialer role. Run only `AndroidGemmaActionLatencyProfileTest` synthetic 2-turn text; report structured JSON PASS and numeric first/warm ms, and sanitized log `conversation_reused=false` on first real synthetic turn, `true` on second. Distinguish this from actual far-end speed, audible RX/TX and CLIR result.

## Status

`F-043 FIXED_IN_SOURCE / QA_PENDING` at checkpoint creation. The actual runtime/field verdict must come from Mac host receipt and, if available, physical S22 instrumentation.
