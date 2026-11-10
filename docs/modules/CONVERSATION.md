# Rozmowa — STT → Gemma → Skills → TTS

**Runtime:** `AndroidConversationRuntime`, `LocalSpeechTextPipeline`, `LocalSpeechGenerationGate` i wspólny `conversation-core`. INCOMING i OUTGOING nie mają osobnych modeli rozmowy. Domyślnie Gemma 4 E2B/GPU, kontekstowe dynamic Skills, Android STT/TTS; Qwen i ALL_ACTIVE tylko w Lab.

## Granice tury
- RX speech-start/stop, VAD/EOS, transkrypcja, snapshot kontekstu, autoryzacja tekstu i typowanych narzędzi, TTS, TX, archiwizacja. Wielokrotne okna RX w tej samej turze zapisują osobne WAV z suffixem part-NNN, bez nadpisania. Przy STOP/Call-ended otwarte okna RX są finalizowane przed END/COMPLETE; nie uruchamia się wtedy spóźnionego STT/TTS/TX. Przy faktycznej presji przestrzeni tylko zweryfikowane ukończone prywatne sesje mogą być usuwane, z ochroną aktywnego katalogu i symlinków.
- Każda STT/model/TTS odpowiedź musi być związana z sesją, generacją i epoką tury; deferred transcript outcome i no-match retry sprawdzają ConversationTurnOwner, a capture error dodatkowo numer próby RX (również przy wielu oknach ciszy tej samej tury), a queued/stale PCM kończy się na granicy klatki. Przerwanie lub STOP anuluje stare wyjście oraz kolejkę; nieprawidłowy/niepewny status nie może automatycznie wznowić TTS.
- Realtime `CallRealtimeAudioPump` ma atomową blokadę gate/queue na barge-in, tombstones i bounded audio; **lokalny Gemma ma syntetyczny RX-during-TX restart z ograniczonym pre-roll; RX-pump EOF blokuje nowe okna, a utrata RX podczas TX kończy stary output bez udawania odpowiedzi; echo/double-talk i fizyczny barge-in S22 pozostają OPEN**.
- Recovery po ciszy/hold nie może sztucznie ucinać rozmowy, ale respektuje zasoby, systemowy Call state i STOP. Przy fizycznym rozłączeniu dokończ dostępny końcowy RX/STT bez udawania brakującej mowy.

**Semantic end-of-turn:** wzorzec TEN rozróżnia FINISHED / UNFINISHED / WAIT, ale obecne PCM VAD/STT nie dostarcza wiarygodnej klasyfikacji semantycznej po polsku. KEEP obecny endpointing; brak importu modelu TEN i automatycznego zatrzymywania odpowiedzi na podstawie słów. Ewentualny classifier wyłącznie po własnych pomiarach i osobnym teście granic STOP.

## Dowody i otwarte
G3 96/96 native tur zakończone decyzją KEEP_E2B; G5.3 18/18 off-call tur i natywny STOP STT/model/TTS sprawdzone; to **nie** jest live G6. Otwarty G6.1 lokalny barge-in i timed speech-start, sprawdzenie stale PCM i manual resume. [Current](../PROJECT.md), [security](../architecture/SECURITY.md).
