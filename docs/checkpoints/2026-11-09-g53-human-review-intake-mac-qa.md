# AI Calls — G5.3 prywatne recenzje, Mac QA final — 2026-11-09

## Status
F-166 / F-167 VERIFIED_SCOPED. main@c30742be04b50010ca31d5ca9c3c8cce31452de0. G5.3 HUMAN_REVIEW_OPEN / UNASSESSED. G6 NOT_RUN.

## Zmiany
Renderer zapisuje w prywatnym eksporcie human-review.json hash SHA-256 manifestu, oświadczenie o odsłuchu i komplet ośmiu PASS/FAIL z turą i uzasadnieniem. Nowy lokalny moduł g53_human_review_gate waliduje dokładny korpus i osiem niepowtarzalnych ocen, po czym prywatnie zapisuje dowód atomowo, bez nadpisania (0700 katalog, 0600 plik); zdalnie publikuje tylko metryki. Poprawiono instrukcję CLI na python3 -m scripts.aicall_tools.diagnostics.g53_human_review_gate. Nie jest to automatyczna ocena semantyki.

## Receipts
Mac task aicalls-g53-human-review-final-gate-mac-20261109-v2 DONE/0, wszystkie 5 komend exit 0: pin main c30742b, 11/11 targeted unit, pełny verify_host.sh 382 Python PASS i host build, audio audit, anti-friction PASS, odczyt lokalnego korpusu zanonimizowany do liczników, Git clean. Prywatny skan: 6 sessions, 0 recorded reviews, 6 unassessed, 0 invalid.

Historyczne aicalls-g53-human-review-readonly-preflight-mac-20261109-v1 oraz aicalls-g53-human-review-binding-mac-qa-20261109-v1 FAILED na przestarzałych pinach HEAD; drugi wykonał fast-forward, ale zakończył na asercji SHA. Finalny task sprawdził aktualny kod bez powtarzania S22.

## Dalej
Na Macu otworzyć prywatny indeks ~/Library/Application Support/ai-calls/qa/g53-private/skills-ab-index.html, odsłuchać sześć sesji, zapisać komplet ośmiu ocen do właściwych sesji, zaimportować przez lokalny validator. Sam skan bez ręcznej recenzji NIE zamyka G5.3. Bez realnych połączeń, zmian produkcyjnego trybu Skills i bez publikowania audio/transkryptów.
