# Next-chat prompt — Gate D generic appointment interpretation

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

Nie powtarzaj także zakończonego finalized-turn shadow lifecycle ani application-owned apply bridge.

Aktualny checkpoint ma już:

- host-green real finalized-turn shadow lifecycle w `LocalTextCallSession`, gdzie deterministyczny PhraseMatrix/CallPlan wynik jest ustalany najpierw, observer pozostaje bounded/stale-safe i zero-authority;
- fail-closed `SupervisorProposalValidator`;
- host-green `TaskGraphApplyBridge`, który ponownie sprawdza graph version/current state/generation, legal transition-to-event mapping, provenance, slot scope/authorization/schema oraz authority-bearing slot IDs **przed** redukcją;
- rejected apply candidates nie wywołują reducer; accepted candidate tworzy typed `TaskGraphEvent`, wywołuje `TaskGraphCore.reduce()` raz i zwraca snapshot/event record/effects wyłącznie jako data;
- bridge nie ma workflow/speech/TTS/dial/target/plaintext identity/commitment authority i nie jest automatycznie podpięty do shadow/session path.

Apply-bridge RED: `8041ffa57181a6a5bf75581134b85d6d10347758`, Android CI #473 — expected failure na Host quality gate.

Apply-bridge GREEN: `052f20ea11d20b97ade324ee734a1cff1c43bec3`, Android CI #474 — success pełnego host gate.

Następny slice to **generic appointment interpretation**. Najpierw zrób konkretny seam audit istniejącej logiki w `BookAppointmentSimulator.kt` i obecnego PhraseMatrix/CallPlan, bez broad refactoru. Następnie RED contracts dla reusable typed parsers/normalizers obejmujących co najmniej:

1. daty, relative dates i weekdays;
2. godziny i time ranges;
3. offered appointment candidates;
4. accept/reject/alternative semantics;
5. common identity-field requests.

Dodaj PhraseMatrix dialogue-act coverage tam, gdzie deterministic phrases mają sens. Parser/matcher output pozostaje candidate data only. Zachowaj `extract -> validate -> commit`; nic z parsera nie może bezpośrednio wejść do authoritative TaskGraph context. Finalny apply nadal ma przechodzić przez application-owned validation + `TaskGraphApplyBridge`.

Po udowodnionym RED zrób minimal GREEN, targeted regressions i pełny `bash scripts/verify_host.sh` / Android CI zgodnie z repo. Nie rób broad refactoru providerów/session/media i nie podpinaj produkcyjnego shadow providera jako efekt uboczny tego slice.

Nie przenoś authority do parsera/matchera/observera/modelu/TaskGraph. Target authorization, plaintext fact disclosure, speech/TTS release, proposal approval, user confirmation i commitment pozostają u istniejących application owners.

Frozen boundaries: nie ruszaj `privileged-helper/`, zamrożonego Samsung media path ani fizycznie sprawdzonego `CallMediaSessionCoordinator` bez osobnego root-cause i jawnej decyzji scope. Jeśli wróci `CallRealtimeMediaSessionTest.pumpFailure...`, najpierw zbadaj test-order/test-pollution; nie naprawiaj media w ramach Gate D na ślepo.

Orange pozostaje persistent checkpointed ServicePack i nie jest teraz głównym celem.

Jeżeli używasz Local Chat Bridge / Local Agent, użyj wyłącznie świeżego binding envelope z bieżącego czatu, sprawdź fresh daemon/current-task state w dokładnie bound repo i nie kopiuj starego `agent_binding`. GitHub jest właściwy dla bounded reviewowalnych diffów, Local Agent dla lokalnych buildów/ADB/device evidence. Queue/ACK nie jest sukcesem — wymagaj terminal result. Live-call authorization również nie przechodzi między czatami.

Pracuj autonomicznie: audit konkretnego seam -> RED -> potwierdzenie właściwego failure -> minimal GREEN -> regressions -> aktualizacja dokumentacji/handoffu. Nie pytaj ponownie o decyzje już jednoznacznie zapisane w repo.
