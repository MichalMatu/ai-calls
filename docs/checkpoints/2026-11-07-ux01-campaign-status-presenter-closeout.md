# AI Calls — UX-01 CampaignExecutionStatusPresenter, closeout (2026-11-07)

**Status: SOURCE_PASS / HOST_PASS / FRICTION_PASS / PHYSICAL_NOT_RUN**.
**Source HEAD tested:** `82d86e3faa256ee5b6d5ec8ab812ea104733bd29` on `main`.
**User-facing Sprint 6 UX remains CLOSED**; its real S22/Gemma acceptance is not part of UX-01 and was not repeated.

## Scope

- Introduced pure `CampaignExecutionStatusPresenter.present(record: String?, locale: Locale)` with typed `CampaignExecutionScreenState`, meaning and tone. It distinguishes missing record, disconnect requested, explicit confirmation, failed cleanup and unverified history, with PL/EN summaries.
- `CampaignToolActivity.updateLastExecutionStatus` now renders the presenter's read-only summary; `CampaignRuntimeStatusStore` still owns the single historical record and the existing `diagnosticStatus` accordion still displays raw, unchanged status.
- Exact diagnostic tokens only: `targeted_disconnect_requested` must **not** claim the owned Call actually ended. Explicit `targeted_disconnect_confirmed` is a recognized future evidence code, **not emitted by the current campaign runtime**, and not proof of external task success. Carrier-reported CLIR with independent verification pending remains unverified.
- No change to `CampaignAgentControl`, grant/revoke, service/executor, Android Telecom, STOP, Samsung/Shizuku PCM/PFD/RX/TX, model execution, files or private archives. No real calls, no S22 installation or image capture.

## Commits and precise receipts

| Source/QA | Receipt | Result |
|---|---|---|
| Sync Mac documentation-only gap | `aicalls-ux01-mac-main-preflight-20261107-v1` | DONE/0: Mac `main` fast-forward `688c707` → `977cc2b`, worktree clean, Sprint 6 functional files identical |
| First RED attempt | `aicalls-ux01-presenter-red-jvm-20261107-v1` | Superseded for evidence: lacked explicit `ANDROID_HOME`; do not use as reliable compiler RED |
| Verified RED in disposable old source | `aicalls-ux01-presenter-red-jvm-sdk-20261107-v2` | DONE/0: known SDK, isolated `977cc2b` source + six proposed tests, Kotlin unresolved presenter/meaning/tone as expected |
| Presenter + 6 JVM tests | `5d1ba963e624f631d1d2e32c685cce71b964c527` | Source extraction; no mutation of campaign execution behavior |
| First GREEN attempt | `aicalls-ux01-presenter-green-host-friction-20261107-v1` | FAILED before build: missing `ANDROID_HOME`, not product defect |
| Initial full gate | `aicalls-ux01-presenter-green-host-friction-20261107-v2` | JVM, friction, APK and AndroidTest compile PASS; full 325 Python suite had 1 outdated static assertion requiring inline Activity mapping; source did not fail JVM |
| Migrated source contract | `82d86e3faa256ee5b6d5ec8ab812ea104733bd29` | Replaced only the stale Sprint 6 test implementation assertion with checks of presenter routing and preserved raw diagnostics |
| Final GREEN and host | `aicalls-ux01-presenter-green-host-friction-20261107-v3` | DONE/0: focused Sprint 6 contract PASS, new Kotlin JVM 6 PASS, anti-friction PASS, `scripts/verify_host.sh` PASS, **325 host Python PASS**, JVM/AndroidTest compile/APK PASS, worktree clean |

**Physical QA:** `NOT_RUN`, intentionally; UX-01 modifies presentation only. The latest S22 Sprint 6 installation and real Gemma receipt remain untouched and do not certify this new source SHA as installed. `DEVICE_STATUS=NOT_CHECKED` in full host gate is expected, not an S22 failure.

## Next

`UX-02 ConversationHistoryController` **OPEN**, independent sprint only after a fresh HEAD/clean preflight. Preserve `RecordedConversationHistory` and `ArchivedConversationPlayer` as owners, enforce completed-only deletion and guard stale callbacks, without touching real archives in tests. Other UX-03/04/05, F-080 and real call-flow requirements remain independent.

No new product blocker discovered; historic F-118 remains VERIFIED. Do not reopen Sprint 6, rerun its accepted S22/Gemma tests, delete old RED evidence or place private media/transcripts in Git.
