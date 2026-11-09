# AI Calls — audyt gotowych rozwiązań do G6 (materiał do JEDNEGO planu)

Stan źródłowy: AI Calls main@cbbfba889f14426d80579e723f55f31dc8b48546 (2026-11-09). Dokument jest **analizą/rejestrem importu**, nie alternatywną roadmapą. Jedyną kolejnością wykonawczą pozostaje [AI_CALLS_MASTER_EXECUTION_PLAN](AI_CALLS_MASTER_EXECUTION_PLAN.md). Realizacja i priorytety mogą się zmienić wyłącznie po sprawdzeniu świeżego HEAD oraz aktualnego handoffu.

## Decyzja architektoniczna

**Reuse contracts and tests first; do not replace the Android runtime.** Obecne AI Calls ma jeden wspólny AndroidConversationRuntime, prawdziwą telefonię Android / Samsung / Shizuku, CallTask/CallResolvedTarget, trwały dokładny grant, FactDisclosurePolicy, jeden CallCommitmentGate, model Gemma 4 E2B, dynamiczne Skills, prywatne RX/TX i kontrolowany STOP. To system lokalny na S22; Pipecat, LiveKit, call-use i Clinic są przede wszystkim usługami Python + infrastrukturą internetową, a DeskRoute jest aplikacją serwerową. Zastępowanie całego runtime'u oznaczałoby regresję sprzętu, naruszenie zamrożonego toru lub podwójny system własności Call. To nie jest cel.

Pojedynczy **transport adapter** może w przyszłości dostarczać to samo wejście/wyjście rozmowy: (A) istniejący Android SIM / Telecom, (B) osobny opt-in SIP/LiveKit, (C) osobny opt-in WhatsApp Business. Żaden dodatkowy transport nie uzyskuje prawa wybierania numeru, ujawniania danych, tworzenia skutków ani obchodzenia STOP. Nie wymagamy chmury, Pipecat, SIP, WhatsApp ani dodatkowej aplikacji dla obecnej podstawowej ścieżki.

## Co AI Calls ma już teraz (bez ponownej implementacji)

| Istniejący kontrakt / dowód | Status i co z niego wynika |
| --- | --- |
| AndroidIncomingAssistant, IncomingSecretaryScenario, AndroidOwnedInCallService | INCOMING AI/manual i przygotowanie wspólnego runtime istnieją; wcześniejsze dwa rzeczywiste połączenia A8→S22 potwierdziły fragment odbioru, end_call i recorder. Nie jest to całościowy odbiór G6. |
| AndroidConversationRuntime, LocalSpeechTextPipeline, LocalSpeechGenerationGate | STT → E2B/Skills → TTS, zatwierdzanie wypowiedzi, bezpieczne generacje/STOP; G5.3 18/18 off-call tur, 36 WAV, sprzętowy STOP STT/model/TTS. Nie powtarzać tych prób. |
| CallRealtimeAudioPump | Ma już onRemoteSpeechStarted, wyciszanie starego TX, tombstones i cancelResponse dla istniejącej ścieżki realtime. Przed przenoszeniem wzorca Pipecat ustalić, czy identyczna obsługa istnieje w **lokalnej** ścieżce Gemma/Android STT/TTS. |
| RecordedConversationHistory, CallSessionEvidenceRecorder | Lokalny timeline i części RX/TX są dostępne. Wymaganie pełnego nagrania, w tym fragmentów manualnych, i spójnego odtwarzania całości nadal wymaga kwalifikacji/implementacji (F-080), nie nowej bazy z DeskRoute. |
| CallCommitmentGate, GateDBookAppointmentCommitmentAuthorization, GateDBookAppointmentCompletion, BookAppointmentOutcomeMatcher | Istnieje aplikacyjna walidacja propozycji i dowodów rezerwacji. **Brakuje potwierdzonego realnego adaptera kalendarza/rezerwacji**, nie drugiego commitment gate. |
| FactDisclosurePolicy i IdentityVault | Autoryzacja i scope pozostają właścicielem aplikacji; żaden zewnętrzny agent nie dostaje pełnego sejfu. |
| G5.3 review HTML, g53_human_review_gate | 6/6 prywatnych formularzy przygotowanych, 0/6 indywidualnych ocen. Użytkownik wyraził pozytywną ocenę rozmowy w tym czacie, co nie jest dowodem sześciu osobnych odsłuchów. |

## Analiza pięciu projektów — konkretny import

| Repo, pin HEAD na 2026-11-09 | Kontrakt do wykorzystania | Wpięcie do AI Calls | Co odrzucić/odroczyć | Licencja/ryzyko |
| --- | --- | --- | --- | --- |
| [Pipecat](https://github.com/pipecat-ai/pipecat), fb7c9c5 | Przerwania / barge-in, ramki audio, VAD/turn boundary, anulowanie zaległego TTS, mierzenie false barge-in, opcjonalny WhatsApp Business transport | Dodać tylko brakujące eventy i testy do istniejącego conversation/media bridge; najpierw porównać CallRealtimeAudioPump i lokalny STT→Gemma→TTS | Nie kopiować Python PipelineRunner do APK i nie przebudowywać zamrożonego PCM/Shizuku; WhatsApp po G6 jako osobny provider | BSD-2-Clause, sprawdzić noty źródłowe; WhatsApp wymaga Meta Business API, webhooks, numeru i dostępnej funkcji calling |
| [LiveKit Agents](https://github.com/livekit/agents), 0a928dd i [LiveKit SIP](https://github.com/livekit/sip), bb253f4 | SIP inbound/outbound, trunk/dispatch, WebRTC rooms/sessions, DTMF, stany rozłączeń | Dopiero osobny transport VoIP, z tłumaczeniem eventów na istniejące CallTask/session/evidence; bez przejęcia Android SIM | Bez obowiązkowego serwera, konta SIP, płatnego PSTN i przebudowy telefonu w G6 SIM | Apache-2.0; realny SIP to niezależne środowisko, opłaty/hostowanie i platformowe zgody |
| [call-use](https://github.com/agent-next/call-use), e12b26e | Stan IVR (menu, DTMF, hold, timeout, operator), takeover/resume oraz typowane disposition/transcript/events; scenariusze z testów | Wykorzystać state machine jako wzorzec pod istniejącym app-owned Call; wynik mapować na istniejący outcome/evidence tracker | Nie osadzać całego Python workera/LiveKit/Twilio, nie kopiować własnych approval/grant/STOP; v0.1 ma ograniczenia US/Canada i stan in-memory | MIT, early v0.1 — wzorce tak, zależność runtime obecnie nie |
| [Clinic Voice Agent](https://github.com/nikhilreddy00/clinic-voice-agent), 45b210d | Check availability → hold slot → przeczytaj szczegóły → potwierdzenie → commit → uzyskaj booking ID; testy tool-trace, failure i rescheduling/cancel | Dopisać brakujący adapter i scenariusz do istniejącego BookAppointmentOutcomeMatcher / CallCommitmentGate / tracker; w pierwszej próbie tylko mock z dowodem stanu | Nie kopiować klinicznych danych, Python/FastAPI, PHI ani obietnic AI bez dowodu backendu | Brak widocznego pliku LICENSE w katalogu głównym: traktować kod jako nieobjęty zezwoleniem na kopiowanie do czasu potwierdzenia |
| [DeskRoute](https://github.com/PrabhatMattoo/DeskRoute), fb707d1 | UX historii, wyszukiwanie sesji, kolejka eskalacji, panel klienta, zewnętrzna polityka multi-tenant | Tylko wzorce funkcji/ekranów: dostosować istniejący RecordedConversationHistory i projekt G7 | Nie kopiować repo/UI/CSS ani wdrażać drugiej bazy/panelu serwerowego w G6 | AGPL-3.0: dla zamkniętego produktu i serwisu sieciowego wymaga osobnej analizy zgodności; domyślnie reference-only |

Piny to **punkty odniesienia**, nie automatyczne zależności. Ponowna ocena licencji jest wymagana przed rzeczywistym kopiowaniem kodu lub dodaniem artefaktu. Architektury Pipecat i Clinic są Python/cloud-first; nie należy twierdzić, że ich publikowane opóźnienia odnoszą się do lokalnej E2B/GPU S22.

### Kontrakty minimalne, bez drugich runnerów

1. **Conversation interruption:** RX speech-start/stop (źródło i confidence), session id + generation, stop/cancel reason, queue TX flush, no-stale-output, ewentualne wznowienie, metryki false positives. Sprawdzić CallRealtimeAudioPump **vs** AndroidConversationRuntime, LocalSpeechGenerationGate; brakujące zachowanie implementować raz w istniejącym module, z zachowaniem STOP i app-owned Call.
2. **IVR events:** DTMF request/observed, hold-start/end, menu option, remote transfer, speaking/waiting/end + timeout tylko tam, gdzie wynika z rzeczywistej telefonii. Model generuje propozycję; aplikacja waliduje ją w zadaniu. Nie dodawać niepotrzebnego ręcznego approval per naciśnięcie, jeśli bezpieczny zakres jest już objęty grantem.
3. **Outcome/evidence:** disposition osobno od external effect, oddzielne fields for requested/validated/dispatched/confirmed, dokładny call/session ID, źródło dowodu i partial/error. Model nie może sam oznaczyć rezerwacji lub CLIR jako dokonanej.
4. **Booking:** availability → optional hold (expires/idempotency key) → owner/party confirmation within existing grant → consume single CallCommitmentGate → provider commit → independently verify booking reference. Jeżeli nowy termin/warunki wykraczają poza grant, brak cichego poszerzenia scope. Nie tworzyć kolejnego BookAppointment gate.
5. **History/dashboard:** wyłącznie widok istniejącego prywatnego archiwum i indeks pokrycia audio, pochodzenie STT/AI/TX, brakujące okresy, przejęcie manualne, wynik i opcjonalny eksport właściciela. Wszelkie multi-tenant dopiero po modelu izolacji danych i licencji.

## Jedna sekwencja prac, bez kolizji dwóch czatów

**Jedynym planem sekwencyjnym jest AI_CALLS_MASTER_EXECUTION_PLAN.** Ten audyt jest załącznikiem z konkretnymi wzorcami. W obu czatach zawsze trzeba odczytać świeży main + NEXT_CHAT_PROMPT przed nowym krokiem; stare checkpointy są historią, nie nowymi zadaniami.

- **Czat wykonawczy (drugi, obecnie pracuje nad kodem):** kontynuuje tylko aktualne G5.3/G6 source QA i realne Android/Local Agent; robi nową integrację G6 INCOMING, bez przerywania działającego sprintu. Nie wdraża Pipecat/LiveKit/DeskRoute w ciemno.
- **Ten czat (audyt/reuse):** wprowadza teraz wyłącznie dokumenty i mapę diff/testów, bez zmian runtime i bez uruchamiania S22/Mac QA. Gdy inżynieria przejmuje dany kontrakt, wyznacza jednego właściciela plików na main; drugi czat nie modyfikuje tych samych plików równolegle.
- **Przed każdym nowym commitem:** porównać HEAD z ustalonym base, brak niezatwierdzonych zmian i aktywnej pracy na tych samych zasobach; CAS/fast-forward, bez force-push, bez nowych gałęzi i bez GitHub Actions. Jeśli HEAD się zmieni, przeczytać nowy diff i przenieść tylko niepokrywającą się część. Wszystkie prawdziwe efekty wyłącznie w dokładnym autoryzowanym grancie.
- **Jedno miejsce do przekazywania statusu:** NEXT_CHAT_PROMPT + bieżący pierwszy blok HANDOFF_NEXT_CHAT; liczby testów i receipts odwołują się do checkpointów i BLOCKERS zamiast kopiować dowody jako nowy PASS.
- **Decyzja właściciela o jakości:** pozytywna ogólna ocena w rozmowie, techniczny G5.3 off-call PASS; indywidualne 6 review JSON/48 rubryk pozostają UNASSESSED. Dopuszcza **przygotowanie G6**, ale nie fałszuje dowodów i nie autoryzuje konkretnego połączenia.

## G6 — tylko braki względem wykonanych testów

| Bramka | Dowód już posiadany: nie powtarzać | Nowa praca i wyłącznie nowy dowód |
| --- | --- | --- |
| G6.0 read-only diff/preflight | G3 E2B vs Qwen 96 tur; G4 dynamic Skills; G5.2/5.3 18 audio turns + STOP; F-166/167/168 i Mac host 386 Python | Skatalogować aktualny G6 source and grants, dokładnie wskazać brakujący kontrakt. Bez uruchamiania modelu/telefonu |
| G6.1 INCOMING | Historyczne dwa live A8→S22: słyszalność w wybranej rozmowie, end_call, recorder; istniejąca automatyczna sekretarka | Nowa kontrolowana próba tylko brakujących funkcji: przyjęcie wiadomości z potwierdzeniem, przerwanie/cancel, manual takeover/resume bez rozłączenia, pełne RX/TX/audio+transkrypt, live outcome. Realny Call dopiero po autoryzacji/IDLE |
| G6.2 OUTGOING | Istniejący Android-owned dial, STOP/egzekucja grantu, CallCommitmentGate, narzędzia GateD, CLIR historyczne przypadki | Jedna konkretna autoryzowana rozmowa samodzielna, prawdziwy RX/TX, wyniki i oddzielny dowód; IVR/DTMF tylko jeśli rzeczywisty scenariusz tego wymaga; Booking mock→realny adapter z dowodem |
| Rozszerzenia po G6 | Istniejący runtime i historia | Opt-in SIP/LiveKit, WhatsApp Business Pipecat, dashboard/multi-tenant G7+; odrębne acceptance per transport |

**Reguła reuse testów:** gdy moduł i jego kontrakt nie zmieniły się od PASS, nie uruchamiać jego kosztownych prób po raz drugi. Przy nowym kodzie dodać minimalny test RED→GREEN nowej różnicy, celowane regresje zależności, pełną bramkę hosta **raz po freeze zmiany**, a na S22 tylko nową fizyczną ścieżkę. Jeśli zmiana dotyka zamrożonego PCM, call ownership, STOP lub grant, odpowiednia istniejąca bramka zostaje unieważniona **tylko w dotkniętym obszarze** i wymaga ponownej kwalifikacji — nie pełnego resetu G3–G5.3.

**Warunki finalne G6:** autoryzowany INCOMING i OUTGOING faktycznie przeprowadzone i udokumentowane jako odrębne próby, prawdziwe audio/słyszalność, STOP/manual takeover, poprawne rozłączenie, zapis i odtwarzanie adekwatnej pełnej sesji, wiarygodny status i niezależne potwierdzenie każdego rzeczywistego skutku. Off-call/testy zewnętrznych repozytoriów nie zastępują dowodu S22.

## Źródła zewnętrzne (sprawdzane 2026-11-09)

- Pipecat: https://github.com/pipecat-ai/pipecat ; WhatsApp: https://github.com/pipecat-ai/docs/blob/main/api-reference/server/services/transport/whatsapp.mdx
- LiveKit: https://docs.livekit.io/telephony/ ; https://github.com/livekit/agents ; https://github.com/livekit/sip
- call-use: https://github.com/agent-next/call-use ; dokumentacja https://docs.call-use.com/
- Clinic Voice Agent: https://github.com/nikhilreddy00/clinic-voice-agent
- DeskRoute (AGPL-3.0): https://github.com/PrabhatMattoo/DeskRoute
