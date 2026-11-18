# Rozwój i QA

`main` = baza. Existing Code First i licencje/NOTICE; mała gałąź → testy → merge → usunięcie gałęzi. Bez duplikatów planu poza [PROJECT](../PROJECT.md).

GitHub Connector + [Mac Local Agent](LOCAL_AGENT.md); bez Codex/Work/Actions i schedulerów. `bash scripts/bin/test-profile.sh focus app <TestClass>`, potem `preflight`; build/debug po zmianie Androida. Nie uruchamiać full gate bez potrzeby.

S22+ `SM-S906B`: tylko IDLE, za zgodą, z kopią podpisanego APK. `ANDROID_SERIAL=... bash scripts/bin/install-s8-gsm-lab.sh --install` + Binder gate. Po reboocie Mac `s8_gsm_reconnect.py` nasłuchuje ADB. Bez WAV/PII w raportach.
