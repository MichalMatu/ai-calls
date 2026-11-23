# Budowanie i odbiór

`main`: kod MVP; `agent-control`: zadania i raporty Mac Local Agenta. Testy: `scripts/bin/test-profile.sh preflight|quick|full|device-build`. Każdy PASS dotyczy konkretnego SHA.

Wydania: wersja SemVer, rosnący `versionCode`, GitHub Release, podpisany APK i SHA256. Nie nadpisywać opublikowanych paczek.

S22+: przed instalacją sprawdzić IDLE, podpis, kopię poprzedniej wersji i host Binder. Bez autoryzacji nie wykonywać rzeczywistych połączeń GSM. Wersja testowa `.s8lab` instaluje się obok głównej aplikacji. [Stan MVP](../PROJECT.md).
