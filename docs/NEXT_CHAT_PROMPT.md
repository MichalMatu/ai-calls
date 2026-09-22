# Next-chat prompt — Gate D continuation

Kontynuuj rozwój repozytorium `MichalMatu/android-ai-call-bridge` z aktualnego checkpointu Gate D.

Pracuj na branchu `gate-d-taskgraph-core` / PR #5 i najpierw pobierz świeży stan repo. Nie zakładaj, że SHA zapisane w starym czacie jest nadal HEAD.

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

Stan wejściowy: Gate D ma już hostowy `CustomTaskGraphCore`, `BOOK_APPOINTMENT` simulator, typed `AuthorizedFactSnapshot`/`FactDisclosurePolicy`, `DialogueFit`, `SupervisorProposalValidator`, read-only `LocalTextCallGateDRuntime` oraz binding `TaskGraphDefinition + AuthorizedFactSnapshot` przez Android/LocalPhone readiness -> coordinator -> prepared call -> `LocalTextCallSession`.

Nie powtarzaj preimplementation audytu ani spike `custom reducer vs KStateMachine`: decyzja v1 jest zamknięta na minimalnym custom reducerze. Nie dodawaj KStateMachine bez nowego konkretnego dowodu, że jest potrzebny.

Najbliższy slice ma być host-only i **shadow-only**. Zacznij od RED contract tests dla realnego finalized-turn path w `LocalTextCallSession`, tak aby przy zbindowanym Gate D:

1. finalized turn tworzył dokładnie jedną bounded `ShadowDialogueObservation` z aktualnego autorytatywnego snapshot/context;
2. obecny PhraseMatrix/CallPlan deterministic result pozostał bez zmian;
3. observer/hypothesis lifecycle był session-owned i odporny na stale generation / cancel / close;
4. observation i zwykłe diagnostics nie zawierały plaintext identity values;
5. hypothesis przechodziła przez istniejący `SupervisorProposalValidator`, a wynik mógł zasilać `DialogueFit` wyłącznie jako diagnostics/candidate data;
6. w tym slice **nie wolno wywoływać `TaskGraphCore.reduce()`**, mutować `CallWorkflow`, wypuszczać modelowego speech/TTS, dialować, ujawniać plaintext facts ani konsumować commitment authority.

Po udowodnionym RED zrób minimal GREEN i targeted regressions. Następnie uruchom pełny host gate / Android CI zgodnie z repo. Nie wykonuj broad refactoru providerów/session/media tylko po to, żeby ten slice przeprowadzić.

Dopiero w osobnym późniejszym slice, po host-green shadow lifecycle, wolno dodać jawny application-owned bridge:

```text
validated candidate
 -> typed TaskGraph event
 -> CustomTaskGraphCore.reduce()
 -> effects as data
 -> istniejący workflow / proposal / confirmation / commitment owners
```

Zachowaj `extract -> validate -> commit` dla wszystkich slot/fact candidates.

Frozen boundaries: nie ruszaj `privileged-helper/`, zamrożonego Samsung media path ani fizycznie sprawdzonego `CallMediaSessionCoordinator` bez osobnego root-cause i jawnej decyzji scope. Jeśli wróci `CallRealtimeMediaSessionTest.pumpFailure...`, najpierw zbadaj test order/pollution; nie naprawiaj media w ramach Gate D na ślepo.

Orange pozostaje persistent checkpointed ServicePack i nie jest teraz głównym celem. Nie wracaj do broad Orange mappingu bez konkretnego powodu z roadmapy.

Jeżeli używasz Local Chat Bridge / Local Agent, użyj wyłącznie świeżego binding envelope z tego nowego czatu. Nie kopiuj starego `agent_binding` z handoffu ani historii. Live-call authorization również nie przechodzi między czatami.

Pracuj autonomicznie w opisanym scope: audit konkretnego seam -> RED -> udowodniony failure -> minimal GREEN -> regressions -> aktualizacja dokumentacji/handoffu. Nie pytaj ponownie o rzeczy jednoznacznie zapisane w repo.