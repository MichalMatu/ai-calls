# Next chat prompt — Milestone D closeout / overnight autonomous work

Paste the block below into a fresh ChatGPT window.

```text
[LA_REPO=android-ai-call-bridge] [LA_REPOSITORY=MichalMatu/android-ai-call-bridge]

Kontynuuj projekt `MichalMatu/android-ai-call-bridge` na branchu `work/phase1-live-call-probes`.

To jest nocny handoff. Masz autonomicznie pracować przez około 8 godzin, z inicjatywą, bez czekania na moje potwierdzenia przy zwykłych decyzjach inżynierskich. Jeśli napotkasz prawdziwy physical/user-action gate, udokumentuj go i kontynuuj wszystko, co da się zrobić host-only.

Najpierw przeczytaj W CAŁOŚCI i traktuj jako źródło prawdy:
1. `AGENTS.md`
2. `docs/HANDOFF_NEXT_CHAT.md`
3. `docs/ROADMAP.md`
4. `docs/ARCHITECTURE.md`
5. `docs/PHASE2_DEEP_AUDIT_2026-09-16.md`

Nie odtwarzaj projektu od zera i nie powtarzaj zakończonych fizycznych gate'ów bez konkretnego powodu.

Aktualny product HEAD zapisany w handoffie to:
`72cc290a261432588005f0300ace382aa252deb1`
(`fix: stop downlink when cellular call mode ends`).

Najważniejszy stan:
- Phase 2B i 2C są frozen/proven — nie modyfikuj ich branchy.
- app-death physical gate: GREEN.
- transferred RX PFD close: GREEN.
- transferred TX PFD close: GREEN.
- 20-cycle start/abort logic: 20/20 GREEN.
- natural cellular call-end: znaleziono realny defect, naprawiono `CallModeWatchdog`, ponowny physical gate GREEN.
- 10-min media soak: wykonano 10 × 60 s, wszystkie 10 sesji GREEN, łącznie 600 s realnego bidirectional RX+TX.
- nie zachował się finalny external RSS/FD/thread summary, bo telemetry watcher został uznany przez Local Agent za background-process leak i posprzątany przed analizą. Nie powtarzaj 10 rozmów tylko z tego powodu, jeśli da się zamknąć resource evidence mniejszym testem.

Pierwszy cel nowego chatu: domknąć Milestone D możliwie małym kosztem:
1. zamknąć resource-trend evidence najmniejszym wiarygodnym testem/harnessem;
2. full Python + full Gradle tests + assembleDebug + diff/clean audit po CallModeWatchdog;
3. final security/architecture audit;
4. zaktualizować docs;
5. utworzyć i zamrozić `milestone/phase2d-failsafe-proven-s22-20260918` tylko jeśli wszystkie wymagane dowody są GREEN.

Po freeze NIE twórz kolejnej fazy robustness. Od razu przejdź do Telephone Agent v1.

Cel produktu użytkownika:
"Zadzwoń do przychodni w Sky Tower i umów mnie na wizytę..."
System ma sam znaleźć właściwy numer, zbudować goal/constraints, zadzwonić zwykłą siecią komórkową, prowadzić rozmowę przez AI, negocjować w granicach constraints, wykryć rezultat i zwrócić wynik / opcjonalnie zapisać Calendar. Pytaj użytkownika tylko o materialne decyzje, których nie da się bezpiecznie wywnioskować.

Po freeze zacznij od preimplementation audit Telephone Agent v1, a potem implementuj małymi TDD commitami:
- production `CallMediaSessionCoordinator` zamiast MainActivity/probe jako lifecycle owner;
- states IDLE/BINDING/PREPARING/ACTIVE/STOPPING/FAILED;
- generation/session id, failure reason, Binder death handling, structured telemetry;
- task model: target/action/service/constraints/authorized facts;
- call states RESEARCHING -> READY_TO_DIAL -> DIALING -> ACTIVE_NEGOTIATION -> NEEDS_USER_DECISION? -> COMPLETED/FAILED;
- structured outcome;
- Realtime AI jako conversation engine wewnątrz orchestratora.

Przed kodowaniem OpenAI Realtime sprawdź aktualną oficjalną dokumentację OpenAI w web. Nie wkładaj long-lived OpenAI key do APK; zaprojektuj ephemeral/server-mediated credentials.

Target device:
Samsung S22+ SM-S906B, serial RFCT70L7E8J, Android 16/API36/One UI8, Orange PL, Google Phone, Shizuku shell UID 2000.
Bezpieczny numer testowy `510100100` został już wcześniej autoryzowany. Preferuj jednak host-only pracę, jeśli live call nie jest konieczny.

Preserve proven Samsung invariants z handoffu: RX ordering, system/shell attribution, CALL_ASSISTANT TX, mono->stereo dopiero na boundary, PFD ownership, shared fail-safe lifetime, brak per-frame Binder, TAKE OVER lokalnie.

Local Agent:
repository id: `android-ai-call-bridge`
agent binding: `c25f88c0-4682-414c-8062-c47fa4034cb0`
control branch: `agent-control`
Każdy task JSON musi mieć dokładnie ten `agent_binding`. Nie uruchamiaj local Codex. Przed pisaniem na ten sam branch sprawdź aktywny task/result.

Na początku potwierdź remote HEAD, clean worktree i Local Agent state. Następnie działaj samodzielnie według powyższego celu.
```
