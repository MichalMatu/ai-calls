# AI Calls — dokumentacja

**Jedyny punkt wejścia.** Najpierw [PROJECT.md](PROJECT.md): bieżący stan, priorytety, realne blokady i bramki akceptacji. Wszystkie aktualne dokumenty techniczne są w modułowych podkatalogach; starych checkpointów/handoffów/rejestru historycznych problemów nie prowadzimy.

## Architektura
- [SYSTEM](architecture/SYSTEM.md) — komponenty, odpowiedzialności, granice Android/JVM.
- [SECURITY](architecture/SECURITY.md) — autoryzacja, telefonia, bezpieczeństwo, prywatność i sprzętowy freeze.

## Moduły
- [CONVERSATION](modules/CONVERSATION.md) — runtime, STT/Gemma/TTS, przerwania i generacje.
- [INCOMING_OUTGOING](modules/INCOMING_OUTGOING.md) — własność Call, takeover, lifecycle, IVR/DTMF.
- [AUDIO_HISTORY](modules/AUDIO_HISTORY.md) — recorder, WAV, transkrypcje i odtwarzanie.
- [SKILLS_LAB](modules/SKILLS_LAB.md) — modele, Skills i laboratorium.
- [BOOKING_EFFECTS](modules/BOOKING_EFFECTS.md) — typowane skutki, rezerwacje i potwierdzenia.
- [UI](modules/UI.md) — interfejs, nawigacja, statusy i dostępność.

## Praca i urządzenia
- [DEVELOPMENT](operations/DEVELOPMENT.md) — FAST FLOW, test profile, cache, zasady Git i aktualizowania dokumentacji.
- [LOCAL_AGENT](operations/LOCAL_AGENT.md) — Mac Host Control, zadania i receipts.
- [DEVICE](operations/DEVICE.md) — S22, Shizuku, bezpieczna instalacja i fizyczne QA.
- [EXTERNAL_REUSE](operations/EXTERNAL_REUSE.md) — licencje i granice adaptacji open source.

**Reguła:** statusy i priorytety tylko w `PROJECT.md`. Pliki modułowe zawierają **trwałe kontrakty i aktualne ograniczenia**, nie raporty z dawnych sprintów. Po zmianie kodu popraw tylko dotknięty moduł i stan; nowe pliki twórz jedynie dla rzeczywiście nowego modułu. Szczegółowe dowody historyczne pozostają w Git i oryginalnych receipts Host Control, nie w repozytorium Markdown.
