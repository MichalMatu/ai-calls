# Skills i Laboratorium Gemmy — AI Calls MVP

## Menu i konfiguracja

Osobne menu Skills i presety Gemmy z głównego ekranu, ustawień odbierania i przygotowania rozmowy.
Skills MessageCapture, Clarification, NaturalClosure oraz ConversationStyle są podzielone na trzy sekcje.
Fabryczne presety: Sekretarka naturalna, Sekretarka formalna, Zbieranie wiadomości.
Fabrycznych presetów nie zmienia się w miejscu: utwórz własną kopię, aby edytować instrukcję.
Każda edycja tworzy numerowaną wersję. Preset przypina konkretną wersję skilla, umożliwia jej przywrócenie i wyłączenie skilla.
Wszystkie ustawienia są szyfrowane w istniejącym magazynie ConversationPreparation no-backup.
Nowa instrukcja jest zamrażana jako snapshot przy starcie następnej rozmowy przychodzącej.

## Laboratorium

Laboratorium pozwala wybrać preset do testu bez zmiany aktywnego presetu.
Wybrany lokalny model wykonuje syntetyczne wieloturowe rozmowy. Każdy przypadek zaczyna od świeżego kontekstu, zachowując historię między turami.
Mierzymy trafność propozycji END_CALL, przedwczesne i brakujące zakończenia, latencję i heurystyczną liczbę zbędnych pytań (znak zapytania).
Naturalność ma wyłącznie ręczną ocenę 1–5; nie udajemy automatycznej oceny semantycznej.
Nie wykonujemy połączeń, nie uruchamiamy STT/TTS i nie wykonujemy żadnego narzędzia. Wyniki i syntetyczne dialogi pozostają w pamięci Activity.

## Granice i QA

Skill to instrukcja, a nie upoważnienie. Android Telecom, STOP, grant, FactDisclosurePolicy i aplikacyjny executor pozostają właścicielami efektów.
Zamrożone Samsung/Shizuku/PFD audio nie zostało zmienione.
GREEN źródła wymaga celowanych JVM, kompilacji AndroidTest, pełnego verify_host, audytu product friction i czystego commita.
APK z commita e8db8db jest zainstalowane na S22, a F-091 off-call i natywna matryca trzech presetów PASS. Ocena naturalności i faktyczne rozmowy nadal QA_PENDING.
F-080/F-048/F-045/F-047 oraz fizyczny F-091 nie są w tym sprincie zamknięte.

## Matryca S22 — 2026-11-06

Model GEMMA_4_E2B; 4 historie / 12 tur na preset.

| Preset | END_CALL poprawny | Za wcześnie | Brak końca | Pytania: heur. | Generacja średnia | Inicjalizacja |
|---|---:|---:|---:|---:|---:|---:|
| natural | 12/12 | 0 | 0 | 3 | 7029 ms | 8 ms |
| formal | 12/12 | 0 | 0 | 3 | 11012 ms | 8 ms |
| message | 12/12 | 0 | 0 | 3 | 12361 ms | 18 ms |

[Wynik i ograniczenia](checkpoints/2026-11-06-gemma-skills-three-presets-s22-offcall.md).
