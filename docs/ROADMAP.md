# Roadmap

## Status legend

- `DONE` — implementation complete for the stated scope.
- `HOST_GREEN` — canonical host verification passed.
- `PROVEN_S22` — physically executed on the target phone.
- `FROZEN` — change only with a concrete root cause.

## Stable foundation

- Samsung cellular media + Shizuku helper — `PROVEN_S22 / FROZEN`.
- local Polish STT/TTS — proven.
- Gemma 4 LiteRT-LM runtime + Gemma-first dialogue action router — `PROVEN_S22`; current real-model off-call probe is 8/8.
- Android Keystore-backed `IdentityVault` — `PROVEN_S22`.
- `BOOK_APPOINTMENT` Gate D — `DONE / HOST_GREEN / PROVEN_S22`.
- generic `CallExternalEffect` + shared one-shot `CallCommitmentGate` — `DONE / HOST_GREEN`.
- `SET_SERVICE(CLIR=true)` validation + separate external-success tracking — `DONE / HOST_GREEN`.
- local `PHONE` fact resolver through `IdentityVault -> AuthorizedFactSnapshot -> FactDisclosurePolicy` — `DONE / HOST_GREEN`.
- private app-owned `PHONE` enrollment into `AndroidIdentityVault` — `DONE / HOST_GREEN`; `PROVEN_S22` pending.

`docs/AUTONOMOUS_OPERATION_MODE.md` remains normative for physical acceptance.

## Active gate — Orange CLIR enable

Physical execution is active and **not yet successful**.

Already established on the real S22/Orange path:

- on-net campaign route is `*100`;
- multi-turn dialogue is `script/PhraseMatrix -> Gemma -> live supervisor`;
- endpointing uses 1.5 s trailing silence / 60 s hard capture to tolerate IVR pauses;
- Gemma correctly classifies the service-number prompt as `DISCLOSE_AUTHORIZED_FACT(PHONE)`;
- unsupported fact requests fail closed;
- commitment and factual success remain application-owned;
- independent state interrogation is available;
- latest independent state: caller ID defaults to **not restricted**.

The code blocker for identity enrollment is closed: the private app-owned enrollment path and reusable authorized PHONE backend are host-green, and the old ADB/host plaintext bootstrap is removed. The remaining blocker is physical proof on S22 plus local enrollment of the real service number. Plaintext must not move through Git, Local Agent JSON, durable logs or supervisor/model context.

### Next execution order

1. Install the exact current-head APK and run the no-call S22 proof using a synthetic phone value: local enrollment -> exact control -> policy `ALLOW` -> encrypted vault read -> application output approval -> local speech, with no plaintext in logs/evidence.
2. Enroll the real service number manually through the private app-owned screen; do not route it through Git/ADB/relay/task JSON.
3. Run one controlled real `*100` iteration with readiness + `IDLE`, preserving typed/redacted evidence.
4. Accept enable only after factual Orange success **and** independent `*#31#` confirms restriction active.
5. Then run the inverse `SET_SERVICE(CLIR=false)` acceptance loop.

## Acceptance invariant

```text
exact authorized task/target/effect
 -> deterministic validation
 -> one fresh shared CallCommitmentGate permit
 -> permit consumed exactly once
 -> factual external success
 -> independent state verification
 -> cleanup to IDLE
```

Authorization, route discovery, model output, permit consumption and call termination are not success evidence by themselves.

After CLIR enable/disable acceptance, return to generic phone-task development rather than building an Orange-specific bot.
