# AI Calls — architektura

## Cel i granice

Produkt wykonuje **konkretnie autoryzowane zadanie telefoniczne na urządzeniu**: interpretacja celu → wybór zatwierdzonego numeru/SIM → telefon → dialog STT/Gemma/TTS → deterministyczny skutek i dowód → selektywne zakończenie własnego połączenia → wynik użytkownikowi. CLIR jest aktualnym przypadkiem testowym; nie ogranicza silnika do operatora ani nie dowodzi implementacji ogólnych wizyt/rezerwacji.

## Przepływ i właściciele decyzji

```text
użytkownik → aplikacyjny grant (cel / target / SIM / fakty / dozwolone skutki)
  → sprawdzenie Android / IDLE / uprawnień
  → Telecom + dokładnie własny Call
  → Samsung/Shizuku cellular RX
  → lokalny polski STT
  → workflow/PhraseMatrix + Gemma (propozycja typowanej akcji/odpowiedzi)
  → walidator celu/skutku + FactDisclosurePolicy + output approval
  → lokalny TTS → autoryzowany cellular TX
  → wykonanie w granicach grantu + niezależny dowód zewnętrznego efektu
  → selektywny STOP/teardown → IDLE → raport
```

Gemma, ServicePacks, TaskGraph, PhraseMatrix, parser i supervisor **nie są magazynami uprawnień**. Model interpretuje kontekst i proponuje; aplikacja ogranicza i zatwierdza. `CallTask`, `CallResolvedTarget`, `CallExternalEffectValidator`, `CallCommitmentGate`, `CallExternalEffectCompletionTracker`, `FactDisclosurePolicy` i właściciel workflow zachowują odrębne role. `BookAppointment` i `SetService(CLIR)` to typowane efekty w kodzie, **nie dowód gotowości ich wszystkich dialogowych wykonawców w realnym połączeniu**.

```text
autoryzacja ≠ walidacja propozycji ≠ permit ≠ zużycie permitu
           ≠ zewnętrzny sukces ≠ ukończenie zadania
```

## Pamięć dialogu i przyspieszenie

- `CampaignGemmaPreflight` rozgrzewa silnik Gemmy przed wybieraniem numeru. Syntetyczny kontekst jest czyszczony przed prawdziwym dialogiem, ale wagi/Engine pozostają gotowe.
- F-043 utrzymuje jeden zdrowy natywny LiteRT-LM `Conversation` dla kolejnych wypowiedzi w tym samym połączeniu. Zakończona tura nie uruchamia `cancelProcess()`. Aktywne anulowanie, STOP i błędy odrzucają niebezpieczną sesję; backend po zakończeniu rozmowy zamyka zasoby. Warianty bez pamięci natywnej mogą nadal korzystać z aplikacyjnego kontekstu tur.
- F-042 przygotowuje neutralną zapowiedź w osobnym lokalnym TTS; po wykryciu końca mowy może ją wysłać wyłącznie w aplikacyjnie autoryzowanej kampanii. Zapowiedź i odpowiedź główna korzystają z jednego serializowanego TX; inferencja Gemmy biegnie niezależnie. Brak nakładania i realna słyszalność wymagają akceptacji fizycznej.
- `LocalTtsSpeechOutput` obecnie tworzy instancję silnika TTS przy wywołaniu `synthesize`; ewentualny pooling/reuse wymaga pomiaru wpływu na czas, testów zamykania i braku regresji STOP.

## Zamrożona granica mediów

`CallMediaSessionCoordinator` i Samsung/Shizuku pod `privileged-helper/` stanowią wcześniej zweryfikowaną granicę RX/TX. Przed zmianami przeczytaj [PHASE2D_FREEZE](PHASE2D_FREEZE_2026-09-18.md). Nie utożsamiaj poprawności plików PCM/liczników z niezależnym odsłuchem z drugiej strony połączenia.

## Bezpieczeństwo

Jeden własny aktywny Call; realne uprawnienia i stan telefonu; konkretny, trwały, odwoływalny zakres grantu; ujawnienie faktów dopiero po `FactDisclosurePolicy`; bez dowolnych skutków modelu. STOP wygrywa z inferencją/TX/retry. Nie rozłączaj połączeń obcych. Nie zapisuj tożsamości i nagrań w GitHub ani zwykłych wynikach narzędzi. Wymagania: [SECURITY_PRIVACY](SECURITY_PRIVACY.md), [GENERIC_PHONE_TASK_AUTHORITY](GENERIC_PHONE_TASK_AUTHORITY.md), [AUTONOMOUS_OPERATION_MODE](AUTONOMOUS_OPERATION_MODE.md).

## Stan i dowody

Architektura opisuje **kontrakt**, nie bieżącą akceptację wdrożenia. Aktualny zakres PASS/OPEN jest w [HANDOFF](HANDOFF_NEXT_CHAT.md) i jedynym rejestrze [BLOCKERS](../BLOCKERS.md); fizyczne pomiary F-042/F-043 w ich checkpointach. Syntetyczny test dwóch tur Gemmy na S22 nie dowodzi pełnej rozmowy, słyszalności ani efektu CLIR.
