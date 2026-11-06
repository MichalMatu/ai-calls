# AI Calls — etap 3, krok 1: ConversationSessionRegistry do JVM core (2026-11-06)

## Preflight i motywacja

- Bazowy HEAD main = origin/main = `b7818fb00d138b7e50a1fabe86def02738ddf434`; Git clean przed zmianą, Mac `macbook-michal`, instalacja `cfea4cc3-b54c-4b6d-b9a6-758d12733e98`.
- Mac read-only task `aicalls-20261106-mod-stage3-preflight-v1`: DONE/0. `ConversationSessionRegistry` miał dokładnie trzy referencje źródłowe: definicję w app, konsumenta `AndroidConversationRuntime` oraz cztery testy JVM app. Zero Android imports w samej regule.
- Wydzielenie ma realną granicę: jeden aktywny właściciel sesji, odrzucenie drugiego startu bez konstruowania backendu, ochrona nowej sesji przed stale release. Prawidłowe miejsce dla tej platformowo niezależnej reguły to `conversation-core`; Android runtime pozostaje jedynym adapterem mediów, auth i Telecom.

## Jedno wydzielenie, bez zmiany semantyki

- Przeniesiono `ConversationSessionRegistry.kt` z `app/conversation` do `conversation-core/conversationcore`. Zmieniono tylko package i widoczność `internal` → `public`, wymaganą przez zależność Gradle. Oryginalny SHA-256 zawartości: `f0dcd77ceb3dbd503de5d1d4cef80b874146c314f18858ec3691091722b78f8e`. Odtworzenie nagłówka daje identyczny SHA, a ciało implementacji niezmienione.
- Przeniesiono bez zmian cztery testy do JVM core (poza deklaracją package). `AndroidConversationRuntime` otrzymał jeden import. Nie dodano aliasów, wrapperów, gałęzi, limitów czy callbacków.
- INCOMING/OUTGOING, 30+15 s, CALL_ENDED, STOP, human takeover, polityka grantu, Samsung/Shizuku, PCM/PFD, STT/TTS, full TX → end_call i recorder pozostają nietknięte.

## Rzeczywiste receipts

- Mac task `aicalls-20261106-mod-stage3-registry-move-gate-v1`: **DONE/exit 0**, 222,77 s.
- `:conversation-core:test` **PASS** (w tym 4 przeniesione testy), `:app:compileDebugAndroidTestKotlin` **PASS**, celowane app JVM `ConversationToolPlaybackTest`, `IncomingIdlePlaybackTest`, `ConversationRxInterruptionCodeTest` **PASS**.
- `bash scripts/verify_host.sh`: **PASS**, `host_quality_gate_green=true`, Gradle BUILD SUCCESSFUL w trzech fazach (9 s, 2m55s, 15 s), **266 testów Python PASS**.
- `audit_product_friction.py`: **ANTI_FRICTION=PASS**; git diff --check i dokładny zakres zmian PASS. Brak nowego blokeru do rejestru.
- Prywatne receipts na Mac: `~/Documents/AI-Calls/local-agent/2026-11-06-modular-stage3`, mode 0700/0600. Bez prywatnego audio, transkryptów, numerów czy wywołań na Samsungu.

## Publication recovery

Publikacja v1 (`aicalls-20261106-mod-stage3-registry-checkpoint-commit-v1`) zakończyła się FAIL/128 na `git add` starej ścieżki pliku już przeniesionego przez `git mv`; nie powstał commit ani push i nie zmieniono kodu. v2 używa stagingu zakresu po sprawdzeniu kompletu ścieżek; wynik v1 pozostaje w historycznym task-result. Testy źródła z taska GREEN nie były powtarzane.

## Status i handoff

**Etap 3, krok 1: PASS (host) — małe wydzielenie zamknięte.** Nie deklaruje ukończenia wszystkich możliwych kroków etapu 3 i nie uruchamia etapów 4–5. Na S22 nadal starsze APK sprzed etapu 1; bez nowego instalowania i połączeń.

**QA_PENDING osobno:** event-level timeline ostatniego timeoutu, manual/TAKE OVER/powrót AI/STOP oraz głos podczas 15 s grace; wcześniejsze fizyczne end_call i obserwację właściciela zachowano.

**Następny pojedynczy krok w osobnym czacie:** read-only mapa neutralnej logiki stanu tury w `AndroidConversationRuntime`; kolejne wydzielenie wyłącznie gdy rzeczywiście poprawia granice i przejdzie nowy gate, w przeciwnym razie NO_CHANGE.
