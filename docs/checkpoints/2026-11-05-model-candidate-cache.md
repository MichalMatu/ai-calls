# Model candidates downloaded — 2026-11-05

At the owner's request, direct local Codex prepared three native `.litertlm`
files for comparison. Every file was downloaded from its pinned Hugging Face
revision and verified against the published LFS SHA-256 and byte count.

Cache: `~/Documents/AI-Calls/model-candidates/` (directory 0700, files 0600).
Private manifest: `2026-11-05-manifest.json` in that directory.

| Candidate | File bytes | Revision | Runtime qualification |
|---|---|---|---|
| Gemma 4 E4B IT | 3,659,530,240 | `e6a3892a8c22cf87ff99583996149cf3cb32452b` | Download verified; app provider and S22 inference not yet tested |
| Qwen3 1.7B dynamic INT4 | 977,184,032 | `e3496c7708baf1efe5482c6069a1f94695469ebc` | Card requires LiteRT-LM 0.18.0+; current app uses 0.17.1 |
| Qwen3 0.6B dynamic INT4 | 344,671,744 | `e6bb3e4a1eb3f663153f0c1c12b735d64be657a7` | Earlier artifact documented for 0.17.1; no app/S22 inference yet |

Published and verified SHA-256, in the same order:

```text
0b2a8980ce155fd97673d8e820b4d29d9c7d99b8fa6806f425d969b145bd52e0
2a72330b504a6596d53a2c00923c57173b10f1b83e6a541213ffec08d1fc5de8
03e7da1eb1108b50dffaa9bb52cc7bcbad2eb0c66ca990267f480c1e545d2856
```

Sources: [E4B model card](https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm),
[Qwen3 1.7B card](https://huggingface.co/litert-community/Qwen3-1.7B),
[Qwen3 0.6B compatibility notes](https://huggingface.co/litert-community/Qwen3-0.6B).
The newest 0.6B artifact has different runtime requirements; preserve the pinned
revision above when testing the current 0.17.1 app.

No phone files, profile, runtime dependency, grant or application code changed.
E2B was not downloaded again. Download verification is not model inference or
integration acceptance.

The settings already separate input, dialogue model and output. The currently
working combinations are Android STT or Parakeet → Gemma E2B → Android TTS.
Piper is installed but has the measured voice API defect F-057. Gemma Audio ASR
passed its synthetic set after F-056; the complete audio dialogue backend and
selectable M4 profile remain unqualified. E4B and Qwen need actual providers and
device tests before they can become functional settings choices.
