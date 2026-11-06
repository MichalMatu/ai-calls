# AI Calls — aktualna roadmapa

**Bieżący cel:** bezpieczna, małokrokowa modularizacja laboratorium Android. Nie rozwijać równolegle nowych funkcji, jeżeli mogłoby to zasłonić regresję działającego flow INCOMING/OUTGOING. [Zasady i bramki](SAFE_MODULARIZATION_PLAN.md).

## Ukończone

- Etap 0: mapa zależności i kontrakt testowy bez zmian zachowania — PASS; [dowód](checkpoints/2026-11-06-mod-stage0-baseline.md).
- Etap 1: przeniesienie czystego IncomingIdleTimeout do conversation-core, rozdzielenie testów JVM i playback oraz dwa nowe kontrakty czasowe — PASS; [dowód](checkpoints/2026-11-06-mod-stage1-incoming-idle-core.md).
- Produkcyjna sekretarka, własny Call i dwie fizyczne sekwencje end_call — potwierdzone. Timeout 30+15 s potwierdzony przez właściciela; pełny timeline tej sesji jeszcze QA_PENDING.

## Etap 2 — ukończony: NO_CHANGE / PASS

Pełna mapa wyników rozmowy 5/5/3 i `ConversationTermination` wykazała brak uzasadnionego nowego wydzielenia. Kod pozostał nietknięty. Celowane JVM, AndroidTest compile, pełna bramka hosta (266 Python), product friction i clean Git PASS. [Dowód](checkpoints/2026-11-06-mod-stage2-outcomes-nochange.md).

## Etap 3 — pierwszy mały krok ukończony / PASS

`ConversationSessionRegistry` przeniesiono z Android app do czystego JVM `conversation-core` (body identyczne, 4 testy core). Pełna bramka hosta, native compile i friction PASS, 266 Python. [Dowód](checkpoints/2026-11-06-mod-stage3-session-registry-core.md). Następny pojedynczy krok: audyt neutralnego stanu tury, bez domyślnego refaktoru.

## Potem, w oddzielnych małych sprintach

- Etap 3: pojedyncze, czyste odpowiedzialności wydzielane z 815-liniowego AndroidConversationRuntime, bez rozdzielania mediów i cyklu własności Call.
- Etap 4: osobne audyty dużych modułów lokalnej inferencji i realtime, bez przepisywania działających silników.
- Etap 5: usuwanie wyłącznie kodu z potwierdzonym zerowym użyciem — po analizie manifestu, testów, reflection/JNI i diagnostyki. Stare probe i checkpointy nie są automatycznie balastem.

## Równoległy backlog funkcjonalny — nie mieszać z refaktorem

- Fizyczne QA manual/TAKE OVER/powrót AI/STOP i odpowiedź w 15-sekundowym oknie po ostrzeżeniu.
- F-080: ciągły zapis całego połączenia (także manual) i odtwarzacz całości/fragmentów.
- F-044/F-045/F-048: ogólne zadania, zakres faktów, walidacja wypowiedzi i niezależne dowody skutków; porównawcze korpusy STT/model/TTS i późniejsza integracja w jednym APK.

[Plan produktu](PRODUCT_ASSEMBLY_PLAN.md) · [Kwalifikacja laboratorium](TEST_LAB_SCOPE.md) · [Rejestr blockerów](../BLOCKERS.md) · [Handoff](HANDOFF_NEXT_CHAT.md). Historyczne pomiary modeli i Orange pozostają w checkpointach, nie w tym aktywnym harmonogramie.
