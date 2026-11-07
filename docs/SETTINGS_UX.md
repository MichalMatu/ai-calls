# AI Calls — UX i ustawienia (stan implementacji: Sprint 6, 2026-11-07)

**Źródło prawdy:** [Zamknięcie Sprintu 6 — host i pełny S22 PASS](checkpoints/2026-11-07-ux-sprint6-final-handoff.md). Funkcjonalne `main@1b359146`; 325 host, 28/28 native UX, 24/24 visual, 4/4 targeted i realna Gemma 2 tury + STOP PASS. UX został zaakceptowany do zakończenia iteracji. [F-103 pełne PL/EN](../BLOCKERS.md) pozostaje OPEN.

## Nawigacja i semantyka

- Cztery dolne zakładki: **Start / Rozmowy / Laboratorium / Ustawienia**. Bez duplikatów kategorii w Home. UI używa płaskich, co najmniej 48dp kontrolek, kart i wektorowych Tabler MIT. Wybory rozwijane przez wspólny `UiSelect`, opcja języka Auto/PL/EN.
- **Start:** osobne etykiety trybu audio, dostawcy tekstowego LLM, modelu dialogu i gotowości pliku. Wskazanie upoważnienia nie jest dowodem trwającej próby.
- **Kampanie:** START, STOP i Anuluj mają różne role. „Ostatni zapis wykonania” to historyczny stan, a nie status call live; kod `targeted_disconnect_requested` jest opisany jako **wysłane żądanie**, nie potwierdzone rozłączenie. Surowe kody pozostają w technicznej diagnostyce.
- **Silniki:** wybór STT, dialogu, TTS i głosu; walidacja gotowości, Zapis/Reset przed opcjonalnymi testami, pobraniami i odsłuchem. Nie zmieniono aktywnego modelu ani fallbacków.
- **Skills:** presety fabryczne, własna kopia, numerowane wersje, snapshot na nową sesję. Wybrany aktualnie aktywny preset pokazuje neutralną etykietę i nie ma powtórnej aktywacji. Wtórne akcje są krótsze.

## Rozmowy i prywatne audio

- `ConversationChatFeed`: prywatny live chat jednej sesji; nie jest trwałym archiwum.
- `RecordedConversationHistory`: czyta oryginalny `timeline.jsonl` bez duplikacji treści. Lista pokazuje kierunek, godzinę i liczbę wypowiedzi, oddziela dni. Details/Back nie opuszczają zakładki Rozmowy.
- **Usuwanie pojedyncze:** w szczegółach sesji, z potwierdzeniem.
- **„Usuń wszystkie zakończone rozmowy”**: ostatnia akcja na dole listy, tylko gdy istnieje bezpieczny kandydat. Potwierdzenie ostrzega o utracie lokalnego transkryptu i klipów. Implementacja usuwa wyłącznie prywatne sesje z COMPLETE + END po ponownym sprawdzeniu ścieżek, symlinków i ownership katalogu. Nie kasuje niedokończonych, aktywnych ani obcych plików; nie czyści modeli, preferencji ani innych danych aplikacji. Test S22 potwierdzenia ma **wyłącznie anulować** — bez rzeczywistego bulk-delete.
- **WAV przy dymkach:** tylko wypowiedzi z istniejącym RX/TX mają aktywny dymek i etykietę ▶; pierwsze tapnięcie odtwarza, drugie zatrzymuje, następny dymek przełącza źródło. Status jest osobny, player zwalnia zasoby przy Back/pause/destroy. Pozostałe, niesparowane segmenty (w tym ACK) są ukryte pod rozwijaną sekcją. Nie ma wielkiego przycisku pod każdym dymkiem.
- Odtwarzanie jest dozwolone **tylko poza połączeniem** i dla poprawnych lokalnych plików WAV; wymaga uprawnienia do odczytu stanu telefonu. Testy wykonują syntetyczny, cichy WAV. Nie publikować zapisów użytkownika.
- **F-080 OPEN:** segmenty RX/TX **nie** są pełnym, ciągłym nagraniem rozmowy, nie zawierają gwarantowanie przejęcia manualnego/IVR/hold; nie ma odtwarzacza całej sesji.

## Laboratorium

Szybki test 2 tur lub pełny korpus historii offline dla wybranego presetu — nie zmienia aktywnego ustawienia. Interfejs pokazuje postęp, wejście, tekst modelu, proponowaną decyzję `END_CALL`, latencję, stop/error oraz kartę oceny. Auto-follow celuje w faktyczny widoczny koniec karty po relayout; STOP ma trwałą informację o ukończonych turach. `END_CALL` w Lab nie wykonuje połączenia ani narzędzia. `2/2` to trafność **decyzji zakończenia**, nie jakości wypowiedzi. Pytania wliczane do heurystyki (także bez `?`) wymagają oceny ręcznej. [Dokładny zakres](SKILLS_AND_LAB.md).

## Co nie jest jeszcze zweryfikowane

| Obszar | Stan |
|---|---|
| Sprint 6 Kotlin/JVM/host | **325 PASS**, source green |
| Sprint 6 S22 instalacja i UI | **PASS**: dokładny S22, 28/28 native, 24/24 visual, app+test APK, bez utraty danych |
| Sprint 6 hotfix + real Gemma | **PASS**: 4/4 targeted, realne 2 tury + STOP, 46 prywatnych JPEG |
| Sprint 5 real Gemma 2 tury i STOP | **PASS historyczny** |
| Ręczny przegląd screenshotów Sprint 6 | **DONE**: właściciel zaakceptował obecny poziom UX; dodatkowy polishing odłożony |

## Granice odpowiedzialności

`ConversationHistoryActivity`, `ConversationSkillLabActivity`, `CampaignToolActivity` i `AudioEngineSettingsActivity` nadal łączą rysowanie z operacjami. Planowana separacja view → typed state → controller/presenter → istniejące repo/provider jest opisana w [roadmapie](ROADMAP.md) i [planie modularizacji](SAFE_MODULARIZATION_PLAN.md). Nie przenosić telefonii, recorder ani walidatora grantu w ramach samego UX.
