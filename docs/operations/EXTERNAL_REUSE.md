# Open source — zasady adaptacji

**Nie zastępuj lokalnego Android runtime drugim systemem Python/Twilio/Go/JS.** Reużywaj najpierw kontrakty i testy, dopiero potem ewentualne małe kompatybilne fragmenty kodu po sprawdzeniu licencji i notice. Żadnego kopiowania bez prawa/licencji; dla kodu chronionego przechowuj wymagane attribution/notices.

| Projekt | Licencja (stan audytu 2026-11-09) | Wzorzec | Granica |
| --- | --- | --- | --- |
| [Pipecat](https://github.com/pipecat-ai/pipecat) | BSD-2-Clause | `user_turn_controller.py`, `speculation_gate.py`: hold/release/drop, interruption | G6 Kotlin ma własny gate; nie importuj pipeline do APK |
| [call-use](https://github.com/agent-next/call-use) | MIT | lifecycle, idempotent cancel, takeover/resume, typed disposition, testy BDD | Własny `OutgoingIvrDtmfGate` nie jest jeszcze prawdziwym Android tone adapterem |
| [Clinic Voice Agent](https://github.com/nikhilreddy00/clinic-voice-agent) | brak potwierdzonej licencji | availability → hold → confirm → booking ID | pomysły i własne testy, nie kopiuj kodu |
| [DeskRoute](https://github.com/PrabhatMattoo/DeskRoute) | AGPL-3.0 | historia, escalations, struktura danych | reference-only, nie kopiuj UI/serwera do produktu bez osobnej analizy |
| [LiveKit Agents/SIP](https://github.com/livekit/agents) | Apache-2.0 dla wskazanego kodu (sprawdź ponownie przed użyciem) | SIP/VoIP, trunk, dispatch | opcjonalny późniejszy transport, nie wymagany do Android SIM/G6 |

Dotychczasowe zmiany G6 były autorskim Kotlinem inspirowanym wzorcami Pipecat/call-use; **nie kopiowano** źródła zewnętrznych projektów. Nowy transport nie może ominąć exact Call authority, grant, STOP, CallCommitmentGate, ani prywatności. [System](../architecture/SYSTEM.md), [security](../architecture/SECURITY.md).
