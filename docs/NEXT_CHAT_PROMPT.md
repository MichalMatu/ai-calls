# Next-chat prompt — Gate D TaskGraph v1

Kontynuuj rozwój repozytorium `MichalMatu/android-ai-call-bridge` zgodnie z aktualnym stanem zapisanym w repo. Pracuj autonomicznie w ramach udokumentowanego scope; nie wracaj do szerokiego mappingu Orange jako głównego celu.

## Najpierw świeży stan

Przed zmianami przeczytaj świeżo:

- `AGENTS.md`;
- `README.md`;
- `docs/HANDOFF_NEXT_CHAT.md`;
- `docs/ROADMAP.md`;
- `docs/HANDOFF_PROTOCOL.md`;
- `docs/ARCHITECTURE.md`;
- `docs/SECURITY_PRIVACY.md`;
- `docs/PHASE2D_FREEZE_2026-09-18.md`.

Pobierz świeży `origin/main` i traktuj `docs/ROADMAP.md` jako autorytatywną kolejność pracy, a `docs/HANDOFF_NEXT_CHAT.md` jako checkpoint. Nie ufaj historycznemu SHA bardziej niż świeżemu repo.

## Local Chat Bridge / Local Agent

Jeżeli ten czat działa przez Local Chat Bridge, użyj wyłącznie świeżego binding envelope dostarczonego w **tym nowym czacie**. Nie kopiuj `agent_binding` z poprzedniego czatu, dokumentacji, historii ani starych tasków.

Pracuj wyłącznie na dokładnie zbindowanym repozytorium. Przed taskiem sprawdź świeży daemon/current-task evidence. Direct GitHub edits stosuj do małych, dokładnie reviewowalnych zmian; Local Agent do lokalnych buildów/testów/Gradle/ADB/device. `.agent` zostaje na `agent-control`. Nie uruchamiaj lokalnego Codex z taska Local Agent i nie restartuj Local Agent jako obejścia problemu.

## Aktywny cel

Aktywny milestone to:

```text
Gate D — hybrid multi-turn Task Engine
```

Pierwszy realny use case:

```text
BOOK_APPOINTMENT
```

Docelowa hybryda:

```text
User goal
 -> CallTask + constraints/preferences/authorized facts
 -> TaskGraph
 -> deterministic PhraseMatrix / typed parsers first
 -> bounded LLM supervisor only for ambiguity/unknown
 -> existing transition ID + typed slots only
 -> deterministic validation
 -> CallWorkflow / CallPlan / output approval
 -> proposal / confirmation / CallCommitmentGate
 -> structured completion
```

LLM pozostaje ważny, ale jako ograniczony supervisor/classifier. Nie może sam tworzyć targetu, arbitrary telephony speech, credentials, nowych executable transitions ani commitmentu. Skills przyjdą później jako task builders/supervisors nad tym samym authority boundary.

## Pierwsza praca w tym czacie

Zacznij od **preimplementation audit TaskGraph v1** bez szerokiego refactoru i bez zmian frozen media.

1. Zmapuj istniejące typy i odpowiedzialności związane z `CallTask`, `CallWorkflow`, CallPlan/CallPlanTurnCoordinator, proposal/confirmation/commitment i prepared/local text session.
2. Ustal najmniejszy TaskGraph v1, który **komponuje istniejących authority owners zamiast ich duplikować**.
3. Zidentyfikuj dokładne miejsce product session ownership — nie zamieniaj diagnostic probes/runners w orchestrator.
4. Zaprojektuj typed state IDs, transitions, slots, guards, bounded recovery i replayable event log.
5. Następnie rozpocznij TDD: RED tests dla core TaskGraph v1, dopiero potem minimal GREEN.
6. Kolejny slice to host-only `BOOK_APPOINTMENT` + deterministic simulated receptionist.

Nie wykonuj prawdziwych połączeń w pierwszym slice. Live calls wymagają świeżej, jawnej autoryzacji użytkownika w tym czacie i dopiero po host/simulation GREEN.

## Orange

Nie usuwaj Orange ani nie traktuj go jako martwego testu. `service-packs/orange/service_tree.v1.json` to pierwszy persistent evidence-backed IVR ServicePack i ma zostać zachowany pod przyszłą obsługę realnych zadań Orange, regresję IVR oraz rozwój standardu ServicePack.

Nie kontynuuj jednak szerokiego mappingu Orange domyślnie. Wróć do niego tylko gdy wspiera konkretny user task lub nową generyczną funkcję ServicePack. Wtedy użyj `docs/ORANGE_MAPPING_RUNBOOK.md`.

## Frozen / safety

Nie ruszaj bez osobnego uzasadnienia:

- `privileged-helper/` i frozen Samsung media path;
- fizycznie sprawdzonego `CallMediaSessionCoordinator` dla stylistycznego refactoru;
- diagnostic runnerów jako product orchestratorów.

Zachowaj istniejących authority owners: `CallTask`, `CallResolvedTarget`, `CallWorkflow`, `CallConfirmationPolicy`, `CallCommitmentGate`, application-owned output approval.

Dla przyszłych realnych rejestracji stosuj politykę z `docs/ROADMAP.md`/`docs/SECURITY_PRIVACY.md`: test-only disclosure+consent na początku, genuine task tylko z user authorization, domyślnie jeden meaningful call na organizację, drugi tylko po wczesnym technical failure lub zgodzie na powtórkę, żadnych emergency/urgent/crisis lines.

## Styl pracy

- audit/root cause przed implementacją;
- TDD: RED -> udowodnij właściwy failure -> minimal GREEN -> regressions;
- małe cohesive zmiany zamiast szerokiej przebudowy;
- nie pytaj ponownie o rzeczy już jednoznacznie zapisane w repo;
- przy dłuższej pracy informuj krótko o postępie;
- po zmianach uruchom targeted tests i `bash scripts/verify_host.sh` przed `HOST_GREEN`;
- physical evidence tylko wtedy, gdy naprawdę wymagane przez zmieniony boundary;
- na końcu większej sesji zastosuj `docs/HANDOFF_PROTOCOL.md` i odśwież handoff + ten prompt.

Rozpocznij od audytu istniejącego domain/task/workflow code pod TaskGraph v1 i przejdź autonomicznie do RED tests, jeśli audyt nie ujawni architektonicznego blokera.
