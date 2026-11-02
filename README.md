# AI Calls

Prototype Android for bounded phone tasks on **Samsung Galaxy S22+ (SM-S906B)**. User-controlled call audio uses Shizuku, local Polish STT, a constrained Gemma 4 dialogue skill (or deterministic script), application-approved speech and local TTS. Google Phone remains the default dialer.

## Current acceptance target — first supervised call

**The app is ready for one *consenting, manually started* supervised test call; live cellular audio has not yet been accepted.** On the phone, open **Pierwsze połączenie — test nadzorowany**. The user starts the call in Google Phone, returns to AI Calls, confirms attaching AI audio and retains control of the call. STOP revokes **AI audio only**, not the cellular call. No unattended dialing, service changes or personal-data disclosure are authorized by this test.

Physical Galaxy S22+ evidence (2026-11-02, off-call):

- Local Polish TTS PCM16/16 kHz → STT exact transcript: **PASS**.
- Local Polish STT → deterministic complete-text backend → application approval → TTS PCM: **PASS**.
- Real on-device Gemma 4 approved dialogue skill, no-call Android instrumentation: **PASS**. Local model is READY; no redownload needed.
- Shizuku Binder, permissions and device readiness: **PASS**.
- Call media correctly refuses to start off-call and returns to IDLE: **PASS**.
- UI refuses supervised attach while no call is active; STOP stays disabled: **PASS**.
- Actual carrier RX/STT/Gemma-or-script/TTS/TX, remote audibility and acoustic STOP: **NOT TESTED**.

A former Google STT `recognition_error_10` issue resolved after re-enabling `com.google.android.as` and `com.google.android.as.oss`. Keep both enabled. The app also has a bounded startup/error handling fix (PR #22).

**Current point of truth:** [Pre-call checkpoint](docs/checkpoints/2026-11-02-before-first-call-checkpoint.md) and [new-chat handoff](docs/HANDOFF_NEXT_CHAT.md). Historical evidence remains in the [STT recovery checkpoint](docs/checkpoints/2026-11-02-ai-calls-s22-polish-stt-recovery.md) and [no-call acceptance checkpoint](docs/checkpoints/2026-11-02-ai-calls-autonomous-nocall-supervised-acceptance.md).

## Safety and architecture

The separate **autonomous CLIR / app-owned dialing** development track remains **BLOCKED**: no proven exact-call owner with targeted hangup. Do not bypass the guard, place an automatic `*100` call, change the default dialer or use a global hangup. A supervised attach-only call is not proof of autonomous call ownership.

The product architecture is:

```text
exact task + authorization -> call audio -> Polish STT
  -> deterministic state / PhraseMatrix -> bounded local Gemma skill or reviewed script
  -> application-owned speech approval -> Polish TTS / PCM uplink
  -> separate factual external-success verification where an effect is authorized
```

Frozen boundaries: Samsung media/Shizuku helper, `IdentityVault` disclosure, app-owned external-effect permits and approval. See [AGENTS.md](AGENTS.md), [ARCHITECTURE](docs/ARCHITECTURE.md), [SECURITY_PRIVACY](docs/SECURITY_PRIVACY.md), and [AUTONOMOUS_OPERATION_MODE](docs/AUTONOMOUS_OPERATION_MODE.md).

## Development and continuation

Run work through **Mac Local Agent + GitHub Connector**. No Codex, Work or GitHub Actions. Exact source quality gate: `scripts/verify_host.sh` (Android unit, lint, debug APK, AndroidTest APK, Python tests, static safety checks). Follow [Local Agent Host Control](docs/LOCAL_AGENT_HOST_FLOW_2026-11-01.md) and [ROADMAP](docs/ROADMAP.md). Read the short handoff before a new session; source code and `main` SHA must be verified afresh.
