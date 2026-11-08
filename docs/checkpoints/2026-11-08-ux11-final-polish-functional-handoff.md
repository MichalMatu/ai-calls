# AI Calls — UX-11, ostatni sprint wizualny (2026-11-08)

**Baseline** `main@73d96911f987a9e1c08d1859d35eb3b4fb1c0ee2`. Ostatni kontrakt UX: czytelna rozmowa w Lab, kompaktowy Sejf i rzeczywiste pokazanie floating overlay przy *syntetycznym* feedzie. UX-10: 353/353 Mac QA i 26/26 S22 Android, 17/17 prywatnych screenshotów w archiwum `AI-Calls-S22-UX10-top17-private.zip` (exact receipts `aicalls-ux10-floating-vault-lab-mac-20261108-v5`, `aicalls-ux10-s22-guarded-visuals-20261108-v3`).

## Zmiany
- **Lab:** nagłówek `Przebieg rozmowy` / `Conversation timeline`, neutralny placeholder, ukryty progress READY, zachowany niezależny scroll i stale dostępny STOP. Silnik offline i metryki bez zmian.
- **Sejf:** jeden zwarty wiersz na pole (nazwa + bezpieczny status, ikony edytuj/usuń), duży przycisk `Dodaj własne pole` nad listą; żadnego plaintext w overview. Nie rusza szyfrowania, istniejących rekordów, udostępniania ani ujawnień.
- **Pływający czat:** sam komponent UX-10 niezmieniony; test `AndroidUx11FloatingSyntheticTest` wstawia jedynie *syntetyczne* `ConversationChatFeed.begin/heard/replied/finish`, bez zlecania rzeczywistej rozmowy. Utrwala prywatny zrzut `chat-active-synthetic.jpg` tylko w katalogu UI-audit S22.

## Test / przekazanie
- Nowy `scripts/tests/test_ux11_final_polish.py`, rozszerzony `AndroidUx10OverlayVaultLabTest`, nowy `AndroidUx11FloatingSyntheticTest`.
- Mac Local Agent: compile app + AndroidTest, pełny `verify_host.sh`, friction, git clean. S22: tylko gdy Mac quality PASS; preflight SM-S906B, IDLE, wolna kampania/Call, instalacja `-r`, testy syntetyczne, prywatne screenshoty. **Status QA_PENDING do dokładnego receipt.**
- UX po zamknięciu QA: traktować jako frozen do chwili wykrycia funkcjonalnego P0/P1. Następny etap: funkcjonalna macierz STT → model → Skills → TTS → kontrolowany test offline → właściwości start/stop/rollback i po autoryzacji realna telefonia na celu znanym użytkownikowi. **Nie deklarować sukcesu połączenia, archiwizacji audio ani skutku zewnętrznego bez osobnych dowodów.** Nie wprowadzać nowych permission, grant, audio/Telecom, autoryzacji, modeli lub migracji sejfu w UX-11.
