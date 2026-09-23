# Next-chat prompt — Gemma full-download physical acceptance requires explicit start

Kontynuuj `MichalMatu/android-ai-call-bridge` z aktualnego `main`. Pobierz świeży `origin/main`, fresh daemon i current Local Chat Bridge binding. Przeczytaj `AGENTS.md`, `README.md`, `docs/HANDOFF_NEXT_CHAT.md`, `docs/ROADMAP.md`, `docs/ARCHITECTURE.md`, `docs/SECURITY_PRIVACY.md`, `docs/HANDOFF_PROTOCOL.md`; freeze doc przed media changes.

Gate D/media pozostają proven/frozen. Gemma runtime, SAF lifecycle, provider/readiness są PROVEN_S22. Pinned acquisition source, downloader oraz progress/cancel/operation-gate są HOST_GREEN. Explicit source/license/2.59 GB confirmation/cancel UI jest PROVEN_S22.

Pełny sieciowy download 2,588,147,712 B **nie został uruchomiony**. Nie traktuj `kontynuuj`, handoffu, podłączonego telefonu ani readiness jako zgody na taki transfer.

## Pierwszy task

1. Fresh HEAD/daemon/binding + baseline.
2. Jeżeli operator w bieżącym czacie jawnie każe uruchomić pełny ~2.59 GB transfer, wykonaj fizyczny acceptance przez istniejący UI: before-stat/hash -> positive confirmation -> progress -> installer verify -> atomic activation -> final hash/staging/ownership -> no-call Gemma regression.
3. Jeżeli nie ma jawnej zgody na pełny transfer, nie uruchamiaj go automatycznie. Możesz audytować/ulepszać lifecycle robustness, ale zachowaj restart-from-byte-0 i fail-closed semantics albo jawnie zaprojektuj resume przed implementacją.
4. Nie wykonuj live calla bez osobnej świeżej autoryzacji konkretnego numeru/targetu i zadania.
5. Nie ruszaj frozen Samsung media/privileged-helper bez nowego root cause.
