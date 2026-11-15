# AI Calls — future local model candidates, 2026-11-15

Status: **research backlog only**. No new phone inference, import, downloads,
runtime or profile changes. English-first market. Maintain Gemma 4 E2B as the
qualified off-call reference; do not conflate phone-call and synthetic evidence.
Real S22 end-to-end Telecom acceptance remains open (PROJECT.md).

## English-first: recommended research order

| Priority | Candidate | Why for a telephone secretary | Main blockers |
| --- | --- | --- | --- |
| P1, separate R&D | [LiquidAI LFM2.5-Audio-1.5B](https://huggingface.co/LiquidAI/LFM2.5-Audio-1.5B) | English-native end-to-end speech/text conversation; streaming, potentially replaces STT/LLM/TTS | Different audio runtime/architecture; no evidence of S22 Telecom integration; LFM Open License v1.0 needs commercial review |
| P1, text | [Ministral 3 3B Instruct 2512](https://huggingface.co/mistralai/Ministral-3-3B-Instruct-2512) | English chat, system instruction, native function calls and JSON; Apache-2.0 | FP8 vision+text model; inspect text-only/quantized footprint and LiteRT-LM support; no S22 inference |
| P2, fast text | [IBM Granite 4.0 1B](https://huggingface.co/ibm-granite/granite-4.0-1b) | On-device English dialogue, extraction, function calling, Apache-2.0 | Possible quality ceiling vs E2B; custom tokenizer/template and LiteRT integration unverified |
| P2, fast text | [LiquidAI LFM2.5 1.2B Instruct](https://huggingface.co/LiquidAI/LFM2.5-1.2B-Instruct) | Fast small model designed for on-device agentic tasks and tool use | Custom hybrid runtime/template and LFM1.0 license; native LiteRT not validated |

No model above has been measured or certified on this Samsung S22.
Do not assume availability as .litertlm; import only accepts .litertlm
but extension does not establish native compatibility. Validate licenses before
commercial deployment. Compared with Gemma, avoid estimating performance
from published cross-device benchmarks.

## Polish-specific deferred research

- **[Bielik 4.5B v3.0 Instruct](https://huggingface.co/speakleash/Bielik-4.5B-v3.0-Instruct-GGUF)**:
  high-priority Polish naturalness candidate, Apache-2.0, existing GGUF Q4.
  Larger than mobile E2B; requires a compatible runtime, not directly
  importable as .litertlm. Its base Bielik 4.5B was initialized from
  Qwen2.5 3B, then independently trained on Polish data, so it is not
  the same model as previously tested Qwen3.5.
- **[Bielik 1.5B v3.0 Instruct](https://huggingface.co/speakleash/Bielik-1.5B-v3.0-Instruct-GGUF)**:
  lighter alternative if 4.5B is too slow or memory-hungry.
- 11B v3.0 web chat: one user-provided, qualitative Polish dialogue
  showed good memory and farewell but prematurely asserted a booking.
  This is not a benchmark of 4.5B, not S22 or timing proof.

## Excluded from near-term testing

Qwen family: owner excludes following prior G3 decision KEEP_E2B.
Avoid re-running its G3 matrix. Historical comparison (2026-11-09):
Gemma E2B on GPU vs Qwen3.5 2B on CPU, 96 synthetic turns total;
Gemma better latency and rule-based detail retention.
[Archived decision from commit 3e9c461](https://github.com/MichalMatu/ai-calls/blob/3e9c4610f9c915d1430850f83343828ef954764b/docs/checkpoints/2026-11-09-g3-keep-e2b-final-decision.md).

Salesforce xLAM-2 1B/3B is tuned for multi-turn function-calling,
but based on Qwen2.5. Do not shortlist while the Qwen family is excluded.
FunctionGemma 270M is a function router for fine-tuning, **not**
a standalone conversational secretary. Larger 7B/11B voice LLMs
are not immediate on-device S22 priorities.

## Acceptance plan for any future candidate

1. Mac-only/off-call: check exact source, license, runtime/chat template,
   memory, supported tokenizer and native JSON tool contract. If
   incompatible with LiteRT, test as a separate isolated prototype,
   not a production runtime swap.
2. Frozen **English** secretary corpus: appointment proposed vs confirmed,
   caller name/number, correction/retraction, message summary, memory,
   privacy/secret refusal, no fabricated external actions, valid END_CALL.
   Repeat across sessions with identical tool schema; include real
   human evaluation of naturalness in addition to mechanical checks.
3. Measure fresh/new-engine, prewarm, first JSON reply, hot p50/p95,
   output length, repairs, RSS/PSS, crashes and STOP.
4. Only after gate PASS compare to same-prompt Gemma E2B on S22.
   User authorizes any separate changes to selected call profile,
   production APK or real telecom test.

No change to Gemma E2B baseline, selected Parakeet/Piper profile,
phone/grants, schedulers, or real calls.
