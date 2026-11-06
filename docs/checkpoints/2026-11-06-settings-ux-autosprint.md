# AI Calls — autonomiczny sprint UX ustawień, nawigacji i archiwalnego audio

**Data:** 2026-11-06. **Branch:** `main`. **Punkt wejścia:** `413886372d62cc63d6672b16f6dd08a10a539b4c`. **Zweryfikowany HEAD końca zmian źródłowych:** `d59b459a9a50d2f9c8f037f2116d802a9b288617`.

## Wynik etapów

1. **Pięć kategorii ustawień**, ergonomiczne sekcje, zachowany START/STOP, kontrolki i magazyny danych. Commit `b448ace16e3a122afeb34477ba6c93f9b3f82ddd`. Quality gate Mac `aicalls-20261106-settings-ux-stage1-full-android-gates-v3` DONE/0, publikacja `aicalls-20261106-settings-ux-stage1-publish-green-v3` DONE/0. Testy: 266 Python, kompilacja AndroidTest, pełny host i friction PASS. Dwie próby wcześniejszego kontraktu/shell zakończyły się FAIL i zostały zachowane, nie maskowane.
2. **Bezpośrednie wejścia** `Rozmowy i nagrania` oraz `Laboratorium offline` poza ustawieniami. Commit `2fca5f9152e023146f1f0eccfb2a8aaab4009c10`. Gate `aicalls-20261106-settings-ux-stage2-primary-navigation-gates-v1` DONE/0 i publikacja `aicalls-20261106-settings-ux-stage2-publish-green-v1` DONE/0. Dwa celowane kontrakty, 268 Python, AndroidTest compile, full host, friction PASS.
3. **Odsłuch istniejących WAV z archiwum czatu**: powiązanie tur z segmentami RX/TX/ACK, walidacja nazw/katalogów, bezpieczny player z guardem stanu połączenia, zwalnianie w `onPause`. Commit `d59b459a9a50d2f9c8f037f2116d802a9b288617`. Gate `aicalls-20261106-settings-ux-stage3-archive-audio-gates-v1` DONE/0, publikacja `aicalls-20261106-settings-ux-stage3-publish-green-v1` DONE/0. Test JVM poprawności mapowania i odmowy path traversal, 2 kontrakty Python, AndroidTest compile, 270 Python, full host, anti-friction PASS.

## Granice

Nie zmieniono runtime połączenia, modelu, telefonii, backendu RX/TX, Shizuku, uprawnień, grantów, polityki STOP ani prywatnych danych. Tylko wcześniej istniejące nagrane fragmenty są odtwarzalne. F-080 jest **OPEN** dla pełnego zapisu sesji i playera całości.

**S22:** `aicalls-20261106-settings-ux-s22-readonly-preflight-v1` FAILED/2: `exact_s22_adb_transport_unavailable`. Brak potwierdzenia fizycznej nawigacji i odtwarzania; nie instalowano APK, nie wykonywano połączeń, żaden model nie był uruchamiany. Status fizyczny **QA_PENDING**, nie PROVEN_S22.

## Kontynuacja

Po odzyskaniu fizycznego S22 najpierw 2× IDLE, brak AppOwnedCampaignService/pending retry, zgodność sprzętowa, potem instalacja zweryfikowanego APK zachowująca dane i celowana instrumentacja UI. Osobny etap F-080 obejmuje pełną oś mediów, luki, manual takeover i player całości. Spójny dolny pasek zakładek i wizualne ujednolicenie podstron pozostają do wykonania. Zob. [mapa UX](../SETTINGS_UX.md) i [rejestr blockerów](../../BLOCKERS.md).
