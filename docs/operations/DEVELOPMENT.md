# Rozwój i QA

`main` → gałąź robocza → QA → PR → merge. Po merge usuń gałąź roboczą. Stałe gałęzie to `main` i `agent-control`. Testy uruchamiaj przez [test-profile.sh](../../scripts/bin/test-profile.sh).

- `preflight` sprawdza diff, dokumentację i granice modułów. `full` obejmuje JVM, lint, Python i kontrole statyczne.
- `device-build` pakuje APK aplikacji i testów. `lab-ui` wykonuje wybrane testy Android na emulatorze. Oba wyniki mają odrębny zakres.
- S22+ wymaga IDLE, zgody, kopii APK, kontroli podpisu i SHA oraz potwierdzenia hosta UID 2000/Binder po instalacji. Próby GSM wymagają osobnej zgody.
- Wyniki wersji i otwarte próby zapisuj w [PROJECT](../PROJECT.md) z SHA i zakresem.
