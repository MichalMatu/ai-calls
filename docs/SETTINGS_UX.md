# AI Calls — architektura UX ustawień i historii

**Stan kodu:** `d59b459a9a50d2f9c8f037f2116d802a9b288617` (2026-11-06). To mapa stanu aplikacji i następnych kroków, nie makieta ukończonego całego produktu.

## Nawigacja

- **Start:** status silników i modelu, rozpoczęcie autoryzowanego zadania, kampanie, STOP AI. START/STOP pozostają poza zwijanymi sekcjami.
- **Rozmowy i nagrania:** osobny przycisk na głównym ekranie prowadzi do `ConversationHistoryActivity`. Pinned `ConversationChatPanel` pokazuje czat na żywo i ma dodatkowe wejście do historii.
- **Laboratorium offline:** bezpośrednie wejście na głównym ekranie, bez inicjowania rozmowy telefonicznej.
- **Ustawienia aplikacji:** pięć czytelnych sekcji `UiAccordionSection`, bez nowego magazynu konfiguracji.

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

Odtwarzanie wymaga dostępnego prawidłowego pliku w katalogu prywatnej sesji i potwierdzenia, że nie trwa rozmowa; player zwalnia zasoby po opuszczeniu ekranu. `FLAG_SECURE` pozostaje włączone. Nie eksportuje się treści, WAV ani identyfikatorów rozmów do GitHub.

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
