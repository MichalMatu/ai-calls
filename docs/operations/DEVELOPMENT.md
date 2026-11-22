# Rozwój i QA

Stałe gałęzie: `main` (kod) i `agent-control` (zadania Local Agenta). Krótką gałąź roboczą usuwaj po PR/merge. Nie nadpisuj lokalnych zmian. [Tryb Mac](LOCAL_AGENT.md).

`scripts/bin/test-profile.sh`: `preflight` — diff/dokumentacja/moduły; `quick` — JVM; `full` — host/lint/Python; `device-build` — APK; `lab-ui` — emulator, nie telefon. Każdy wynik dotyczy konkretnego SHA i zakresu.

Wydania: `versionName` SemVer, rosnący `versionCode`, tag `vX.Y.Z`, APK `ai-calls-vX.Y.Z.apk`, SHA256 i krótka lista zmian. Publikuj po QA, nie podmieniaj opublikowanych paczek. Bez Actions.

S22+ testuj dopiero po sprawdzeniu stanu IDLE, podpisu/SHA APK, hosta UID 2000/Binder i rollbacku. Rozmowy tylko po zgodzie, z exact Call/SIM, STOP/takeover i END_CALL po TTS. Wyniki oraz otwarte bramki zapisuj w [PROJECT](../PROJECT.md).
