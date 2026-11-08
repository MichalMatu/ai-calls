# AI Calls — kompletny checkpoint USB/ADB, Laboratorium i telefonia

**Data:** 2026-11-08 · **Repozytorium:** `MichalMatu/ai-calls` · **Źródło podsumowania:** `main@af571f06a7a34b05a14a8e0b54b8642f40a89e57`

## Werdykt i granice

**SOURCE_PASS / MAC_QA_PASS. Nowy kanał USB/ADB na Samsungu S22: NOT_RUN. Interwencje telefoniczne poprzez nowy kanał: NOT_RUN.** To pełny checkpoint implementacji i dowodów, **nie** fizyczny odbiór nowej APK. Próba publikacji zadania urządzeniowego obejmującego interwencje została zablokowana przed wykonaniem; nie uruchamiano obejścia. Nowa APK nie została w tym sprincie zainstalowana na S22. Starszy preflight ADB `run-as` nie jest dowodem działania nowego receivera.

### Macierz stanu

| Element | Status | Granica dowodu |
| --- | --- | --- |
| Receiver USB, Mac CLI, manifest `DUMP`, debug-only | **SOURCE_PASS** | Kod `main`; brak publicznego TCP/HTTP |
| Mac: Gradle, JVM, lint, app APK, AndroidTest APK, audio | **MAC_QA_PASS** | `scripts/verify_host.sh` i task v4 DONE/0 |
| Testy Python | **370/370 PASS** | Host v2/v3/v4; w tym 5 nowych testów diagnostycznego CLI |
| Android USB kontrakty `AndroidUsbLabControlContractTest` | **BUILT / S22_NOT_RUN** | Kompilacja testów, nie ich fizyczne uruchomienie |
| Sesja Gemmy START/SEND/STOP przez nowy kanał ADB | **SOURCE_PASS / S22_NOT_RUN** | Wspólny `SkillLabRunner`, prywatny transcript |
| Odczyt statusu, RAM, konfiguracji i ustawień INCOMING przez USB | **SOURCE_PASS / S22_NOT_RUN** | Nie potwierdzono reakcji nowej APK |
| TAKEOVER / RESUME / exact owned STOP w aktywnym Call | **SOURCE_PASS / LIVE_CALL_NOT_RUN** | Wyłącznie istniejący właściciel Call; brak dodatniego testu |
| Wyłączność modelu Lab GUI/USB | **HOST_PASS / S22_NOT_RUN** | F-150; procesowa dzierżawa natywnego backendu |
| E2B przy 2048 tokenów | **S22_FAIL_REPRODUCED** | Dwukrotny `LiteRtLmJniException` przy turze 10 |
| E2B przy 4096 tokenów | **S22_PASS_TECHNICAL N=3** | 39/39 tur; 0 JNI, heurystyki 11/13, 13/13, 11/13 |
| E2B przy 8192 tokenów (jawny test) | **MAC_PASS / S22_NOT_RUN** | Guard odmówił, PocketHost foreground; bez instalacji/inferencji |
| Semantyka długiej rozmowy, korekty i halucynacje | **OPEN / HUMAN_UNASSESSED** | Heurystyka nie zastępuje niezależnego review |
| Ledger faktów / context rollover; E4B i inne modele | **PLANNED / NOT_RUN** | Osobne etapy, bez zmiany Call |

## Co zostało zaimplementowane

- `app/src/main/kotlin/pl/michalmatu/aicallbridge/debug/UsbLabControlReceiver.kt`: jednorazowy prywatny request z `run-as`, nonce w chronionym `BroadcastReceiver`, JSON odpowiedzi bez treści. Kontrola `READ_PHONE_STATE`; tylko wariant debug, uprawnienie `android.permission.DUMP`.
- `app/src/main/AndroidManifest.xml`: jawnie chroniony odbiornik USB; nie jest usługą sieciową ani alternatywnym dialerem.
- `scripts/aicall_tools/diagnostics/ai_calls_usb_lab.py`: `status`, `call-status`, `ram`, `watch`, `config-get`, `profile-check/set/reset`, `incoming-config`, `lab-start/send/status/stop/save`, `call-takeover/resume/stop-owned`, `incoming-ai-disable`. Tekst promptów przekazywany do aplikacji przez stdin/ADB, a nie `am broadcast`.
- `AndroidSkillLabSessionFactory`: jedna procesowa dzierżawa backendu LiteRT dla GUI/USB (F-150); brak drugiego model runnera i sztucznego limitu tur. Domyślne: Lab 4096 tokenów, poza Lab Call 2048; wariant 8192 jest jawnie testowy.
- `scripts/tests/test_ai_calls_usb_lab.py` i `app/src/androidTest/kotlin/pl/michalmatu/aicallbridge/debug/AndroidUsbLabControlContractTest.kt`: kontrakty prywatności, nonce, odmowy nieznanych komend, manifest/permission i negatywnych operacji Call. **AndroidTest zbudowane, ale nie uruchomione na S22**.

### Własność działań, dane prywatne, ograniczenia

Nie ma komendy dowolnego `place_call`, globalnego `endCall` ani bezpośredniego wstrzykiwania tekstu do TTS w trakcie rzeczywistej rozmowy. `call-takeover` wywołuje istniejące kontrolery przejęcia własnej aktywnej rozmowy, `call-resume` dotyczy obsługiwanego INCOMING, `call-stop-owned` anuluje konkretny własny OUTGOING i jego kontynuację. Te ograniczenia należy dopiero potwierdzić na urządzeniu.

Dane i prywatny transkrypt Lab pozostają w `files/usb-lab-control/` w aplikacji; na żądanie `lab-save` zapisuje kopię na Macu w `~/Documents/AI-Calls/private-exports/lab-sessions/` (katalog 0700, plik 0600). Bez automatycznej publikacji w GitHub, Local Agent, logcat, bez udostępniania numerów i danych IdentityVault. Śmierć procesu kończy natywną sesję: plik transkryptu nie oznacza możliwości wznowienia KV-cache.

## Zweryfikowane zadania Mac Local Agent

| Task ID (2026-11-08) | Wynik | Znaczenie |
| --- | --- | --- |
| `aicalls-lab-usb-mailbox-preflight-20261108-v1` | DONE/0 | **Starszy** ADB/run-as, S22 IDLE/AI Calls foreground — nie odbiór nowego mostu |
| `aicalls-usb-live-lab-bridge-mac-gate-20261108-v1` | FAILED | Pierwszy lint: brak jawnej kontroli READ_PHONE_STATE, F-149 |
| `aicalls-usb-lab-telephony-permission-qa-20261108-v2` | DONE/0 | Poprawa F-149, pięć testów CLI PASS; cały gate 370/370 |
| `aicalls-usb-live-config-contract-mac-qa-20261108-v3` | DONE/0 | Konfiguracja incoming, Gradle, AndroidTest build, lint, Python 370 PASS |
| `aicalls-usb-lab-shared-native-lease-mac-qa-20261108-v4` | DONE/0 | Poprawka F-150, pełne QA hosta 370 Python/Gradle/JVM/lint/audio PASS, czysty checkout |
| `aicalls-usb-lab-telephony-final-doc-sync-20261108-v1` | DONE/0 | Poprzedni handoff USB zsynchronizowany, Mac clean |

Odrębne dowody starszej ścieżki E2B: `aicalls-semantic2-s22-context4096-20261108-v1` DONE/0 oraz `aicalls-e2b4096-n3-ram-native-20261108-v1` DONE/0, łącznie **3×13 = 39/39** tur; średnia na odpowiedź w kolejnych sesjach 6686/5346/7633 ms. Próbkowany najwyższy app PSS **2 348 103 kB**, najniższe systemowe `MemAvailable` **1 378 220 kB** — to nie jest dokładny pomiar maksimum ani VRAM.

## Statusy blokad i jakości

- **F-148 OPEN:** 2048 dwukrotnie przerwało się w turze 10; 4096 przeszło N=3 technicznie. Przyczyna JNI i szeroka semantyka nadal nieudowodnione; ocena przez człowieka UNASSESSED.
- **F-149 VERIFIED:** jawna kontrola `READ_PHONE_STATE` naprawiona i potwierdzona pełnym Mac lint/QA; nie oznacza fizycznego PASS USB.
- **F-150 FIXED_IN_SOURCE / HOST_PASS:** jeden backend dla GUI i USB; realna konkurencja S22 **NOT_RUN**. Pozostawić DEVICE_QA_OPEN.

## Handoff — bez powtarzania już zaliczonych testów

1. Odczytać świeży `main` HEAD, AGENTS, handoff, stan Local Agenta i clean Mac; nie zakładać SHA checkpointu jako bieżącego.
2. **Kiedy wykonanie urządzeniowe będzie dopuszczalne:** wgrać debug APK bez wipe, odczytać `DUMP` receiver, wykonać `status/config-get/ram`, odmowy interwencji bez Call, `AndroidUsbLabControlContractTest`, dwie lokalne tury Gemmy START/SEND/STOP, prywatny `lab-save`, brak równoległego kontekstu UI/USB.
3. Rzeczywiste interwencje sprawdzić **oddzielnie** tylko dla konkretnie autoryzowanego własnego Call: status, TAKEOVER, RESUME, exact owned STOP i czas reakcji; bez przypadkowych połączeń.
4. Następnie porównać 8192 z 4096 na realnym S22 (RAM/JNI/latencja/semantyka), nie zmieniając poza Lab budżetu 2048 bez odrębnego odbioru.
5. W osobnym sprincie wdrożyć typowany rejestr faktów/sprostowań i bezpieczny rollover kontekstu; dopiero potem porównać E4B/inne modele.

**Tryb:** GitHub Connector + Mac Local Agent, `main` bez nowych gałęzi, `[skip ci]`, bez Codex/Work/GitHub Actions, bez odtwarzania czterech odrzuconych fabrycznych historii, bez zmiany zamrożonego toru Samsung/Shizuku. Wymagania: [USB Lab/telefonia](../USB_LAB_TELEPHONY_CHANNEL.md), [kontekst E2B](2026-11-08-e2b4096-n3-ram-and-8192-instrumentation.md), [pamięć rozmowy](../LAB_E2B_CONTEXT_MEMORY_PLAN.md), [rejestr blokad](../../BLOCKERS.md).
