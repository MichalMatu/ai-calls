# Architecture

## Goal

Bridge an ordinary cellular call on the target Samsung S22+ to selectable AI engines while keeping five ownership boundaries separate:

1. cellular media;
2. speech conversion;
3. model inference;
4. task/dialogue authority;
5. user takeover and fail-safe cleanup.

Failure must move toward a normal human call. Model text or counterparty speech must never widen authority.

## Current proven local path

```text
CallTask / explicit authority
          |
          v
CallWorkflow + deterministic policy
          |
          v
frozen telephony RX
          |
          v
PcmEndOfUtteranceDetector
          |
          v
local on-device STT
          |
          v
TextCallAgentBackend
          |
          v
application-owned output / commitment approval
          |
          v
local on-device TTS
          |
          v
frozen telephony TX
```

One bounded `LOCAL_PHONE_LLM` cellular turn and silence endpointing are physically proven on the S22+.

## Runtime selection

Current selector model:

```text
Audio mode
├── LOCAL_STT_TTS
│   └── text provider
│       ├── LOCAL_PHONE_LLM
│       ├── LOCAL_MAC_LLM
│       └── OPENAI_TEXT          (preserved/deferred)
├── OPENAI_REALTIME_AUDIO        (preserved/frozen)
└── LOCAL_REALTIME_AUDIO         (future)
```

Provider selection does not change task authority or Samsung media ownership.

## Frozen media boundary

`CallMediaSessionCoordinator` owns one privileged RX+TX generation, bind/prepare/start ordering, heartbeat, endpoint leases, helper failure observation and whole-generation teardown.

Continuous PCM crosses the privilege boundary through transferred PFDs. Binder/AIDL is control only.

Internal telephony format:

```text
signed PCM16LE
mono
16 kHz
```

Privileged Samsung implementation remains in `privileged-helper/`:

```text
RX: VOICE_DOWNLINK -> SamsungVoiceDownlinkCapture -> PFD
TX: PFD -> SamsungUplinkPipeSession -> SamsungCallAssistantTrack
    -> USAGE_CALL_ASSISTANT / TELEPHONY_TX
```

Stereo duplication exists only at the Samsung TX boundary.

Before touching this layer, read `docs/PHASE2D_FREEZE_2026-09-18.md`.

## Speech boundary

`LocalSpeechTextPipeline` is the current conservative local-speech turn:

```text
PCM input
 -> OnDeviceSpeechInput
 -> final transcript
 -> TextCallTurnController
 -> complete approved text
 -> LocalTtsSpeechOutput
 -> PCM output
```

It deliberately does not emit TTS PCM until the complete model response has passed application-owned approval.

`PcmEndOfUtteranceDetector` determines normal live input completion. The old fixed eight-second normal wait is gone; eight seconds remains only the hard safety maximum.

## Text-model boundary

Provider-neutral contract:

```text
final transcript
 -> TextCallAgentBackend
 -> complete candidate text
 -> TextCallTurnController
 -> TextOutputApprovalPolicy
```

Model adapters own inference only. They do not own:

- dialing;
- target selection authority;
- workflow mutation;
- commitment authorization;
- Samsung media;
- TAKE OVER.

### Local phone model

Current proven model:

```text
Qwen2.5-1.5B-Instruct Q4_K_M
alias qwen-phone-1.5b
loopback http://127.0.0.1:18115/v1/
```

`IdentityVerifiedLocalPhoneLlmBackend` requires runtime readiness plus exact model identity. `ShizukuLocalPhoneLlmRuntimeGate` and `LocalPhoneLlmRuntimeUserService` provide product-owned start/stop/recovery.

A health response alone is not identity. `/props` alias/model path verification is required because a stale 0.5B server previously occupied the expected port.

## Authority boundary

The existing domain model remains authoritative:

- `CallTask`;
- `CallConstraints`;
- `CallPreferences`;
- `authorizedFacts`;
- `CallWorkflow`;
- `CallConfirmationPolicy`;
- `CallCommitmentGate`;
- output approval policies.

Strict proposal parsing and model output are untrusted input. One approved proposal does not create standing authority for another.

Future `CallPlan v1` must build on these concepts rather than create a parallel authority system.

## Next product ownership boundaries

The next phase should add responsibilities without turning probes or Activities into product orchestrators.

Conceptual target:

```text
pre-call task/research
        |
        v
     CallPlan v1
        |
        v
 Ready-to-dial coordinator
   |       |       |
   |       |       `-> model readiness + warm-up
   |       `----------> local STT/TTS readiness
   `------------------> task/target/authority validation
        |
        v
   READY_TO_DIAL
        |
        v
 product local text-call session
        |
        +-> frozen media generation
        +-> endpointing
        +-> LocalSpeechTextPipeline
        +-> deterministic dialogue state
        +-> selected TextCallAgentBackend
        `-> TAKE OVER / fail-safe cleanup
```

Names above are architectural roles, not a requirement to create one class per box.

### READY_TO_DIAL

Dialing must not race model loading.

A local call may become ready only after:

- task/scenario is valid;
- destination is explicitly authorized;
- STT is usable;
- local TTS is usable;
- selected local model runtime is started;
- exact model identity is verified;
- bounded warm-up succeeds;
- required plan/preset data is available.

A failed readiness check blocks dial rather than degrading silently to a different provider.

### CallPlan and constrained dialogue

The desired local model role is primarily language handling, not unrestricted world planning.

Preferred order for a turn:

```text
caller transcript
 -> deterministic intent/state check
 -> use authorized fact/preset/rule when sufficient
 -> local LLM only for bounded classification/paraphrase when useful
 -> low confidence/unknown -> repeat or escalate
 -> commitment -> existing application-owned gate
 -> approved text -> TTS
```

Research and task preparation may occur before the call in a stronger interactive environment. The resulting plan is data, not new authority: a researched phone number still requires explicit live-call authorization.

## Diagnostic separation audit

Current audit decision:

### Keep cohesive

- `CallMediaSessionCoordinator`: large but safety-cohesive and frozen;
- `CallRealtimeSessionOrchestrator`: large but preserved/frozen and not current work;
- `LocalSpeechTextPipeline`: cohesive speech-turn boundary;
- `TextCallTurnController`: cohesive backend + approval boundary;
- local LLM runtime gate/service: currently cohesive around privileged process readiness.

Do not split these merely for line count.

### Prevent further growth

`DiagnosticProbeActivity` currently routes many unrelated probes. It is diagnostic infrastructure, not a product god object yet, but it is the clearest growth hotspot. If the next change needs another substantial probe route, extract a diagnostic dispatcher/registry first instead of adding more branches.

`LocalPhoneLlmLiveCallProbe` currently combines live evidence orchestration, metrics and a one-turn test workflow. Do not evolve it into multi-turn product logic. The first readiness/live-session work should extract/reuse product-owned orchestration and leave this probe as a thin evidence driver.

`MainActivity` should remain UI/configuration. It must not own the future call session, planning state or model lifecycle.

This is intentionally a targeted separation policy rather than a broad refactor of physically proven code.

## Developer ChatGPT relay benchmark

A future benchmark may use:

```text
S22 RX -> local STT -> Local Agent/ADB -> current ChatGPT conversation
       -> response text -> Local Agent/ADB -> local TTS -> S22 TX
```

This is interactive developer tooling only. It is not an autonomous/background runtime and must not be presented as one. Prefer transcript/reply text over exporting raw call audio.

It exists to provide a strong-model quality reference while keeping the phone STT/TTS and telephony path identical to local-model tests.

## Remote/OpenAI paths

`OPENAI_TEXT` and `OPENAI_REALTIME_AUDIO` code is preserved but API-dependent work is currently deferred.

The standard OpenAI API key remains host/backend-only if this work is resumed. It must never enter Android source, APK, Intent, ADB argv or phone storage.

## TAKE OVER invariant

Every engine preserves local-first cancellation:

```text
stop accepting/releasing AI output
 -> abort local telephony media generation
 -> stop local speech/audio workers
 -> invalidate active model generation
 -> best-effort stop/cancel model/network work
```

The first steps never wait for model or network acknowledgement.

## Diagnostics and evidence

Diagnostics retain bounded state/timing/size/sanitized text only as required for validation. Raw PCM and full call recordings are not default product logs.

`HOST_GREEN` never implies `PROVEN_S22`. Hardware/OEM claims require target-device evidence.
