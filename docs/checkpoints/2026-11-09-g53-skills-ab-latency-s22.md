# AI Calls — G5.3 Skills latency A/B, S22 6 × 3 tury — 2026-11-09

## Status
**G5.3 SKILLS_AB_OFFCALL_S22_TECHNICAL_PASS / HUMAN_QUALITY_OPEN.** Eksperyment porównał `CONTEXTUAL` (istniejąca ścieżka dynamicznego routingu) i `ALL_ACTIVE` (statyczny cały zestaw aktywnych Skills) na tym samym Samsungu S22, Gemma 4 E2B/GPU, `context_token_budget=4096`, syntetycznej polskiej mowie i tym samym scenariuszu trzech tur (wiadomość o poniedziałku → korekta na wtorek → pytanie o zapamiętany dzień). **6 sesji, 18/18 tur technicznie PASS, 36 WAV** prywatnie na Mac/S22. Nie wykonywano żadnego połączenia ani narzędzia, nie zmieniono profilu ani normalnego zachowania aplikacji. Dla `ALL_ACTIVE` użyto wyłącznie opt-in AndroidTest `g53_skills_mode=ALL_ACTIVE`. Model E2B nadal wybrany.

**Naturalność, prawdziwość, obietnice i pozostałe 8 wymiarów `LabDialogueReport` = UNASSESSED; brak promocji trybu, G5.3 pozostaje OPEN i G6 NOT_RUN.** Ocenę przygotowano lokalnie, bez wysyłania surowego tekstu/WAV do GitHub, logcat, tasków Local Agenta albo wyników. Wyniki nie są reprezentatywnym p95 i nie pozwalają wyizolować samego wpływu strategii na jakość.

Źródło kodu przed checkpointem: `main@23aacee312c50ca4f1bf643a5be75e046c5edc48`; wcześniejszy korpus [CONTEXTUAL](2026-11-09-g53-private-voice-quality-corpus-latency.md), wcześniejszy natywny [STOP PASS](2026-11-09-g5p3-active-native-stop-stt-e2b-tts-technical-pass.md). Nie powtarzać STOP ani tych sesji bez konkretnej przyczyny.

## Wyniki mierzone na S22
Mediana (n=3 sesje/tryb), każdy czas od startu wejścia syntetycznego PCM do gotowego TTS PCM:

| Pozycja | CONTEXTUAL | ALL_ACTIVE | różnica |
| --- | ---: | ---: | ---: |
| Tura 1 | 42 569 ms | 23 037 ms | −45,9% |
| Tura 2 | 26 791 ms | 10 023 ms | **−62,6%** |
| Tura 3 | 12 972 ms | 8 965 ms | −30,9% |

| Mediana składnika / pozycja | CONTEXTUAL 1/2/3 (ms) | ALL_ACTIVE 1/2/3 (ms) |
| --- | --- | --- |
| STT final | 4 395 / 5 898 / 5 121 | 4 230 / 5 702 / 4 942 |
| Od STT do zatwierdzenia E2B (model + approval) | 33 746 / 17 635 / 4 626 | **16 889 / 2 821 / 2 575** |
| Od aprobaty do PCM TTS | 4 428 / 3 365 / 3 225 | 1 914 / 1 490 / 1 448 |
| Długość tekstu odpowiedzi (znaki) | 144 / 64 / 48 | 112 / 119 / 87 |
| Długość odpowiedzi (słowa) | 23 / 11 / 6 | 18 / 17 / 12 |

**STT: 0 błędów na 150 słowach syntetycznych (WER=0,0000);** nie stanowi to oceny realnych głosów i hałasu. Oba warianty w każdej z trzech sesji poprawnie zachowały korektę na wtorek, ale nie jest to dowód równoważnej jakości naturalnej rozmowy.

Natywne LiteRT `AiCallBridgePerf` w każdym trybie po 9 rekordów:
- `CONTEXTUAL`: `conversation_reused=false,false,true` (tura 2 odświeża Skills: `MessageCapture`→`Clarification`), tworzenie nowej `Conversation` trwa ~3–4 s; w turze 2 `generate_ms=13175/13581/24996`.
- `ALL_ACTIVE`: `conversation_reused=false,true,true`, tworzenie Conversation tylko w turze 1 (~1593–1749 ms), a w turze 2 `generate_ms=3210/2820/2697`; bez utraty aktualizacji pamięci w testowym scenariuszu.
- ALL_ACTIVE w turach 2 i 3 zwracał **dłuższe** odpowiedzi tekstowe, mimo wyraźnie mniejszego czasu. Krótszy tekst pierwszej tury i możliwe cache/warunki telefonu nie pozwalają z przypisaniem przyczynowym rozliczyć całej poprawy zimnego startu.

**Wniosek techniczny:** brak rekonstrukcji Conversation bardzo prawdopodobnie pomaga drugiej turze, ale A/B zmienia także zawartość promptu i strategię Skills. Nie przełączać produkcyjnego `CONTEXTUAL` na `ALL_ACTIVE` bez ludzkiej oceny odpowiedzi. Następna praca optymalizacyjna, odrębna od G5.3 human review: porównać strategie zachowujące kontekst i jakość, prewarm Engine oraz selekcję/predykcję Skills; mierzyć oddzielnie zimny start, refresh, generowanie i TTS. Bez wyłączania STOP, pamięci i ochrony skutków.

## Niezmienione bezpieczeństwo / prywatność
`AndroidG52MultiturnSpeechSkillsE2ETest` nadal używa istniejącego `LocalSpeechTextPipeline` i `AndroidSkillLabSessionFactory`. Bez flagi pozostaje `CONTEXTUAL`; opt-in `g53_skills_mode=ALL_ACTIVE` nie dotyka profilu. `G53PrivateAudioEvidence` rejestruje `skills_loading_mode` w prywatnym manifeście, a `ALL_ACTIVE` ma `routing_observed=false` (brak fałszywych zdarzeń per-turn). Każda sesja zapisana bez nadpisania, pliki 0600, katalog 0700. Lokalny indeks do odsłuchu i porównania to **`~/Library/Application Support/ai-calls/qa/g53-private/skills-ab-index.html`**. Niezależny recenzent ma obejrzeć sześć sesji, wskazać konkretne tury i notatki dla każdego PASS/FAIL; żadna automatyczna heurystyka nie ustawia human PASS.

## QA / dokładne receipts
| Zadanie | Wynik |
| --- | --- |
| `aicalls-g53-skills-mode-ab-mac-qa-20261109-v1` | DONE/0 — Gradle AndroidTest, full `verify_host.sh`, **375 Python**, anti-friction, clean |
| `aicalls-g53-all-active-skills-voice-ab-s22-20261109-v1` | **FAILED/PREFLIGHT**, `idle_timeout=6100` ponad maksymalny `3600`; **0 komend, 0 instalacji, 0 testów**; historyczne RED zachować |
| `aicalls-g53-all-active-skills-voice-ab-s22-20261109-v2` | DONE/0 — poprawiony limit 3000, fizyczne 3×3 tur, 18 WAV, profil i Call IDLE, instalacja `-r -t` bez wipe, Git clean |
| `aicalls-g53-skills-ab-local-compare-mac-20261109-v1` | DONE/0 — sześć prywatnych corpusów, 18 tur, WER 0/150, zliczenia odpowiedzi, mediana etapów i 9 natywnych rekordów ALL_ACTIVE, offline indeks, Git clean |

**F-164 VERIFIED_SCOPED**: błąd jedynie w deklaracji Host Ops, nie w APK/telefonie. Retry v2 z prawidłowym `idle_timeout` przeszedł. Nie modyfikowano realnego połączenia, nie dotykano działających usług. Ocenę jakości pozostawić OPEN.

Następny krok: rzeczywista niezależna **human review** prywatnych sześciu sesji, dołączenie jej do `LabDialogueReport`, decyzja jakościowa G5.3. G6 realna telefonia dopiero osobno z dokładnym grantem i odbiorem.
