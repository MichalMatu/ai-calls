# AI Calls — roadmap (stan 2026-11-04)

**Źródło statusów:** [BLOCKERS.md](../BLOCKERS.md). Dotychczasowe wyniki i pomiary są udokumentowane w [F-042 pacing 04.11](checkpoints/2026-11-04-f042-ack-pacing-measurement-sprint.md), [F-042 bazowym](checkpoints/2026-11-03-f042-parallel-ack-gemma-latency.md) i [F-043](checkpoints/2026-11-04-f043-gemma-one-conversation-per-call.md). Poniższa kolejność jest planem, nie potwierdzeniem wykonania.

## Priorytet przy wznowieniu: Gemma Direct — pierwszy live-call

[Kompletny checkpoint i dokładny scenariusz](checkpoints/2026-11-04-gemma-direct-next-live-handoff.md). Oddzielny generatywny `GEMMA_DIRECT` i domyślny `SCRIPT_GEMMA` istnieją, a Mac full QA i S22 synthetic 2-turn GPU **PASS**; rzeczywista rozmowa Direct i skutek CLIR **OPEN**.

1. **F-045:** uszczelnić semantyczną kontrolę swobodnego tekstu przed TTS; model może formułować zdania, ale tylko aplikacja autoryzuje nowe zobowiązania/efekty. Dodać testy parafraz i prompt injection. Nie osłabiać STOP, `FactDisclosurePolicy` ani mechanizmu commitment.
2. **F-044:** w kolejnym etapie zbudować `TaskBrief` per grant/CallTask + dozwolone fakty po identyfikatorach + skills i kryteria wykonania. Obecny prompt to **stały CLIR-only brief**, wystarczający do badania dokładnie tego zadania, ale nie do deklaracji wsparcia ogólnych zadań.
3. **Realny test tylko w autoryzowanym zakresie**: zweryfikować S22, `IDLE`, własność połączenia, grant/target/SIM, zgodę na prywatne RX/TX; właściciel uruchamia przycisk **Gemma Direct** w aplikacji. Nie używać zabronionej drogi zdalnego dzwonienia. Zebrać STT → tekst Gemmy → action/source → TTS → RX/ACK/TX WAV, latencję i w miarę możliwości niezależną słyszalność. Wynik CLIR weryfikować oddzielnie. Powrót do `IDLE`, bez przypadkowego retry.

## 1. F-042 — rzeczywisty dialog i opóźnienie (następny punkt)

**Stan 2026-11-04:** korekta czasu TX zapowiedzi, RED/GREEN/pełne QA Mac i bezpieczna instalacja S22 PASS; pomiar słyszalności oraz EOS→pierwszy dźwięk w rzeczywistym połączeniu nadal OPEN.

- Warunek wejścia: sprawdzić dokładny S22+, `IDLE`, bieżącą rolę dialera, grant oraz brak aktywnego połączenia/kampanii; działać wyłącznie w granicach konkretnej autoryzacji. Nie obchodzić wcześniejszej odmowy narzędzia wykonania połączenia.
- Zmierzyć w rzeczywistej rozmowie: wykrycie EOS, STT, pierwsze TX neutralnej zapowiedzi, decyzję Gemmy, TTS i pełny TX; rozdzielić opóźnienie modelu, reguł i syntezy. Potwierdzić słyszalność u odbiorcy oraz brak nakładania się głosów.
- Sprawdzić reakcje na przerwanie mowy, STOP, utratę uprawnień, zakończenie połączenia oraz wznowienie nasłuchu po STT 6/7/pustym transkrypcie.
- Zamknąć F-042 dopiero po danych fizycznych; źródłowe QA i instalacja są już potwierdzone, więc nie powtarzać ich bez zmian.

## 2. Wąskie gardła wydajności — tylko po pomiarze

- Porównać rzeczywiste czasy przygotowania i generacji TTS z czasem Gemmy; rozważyć ponowne użycie instancji TTS i etapowe TX, jeśli dowody wykażą korzyść.
- Rozważyć krótszy, poprawnie walidowany kontrakt odpowiedzi i skuteczne zarządzanie kontekstem przy długim połączeniu. Trwały czat F-043 nie znosi limitu okna kontekstowego.
- Każda zmiana z testem jednostkowym, testem anulowania/STOP i porównaniem na tym samym S22; bez arbitralnych limitów tur i dodatkowych potwierdzeń.

## 3. Generyczne skills i skutki zewnętrzne

- Opracować kontrakt zadania/skilla: identyfikator, typowane wejścia, dozwolone fakty/akcje, zakres grantu, warunki wykonania, potwierdzenie wyniku i bezpieczny fallback.
- Zachować podział: Gemma proponuje/rozumie, aplikacja autoryzuje, deterministyczny executor wykonuje, niezależny dowód potwierdza efekt. CLIR jako pierwszy przypadek; rejestracja wizyty i mechanik dopiero po implementacji ich akcji.
- Weryfikować rezultat zewnętrzny osobno od faktu rozmowy, wypowiedzi modelu i licznika TX.

## 4. Domknięcie produktu

Autoryzowane zadanie → samodzielny dial → zrozumiały dialog → autoryzowany skutek → weryfikacja → selektywne zakończenie własnego Call → `IDLE` → raport. Test syntetyczny, build albo samo zakończenie połączenia nie zastępują akceptacji end-to-end.

## Trwałe granice

[AGENTS.md](../AGENTS.md), [ARCHITECTURE.md](ARCHITECTURE.md), [SECURITY_PRIVACY.md](SECURITY_PRIVACY.md), [GENERIC_PHONE_TASK_AUTHORITY.md](GENERIC_PHONE_TASK_AUTHORITY.md) i [AUTONOMOUS_OPERATION_MODE.md](AUTONOMOUS_OPERATION_MODE.md) obowiązują niezależnie od planu. Historyczne roadmapy i checkpointy nie mogą przywracać rozwiązanych blokad ani dawnego trybu ręcznego pilota.
