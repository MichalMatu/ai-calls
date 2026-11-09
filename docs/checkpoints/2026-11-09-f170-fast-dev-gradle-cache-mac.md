# AI Calls — F-170 szybka pętla i cache Gradle — 2026-11-09

**F-170 VERIFIED_SCOPED.** Źródło main@8d06055 po zmianach toolingowych `b6162b0` + `8d06055`; obecny main należy zawsze odczytać ponownie.

## Co zmieniono
- Z `scripts/verify_host.sh` usunięto `:app:assembleDebug`, `:app:assembleDebugAndroidTest` oraz wymuszone `--no-daemon`. Pełna host bramka nadal obejmuje unit/lint/Python/static/audio, ale **nie** pakuje APK.
- `test-profile.sh auto <pre-sprint-sha>` rozpoznaje zmiany od bazy + staged/unstaged/untracked i wybiera tylko adekwatne jednostkowe zadania Kotlin/Android oraz sparowane pliki `test_*.py`. `focus <module> '<test-filter>'` pozwala uruchomić jeden test JVM. Docs-only przechodzą sam preflight. Niepokryte pliki/Python lub build logic kończą `SCOPE_ESCALATION`, bez pozornego PASS.
- Oddzielny `device-build` jest jedynym pakowaniem APK i ma być uruchamiany tylko przed autoryzowaną instalacją S22; same profile niczego nie instalują ani nie dzwonią. Lokalny opt-in hook wykonuje tylko whitespace/static (bez Gradle). GitHub Connector nie uruchamia lokalnych Mac hooków.
- `gradle.properties` już zawiera `org.gradle.caching=true`, `org.gradle.configuration-cache=true`, `org.gradle.parallel=true` i zachowano cache w `~/.gradle/caches` i worktree `.gradle`; bez nowego niebezpiecznego cache wyników Python.

## Rzeczywiste wyniki Mac
- Local Agent task `aicalls-f170-fast-dev-targeted-and-cache-mac-20261109-v1`: **DONE/0, 5/5 etapów**. 13/13 celowanych Python PASS, `auto 31b6410` uruchomiło tylko 13 testów Python, bez APK i Gradle, bash syntax PASS, anti-friction PASS, Git clean. Gradle cache obecny; `./gradlew --status` przed testem wskazywał brak uruchomionego daemon.
- Task `aicalls-f170-cache-warm-gradle-focused-mac-20261109-v1`: **DONE/0, 3/3 etapów**. Dwa uruchomienia samego `:conversation-core:test` w identycznym checkout: **9,73 s i 0,87 s**; w drugiej próbie configuration cache reused, UP-TO-DATE, bez FROM-CACHE (niepotrzebny) i **bez pakowania APK**. Lokalny `hooks-enable` potwierdził `core.hooksPath=scripts/bin/git-hooks`; static preflight PASS, Git clean.

**Nie wykonano nowego pełnego builda, testów S22, instalacji ani połączeń** — nie zmieniano kodu Androida. Wydajność 9,73→0,87 dotyczy dwóch kolejnych prób jednego taska, nie całego builda. Historyczne próby G3/G5.3, native STOP i G6 pozostają na dotychczasowych statusach.

## Następna praca
Preflight → `auto <pre-sprint-SHA>` → tylko gdy zakres jest jednoznaczny `focus` → milestone `full` bez APK → przed faktycznym S22 `device-build` i dokładne device/IDLE/grant guardrails. Nie kasować cache, nie używać `--no-daemon`, nie uruchamiać stale pełnej kompilacji, nie wykonywać testów device na małe zmiany.
