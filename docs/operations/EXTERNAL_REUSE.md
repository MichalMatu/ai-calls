# Open source — zasady adaptacji


## Selective Reuse / No Unnecessary Rewrite

**Existing Code First.** Dla konkretnej luki lub potwierdzonej słabości istniejącego kodu porównaj maksymalnie 2–3 trafne rozwiązania; jeśli własny kod spełnia wymagania i nie ma dowodu przewagi alternatywy, **KEEP**. Nie rozpoczynaj masowego audytu repozytoriów i nie przepisuj stabilnych modułów dla popularności biblioteki.

1. **Baseline:** wskaż istniejący plik/kontrakt, brakujący przypadek użycia lub mierzalny problem oraz aktualny test/wynik; brak baseline → najpierw własny test.
2. **Kandydat:** konkretny plik, funkcja, test i wersja/commit zewnętrzny; porównaj zachowanie, opóźnienia/CPU/RAM (gdy istotne), niezawodność, pokrycie, Android/Kotlin, rozmiar zależności i koszt utrzymania. Dowód powinien być powtarzalny.
3. **Decyzja:** **KEEP** — obecny kod wystarcza; **IMPROVE** — implementacja własna na podstawie pomysłu lub testu; **ADAPT** — najmniejszy zgodnie licencjonowany fragment kompatybilny z naszym runtime; **REPLACE** — dopiero gdy przewaga jest udowodniona testem porównawczym, a zmiana zmniejsza ryzyko lub złożoność w całym cyklu życia.
4. **Legal & security:** przed adaptacją sprawdź aktualną licencję **konkretnego pliku i zależności**, kompatybilność z dystrybucją, autorstwo, NOTICE i zobowiązania copyleft; przy braku licencji lub niejasnym prawie **reference-only, bez kopiowania**. Zachowaj źródło+wersję+attribution w kodzie/notice zgodnie z licencją. Wzorzec nie uprawnia do kopiowania chronionego kodu.
5. **Gate:** celowane testy zachowania, STOP/cancel/stale output, Call ownership/grant/privacy, degradacji i wydajności; brak spadku bezpieczeństwa. Jeśli wynik jest gorszy albo nieudowodniony, wróć do KEEP. Bez drugiego pipeline'u telefonicznego, alternatywnego controllera i nieuzasadnionych zależności.

**Minimalny zapis decyzji w bieżącym sprincie:** luka → nasz baseline → kandydat (link+commit, licencja) → pomiar/kontrakt → decyzja KEEP/IMPROVE/ADAPT/REPLACE → test. Przy KEEP wystarcza jedno zdanie w wyniku sprintu; nie twórz rejestrów, archiwów ani nowych dokumentów. Research jest pomocą **przed implementacją brakującej funkcji**, nie bramką blokującą każdą zmianę.

**Nie zastępuj lokalnego Android runtime drugim systemem Python/Twilio/Go/JS.** Reużywaj najpierw kontrakty i testy, dopiero potem ewentualne małe kompatybilne fragmenty kodu po sprawdzeniu licencji i notice. Żadnego kopiowania bez prawa/licencji; dla kodu chronionego przechowuj wymagane attribution/notices.

| Projekt | Licencja (stan audytu 2026-11-09) | Wzorzec | Granica |
| --- | --- | --- | --- |
| [Pipecat](https://github.com/pipecat-ai/pipecat) | BSD-2-Clause | `user_turn_controller.py`, `speculation_gate.py`: hold/release/drop, interruption | G6 Kotlin ma własny gate; nie importuj pipeline do APK |
| [call-use](https://github.com/agent-next/call-use) | MIT | lifecycle, idempotent cancel, takeover/resume, typed disposition, testy BDD | Własny `OutgoingIvrDtmfGate` nie jest jeszcze prawdziwym Android tone adapterem |
| [Clinic Voice Agent](https://github.com/nikhilreddy00/clinic-voice-agent) | brak potwierdzonej licencji | availability → hold → confirm → booking ID | pomysły i własne testy, nie kopiuj kodu |
| [DeskRoute](https://github.com/PrabhatMattoo/DeskRoute) | AGPL-3.0 | historia, escalations, struktura danych | reference-only, nie kopiuj UI/serwera do produktu bez osobnej analizy |
| [LiveKit Agents/SIP](https://github.com/livekit/agents) | Apache-2.0 dla wskazanego kodu (sprawdź ponownie przed użyciem) | SIP/VoIP, trunk, dispatch | opcjonalny późniejszy transport, nie wymagany do Android SIM/G6 |

Dotychczasowe zmiany G6 były autorskim Kotlinem inspirowanym wzorcami Pipecat/call-use; **nie kopiowano** źródła zewnętrznych projektów. Nowy transport nie może ominąć exact Call authority, grant, STOP, CallCommitmentGate, ani prywatności. [System](../architecture/SYSTEM.md), [security](../architecture/SECURITY.md).
