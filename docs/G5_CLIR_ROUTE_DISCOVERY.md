# G5 CLIR physical acceptance

G5 began as route discovery; the active gate is now iterative physical CLIR execution on the S22. Read `docs/AUTONOMOUS_OPERATION_MODE.md` before continuing.

## Current physical state

```text
Orange campaign route = *100
CLIR enabled = false
physical enable acceptance = incomplete
```

The latest independent network interrogation reports caller ID as not restricted. Never infer success from route entry, model output, permit consumption or call termination.

## Live dialogue path

```text
readiness + IDLE
 -> real Orange call
 -> known turn: script/PhraseMatrix
 -> bounded unknown turn: Gemma action router
 -> unresolved/TAKE_OVER: live supervisor in the same call
 -> application output approval
 -> commitment only through shared CallCommitmentGate
 -> factual Orange result
 -> independent *#31# state check
 -> cleanup to IDLE
```

Relevant physical findings already absorbed into the implementation:

- Orange asks for the service number (`podaj dowolny numer twojej usługi...`);
- the real Gemma router maps this to `DISCLOSE_AUTHORIZED_FACT(PHONE)`;
- live endpointing is 1.5 s trailing silence / 60 s hard capture to tolerate IVR pauses;
- recurrent unsupported facts fail closed to takeover;
- supervisor relay is dialogue fallback, not an identity transport.

## Identity boundary and current blocker

`PHONE` must resolve locally:

```text
IdentityVault
 -> AuthorizedFactSnapshot
 -> FactDisclosurePolicy
 -> exact CLIR task/target/state/generation
 -> ALLOW
 -> plaintext resolved only on-device
 -> approved local speech
```

The local resolver is implemented and host-green; Android Keystore-backed vault storage is physically proven on S22. The remaining blocker is safe local enrollment of the real service number. Do not place that plaintext in Git, Local Agent task JSON, normal logs, ServicePacks or model/supervisor context, and do not bypass external platform controls that block such transfer.

Before the next real call, prove the complete local disclosure path on S22 with a synthetic value, then enroll the real value through an app-owned local mechanism.

## Commitment and completion

For a real mutation use only the generic lifecycle:

```text
accepted exact authorization scope
 -> CallExternalEffect.SetService(CLIR=<desired state>)
 -> deterministic validation
 -> one fresh shared CallCommitmentGate permit
 -> reviewed commitment speech/execution
 -> exact permit consumption
 -> separate factual external-success evidence
 -> independent network-state verification
 -> workflow completion
```

Do not add a `ClirCommitmentGate`. Permit consumption is not external success.

## Physical-first rule

Real calls are the acceptance loop after code/device prerequisites are green. During an owned `OFFHOOK` call, relay/call handling has priority over docs/builds. Preserve only sanitized evidence and return the phone to `IDLE`.

After enable succeeds, execute the inverse `SET_SERVICE(CLIR=false)` flow and move recurrent supervisor cases into script/PhraseMatrix or Gemma skills.

Historical G5a/G5b discovery remains in Git history and `scripts/aicall_tools/orange/history/`; it does not need to be repeated in this runbook.
