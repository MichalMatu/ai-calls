# AI Calls — roadmap po działającym Gemma RAW (2026-11-04)

**Stan, nie plan:** 2 rzeczywiste sesje Gemma Direct na S22, **8/8 oraz 7/7** STT → Gemma RAW → pełny lokalny TX, bez `DIRECT_REJECTED`; dynamiczny, zgodny z grantem `TaskBrief` CLIR i menu czterech prompt-skills, dolny prywatny panel rozmowy. Mac host QA i exact S22 off-call cztery tury PASS, obecny APK z kodu `268be1f8f13502c7202822438b501706e0fcb0e1`. Obserwacja właściciela: rozmowa działa, ale Gemmie brakuje dozwolonych danych, a pierwsza odpowiedź zaczęła się przed końcem komunikatu infolinii. **Nie** potwierdzono włączenia CLIR. [Checkpoint](checkpoints/2026-11-04-raw-live-dialogue-facts-turn-taking-freeze.md).

## Następny sprint — bez ruszania udanego toru media/modelu

### A. F-049: poprawne przejmowanie tury IVR

- Zdiagnozować na prywatnym lokalnym RX/STT, dlaczego pierwsze 3 słowa powitania + 1,5 s ciszy uruchamiają ACK i pierwszą odpowiedź, mimo że automat może kontynuować komunikat. Oddzielić rzeczywisty koniec tury od pauzy IVR.
- Dodać deterministyczny replay/test na odpowiedź **po zakończeniu zapowiedzi**, z zachowaniem szybkich odpowiedzi na krótkie rzeczywiste pytania. Nie wprowadzać sztywnej pauzy dla każdej wypowiedzi, sztucznego limitu tur, ani nowego koniecznego kliknięcia.
- Osobno sprawdzić, czy neutralny ACK można wyemitować bez przedwczesnego zagłuszenia. Zmiany za warstwą mediów, bez naruszania zamrożonego Samsung/Shizuku RX/TX. RED → GREEN → pełna regresja.

### B. F-048: autoryzowany dostęp do potrzebnych faktów

- Z prywatnego transkryptu zidentyfikować **typy** danych wymagane przez infolinię, bez ujawniania wartości w kodzie, GitHub, promptach lub wynikach Local Agenta.
- Zbudować typowany `FactRequest`, katalog bezpiecznych identyfikatorów i odpowiedź aplikacji. `FactDisclosurePolicy` weryfikuje grant, dokładny cel, odbiorcę, zakres, stan STOP; dopiero wtedy `IdentityVault` może zwrócić wymagany plaintext do kontrolowanej, jednorazowej wypowiedzi. Brak faktu → rozstrzygnięcie przez właściciela, nie halucynacja.
- Rozbudować `TaskBrief` wyłącznie o nazwy dopuszczonych umiejętności/faktów, nigdy globalną listę wartości prywatnych; nie mylić prompt-skills z wykonywalnym tool-calling. Testy prawidłowego faktu, odmowy, niezgodnego grantu, cofnięcia, zmiany odbiorcy i prompt injection.
- Dopiero po testach hostowych i off-call sprawdzać dalszy live dialog, jedynie w ważnym grancie i świadomie uruchomiony przez użytkownika na telefonie.

### C. F-047 / F-045 i faktyczny efekt

- **F-047:** marker `RX_INTERRUPTED` jest obecny i zaobserwowany; odzyskanie urwanego STT po zakończeniu Call może służyć tylko prywatnej diagnostyce, nigdy Gemma/TX po STOP.
- **F-045:** niezależna kontrola semantyczna swobodnej odpowiedzi Gemmy, nowych zobowiązań/zakupów i wypowiadanych danych. Wykonywalne skutki nadal tylko przez aplikacyjny commitment gate i niezależny dowód potwierdzenia.
- **Zamknięcie zadania:** niezależnie potwierdzić realny skutek CLIR, jeśli możliwe; liczba tur i `WRITE_COMPLETE` nie są rezultatem u operatora.

## Status i reguły

[BLOCKERS.md](../BLOCKERS.md) jest jedynym rejestrem statusów, [ARCHITECTURE](ARCHITECTURE.md) opisuje granice modułów, [HANDOFF](HANDOFF_NEXT_CHAT.md) krótką instrukcję wznowienia. Stare checkpointy, QA i wykresy opóźnień pozostają archiwum, a **nie** bieżącą listą pracy. Nie powtarzać testów na niezmienionym SHA tylko dla potwierdzenia historii. GitHub Connector + Mac Local Agent; bez Codex/Work/Actions. Żadnych rozmów lub zmian grantu podczas porządkowania dokumentacji.
