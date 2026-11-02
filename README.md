# AI Calls

**Autonomous local-AI phone task agent for Android.** A person delegates an outcome in natural language; the application plans and performs ordinary telephone tasks on the person's behalf. **The supervised call pilot is a test harness, not the product definition.**

The target device is the Samsung Galaxy S22+ (`SM-S906B`). The implementation uses Android Telecom/telephony, Samsung/Shizuku cellular RX/TX, Polish STT/TTS, deterministic task state and a local Gemma 4 dialogue skill. The intention is that execution happens **on the phone with local AI**, not by having a person click through every dialogue or by requiring ChatGPT/ADB to serve as the product runtime.

## Product contract

```text
user delegates a concrete telephone task and permitted scope
 -> local AI interprets goal, target(s), constraints and needed facts
 -> application authorizes and schedules bounded attempt(s)
 -> check device/SIM/permissions and absence of an unrelated active call
 -> autonomously dial the approved target
 -> understand IVR and live replies: cellular RX -> Polish STT
 -> deterministic workflow/PhraseMatrix -> local Gemma 4 when needed
 -> validate response and permitted data disclosure -> Polish TTS -> cellular TX
 -> carry out authorized read-only inquiries or typed changes
 -> verify the factual outcome (and external state when relevant)
 -> stop/retry/escalate according to the task; end only the test's own call
 -> return result, evidence and next action to the user
```

Generic examples include inquiries, appointments, reservations and changes or cancellations of **user-authorized** services. The system must be designed for user-specified ordinary, lawful destinations, **not hard-coded to Orange, `*100`, or a test whitelist**. Authorized tasks may involve reading, submitting or updating the user's own account data when the task's granted scope explicitly permits it.

**Delegation and developer autonomy:** The owner authorizes self-directed code changes, building, deploying to the designated test handset, read-only diagnostics, tests, routine retries, and cleanup within already accepted task scope. Do not request a new confirmation for the same unchanged approved action. This is a product/development objective, **not** an unrestricted standing grant to dial unspecified numbers, alter arbitrary third-party data, reveal personal details, spend money, or bypass Android, telecom-provider, legal or external tool safeguards. The application stores a specific, revocable authorization scope for external actions; permissions and consequential actions still need their applicable authority. Details: [Autonomous operation](docs/AUTONOMOUS_OPERATION_MODE.md), [Task authority](docs/GENERIC_PHONE_TASK_AUTHORITY.md), [Security](docs/SECURITY_PRIVACY.md).

## Actual status — 2026-11-02

- **PASS on S22, off-call:** Polish STT/TTS, local Gemma 4, Shizuku, approved speech pipeline, no-call safety and the one-tap supervised pilot's idle-state instrumented acceptance. The one-tap button is installed and verified; it **attaches to an existing call**, not a fully autonomous dialer.
- **OPEN:** genuine cellular RX/STT/local AI/TTS/TX, audible response at the other end and physical STOP acceptance. A real-call screenshot showed `MODE_IN_COMMUNICATION` while the Samsung media path expects `MODE_IN_CALL`; do not label routing as working until measured and fixed.
- **OPEN:** a working app-owned autonomous call controller and verified termination of the intended call. The current CLIR campaign executor refuses autonomous dialing because its `CampaignOwnedCallController` adapter is not installed. A separate ADB global-endcall experiment has only targeted unit-test acceptance, not physical in-call proof.
- An experimental Orange dial-and-global-cleanup runner remains unqualified and its external tool execution was blocked; do not treat that as a successful product call.

The immediate next hardware step is only one acceptance gate **toward** the full product; it is not a change of the product goal to permanent manual calling. A useful complete product milestone must demonstrate a user-authorized **real** call from start through measurable audio/dialogue, actual task result, cleanup and independently verified `IDLE`.

## Engineering

Read [AGENTS.md](AGENTS.md), [Architecture](docs/ARCHITECTURE.md), [Roadmap](docs/ROADMAP.md), and the [handoff](docs/HANDOFF_NEXT_CHAT.md). Build/test/device work uses **GitHub Connector + Mac Local Agent**; do not use Codex, ChatGPT Work or GitHub Actions. Verify the current `main` HEAD and exact Mac host/device; use existing PASS receipts and avoid redundant test churn. No result is PASS merely because code was committed or a task was queued.
