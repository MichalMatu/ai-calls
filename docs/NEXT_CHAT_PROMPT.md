# Next-chat prompt — Gate D apply-bridge continuation

Kontynuuj rozwój repozytorium `MichalMatu/android-ai-call-bridge` z aktualnego checkpointu Gate D.

Pracuj na branchu `gate-d-taskgraph-core`, PR #5. Najpierw pobierz świeży stan repo i nie zakładaj, że SHA zapisane w starym czacie jest nadal HEAD.

Przed zmianami przeczytaj kolejno:

- `AGENTS.md`
- `README.md`
- `docs/HANDOFF_NEXT_CHAT.md`
- `docs/ROADMAP.md`
- `docs/ARCHITECTURE.md`
- `docs/GATE_D_TASKGRAPH_V1_AUDIT_2026-09-22.md`
- `docs/SECURITY_PRIVACY.md`
- `docs/HANDOFF_PROTOCOL.md`
- `docs/PHASE2D_FREEZE_2026-09-18.md` przed jakąkolwiek zmianą media

Nie powtarzaj preimplementation audytu ani spike `custom reducer vs KStateMachine`. Produkcyjnym core v1 pozostaje minimalny application-owned `CustomTaskGraphCore`.

Aktualny checkpoint ma już host-green real finalized-turn shadow lifecycle w `LocalTextCallSession`: deterministyczny PhraseMatrix/CallPlan wynik jest ustalany najpierw; explicit host-bound observer dostaje dokładnie jedną bounded `ShadowDialogueObservation`; lifecycle jest session-owned i stale/cancel/close-safe; hypothesis przechodzi przez `SupervisorProposalValidator`; ordinary diagnostics są redacted i mogą zasilać `DialogueFit` wyłącznie jako candidate/diagnostic data. Publiczny Android path nie ma jeszcze produkcyjnego shadow providera.

Nie powtarzaj tego slice. Następny osobny slice to application-owned apply bridge:

```text
already validated deterministic/supervisor candidate
 -> re-check current generation/state
 -> legal transition/event mapping owned by application
 -> validate slot types/constraints/provenance/authorization
 -> typed TaskGraphEvent
 -> CustomTaskGraphCore.reduce()
 -> effects as data
 -> istniejący workflow / proposal / confirmation / commitment / output owners
```

Zacznij od konkretnego seam audit, potem RED contracts. Udowodnij co najmniej, że stale generation i nielegalny/unmapped transition nie mogą wywołać redukcji; invalid/unauthorized/authority-bearing slots nie mogą wejść do autorytatywnego context; `extract -> validate -> commit` jest zachowane; accepted reduction zwraca effects wyłącznie jako data; reducer output sam nie mówi, nie dialuje, nie mutuje `CallWorkflow` i nie konsumuje commitment authority.

Po udowodnionym RED zrób minimal GREEN, targeted regressions i pełny `bash scripts/verify_host.sh` / Android CI zgodnie z repo. Nie rób broad refactoru providerów/session/media.

Nie przenoś authority do observera/modelu/TaskGraph. Target authorization, plaintext fact disclosure, speech/TTS release, proposal approval, user confirmation i commitment pozostają u istniejących application owners.

Frozen boundaries: nie ruszaj `privileged-helper/`, zamrożonego Samsung media path ani fizycznie sprawdzonego `CallMediaSessionCoordinator` bez osobnego root-cause i jawnej decyzji scope. Jeśli wróci `CallRealtimeMediaSessionTest.pumpFailure...`, najpierw zbadaj test-order/test-pollution; nie naprawiaj media w ramach Gate D na ślepo.

Orange pozostaje persistent checkpointed ServicePack i nie jest teraz głównym celem.

Jeżeli używasz Local Chat Bridge / Local Agent, użyj wyłącznie świeżego binding envelope z tego nowego czatu. Nie kopiuj starego `agent_binding`. Live-call authorization również nie przechodzi między czatami.

Pracuj autonomicznie: audit konkretnego seam -> RED -> potwierdzenie właściwego failure -> minimal GREEN -> regressions -> aktualizacja dokumentacji/handoffu. Nie pytaj ponownie o decyzje już jednoznacznie zapisane w repo.
