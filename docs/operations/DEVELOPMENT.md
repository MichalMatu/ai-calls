# Rozwój i QA

`main` = punkt golden po PR #29; historycznie sprawdzony commit `18f4c72b`. Existing Code First, licencje/NOTICE, mały diff → QA → commit/tag po fizycznym gate. Jeden bieżący [plan](../PROJECT.md), bez historycznych Markdown checkpointów.

GitHub Connector + [Mac Local Agent](LOCAL_AGENT.md), bez Codex/Work/Actions/schedulerów. `bash scripts/bin/test-profile.sh preflight`, `full` przed golden, `:app:assembleDebug` przed APK. Nie maskować Lint baseline.

S22+ SM-S906B: sprawdź IDLE i model, wykonaj prywatny backup APK. `scripts/bin/start-s8-gsm-shell.sh` to tylko Mac fallback; docelowy przycisk używa lokalnego Kadb. Po reboocie debugowanie bezprzewodowe włącza użytkownik. Bez WAV/PII/kluczy w raportach.
