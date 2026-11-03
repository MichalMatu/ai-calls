# AI Calls — prywatny zapis dwustronnej rozmowy (2026-11-03)

## Cel
Świadomie uzbrojony zapis **jednej** kampanii: rozpoznany tekst konsultanta (STT), zatwierdzony tekst wypowiadany przez agenta, odebrany audio RX i rzeczywiście wysłany audio TX, wraz z osią czasu i identyfikacją tury.

## Włączanie i wyłączanie
- Domyślnie WYŁĄCZONE. W panelu Campaign tools przycisk `Zapisz następną rozmowę...` wyświetla ostrzeżenie o danych osobowych i potrzebie poinformowania drugiej strony, jeśli jest to wymagane. Wymagane osobne świadome zatwierdzenie.
- Uzbrojenie ważne 10 minut i **zużywane przy jednej kwalifikującej się sesji audio**. Można je anulować. Nie jest równoznaczne ze zgodą na zmianę CLIR ani nie rozszerza zakresu numeru/SIM/zadania.
- `Odsłuchaj ostatnią odpowiedź AI` działa tylko poza rozmową, odtwarza ostatni TX WAV lokalnie; nie wstrzykuje dźwięku do trwającej rozmowy i nie tworzy sprzężenia.

## Przechowywanie
`/data/user/0/pl.michalmatu.aicallbridge/files/call-session-evidence/<session>/` (wewnętrzne, prywatne pliki aplikacji):
- `timeline.jsonl` — osobne zdarzenia `COUNTERPART_STT`, `AGENT_APPROVED_TEXT`, `RX_PCM`, `TX_PCM`, `START` i `END`, `elapsed_ms`, `turn`;
- `turn-NN-rx.wav` — mono PCM16LE 16 kHz z okien przechwytywania STT; nie jest to ciągły zapis ciszy pomiędzy turami;
- `turn-NN-tx.wav` — mono PCM16LE 16 kHz zawierający wyłącznie bajty przesłane do telefonicznego uplinku, nie sam plan TTS;
- `COMPLETE` — zakończenie zapisu, zanim wolno eksportować całą sesję.

Kopia audio ma ograniczenie 2 MB na turę i 24 MB na sesję, pliki sygnalizują ucięcie `truncated`. Utrzymujemy maksymalnie trzy ostatnie sesje. Wymazanie danych aplikacji usuwa cały zapis. Taki zapis nie zastępuje testu fizycznego odsłuchu po drugiej stronie: potwierdza wysyłkę do uplinku, nie odbiór przez operatora.

## Eksport do odsłuchu i analizy
Na Macu uruchom `scripts/aicall_tools/diagnostics/export_private_call_evidence.sh`. Używa `adb exec-out run-as` i zapisuje lokalnie w `~/Documents/AI-Calls/private-exports/` prywatny plik `.tar.gz`. Nie zapisuje rozmowy w Git, nie wysyła do żadnej chmury ani nie drukuje wypowiedzi w logach. Właściciel może rozpakować archiwum i wgrać wybrane WAV/transkrypt do czatu po ocenie prywatności.

Przez ADB można też diagnostycznie odczytać `timeline.jsonl` z uprawnieniami aplikacji, ale **nie wolno** kopiować treści rozmowy do task-results repozytorium Local Agent ani do konsoli GitHub. W zwykłych task-results dozwolone są liczniki, sumy kontrolne, rozmiary i kody błędów.

## Poprawka semantyki zakończenia
Poprzednie `campaign_authority_revoked_or_expired` mylnie opisywało naturalne lub zdalne zakończenie, bo `liveAuthorization` obejmowało również `isExactCallActive()`. Wykorzystuj osobną obserwację `callStillActive` i zapisz `owned_call_ended` po rzeczywistym zakończeniu, nadal natychmiast odcinając TX. Prawdziwe wygaśnięcie/odwołanie zgody nadal kończy sesję.

## Zabezpieczenia
Wspólny moduł Samsung RX/TX nie jest modyfikowany. Brak domyślnego nagrywania, automatycznego uploadu, niewidocznego podsłuchu innej rozmowy, obejścia roli dialera i ukrytego włączania audio. Zgoda na zapis jest niezależna od autoryzacji działania AI. Przed udostępnieniem plików zachowaj zgodność z prawem i prywatnością rozmówców.
