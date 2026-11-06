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
Instalacja nowego APK i rzeczywista jakość Gemmy na S22 pozostają QA_PENDING.
F-080/F-048/F-045/F-047 oraz fizyczny F-091 nie są w tym sprincie zamknięte.
