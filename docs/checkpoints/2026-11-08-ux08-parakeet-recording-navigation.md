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


## Zakończone dowody i decyzja QA (2026-11-08, kod `main@9c8229179f88e6421eab99efe58d0e5f42469d01`)

- **Zmiana źródła:** `367cbaeb49bcb90b630ff6602cf17ec0d8970a42`; kontrakt jakości po przesunięciu download do chronionej gałęzi `checkNotNull(transfer).download`: `9c8229179f88e6421eab99efe58d0e5f42469d01`.
- **Mac v1:** `aicalls-ux08-parakeet-recording-shortcuts-mac-20261108-v1` FAILED: 345 Python, 1 fail z nieaktualnym regex `transfer.download`; Kotlin + AndroidTest compile oraz 3 testy celowane PASS. Nie mylić z regresją Parakeet runtime.
- **Mac v2:** `aicalls-ux08-parakeet-recording-shortcuts-mac-20261108-v2` DONE/0: 3/3 nowe testy UX08, 345/345 Python, pełne `verify_host.sh`, Gradle/JVM/lint/APK i audio structural gate PASS, anti-friction PASS, clean HEAD.
- **S22 v1:** `aicalls-ux08-s22-safe-ui-and-fresh-screens-20261108-v1` FAILED PRZED INSTALACJĄ, ponieważ źródłowa bramka była RED dla wcześniejszego 367cbae.
- **S22 v2:** `aicalls-ux08-s22-safe-ui-and-fresh-screens-20261108-v2` agregat FAILED (exit 33) **wyłącznie w skrypcie agregacji screenshotów**. Preflight PASS (SM-S906B, IDLE, rola dialera zweryfikowana); APK i AndroidTest APK install -r PASS bez wipe; `adb am instrument` natywnie `OK (36 tests)`. Brak w stdout 16 linii UI_AUDIT nie jest błędem AndroidJUnit; testy Androida PASS, odbiór pełnej fotomacierzy NIEPOTWIERDZONY.
- **Screenshot recovery:** `aicalls-ux08-s22-fresh-screenshot-recovery-20261108-v1` FAILED na operatorowym adb stat; preflight IDLE PASS. `aicalls-ux08-s22-top16-screenshot-archive-20261108-v1` DONE/0: 16 świeżych zdjęć `-0.jpg` — po jednym z 16 ekranów — w prywatnym pliku `$HOME/Library/Application Support/AI-Calls/UI-audit/2026-11-08-ux08/AI-Calls-S22-UX08-top16-private.zip`, bez starych klatek; IDLE PASS przed/po; checkout czysty.

**Status końcowy:** PRODUCT_SOURCE_MAC_PASS / SAFE_S22_SYNTHETIC_36_PASS / PRIVATE_TOP16_CAPTURE_PASS, mimo czerwonego agregatora pełnej fotomacierzy. QA nie obejmuje realnego 615 MB Parakeet download, rzeczywistego wywołania offline VERIFY, rozmowy, RX/TX ani aktywnego rejestratora; tych wyników nie wolno domniemywać. Nie dokonano zmian w uprawnieniach, danych sejfu, schematach ani grantach. Sześć zakładek bez przebudowy. F-122/123/124/080 nadal OPEN; F-135 tylko SOURCE+UI, F-136 operacyjny pełny parser OPEN.
