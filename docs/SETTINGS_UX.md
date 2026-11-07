# AI Calls — architektura UX ustawień i historii

**Aktualny stan:** `6a3aad0` — minimalistyczne Home, kompaktowa lista Calls, Tabler, spójny wybór i rozwijany język; 300 host + 22/22 S22 PASS. Starsze checkpointy poniżej są historyczne. [Bieżący checkpoint](checkpoints/2026-11-07-minimal-ui-tabler-s22-22of22.md).

## Nawigacja

- **Start:** status silników i modelu, rozpoczęcie autoryzowanego zadania, kampanie, STOP AI. START/STOP pozostają poza zwijanymi sekcjami.
- **Rozmowy / Laboratorium / Ustawienia:** jedynie cztery stale widoczne dolne zakładki Home/Calls/Lab/Settings; nie ma zduplikowanych skrótów Home i czatu.
- **Rozmowy:** pojedynczy wiersz z ikoną kierunku, datą/liczbą wiadomości oraz osobnym koszem dla zakończonej sesji; potwierdzenie kasowania nadal wymagane.
- **Ustawienia aplikacji:** bezpośrednie ekrany kategorii, wspólne listy wyboru `UiSelect`, język/wygląd jako zwijany wiersz; brak fikcyjnych sekcji. Poprzednie akordeony są historyczne.

## Inwentaryzacja istniejących kontrolek

| Sekcja | Faktyczne kontrolki i ekran docelowy |
|---|---|
| Podstawowe | Tryb audio (Local STT/TTS, OpenAI Realtime frozen, Local Realtime server); dostawca tekstowego LLM (OpenAI, lokalny S22, LiteRT-LM, lokalny Mac); katalog i transfer modeli (Gemma 4 E2B/E4B, Qwen3 1.7B/0.6B); ustawienia wejścia STT (Android, Parakeet, eksperymentalna Gemma Audio), modelu dialogu, wyjścia TTS (Android, Piper, niedostępne dla PL Kokoro/PocketTTS), głosu, sprawdzenia offline, odsłuchu, pobrania i resetu; mikrofon, sejf telefonu |
| Sekretarka | Polityka ręcznie/AI, SIM, zapis rozmowy; profile przychodzące/wychodzące, notatki, cztery skillsy przygotowania, referencje 7 pól sejfu, podgląd roli i zapis |
| Skills i presety | Trzy presety fabryczne, własne kopie, aktywacja, włączanie skillsów, nowe wersje instrukcji, wybór/przywracanie wersji, podgląd złożonego promptu. Preset skillsów nie jest jeszcze presetem całej ścieżki STT→model→TTS |
| Laboratorium | Wybór presetu testowego, wieloturowy test modelu offline, anulowanie, raport wyników i ręczna ocena naturalności |
| Zaawansowane | Narzędzia AI (`END_CALL`), status zdarzeń, test AI w trwającej rozmowie, USB/ADB, sondy urządzenia/Shizuku i pozostałe eksperymentalne kontrolki |
| Kampanie i telefon | Osobne ekrany: START skrypt/Direct, polityka zapisu, STOP, anulowanie, dialer Android; ręczne połączenie, odbiór, przejęcie/wznowienie AI i odrzucenie. Nie są częścią akordeonów ustawień |

## Czat, historia i dostępne audio

`ConversationChatFeed` jest prywatnym podglądem bieżącej sesji, nie archiwum. `RecordedConversationHistory` czyta oryginalny `timeline.jsonl` bez kopiowania transkryptu. Historia wyświetla dymki rozmówcy/AI/narzędzi, stan TX oraz **przycisk odsłuchu istniejącego segmentu WAV RX lub TX przy odpowiadającej wypowiedzi**. Osobno dostępne są dodatkowe segmenty, m.in. TX-ACK.

Odtwarzanie wymaga dostępnego prawidłowego pliku w katalogu prywatnej sesji i potwierdzenia, że nie trwa rozmowa; player zwalnia zasoby po opuszczeniu ekranu. `FLAG_SECURE` został usunięty zgodnie z decyzją właściciela; screenshoty są możliwe, bez automatycznego eksportu prywatnych danych. Nie eksportuje się treści, WAV ani identyfikatorów rozmów do GitHub.

**F-080 pozostaje OPEN:** obecny recorder obejmuje okna RX/STT i zapisany lokalny TX, nie pełną, nieprzerwaną sesję, IVR/hold czy głos użytkownika po przejęciu. Nie ma jeszcze osi pokrycia, odtwarzacza całości ani płynnego seek po rozmowie. Brakujące audio nie jest oznaczane jako cisza.

## Potwierdzone quality gates

- Etap 1 (`b448ace`): AndroidTest compile, 266 Python, pełny host, anti-friction — PASS.
- Etap 2 (`2fca5f9`): AndroidTest compile, 268 Python, pełny host, anti-friction — PASS.
- Etap 3 (`d59b459`): celowany JVM test mapowania audio, AndroidTest compile, 270 Python, pełny host, anti-friction — PASS.
- Fizyczne S22 QA: **PASS 7/7** instrumentacyjnie (sekcje, nawigacja do historii, 8 ekranów, odczytowe zachowanie preferencji, syntetyczny cichy WAV w playerze); podpis APK zgodny, dane zachowane, brak połączeń. [Receipt](checkpoints/2026-11-06-settings-ux-s22-physical-acceptance.md).

## Następny UX etap

Prawdziwy wspólny pasek zakładek i spójne karty Material dla ekranów podrzędnych; ręczna ocena wyglądu i osobna matryca edycji/zapisu każdej kontrolki. Pełny recorder i odtwarzacz całej sesji F-080 pozostają osobną zmianą granicy mediów, bez naruszania zamrożonego toru Samsung.

## Audyt wizualny na S22 — po zrzutach z 2026-11-06

Poprawiono kontrast, tła pól STT/LLM, złożone sekcje i czat, system bars (API 29+), hierarchię start/Kampanie/STOP i skróty Rozmowy/Laboratorium, polskie etykiety home. Kod `2f58adf` + `312db7a`. **Mac host PASS (272 Python), S22 fizyczne 8/8 UI PASS**, bez kasowania danych i połączeń. [Dane testów i ograniczenia](checkpoints/2026-11-06-menu-visual-s22-ux-acceptance.md).

Ekrany podrzędne i wspólny docelowy pasek nawigacji to następny zakres UX. Pełny recorder/player F-080 nadal OPEN.

## Fundament i18n PL/EN i jasnego panelu czatu (2026-11-07)

`values/strings.xml` i `values-en/strings.xml` mają parytet kluczy; `UiDesign` jest wspólną warstwą prezentacji, a dok czatu ma jasną kartę i przycisk rozwijania 48 dp. **Mac host PASS: 275 Python, AndroidTest compile, lint/APK i anti-friction. S22 QA_PENDING:** brak transportu ADB, bez instalacji. [Receipt](checkpoints/2026-11-07-i18n-dock-foundation-host.md).

To dopiero fundament: użytkownik nie ma jeszcze przełącznika Auto/PL/EN, pozostałe podstrony wymagają migracji. Nigdy nie wiązać języka UI z STT/TTS lub językiem wypowiedzi AI.

## S22 acceptance: i18n dock 10/10 PASS

After reconnecting exact S22 and verifying 2× IDLE, no campaign/retry and APK signing lineage, the localized light dock and existing UX matrix passed **10/10 Android instrumented tests**. App data preserved, no calls. [Checkpoint](checkpoints/2026-11-07-i18n-dock-s22-acceptance.md). This only validates the resource catalog and chat dock, not a complete EN app; F-103 remains OPEN.

## Direct menu and shared chrome (2026-11-07)

**The original five Home accordion sections above are historical.** The current code introduces Home/Conversations/Lab/Settings tabs, a direct settings hub with dedicated Basic and Diagnostics screens, and `UiChromeActivity` wrapping child pages. Shared platform theme, button states, Back affordance and density-aware margins are used without switching the Android codebase to Compose. Preference writes for Basic settings are now in `GeneralSettingsController`, delegating to original storage. Current source `0afea79` is host-green **284 Python** and native S22 UI **15/15 PASS** (same signing, -r preserve-data installation, no calls). Earlier 10/10 applies only to previous i18n/chat dock revision; subjective visual acceptance is still open. Detailed [checkpoint](checkpoints/2026-11-07-ui-unified-navigation-checkpoint.md). F-103 full PL/EN and remaining imperative Activity-controller coupling remain open.

## Screenshots / Calls / language finishing (2026-11-07)

All eight legacy `FLAG_SECURE` windows cleared by explicit owner request; screenshots must remain usable on every screen. Calls detail Back stays within Calls; completed conversations have individual Delete with confirmation, including local WAV/transcript removal. Common chrome spaces action buttons. SettingsHub gains Auto/PL/EN chooser backed by system app locale on Android 13+ and local compatibility on older platforms. Older Activities contain some hardcoded Polish text: full i18n remains OPEN F-103. See [checkpoint](checkpoints/2026-11-07-calls-screenshots-ui-phase.md).

## Incoming secretary and AI tools UI/controller split (2026-11-07)

IncomingAssistantSettingsController owns existing policy store, SIM enumeration, explicit save and notification; its Activity only renders controls. ConversationToolsSettingsController owns existing enabled-tool preference writes and typed diagnostic status; the Activity only renders translated labels. PL/EN resources added for both screens. This does not extend call or tool authorization. Complete PL/EN remains open; physical S22 controller/navigation acceptance later passed 21/21;  see [checkpoint](checkpoints/2026-11-07-ux-controller-qa-adb-handoff.md).

## S22 accepted UX build — 21/21 native tests (2026-11-07)

At `035af45`, Home/Calls/Lab/Settings shell, incoming assistant, tool settings, screenshot, safe synthetic Calls Back/Delete/WAV and navigation matrix passed 21/21 physical checks with 0 live calls. The app/test APKs were installed without wiping user data. [Physical receipt](checkpoints/2026-11-07-s22-ux-latest-21-21-acceptance.md). Final human visual review and remaining legacy PL/EN labels are still pending.

## Minimalistyczny interfejs zaakceptowany na S22 — 2026-11-07

Home bez dubli, Calls w jednym wierszu z Tabler kierunkiem i koszem, Tabler MIT VectorDrawable, płaskie przyciski bez dziedziczonego cienia, `UiSelect` w General/Engines/Lab oraz zwijany język Auto/PL/EN. Mac host **300 PASS**, S22 **22/22 PASS** bez połączeń i bez czyszczenia danych. [Dokładny checkpoint](checkpoints/2026-11-07-minimal-ui-tabler-s22-22of22.md). Ręczna estetyka i pełny PL/EN nadal osobno.

## Sprint 5 — bieżąca hierarchia i realny test Skills Lab (2026-11-07)

Ekran Start oddziela dostawcę tekstowego LLM, model dialogowy i gotowość pliku; autoryzacja nie jest utożsamiana z aktywnym połączeniem. Kampanie pokazują ostatni zapis wykonania (nie gwarantuje bieżącej aktywności) oraz krótkie wyjaśnienie START / STOP / Anuluj. Skills rozdziela opisy i blokuje ponowne aktywowanie bieżącego presetu. Silniki mają Zapis przed sekcją opcjonalnych testów; Modele używają wtórnych CTA i czytelnych metadanych. Historia pozostaje zwarta; kasowanie wymaga wejścia w szczegóły i potwierdzenia. Skills Lab: szybkie 2 tury / pełny korpus, odpowiedź i heurystyki na bieżąco, bez wykonania END_CALL. **Realny dwuturowy lokalny model + STOP PASS na S22**, niezależnie od 2/2 syntetycznych prezentacji. 27/27 native UI + 21/21 base/expanded screens, 43 zdjęcia prywatnie. [Dowód](checkpoints/2026-11-07-ux-sprint5-live-lab-s22-acceptance.md).
