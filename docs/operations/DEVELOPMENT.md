# Rozwój i QA

`main` → gałąź → `preflight` → `full` → `device-build` → PR. Uruchamiaj przez [test-profile.sh](../../scripts/bin/test-profile.sh). `focus <moduł> <klasa>` przydaje się podczas zmian.

- `preflight` sprawdza diff, dokumentację i granice modułów. `full` obejmuje JVM, lint, Python i kontrole statyczne.
- `device-build` pakuje APK aplikacji i testów. `lab-ui` wykonuje wybrane testy Android na emulatorze. Oba wyniki mają odrębny zakres.
- S22+ wymaga IDLE, zgody, kopii APK, kontroli podpisu i SHA oraz potwierdzenia hosta UID 2000/Binder po instalacji. Próby GSM wymagają osobnej zgody.
- Wyniki wersji i otwarte próby zapisuj w [PROJECT](../PROJECT.md) z SHA i zakresem.
