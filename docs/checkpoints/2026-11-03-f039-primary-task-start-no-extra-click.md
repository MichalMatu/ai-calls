# F-039 — restore direct authorized task START (2026-11-03)

## Symptom and exact root cause

The owner correctly rejected an artificial extra-click workflow. At UI source `b4bf2091592a1a7730cb251276a4c0caf07dc37b` (Android behavior inherited from `165e5deb`), `MainActivity.onCreate` exposed `Campaign tools` and `Jeden przycisk: uruchom AI w trwającej rozmowie`, but **not** a direct primary control to start an autonomous previously-authorized campaign.

The latter button `MainActivity.startOneTapSupervisedTest` checks for an **already in-progress phone call** and launches the **two-turn read-only supervised audio pilot**. It is not the app-owned CLIR task. To start the true autonomous campaign, the owner needed an unnecessary navigation tap into `CampaignToolActivity` then another START tap. The earlier assistant also incorrectly prescribed this as a required manual process instead of first auditing it.

**Cause:** UI hierarchy and previous assistant guidance, **not** a new failure of `AppOwnedCampaignService` or the Gemma/STT/TTS call engine. The last UI redesign changed only views; authorized calling remains in `CampaignToolActivity.startAuthorizedCampaign` and `AppOwnedCampaignService`. Do not claim a lost physical dial feature without evidence.

## Source fix (F-039)

- `MainActivity.kt`: new always-visible primary control `Rozpocznij autoryzowane zadanie AI` enters the existing campaign activity with an explicit `EXTRA_START_AUTHORIZED_CAMPAIGN` marker. No redundant click on the second screen.
- The previous `oneTapButton` is **preserved** under `Diagnostyka developerska` and accurately treated as a diagnostic for an ongoing phone call, not the main autonomous operation.
- `CampaignToolActivity.kt`: on a *new* activity instance launched by explicit primary START, invoke exactly the **existing** `ensurePermissionsThen(PendingAction.START)` path. A restored activity (`savedInstanceState != null`) does not repeat the launch. The flow retains `CampaignAuthorizationPolicy.authorize`, existing single grant scope, Android OS permissions, exact dialer role checks, STOP and revocation, and the existing app-owned call executor; this does not introduce a separate authorization step or hidden extra approval.
- `AndroidUiAccordionContractTest.kt`: visible primary START and STOP, supervised pilot hidden by default but still reachable after expanding diagnostics.
- `AndroidOneTapSupervisedButtonTest.kt`: continues checking that the supervised pilot does not dial when phone is IDLE, now via the diagnostics accordion.
- `scripts/tests/test_f039_primary_task_entry.py`: no-device regression: direct UI wiring, re-used authorization/Android role checks, pilot separation and visible STOP.

**Non-goals:** no new calls, grant changes, additional service changes, sneaky reactivation of expired/revoked grants, or bypass of a platform/tool denial. Previous tool safety denial of a *host-initiated real external call* is not a product UI restriction and cannot be removed by an alternate task/ADB command. App feature and assistant operational permissions must not be conflated.

## QA

F-039 listed in canonical `BLOCKERS.md` as `OPEN` then `FIXED_IN_SOURCE` before testing. Mac Local Agent job `aicalls-20261103-f039-primary-start-full-qa-v1` pinned source `1659bdc05bfa64c4bd031d29de2768b07a24a98b`. It covers F-039 source regression, `audit_product_friction.py`, and full `verify_host.sh` (Android app/test builds, tests, lint, Python). **Mac source QA PASS**: task `aicalls-20261103-f039-primary-start-full-qa-v1` returned **DONE/0** on pinned `1659bdc05bfa64c4bd031d29de2768b07a24a98b`: F-039 contract **3/3 PASS**, anti-friction **PASS**, full `verify_host.sh` **PASS**, 3 successful Gradle build phases, **247 Python tests PASS**, `host_quality_gate_green=true`, no ADB or calls. The original pre-QA warning is superseded by this exact receipt.

**Physical deployment task queued:** `aicalls-20261103-f039-s22-ui-deploy-nocall-contract-v1` (Mac Local Agent). Requires exact S22/IDLE/default dialer and verified unchanged app grant and continuation hashes; only safe `adb install -r` and UI/diagnostic/model/role instrumentation. **Read the result before claiming installation or S22 PASS.**

Device acceptance must be **off-call**: verify the *existence* of direct start but do not tap it in an instrumentation test with an active grant. A real in-call end-to-end test remains separately open and subject to authorized execution/tool boundaries. No repeat of prior 5/5 UI acceptance unless the changed UI or instrumentation requires it.
