# AI Calls — UX-02 ConversationHistoryController, closeout (2026-11-07)

**Status: SOURCE_PASS / HOST_PASS / FRICTION_PASS / PHYSICAL_NOT_RUN.** UX-02 is a separate source-only sprint; Sprint 6 UX and UX-01 remain independently closed and accepted.

**Tested source HEAD:** `109c446779257fdfb7806716c6f05e97eeff48e2` on `main`. A later docs-only commit does not change that tested application source.

## Scope and preserved ownership

- Introduced `ConversationHistoryController` with immutable `HistoryScreenState` (`Loading`, `ListReady`, `DetailReady`, `Deleted`, `Failed`), typed `HistoryDeleteResult.Single/Bulk` and one serialized worker/executor. The Activity renders states, keeps Android Back/dialogs, and invokes deletion **only after the existing positive confirmation**.
- `showList` and `showSession` are read-only; generation tokens reject out-of-date UI deliveries after Back/re-navigation. `close()` prevents unstarted IO and late callbacks after destroy. A single deletion-in-progress flag blocks simultaneous duplicate single/bulk deletions.
- `RecordedConversationHistory` remains **the only filesystem authority and deletion implementation**. Its `COMPLETE` marker + parsed `END` verification, canonical-parent/symlink/path traversal checks, and no-root-delete constraints remain. No recorder format migration, second storage, data exports, or archive retention quota.
- `ArchivedConversationPlayer` remains Android-only, guarded for off-call playback. Existing bubble tap→play/stop and dialog cancel behavior are preserved; no Android Telephony/Call, STOP, grant, Samsung/Shizuku, PCM/PFD, TX or Gemini/Gemma code was modified.
- All new Kotlin tests use distinct temporary synthetic directories and do **not** open, alter, or delete any real user's conversation archives.

## Precise commits and receipts

| Stage | Commit / Local Agent receipt | Evidence |
|---|---|---|
| Preflight on Mac | `aicalls-ux02-main-preflight-20261107-v1` | DONE/0; synced from `c294f6cd`, correct `main`, clean worktree, no phone touch |
| RED: seven proposed JVM contracts on isolated prior source | `aicalls-ux02-controller-jvm-red-20261107-v1` | DONE/0; test compiler failed with the expected unresolved `ConversationHistoryController` / `HistoryScreenState`, SDK configured |
| Production extraction + Kotlin tests + source contract updates | `8f2f5ece2608d94d7417912b0ef2cd218b99068b` | New controller, Activity boundary, 7 JVM tests, preserved prior history/bulk contract |
| First GREEN host pass | `aicalls-ux02-controller-green-host-friction-20261107-v1` | JVM 7 PASS, source contracts PASS, friction PASS, APK + AndroidTest compile PASS; full 325 Python tests had one outdated static assertion expecting `history.canDelete(session.id)` in Activity |
| Fix stale UI-source assertion | `109c446779257fdfb7806716c6f05e97eeff48e2` | Test now verifies typed Activity state, the controller's guarded eligibility and `RecordedConversationHistory`'s COMPLETE+END rules |
| Final GREEN | `aicalls-ux02-controller-final-green-host-20261107-v2` | **DONE/0; 7 new JVM PASS; 11 targeted Python PASS; 325 total Python PASS; friction PASS; AndroidTest/APK Gradle PASS; `host_quality_gate_green=true`; git diff --check; Mac clean.** |

**Physical QA:** `NOT_RUN` for UX-02's new APK; `DEVICE_STATUS=NOT_CHECKED` is expected. No S22 installation, ADB/device test, actual phone call, private archive mutation or new image evidence. This is **not** a claim of new physical S22 acceptance. Sprint 6's existing S22 28/28 + 24/24, targeted 4/4, Gemma 2-turn + STOP and 46 private screenshots remain accepted only on their own tested source SHA.

## Behavioral test coverage

The seven new JVM tests cover: typed list/detail eligibility, list-to-detail and detail-to-list stale callback rejection, close/destroy callback suppression, double bulk delete rejection while first is pending, completed-only bulk deletion preserving active/unknown directories, traversal/single-delete refusal for active archives, external symlink isolation, and queued deletion suppressed by close. Existing Android instrumentation/source tests retain cancel confirmation, Back and off-call bubble play→stop; **they were compiled and not rerun on a physical phone for UX-02.**

No new production blocker found. The stale source assertion is a test-maintenance issue fixed in this sprint; do not reopen Sprint 6/UX-01 or repeat their completed hardware runs.

## Next independent sprint

**UX-03 OPEN:** `SkillLabRunner` plus immutable `SkillLabUiState`, dedicated cancellation/timeout/late-callback handling. Begin from fresh actual `main` and clean Mac; use RED→GREEN JVM, friction, host/AndroidTest and an explicit physical QA decision. Do not modify actual Call/TX/STOP authority or real private recordings. UX-04/05, F-080, F-091 and real-call semantic quality remain separate.
