# AI Calls — English-first: modele do testów i gotowość komercyjna
Data analizy: 2026-11-09. Status: RESEARCH_BACKLOG / DOCUMENTATION_ONLY. To nie jest nowy sprint ani pozwolenie na pobieranie modeli, zmianę aktywnej konfiguracji czy dzwonienie.

## Decyzja i kolejność

- **GEMMA_4_E2B pozostaje głównym modelem AI Calls.** Decyzja G3 KEEP_E2B jest zamknięta na dotychczasowym korpusie; nie oznacza przewagi w języku angielskim. Qwen3.5 2B pozostaje laboratoryjnym punktem odniesienia.
- Obowiązuje jedna kolejność rozwoju: **G5.3** (human semantic/naturalness i fizyczny native STOP) → **G6** (autoryzowane telefoniczne E2E i rzeczywisty wynik) → **G7** (UX/produkt). Równoległa analiza źródeł/licencji jest dozwolona, ale żadna poniższa propozycja nie blokuje bieżącej bramki.
- Docelowy produkt: angielskojęzyczna sekretarka AI — przychodzące wiadomości i wychodzące zadania, w szczególności telefoniczne umówienie/zmiana wizyty. To wymaganie rynku, nie potwierdzona jeszcze zdolność gotowej aplikacji.
- Każdego kandydata włączać wyłącznie jako **Lab-only**, przez istniejące porty i pojedynczy runtime; bez drugiego runnera, drugiej polityki STOP, rozszerzenia uprawnień Skills lub automatycznej zmiany modelu Call. Wsparcie LiteRT-LM/Android/Exynos S22 oraz realna latencja są **UNVERIFIED** do czasu fizycznego testu.

## Shortlista modeli — tekst i decyzje

| Model (oryginalna karta) | Rola i priorytet | Licencja zadeklarowana przez kartę | Cel próby i niewiadome |
| --- | --- | --- | --- |
| [google/gemma-4-E2B](https://huggingface.co/google/gemma-4-E2B) | **BASELINE / KEEP** | Apache-2.0 | Obecna natywna E2B GPU na S22; angielski korpus, p50/p95, porównanie realnej skuteczności telefonicznej |
| [LiquidAI/LFM2.5-1.2B-Instruct](https://huggingface.co/LiquidAI/LFM2.5-1.2B-Instruct) | **P1 / szybki text agent** | LFM Open License v1.0, warunki komercyjne | Mały edge LLM do extraction/tools/odpowiedzi; wymagane adapter/Android, pomiar TTFT/pamięci i przegląd pełnej licencji |
| [ibm-granite/granite-4.0-1b](https://huggingface.co/ibm-granite/granite-4.0-1b) | **P1 / akcje strukturalne** | Apache-2.0 | Tool calling, argumenty wizyt, terminów, nazw i polityk; sprawdzić naturalność dialogu |
| [Qwen/Qwen3.5-2B](https://huggingface.co/Qwen/Qwen3.5-2B) | **P1 / istniejący benchmark** | Apache-2.0 | Obecny Lab CPU; nie powtarzać zaliczonych polskich G3, osobno ocenić EN |
| [microsoft/Phi-4-mini-instruct](https://huggingface.co/microsoft/Phi-4-mini-instruct) | **P2 / większy quality challenger** | MIT | Trudne wyjątki i instrukcje; ryzyko RAM, temperatury i opóźnień na S22 |

## Shortlista — mowa i voice-to-voice

| Model | Rola i priorytet | Licencja karty | Cel próby i niewiadome |
| --- | --- | --- | --- |
| [LiquidAI/LFM2.5-Audio-1.5B](https://huggingface.co/LiquidAI/LFM2.5-Audio-1.5B) | **P1 / alternatywa całego voice pipeline** | LFM Open License v1.0 + osobne licencje komponentów | Angielskie audio-in/audio-out, generacja interleaved, możliwości barge-in; brak potwierdzenia S22, dostępności narzędzi, native STOP i konwersji do telefonicznego PCM |
| [moonshine-ai/moonshine-streaming-tiny](https://huggingface.co/moonshine-ai/moonshine-streaming-tiny) | **P1 / angielskie STT streaming** | MIT | Endpointing, krótkie pytania, akcenty, cyfry, nazwiska, szybkie poprawki; karta ostrzega, że sam tor Transformers nie jest jeszcze w pełni wydajnym streamingiem |
| [nvidia/parakeet-tdt-0.6b-v3](https://huggingface.co/nvidia/parakeet-tdt-0.6b-v3) | **P2 / STT quality challenger** | CC-BY-4.0 | WER przy hałasie i nazwach własnych; model 0.6B, karta wymienia Linux/NeMo i >=2 GB RAM; natywny Android niepotwierdzony |
| [hexgrad/Kokoro-82M](https://huggingface.co/hexgrad/Kokoro-82M) | **P1 / mały EN TTS** | Apache-2.0 | Naturalność, liczby, daty, pierwsza próbka audio, RAM; sprawdzić również licencje konkretnych głosów i runtime Android |
| [kyutai/pocket-tts](https://huggingface.co/kyutai/pocket-tts) | **P1/P2 / TTS streaming** | CC-BY-4.0 | CPU, streaming i pierwsza porcja dźwięku; prezentowane ~200 ms to wynik autora na Mac, NIE pomiar na S22; oddzielny przegląd głosów, klonowania i zależności |

Wszystkie licencje w tabeli są **metadanymi kart na dzień analizy**, nie opinią prawną ani automatyczną zgodą na sprzedaż dowolnej paczki. Przed dystrybucją sprawdzić dokładną rewizję checkpointu, licencje wag/kodu/tokenizera/TTS voice-packów/danych oraz NOTICE/atrybucję. LFM v1.0 ma szczególne warunki komercyjne (m.in. próg przychodów i zastosowanie właściwych warunków) — wymaga jawnego sign-off. CC-BY wymaga odpowiedniej atrybucji. Nie pobierać ani nie bundlować modeli bez tej bramki.

## Jedna porównywalna macierz English-call

Korpus na kontrolowanym rozmówcy (nie prawdziwa placówka na pierwszym etapie): booking, rescheduling, cancel, message-taking, IVR/DTMF, busy/no answer, wait/hold, brak terminu, dyskusja o cenie, poprawka nazwiska, poprawka daty i godziny, zmiana strefy czasowej, pytanie spoza zakresu, ujawnienie danych poza grantem, STOP/barge-in. Rozróżnić US/UK accents i telefoniczne 8 kHz/G.711 od czystego studyjnego 16 kHz. Dane i nagrania tylko prywatnie, testowa fikcyjna tożsamość.

Miary/obowiązkowe artefakty:
1. **Prawdziwy wynik zadania**: trafna oferta, poprawnie zaakceptowane warunki, faktyczny dowód potwierdzonej rezerwacji / odmowy; modelowe „booked” i wpis w kalendarzu NIE oznaczają zewnętrznej rezerwacji. Brak duplikatów przy retry.
2. **Rozmowa EN**: naturalność oceniana przez człowieka, zrozumienie akcentu/liczb/nazw, pamięć i korekty, poprawna prośba o doprecyzowanie, brak fikcyjnych faktów i bezprawnych deklaracji.
3. **Opóźnienia**: osobno cold/warm, STT final/partial, end-of-speech→pierwszy słyszalny audio chunk (nie samo „model done”), full response, p50/p95 wraz z N, przerwania i reakcja STOP, RAM/PSS, termika, zużycie baterii.
4. **Bezpieczeństwo i integracja**: typed tool proposal → polityka aplikacji → wykonanie/skutek → potwierdzenie; Call ownership, SIM, dane IdentityVault, revocation, brak wypowiedzi po STOP, kontrola task context; audyt prawdziwego słyszalnego RX/TX i rozmówcy.
5. **Reprodukowalność**: model repo/revision/hash, forma/kwantyzacja, runtime/akcelerator, APK/head, profil, korpus i sprzęt. Oddzielić synthetic/off-call, controlled-call i real-world acceptance.

Najpierw mały wspólny korpus off-call dla E2B, LFM Instruct i Granite (oraz już dostępnego Qwen), potem osobne A/B STT/TTS i LFM Audio. Rozszerzać N i liczbę modeli tylko gdy poprzednia bramka uzasadnia koszt. Nie porównywać GPU i CPU jako dowodu jakości modelu bez kontroli konfiguracji. Jeśli LFM Audio nie daje typowanych, audytowalnych akcji i natywnego przerwania, utrzymać rozdzielone STT → E2B → TTS.

## Dodatkowe wnioski produktowe i komercyjne — do walidacji

- **Priorytet 1: dystrybucja/telefonia przed marketingiem.** Standardowa aplikacja Android nie ma zwykłego prawa do RX+TX aktywnego GSM; CAPTURE_AUDIO_OUTPUT wymaga uprzywilejowania. Prototyp S22/Shizuku nie dowodzi uniwersalnej kompatybilności ani akceptacji Google Play. Przeprowadzić osobny feasibility i policy review, nie projektować obchodzenia tych kontroli. Źródło: https://developer.android.com/media/platform/sharing-audio-input oraz https://support.google.com/googleplay/android-developer/answer/10964491
- **Dwie ścieżki wdrożenia, jedna logika sekretarki:** (A) urządzenia/konfiguracje, w których rzeczywista telefonia Android przejdzie zgodny z prawem odbiór; (B) opcjonalny transport SIP/VoIP lub operator z dwukierunkowym audio do tego samego agenta. Transport VoIP wymaga sieci i nie można go reklamować jako w pełni offline. Twilio Media Streams: https://www.twilio.com/docs/voice/media-streams ; outbound DTMF i zachowanie ścieżek różnią się od GSM, więc potrzebny osobny test.
- **Przewaga nie wynika z samego modelu:** zakresowo autoryzowana sekretarka, naprawdę potwierdzone efekty, prywatność, widoczny STOP/takeover, ciągłe archiwum i wiarygodne rozliczenie zadania. Nie sprzedawać „AI booked your appointment” zanim nie ma zewnętrznego dowodu.
- **Pierwsza hipoteza klienta:** personal assistant do umawiania wizyt i call-backów; równolegle mała firma z odbiorem po godzinach. Nie mieszać KPI i cennika B2C/B2B. W pilotażu mierzyć completed task rate, minuty na wynik, koszt operatora/VoIP, CAC, retencję, powody rezygnacji i wsparcie techniczne. Ceny są hipotezą, a nie zweryfikowaną gotowością do płacenia.
- **Zgodność rozmów:** na początku poinformować, że rozmowę prowadzi asystent AI; przeanalizować lokalne zasady zgód na nagrywanie, połączeń wychodzących i ujawnienia danych, zwłaszcza EU/UK/US. Art. 50 EU AI Act obowiązuje od 2026-08-02: https://digital-strategy.ec.europa.eu/en/faqs/transparency-obligations-under-article-50-ai-act . Jurysdykcje oceniać osobno przed premierą.
- **Dostrajanie zamiast chaotycznego swapu:** najpierw Skills + struktura zadania + mierzalne błędy; dopiero jeśli English A/B wykryje trwały deficyt, rozważyć LoRA/SFT na zgodnych licencyjnie, odpersonalizowanych, legalnie zebranych dialogach. Domyślna Gemma zostaje do czasu lepszego wyniku w kompletnym task E2E.
- **Bez przedwczesnego scope creep:** nie uruchamiać teraz model zoo, nie dodawać konfiguracji użytkownikowi, nie planować systemu płatności zanim działanie w kontrolowanych rozmowach jest udowodnione. Następny aktywny etap to G5.3, potem zgodne z planem G6 i G7.

## Kryterium decyzji o zmianie modelu / go-to-market

Nowy model może zostać domyślnym dopiero po poprawie angielskiego **task completion** i jakości mowy przy akceptowalnym p95, RAM/temperaturze, błędach, licencji i zachowaniu bezpieczeństwa STOP/authority na S22. Wcześniejszy mikrobenchmark na tekście nie awansuje go do realnych Calls. Komercyjny pilot dopiero po pozytywnych: kompatybilności kanału dystrybucji, real-call audio, zatwierdzonym łańcuchu licencji, polityce nagrań i jednoznacznym dowodzie rzeczywistego wyniku.

Powiązania: [master plan](AI_CALLS_MASTER_EXECUTION_PLAN.md), [roadmap](ROADMAP.md), [product assembly](PRODUCT_ASSEMBLY_PLAN.md), [handoff](HANDOFF_NEXT_CHAT.md), [quality gates](FUNCTIONAL_ACCEPTANCE_MATRIX.md), [blockers](../BLOCKERS.md).
