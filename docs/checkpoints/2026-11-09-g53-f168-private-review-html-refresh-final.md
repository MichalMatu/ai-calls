# AI Calls — G5.3 F-168 private review HTML refresh — 2026-11-09

## Wynik i granice

**F-168 VERIFIED_SCOPED; G5.3 technical READY, human quality OPEN.** Repo main@63abb3f283ee3e92d8149d8f3f9829be55e0ff36. Wybrany Mac macbook-michal, instalacja cfea4cc3-b54c-4b6d-b9a6-758d12733e98. Nie wykonywano połączeń, nowych instalacji S22 ani powtórek uprzednio zaliczonych 18 tur.

## Przyczyna RED

Mac read-only task aicalls-g53-private-review-staleness-audit-mac-20261109-v1 DONE/0: w sześciu prywatnych sesjach było 6/6 review.html, ale **0/6** zawierało aktualny kontrakt SHA-256 manifestu i oświadczenie odsłuchu; lokalny indeks miał poprawnie sześć linków. F-166/F-167 naprawiły kod źródłowy, ale nie odświeżyły prywatnych wygenerowanych plików. Stare formularze nie mogły zostać przyjęte przez g53_human_review_gate.

## Naprawa i GREEN

W g53_private_audio_review.py dodano --root / render_all: atomowe odświeżanie wyłącznie nieukończonych stron review.html w prywatnych katalogach 0700, strony 0600, odmowa symlinków/nieoczekiwanych uprawnień i brak naruszenia zapisanych human-review.json. HTML pokazuje także tryb Skills. Testy obejmują istniejącą starą stronę, bezstratną regenerację, skip gotowej recenzji oraz ścieżki odmowy.

Mac source QA aicalls-g53-f168-private-review-refresh-code-mac-qa-20261109-v1 **DONE/0**: 15/15 targeted testów, pełne verify_host.sh i **386 Python PASS**, Gradle build, audio audit, anti-friction oraz Git clean.

Mac prywatny refresh aicalls-g53-f168-private-six-review-refresh-mac-20261109-v1 **DONE/0**: sześć stron przepisanych i obecnie zgodnych (6/6), indeks ma sześć linków. SHA-256 oryginalnych manifestów i WAV identyczne przed i po, żadnych transkryptów ani audio w task results. Prywatny scan: sessions=6, recorded=0, unassessed=6, invalid=0. Stan Git clean.

## Następna i jedyna brakująca bramka

**Human quality G5.3 = UNASSESSED.** Indeks na Macu: ~/Library/Application Support/ai-calls/qa/g53-private/skills-ab-index.html. Człowiek musi faktycznie odsłuchać sześć sesji, ocenić każdy z ośmiu wymiarów w kontekście tury i uzasadnić PASS/FAIL. Import tylko lokalny i SHA-bound; ostatni skan validatora dopiero po ocenach. Samo przygotowanie formularzy oraz testy syntetyczne nie dowodzą naturalności. Nie promować ALL_ACTIVE; G6 real calls NOT_RUN. Żaden dodatkowy automatyczny test Androida nie zastępuje niezależnego odsłuchu.
