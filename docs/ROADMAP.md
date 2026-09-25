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

Current blocker is identity enrollment, not model understanding: the real service number is not yet present in the app-owned vault through a production-safe local enrollment path. The resolver itself is implemented; Android vault/Keystore has been physically proven. Plaintext must not move through Git, Local Agent JSON, durable logs or supervisor/model context.

### Next execution order

1. Confirm whether a suitable app-owned `PHONE` enrollment path already exists; if not, implement the smallest local-only enrollment path with redacted diagnostics and encrypted persistence.
2. Add/run a no-call S22 proof of the complete local disclosure path using a synthetic phone value: exact control -> policy `ALLOW` -> vault read -> local speech text, with no plaintext in logs/evidence.
3. Enroll the real service number locally through that app-owned path; do not route it through Git/relay/task JSON.
4. Run one controlled real `*100` iteration with readiness + `IDLE`, preserving sanitized evidence.
5. Accept enable only after factual Orange success **and** independent `*#31#` confirms restriction active.
6. Then run the inverse `SET_SERVICE(CLIR=false)` acceptance loop.

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
