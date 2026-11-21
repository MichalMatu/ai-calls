# Mac Local Agent

`agent-control` służy do zadań i wyników agenta `macbook-michal`; `main` zawiera kod. Zapis zadania **nie oznacza wykonania**: sprawdź świeży heartbeat/ACK, raport i SHA.

Przed pracą: `scripts/local_agent/repo_state.sh`; lokalnych zmian nie resetuj. Następnie `scripts/bin/test-profile.sh preflight`, `quick` lub `full`; `lab-ui` tylko na emulatorze.

S22+: `scripts/local_agent/device_readiness.sh` (bez dzwonienia), IDLE, poprawna SIM/Call i rollback APK/hosta. GSM Incoming/Outgoing testuj dopiero po preflight i zgodzie; zapisuj wynik PASS/HOLD, SHA, zakres i bezpieczne metryki bez PII/WAV.

Brak aktualnego potwierdzenia agenta lub urządzenia = HOLD, nie PASS.
