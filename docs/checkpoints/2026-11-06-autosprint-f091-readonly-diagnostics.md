# AI Calls — autonomous sprint: F-091 evidence, status reconciliation (2026-11-06)

## Exact source and host scope

Base main/origin `c67a756067dd6d6d29901c01f141fd8c0d016f1c`; read-only Mac Local Agent, `macbook-michal` / `cfea4cc3-b54c-4b6d-b9a6-758d12733e98`. No changes to code, APK, phone, grants, device configuration or personal data.

- `aicalls-20261106-autosprint-f091-evidence-readonly-v1`: DONE/0, the previously referenced private evidence folder exists. One technical summary reports 143414 ms duration, 6 STT turns, 6 model decisions, 7 full TX entries, 1 interrupted RX entry, and 0 storage errors.
- `aicalls-20261106-autosprint-f091-typed-metadata-v1`: DONE/0. The technical summary has `model_decisions` length 6 and `full_tx` length 7 but does not expose an explicit typed tool proposal in that summary.
- `aicalls-20261106-autosprint-f091-action-audit-v1`: DONE/0. Four private structured files inspected by a key/value allowlist, 8 `action_id:SPEAK` and **zero `END_CALL` proposals in the inspected files**. The count may span structured evidence files, so it is not a proof of every turn in one session. No transcripts, WAV, phone numbers, user data or unknown strings were emitted.

## Interpretation

This evidence is consistent with a dialogue/model action-selection gap rather than proving failure of the `ConversationToolExecutor`. The earlier live REQUESTED/VALIDATED/DISPATCHED/CONFIRMED end_call receipts remain valid for the app-owned execution path; they do not certify model proposal reliability in a later conversation. F-091 remains **OPEN for natural closure reliability**, not fully VERIFIED. A deterministic phrase-based disconnect is explicitly not a permitted workaround: quoted farewells, corrections and new requests must not trigger unintended teardown.

The current source has typed end reasons: `IncomingAssistantCoordinator` uses `ConversationStopReason.CALL_ENDED` for remote END and `HUMAN_TAKEOVER` for takeover; `AndroidIncomingAssistant` passes typed reasons into runtime and `ConversationRxInterruptionCode` maps them to safe recorder codes. Thus F-089 may be documented as **FIXED_IN_SOURCE**, while proof of the current APK's physical natural-END coding stays **QA_PENDING**.

## Documentation correction

An obsolete handoff statement incorrectly claimed stage 3 had not started, despite step 1 completed and committed. Reconciled in canonical handoff under F-101. No additional production code or test rerun was necessary for this documentation-only change.

## Remaining work and acceptance

F-092 language/greeting prompt **FIXED_IN_SOURCE / HOST_PASS** at `c67a756`; physical language quality QA_PENDING. F-091 natural close remains OPEN and should be investigated with a controlled **off-call** model action-selection regression and private typed codes; any production fix requires a new focused RED→GREEN and full host gate, and live acceptance only under a concrete approved call scope.

F-080 full recording/player, F-048 exact authorized fact access, F-045 semantic output constraint and F-047 interrupted-RX transcript recovery remain OPEN/partial, requiring separate implementation and/or physical gates. F-099 detailed 30+15 s event timeline and manual/takeover/STOP are still QA_PENDING. These issues are not implicitly solved by clean Git or host QA.

**Next safe action:** select a private, synthetic, multi-turn off-call test of the actual `ConversationReplyBackend` action protocol; verify that explicit user-requested closure produces a typed `END_CALL` without executing a Call, and quoted/corrected farewells remain `NONE`. Do not repeat physical calls without a new authorized scope.
