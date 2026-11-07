# AI Calls — indeks aktualnej dokumentacji

**Stan 2026-11-07:** najnowszy source `bbb2ceb9` naprawia zbędne rewizje scenariuszy, a [checkpoint general audit](checkpoints/2026-11-07-general-repository-audit-sprint.md) rozdziela RED, izolowany GREEN i oczekiwane receipts Mac. Ostatni pełny potwierdzony odbiór UX-06: `25a41a0`, Mac 341 Python/JVM/lint/APK PASS i S22 10/10 **syntetyczne UI** PASS. Nowy kod **nie ma** z tego automatycznie fizycznego PASS. UX-01–05 i Sprint 6 to wcześniejsze zamknięte zakresy.

**Rozpocznij od:** [README](../README.md) · [AGENTS](../AGENTS.md) · [aktualny HANDOFF](HANDOFF_NEXT_CHAT.md) · [NEXT_CHAT_PROMPT](NEXT_CHAT_PROMPT.md) · [ROADMAP](ROADMAP.md) · [checkpoint audytu](checkpoints/2026-11-07-general-repository-audit-sprint.md) · [UX-06 źródła i S22](checkpoints/2026-11-07-ux06-source-s22-synthetic-acceptance.md) · [UX-06 handoff](checkpoints/2026-11-07-ux06-complete-checkpoint-user-directed-next-chat.md).

## Gdzie szukać

| Temat | Dokument źródłowy |
|---|---|
| Priorytety, co zrobić teraz i co już zamknięte | [ROADMAP](ROADMAP.md) |
| Separacja widoków, modelu stanu i efektów | [SAFE_MODULARIZATION_PLAN](SAFE_MODULARIZATION_PLAN.md) |
| Mapa modułów i warunki bezpieczeństwa | [ARCHITECTURE](ARCHITECTURE.md) · [CONVERSATION_RUNTIME](CONVERSATION_RUNTIME.md) |
| Aktualne ekrany, bulk delete i audio z dymków | [SETTINGS_UX](SETTINGS_UX.md) |
| Presety Gemmy i znaczenie wyników Laboratorium | [SKILLS_AND_LAB](SKILLS_AND_LAB.md) |
| Wymagania produktu i zakres testów | [PRODUCT_ASSEMBLY_PLAN](PRODUCT_ASSEMBLY_PLAN.md) · [TEST_LAB_SCOPE](TEST_LAB_SCOPE.md) |
| Błędy i realne blokady | [BLOCKERS](../BLOCKERS.md) — jedyny rejestr |
| Zasady pracy, autoryzacja i prywatność | [AGENTS](../AGENTS.md) · [SECURITY_PRIVACY](SECURITY_PRIVACY.md) · [GENERIC_PHONE_TASK_AUTHORITY](GENERIC_PHONE_TASK_AUTHORITY.md) |
| Mac/Samsung, USB i testy fizyczne | [USB_DEBUG_BRIDGE](USB_DEBUG_BRIDGE.md) · [LOCAL_AGENT_HOST_FLOW](LOCAL_AGENT_HOST_FLOW_2026-11-01.md) |

## Historia, nie aktywne polecenia

Starsze pliki w [`checkpoints/`](checkpoints/) są dowodami konkretnego HEAD / daty, a nie zbiorem równoległych planów. Przykłady: [zamknięcie modularizacji](checkpoints/2026-11-06-modularization-handoff-final.md), [mod Stage3](checkpoints/2026-11-06-mod-stage3-session-registry-core.md), [S22 Skills 12 tur](checkpoints/2026-11-06-gemma-skills-three-presets-s22-offcall.md). Powtórzenia usunięte z aktywnego handoffu/roadmapy pozostają w historii Git; nie kasować historycznych RED ani checkpointów.

Prywatne JPG, WAV, numery i transkrypty przechowuje wyłącznie urządzenie/Mac właściciela; **nie** są częścią repozytorium.
