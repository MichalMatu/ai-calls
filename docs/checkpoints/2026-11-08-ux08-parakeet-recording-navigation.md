# AI Calls — UX-08: Parakeet / recording semantics / navigation cleanup

Date: 2026-11-08. Baseline: `main@726cd9b57ae0a0eb41c27a5c2d906cfbda479268`. Owner-directed scope: four screenshot-identified UX issues only; no rearrangement of six bottom tabs and no changes to telephony or authority.

## Implemented in source

- **Parakeet:** offline SHA-256 verification is a separate `VERIFY` command, independent of download `START`. The button explicitly says which action is done; mismatched file sizes/hashes offer explicit redownload of ~615 MB. Hashing runs in the foreground service, not UI thread. No automatic network/installation, and model validity check remains pinned.
- **Recording:** legacy JSON field `recording_enabled` retained for ADB contract, now excludes revoked grants. On-screen status distinguishes future attempt permission from current call recording status (no active call vs unknown, never claims an active recorder based on a grant).
- **Navigation:** removed only duplicate links from Incoming/Campaign/USB screens. Editor screens remain accessible via unchanged 6-tab navigation; no settings deleted or copied. Kept campaign STOP/revoke, SIM, strategy and evidence actions intact.
- **Data-first:** technical campaign details collapsed under a descriptive affordance; shorter descriptions and title „Modele i silniki”.
- **Regression:** source `test_ux08_source_contract.py`; instrumented `AndroidUx08NavigationCleanupTest`; existing UI contract and gap threshold adjusted for fewer cards.

## Boundaries and remaining work

Code is `SOURCE_ONLY / QA_PENDING` until exact HEAD Mac gate and S22 synthetic navigation show PASS. Actual Parakeet 615 MB transfer, real phone calls and recording are **NOT EXECUTED**; SHA path structural-only test does not demonstrate a live model download. F-122 (active recording stop semantics), F-123 (attempt correlation), F-124 and F-080 remain OPEN. Private screenshot collection only on Mac/phone; no public screenshot exports. After verified Mac PASS, run only safe UI navigation on IDLE S22 with reinstall `-r` (no wipe), then update checkpoint with exact task receipts.
