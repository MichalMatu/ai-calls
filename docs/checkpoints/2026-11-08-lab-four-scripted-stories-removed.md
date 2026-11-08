# AI Calls — usunięcie 4 odrzuconych historii Laboratorium

Data: 2026-11-08. Baza `main@b2fc6ce788bfb27d5ef342d387dad6ee555167ba`. Decyzja właściciela: **usunąć wszystkie cztery** sztywne historie; nie zostawiać ich nawet jako regresji i nie podawać starych 12/12 jako wskaźnika naturalności.

## Zmiana kodu
- `SkillLabCorpus.stories` jest teraz puste; z kodu produkcyjnego usunięto wszystkie 12 wpisanych z góry wypowiedzi. Nie dodano zastępczych sztywnych rozmów.
- `SkillLabRunner` otrzymał możliwość wstrzykiwania testowych danych dla unit testów, a brak aktywnych przypadków blokuje `start()` bez inicjalizacji modelu, wykonywania END_CALL i fałszywego wyniku 0/0.
- UI Lab ma wyraźny komunikat PL/EN o braku aktywnych testów oraz nieaktywny START; brak niekontrolowanego udawania przebiegu.
- Testy samej mechaniki worker/STOP/timeout korzystają z małych, sztucznych wejść w `app/src/test`, **nie są częścią Lab**. Testy uzależnione od skasowanego korpusu nie są ponawiane: porównanie presetów na nim usunięte, stary fizyczny test dwóch tur oznaczony `@Ignore`.
- Nie zmieniano silnika Gemma, profilu audio, Sejfu, Skills, Call, zgód, uprawnień ani danych właściciela.

## Następny sprint LAB-DIALOG-01

**Priorytet / TODO do realizacji:** ekran tworzenia/edycji/usuwania lokalnych testów; manualna kontynuacja rozmowy w jednej sesji (użytkownik jest rozmówcą); następnie adaptacyjne reguły rozmówcy reagujące na treść odpowiedzi modelu. Ocena: utrzymywanie tematu, poprawki, pamięć faktów, brak halucynacji/obietnic, sensowne dopytanie, naturalność, END_CALL, latencja, STOP/recovery. Wersjonowanie testów, wybór modelu/Skills/scenariusza, A/B tych samych przypadków. Bez rzeczywistych połączeń. [Roadmap](../ROADMAP.md).

**Stan QA:** wymagany konkretny `Mac Local Agent` receipt po zmianie kodu oraz osobna fizyczna kontrola S22 (tylko wolny foreground). Nie twierdzić, że nowy editor istnieje — to zapisane TODO.
