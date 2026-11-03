# ⛔ BLOCKERS — AI Calls: ZERO NOWYCH SZTUCZNYCH OGRANICZEŃ

**To jest pierwszy plik do przeczytania przed zmianą kodu i pierwszy plik do aktualizacji po wykryciu blokera.** Normatywny zakaz i procedura: [AGENTS.md](AGENTS.md), *pierwsze linie*. Historia i szczegóły: [docs/FRICTION_BLOCKER_LEDGER.md](docs/FRICTION_BLOCKER_LEDGER.md).

**Zasada:** żadnych niezamówionych limitów wywołań lub tur, dodatkowych zgód na identyczne zadanie, ręcznych potwierdzeń po zatwierdzeniu zakresu, arbitralnych godzin/whitelist, obowiązku ręcznego kopiowania danych albo cichego ucinania dowodów. Usuwaj bramkę lub napraw brakującą implementację — nie zastępuj jej kolejną. Gdy agent popełni taki błąd, **natychmiast** wpisuje go tu i do szczegółowego rejestru; po naprawie pokazuje commit i test. `OPEN` nie znika bez naprawy.

## AKTYWNE — priorytet do usunięcia

| ID | Co blokuje i dlaczego | Lokalizacja | Co należy zrobić | Stan |
| --- | --- | --- | --- | --- |
| F-002 | Osobne kliknięcie, drugi dialog i 10-minutowy „arm” dla diagnostyki każdej następnej rozmowy. Rozbija ciągły przepływ i zmusza użytkownika do ręcznej interwencji | `CampaignToolActivity.kt`, `CallSessionEvidenceConsent`, `AppOwnedClirCampaignExecutor.kt` | Jeden zgodny z prawem przepływ ustawienia kampanii obejmujący diagnostykę; nie obchodź odmowy narzędzia ani praw rozmówców | **OPEN** |
| F-003 / F-010 | Manualny terminal/ADB eksport nagrań i ręczna orkiestracja kolejnych kroków, mimo że Local Agent i aplikacja mają wykonać techniczną pracę | `export_private_call_evidence.sh`, UI i silnik kampanii | Bezpieczny automatyczny przepływ lokalnych dowodów do analizy, bez wycieku danych w GitHub; wznowienia autoryzowanego zadania | **OPEN** |
| F-007 | Zakończenie po trzech cichych próbach nasłuchu może zamknąć realną rozmowę, choć konsultant/IVR tylko każe czekać | `ChatRelayLiveCallProbe.MAX_NO_SPEECH_RETRIES` | Oczekiwanie zależne od stanu połączenia, nie stałego licznika; STOP zawsze dostępny | **OPEN** |
| F-009 | Przy długiej rozmowie raport techniczny traci zdarzenia po 1024 wpisach | `CallDiagnosticEvidence.plusAssign` | Strumieniowy lub trwały zapis zdarzeń bez cichego pomijania | **OPEN** |

## POPRAWIONE W KODZIE — wymagają testów/weryfikacji

| ID | Zbędna bariera | Poprawka | Stan |
| --- | --- | --- | --- |
| F-004 | Twardy koniec kampanii po 10 turach (`MAX_TURNS=10`) | Kampania działa do własnego końca/rozłączenia/STOP; tylko wyraźnie ograniczony pilot testowy zachowuje liczbę tur | **FIXED_IN_SOURCE, QA OPEN** |
| F-005 | Ucinanie WAV po 2 MB/turę i 24 MB/sesję, kasowanie po 3 sesjach oraz limit 2000 znaków transkryptu | Brak arbitralnych progów; zarządzanie miejscem wyłącznie przy faktycznej presji pojemności Androida | **FIXED_IN_SOURCE, QA OPEN** |
| F-008 | Podgląd transkryptu ograniczony do 100 wpisów i 24 000 znaków | Pełny transkrypt w UI; do sprawdzenia na długim zapisie | **FIXED_IN_SOURCE, QA OPEN** |

## ZAMKNIĘTE

| ID | Bloker | Dowód |
| --- | --- | --- |
| F-001 | Licznik maksymalnie pięciu połączeń | `87c7d903` — kod i test polityki |
| F-006 | Błędne `campaign_authority_revoked_or_expired` po zdalnym zakończeniu | Oddzielny kod `owned_call_ended`; realny S22/Telecom wskazał `REMOTE` |

## SZABLON — obowiązkowo dla każdego nowego blokera

```text
ID: F-011 (kolejny wolny)
Status: OPEN / FIXED_IN_SOURCE / VERIFIED
Kiedy i kto dodał:
Ścieżka + symbol + warunek blokujący:
Co użytkownik lub AI przez to traci:
Czy to zbędne tarcie (A), brak implementacji (B), realne prawo/stan (C), czy platforma (D):
Działanie usuwające przyczynę, nie objaw:
Test regresji i dokładny wynik:
Commit źródłowy i wdrożony SHA:
```

**Nie usuwaj realnych wymagań zgody na czynność, praw rozmówców, uprawnień Androida, kontroli własnego połączenia, STOP ani zabezpieczenia danych.** Są to obowiązki inżynierskie. Nie używaj hasła „zero blokad” do obejścia odmowy narzędzia. Każde odstępstwo z konkretną przyczyną zapisz jawnie, nie dokładając nowego kliknięcia bez decyzji właściciela.
