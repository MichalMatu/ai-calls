# AI Calls — historyczne rozpoznanie modeli (15.11.2026)

**ARCHIWUM R&D — nieaktywny backlog.** Pliki LFM2.5 Audio Q4 pobrano na S22, lecz nie uruchomiono inferencji ani runtime. Brak zmian profilu/aplikacji. Aktualny plan: [PROJECT](../docs/PROJECT.md).
Maintain Gemma 4 E2B as off-call reference and separate real Telecom evidence.
Real S22 end-to-end Telecom acceptance remains open (PROJECT.md).

## Modele zidentyfikowane historycznie (bez kolejności prac)

| Typ | Candidate | Why for a telephone secretary | Main blockers |
| --- | --- | --- | --- |
| Audio | [LiquidAI LFM2.5-Audio-1.5B](https://huggingface.co/LiquidAI/LFM2.5-Audio-1.5B) | English-native end-to-end speech/text conversation; streaming, potentially replaces STT/LLM/TTS | Different audio runtime/architecture; no evidence of S22 Telecom integration; LFM Open License v1.0 needs commercial review |
| Text | [Ministral 3 3B Instruct 2512](https://huggingface.co/mistralai/Ministral-3-3B-Instruct-2512) | English chat, system instruction, native function calls and JSON; Apache-2.0 | FP8 vision+text model; inspect text-only/quantized footprint and LiteRT-LM support; no S22 inference |
| Text | [IBM Granite 4.0 1B](https://huggingface.co/ibm-granite/granite-4.0-1b) | On-device English dialogue, extraction, function calling, Apache-2.0 | Possible quality ceiling vs E2B; custom tokenizer/template and LiteRT integration unverified |
| Text | [LiquidAI LFM2.5 1.2B Instruct](https://huggingface.co/LiquidAI/LFM2.5-1.2B-Instruct) | Fast small model designed for on-device agentic tasks and tool use | Custom hybrid runtime/template and LFM1.0 license; native LiteRT not validated |

No model above has been measured or certified on this Samsung S22.
Do not assume availability as .litertlm; import only accepts .litertlm
but extension does not establish native compatibility. Validate licenses before
commercial deployment. Compared with Gemma, avoid estimating performance
from published cross-device benchmarks.

## LFM2.5 Audio 1.5B Q4_0 staging receipt — 2026-11-15

**FILES PASS / INFERENCE NOT_RUN.** LiquidAI/LFM2.5-Audio-1.5B-GGUF,
official Q4_0 variant; 4 files / **1,074,794,688 bytes**.
SHA-256 each file matched Hugging Face LFS metadata on Mac and again
after ADB transfer to authorized S22.

- Mac: ~/Documents/AI-Calls/model-candidates/LFM2.5-Audio-1.5B-GGUF/
- S22: /sdcard/Download/AI-Calls-LFM2.5-Audio-1.5B-Q4/
- Files: LFM2.5-Audio-1.5B-Q4_0.gguf,
  mmproj-LFM2.5-Audio-1.5B-Q4_0.gguf,
  vocoder-LFM2.5-Audio-1.5B-Q4_0.gguf,
  tokenizer-LFM2.5-Audio-1.5B-Q4_0.gguf
- Private Mac manifest: S22-STAGING-MANIFEST.json; no model bytes in Git.
- Local Agent receipts in MichalMatu/local-agent / agent-control:
  aicalls-lfm25-s22-staging-preflight-20261115-v1 and
  aicalls-lfm25-gguf-download-and-s22-upload-20261115-v1.

Device was IDLE before and after; no Calls, APK updates, profile
or grant changes. **These GGUF files cannot be imported as LiteRT-LM
models into current AI Calls.** Brak uruchomionego silnika audio GGUF na ARM64 i pomiarów ASR/TTS/speech-to-speech.
LFM Open License v1.0 commercial terms require review.

## Zidentyfikowane modele polskie (archiwum)

- **[Bielik 4.5B v3.0 Instruct](https://huggingface.co/speakleash/Bielik-4.5B-v3.0-Instruct-GGUF)**:
  Polish naturalness candidate, Apache-2.0, existing GGUF Q4.
  Larger than mobile E2B; requires a compatible runtime, not directly
  importable as .litertlm. Its base Bielik 4.5B was initialized from
  Qwen2.5 3B, then independently trained on Polish data, so it is not
  the same model as previously tested Qwen3.5.
- **[Bielik 1.5B v3.0 Instruct](https://huggingface.co/speakleash/Bielik-1.5B-v3.0-Instruct-GGUF)**:
  lighter alternative if 4.5B is too slow or memory-hungry.
- 11B v3.0 web chat: one user-provided, qualitative Polish dialogue
  showed good memory and farewell but prematurely asserted a booking.
  This is not a benchmark of 4.5B, not S22 or timing proof.

## Historyczne wykluczenia

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

## Odczyt wyników

Te obserwacje nie są zleceniem kolejnych eksperymentów. Gemma E2B pozostaje punktem odniesienia. Ewentualna zmiana modelu wymaga nowej decyzji użytkownika po realizacji [planu GSM → SIP](../docs/PROJECT.md).
