# AI Calls — nowy czat: domknięcie Sprintu 6 lub UX-01

Kontynuuj `MichalMatu/ai-calls` od **aktualnego** `main`, tylko **GitHub Connector + Mac Local Agent** (`macbook-michal`, installation `cfea4cc3-b54c-4b6d-b9a6-758d12733e98`, repo Mac `/Users/michal/agent-workspace/repos/ai-calls/work`). Bez Codex/Work/GitHub Actions, nowych gałęzi, force-push, prawdziwych połączeń bez upoważnienia ani publikowania prywatnych danych. Commity `[skip ci]`.

Najpierw odczytaj [HANDOFF_NEXT_CHAT](HANDOFF_NEXT_CHAT.md), [checkpoint Sprint 6](checkpoints/2026-11-07-ux-sprint6-source-green-s22-adb-pending-doc-freeze.md), [BLOCKERS](../BLOCKERS.md), [ROADMAP](ROADMAP.md), [SAFE_MODULARIZATION_PLAN](SAFE_MODULARIZATION_PLAN.md) i aktualne Mac receipts. Ostatni HEAD funkcjonalny Sprintu 6: `0dface1e86e6bf9bab65090c3af293ff12f9dc7c`; po dokumentacji HEAD może być nowszy.

**Obowiązuje:** source QA Sprint 6 **325 PASS** na exact `0dface1`. Fizyczny odbiór nie odbył się: `adb devices -l` Maca był pusty; S22 instalacja/testy `QA_PENDING_ADB`. Realna Gemma Sprintu 6 `NOT_RUN` (czekała na brakujący ZIP). Nie przypisuj Sprintowi 6 fizycznych wyników Sprintu 5.

**Pierwszeństwo, gdy dostępny S22:** po bezpiecznym IDLE/role/signer/grant/retry preflight wykonać tylko brakujący odbiór Sprint 6 (28 native, 24 visual, min. 42 private screenshoty), następnie model offline 2 tury + auto-follow + STOP i 4 kolejne prywatne screenshoty. Test potwierdzenia `Usuń wszystkie rozmowy` **tylko anuluje**, nigdy nie kasuje rzeczywistych archiwów. Bez S22 nie tworzyć kolejnych pętli retry; zachować QA_PENDING.

**Równoległy kolejny logiczny krok (po świadomej zmianie zakresu):** `UX-01 CampaignExecutionStatusPresenter` — wydzielić wyłącznie czyste read-only mapowanie zapisanego kodu runtime na typowaną lokalizowalną prezentację. Activity renderuje; nie zmieniaj `CampaignAgentControl`, autoryzacji, Telecom, STOP, Call ownership, PCM/PFD ani recorder. Jedna zmiana, kontrakty testowe PL/EN i raw diagnostics, host gate i checkpoint. Historia/bulk delete, Lab runner i F-103 są następnymi **osobnymi** krokami, nie częścią UX-01.

Zamknij wyniki dokładnymi receipts w checkpoincie i aktualnym handoffie; bez oznaczania HOST_GREEN jako PROVEN_S22.
