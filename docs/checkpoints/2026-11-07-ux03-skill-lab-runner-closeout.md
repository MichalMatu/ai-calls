# AI Calls — UX-03 SkillLabRunner closeout (2026-11-07)

**Status: SOURCE_PASS / HOST_PASS / FRICTION_PASS / ANDROIDTEST_APK_COMPILE_PASS / PHYSICAL_NOT_RUN.**
**Exact tested source HEAD: `b53cb820cb4d1f8eb36c38bca67e77cdc33f1e7a` on `main`.** Later documentation-only changes do not extend the QA proof to another binary.

## Scope and ownership

- Introduced `SkillLabRunner` as the single owner of the synthetic Skill Lab worker, model lifecycle, per-story backend, 120-second response timeout, STOP/cancel, callback fencing and final metric calculation.
- `SkillLabUiState` is an immutable snapshot of phase, turn count, completed turn snapshots, pending turn, score, failure and start readiness. Callback generation + revision reject cancelled, superseded, duplicated and post-close deliveries; complete-turn snapshots allow the Activity to reconstruct the latest result when intermediate UI posts are skipped.
- Existing `AndroidSkillLabSessionFactory` uses the original profile, skill snapshot, incoming scenario instruction, `TOOL_REPLY` format and `DialogueEngineProviders`; no second model provider or new persistence.
- `ConversationSkillLabActivity` renders UI only, retains existing view tags and synthetic Android instrumentation presentation helpers, forwards START/STOP, closes runner on destroy. `SkillLabScoring` remains pure; `END_CALL` is scored as an **observed suggestion**, not actual tool execution or semantic quality.
- **No modifications** to Telecom/Call ownership, grant/revoke, real STOP authority, Samsung/Shizuku, PCM/PFD, RX/TX, recorder, files, contact data or the approved-full-TX-before-end_call ordering. This sprint ran off-call; no private WAV, transcripts, numbers or screenshots were added to GitHub.

## RED → GREEN evidence from Mac Local Agent

Selected host `macbook-michal`, installation `cfea4cc3-b54c-4b6d-b9a6-758d12733e98`; Mac checkout `/Users/michal/agent-workspace/repos/ai-calls/work`. All Host Ops requests and immutable receipts are in `MichalMatu/local-agent:agent-control`.

| Phase | Receipt / source | Verified outcome |
|---|---|---|
| Preflight | `aicalls-ux03-skilllab-preflight-20261107-v1` | **DONE/0**: clean `main@c22d6ee6`, origin sync, Android SDK present, no S22 touch |
| Genuine RED | `aicalls-ux03-runner-red-jvm-20261107-v1` | **DONE/0**: isolated old-source worktree, compilation failed with the expected unresolved `SkillLabRunner` and typed contracts |
| First implementation | `07ca41215fb5db93b6f7b1da1ff41c85b1ad8e0e` | New runner/model factory/Activity renderer and nine JVM contracts |
| First GREEN attempt | `aicalls-ux03-runner-targeted-green-20261107-v1` | **FAILED**: 4/9 tests failed because the fake backend returned raw text rather than required `TOOL_REPLY` JSON; production compilation succeeded |
| Synthetic fixture repair | `d0707a6a680aca89d438b645d1f3989f42107249` | Fake now returns valid `reply/tool` JSON, no production contract change |
| Targeted GREEN | `aicalls-ux03-runner-targeted-green-20261107-v2` | **DONE/0**: 9/9 runner JVM, 9/9 targeted Python source contracts, clean Mac |
| Full QA first attempt | `aicalls-ux03-runner-full-host-gate-20261107-v1` | **FAILED**: 328 Python suite exposed 4 stale Sprint 4/5 source-location assertions after intentional extraction; no host Kotlin compilation failure |
| Updated legacy contracts | `b53cb820cb4d1f8eb36c38bca67e77cdc33f1e7a` | Three Python contract files now verify Activity presentation and actual runner cancellation/decoding/model ownership |
| Final full GREEN | `aicalls-ux03-runner-final-green-host-20261107-v2` | **DONE/0**: 17 focused Python PASS, 9 runner JVM PASS, 328 Python PASS, anti-friction PASS, `scripts/verify_host.sh` reports `host_quality_gate_green=true`; explicit AndroidTest Kotlin compile and both APK assembly PASS; clean Mac; no S22 |

The initial failure receipts are preserved as evidence, not relabelled as PASS. The `explicit_real_call_opt_in_required` diagnostic in host verification is the expected safe refusal, **not** a tested real call.

## Regression matrix

Nine new JVM cases protect two complete quick turns and last result, full corpus/per-story model lifecycle, STOP and late callback, STOP during pending model preparation, injected timeout and late completion, duplicate completion/error first-wins, STOP then new run with queued stale UI events, close/destroy suppression, and double START refusal. Existing UI source/Android instrumentation contracts continue to cover STOP notice, final card/scroll and conservative score wording, with APK compilation, **not** a new on-device run. The 120s timeout is the pre-existing laboratory per-response boundary, not a new telephone-call limit.

**Hardware status:** `DEVICE_STATUS=NOT_CHECKED`, `S22 NOT_RUN` for UX-03; no new APK install, model inference, ADB session or physical calls. Sprint 6's separately accepted exact-device evidence remains historical and is not a physical certification of `b53cb820`. No new product friction blocker was proven; updated tests assert relocated code instead of reintroducing old Activity ownership.

## Handoff

**UX-03 source sprint is closed.** Next separate UX-04: settings/model read-only presentation extraction using existing `AudioEngineProfileStore`, probe/providers and `LanguageModelTransferService`, one responsibility at a time. UX-05 PL/EN, F-080 continuous recording and F-091/real-phone semantic acceptance remain independently OPEN. Do not repeat Sprint 6, UX-01/UX-02 or UX-03 accepted host tests without a new regression.
