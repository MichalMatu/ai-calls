# G5 CLIR physical acceptance

Read `docs/AUTONOMOUS_OPERATION_MODE.md` before continuing.

## Current state

```text
Orange campaign route = *100
CLIR enabled = false
physical enable acceptance = incomplete
```

The latest independent network evidence still reports caller ID as not restricted.

## Product call path

```text
app-owned scoped campaign grant
 -> readiness + IDLE
 -> app-owned exact *100 dial
 -> script/PhraseMatrix
 -> local model for bounded unknown dialogue
 -> late-bound PHONE disclosure through app policy
 -> shared CallCommitmentGate
 -> factual Orange result
 -> independent *#31# verification
 -> cleanup to IDLE
```

ChatRelay/supervisor is developer tooling only and is not a dependency of the app-owned campaign executor.

## Identity boundary

`PHONE` must resolve only on device:

```text
IdentityVault
 -> AuthorizedFactSnapshot
 -> FactDisclosurePolicy
 -> exact task/target/state/generation
 -> ALLOW
 -> plaintext resolved locally
 -> approved local speech
```

Do not place the real value in Git, Local Agent JSON, ADB task payloads, ordinary logs, ServicePacks, model context, supervisor context, or this runbook.

## Physical evidence at 2026-10-08 stop

Already established:

- real Orange path reaches the service-number dialogue;
- local policy can classify the PHONE disclosure turn correctly;
- PHONE vault was present in the later S22 preflight;
- required Android call permissions were granted;
- local phone-LLM runtime/model files were present;
- Shizuku Manager 13.6.0 was installed.

Current blocker:

```text
live_call_readiness_failed = shizuku_binder_unavailable
```

The old expected external Shizuku `start.sh` path is not present on this installation. Resume by fixing the current-version server/binder startup path, not by reopening campaign authority or identity design.

## Commitment and completion

For a real mutation:

```text
valid exact campaign grant
 -> CallExternalEffect.SetService(CLIR=<desired state>)
 -> deterministic validation
 -> one fresh CallCommitmentGate permit
 -> exact one-shot consumption
 -> factual Orange success
 -> independent network-state verification
 -> workflow completion
```

Do not create a service-specific commitment gate. Permit consumption is not factual success.

## Resume rule

Once Shizuku readiness is green, run a real call immediately. Do not insert broad audits, extra synthetic suites, new authorization UI, or unrelated refactors before the physical iteration.

After enable succeeds, run the separately authorized inverse `SET_SERVICE(CLIR=false)` acceptance loop.
