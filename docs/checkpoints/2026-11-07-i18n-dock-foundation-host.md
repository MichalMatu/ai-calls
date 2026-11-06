# AI Calls — PL/EN resources and light chat dock: host acceptance

Date: 2026-11-07 (Europe/Warsaw). Base `main@8cba7ae19117da3e1d1fdaeb1cbfa93821559058`.

Scope: `strings.xml` / `values-en/strings.xml` with matching 36+ keys; presentation-only `UiDesign` palette; light `ConversationChatPanel` with an accessible, centered 48-dp chevron, compact History action and conditional clear action. Added a Python parity/source contract and physical Android dock contract. Existing chat feed, history, recording, STOP, grants, model runtime and private audio unchanged.

**HOST PASS:** Mac task `aicalls-20261106-i18n-dock-foundation-gates-v1`, DONE / exit 0. Compile AndroidTest, APK, lint and `bash scripts/verify_host.sh` (275 Python tests) PASS; `ANTI_FRICTION=PASS`.

**S22 QA_PENDING:** `aicalls-20261107-i18n-dock-s22-acceptance-v1` failed before modifying the phone with `exact_s22_adb_transport_unavailable`. Not installed or physically accepted. On reconnection do exact-identity, 2x IDLE, no campaign/pending retry, certificate match, `install -r` with data retained, then new `AndroidLiveChatDockContractTest` plus relevant pre-existing menu/history tests. No real calls authorized.

**LANGUAGE MIGRATION OPEN:** PL/EN resource catalog and translated dock texts are only a foundation; user-selectable Auto/PL/EN and remaining screens are not implemented. UI language must not alter conversation/STT/TTS languages. Complete with native Android per-app locale rules and Android 10-12 fallback, resource key parity and persisted setting tests. Do not claim full app bilingual until every screen has been migrated and physically accepted.
