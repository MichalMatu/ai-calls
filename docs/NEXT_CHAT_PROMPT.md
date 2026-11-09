# AI Calls — NOWY CZAT: zamknij G3 po rzeczywistym 96-turn S22 PASS

Wznów `MichalMatu/ai-calls`, tryb **GitHub Connector + Mac Local Agent + ChatGPT Sandbox**, bez Codex, ChatGPT Work ani GitHub Actions (również ich statusu). Pracuj autonomicznie, `main` i `[skip ci]`, bez branch/force-push. **Pierwszy krok:** świeży `main` HEAD; nie zakładaj SHA z checkpointu. Sprawdź czysty checkout Mac oraz wymagane `AGENTS.md`, `README.md`, `docs/AI_CALLS_MASTER_EXECUTION_PLAN.md`, `docs/HANDOFF_NEXT_CHAT.md`, `docs/checkpoints/2026-11-09-g3-e2b-qwen35-pending-host-receipt-handoff.md`, `docs/ROADMAP.md`, `docs/ARCHITECTURE.md`, `BLOCKERS.md`, `docs/SECURITY_PRIVACY.md`, `docs/AUTONOMOUS_OPERATION_MODE.md`. Dla Host Ops przeczytaj `MichalMatu/local-agent` `AGENTS.md`, `docs/HOST_CONTROL_PLANE.md`, `docs/CURRENT_HANDOFF.md`.

## Zamknięte dowody, których nie powtarzać
- G0 F-158 24/24 natywne E2B/Skills S22 PASS; G1 model pin Qwen3.5 i dane sprzętowe PASS.
- G2 Qwen3.5 2B na CPU S22 3/3 tur PASS, import przez aplikację zweryfikowany SHA, niezależna druga sesja, Mac pełna bramka PASS.
- G3 `aicalls-g3-e2b-qwen35-96turns-s22-20261109-v1` **96/96 syntetycznych tur PASS technicznie**, 48 par, 2 summary, 48 prywatnych par JSONL wyeksportowanych na Mac 0600, Call IDLE bez wipe ani rzeczywistych połączeń. E2B GPU 24/24 native/JSON/tools, hints 15/15, cold/warm mediana 24,20/4,73 s; Qwen3.5 CPU 24/24, hints 12/15, 49,19/7,34 s. Dwa różne akceleratory: nie twierdzić, że to bezpośredni benchmark GPU vs GPU.

**Dlaczego formalny result G3 ma `failed`:** etapy Git pin, kompilacja i instrument S22 były `exit=0`, ale `git status` jako **komenda 4 nie ruszyło** (`task_budget_exhausted`, `exit=125`; required 6460 s, remaining 5460,812 s). To nie jest błąd inferencji. Test trwał 08:58:29–09:34:08 UTC. Starszy log do 09:27 UTC nie mógł wykazać końca testu i jego brak nazwy G3 nie świadczy o zatorze.

## Jedyne bieżące zadanie
**Bez powtarzania 96 tur**, wykonaj na wskazanym Mac `macbook-michal` krótki read-only postflight ze świeżą kontrolą claims/leases, z właściwymi krótkimi timeoutami: `git status --porcelain`, zgodność `main` z `origin/main` po bezpiecznym fetch, liczba i niezmienność prywatnych 48 rekordów `~/Library/Application Support/ai-calls/qa/g3-private/e2b-qwen35-ab-*.jsonl` (bez wypisywania ich treści). Policz per-model/per-scenario JSON/tools/content hints, wyjaśnij 3 niezaliczone hinty i niepewność obecnego agregatora `model:unknown`, który źle parsuje prefiks logcat. Zachowaj odpowiedzi wyłącznie lokalnie; do GitHub tylko liczby.

Po analizie wykonaj rzeczywisty **semantic review** wszystkich 8 scenariuszy: pamięć, sprostowania, niejasność, prywatność, naturalność, narzędzia i zakończenie. Samo JSON PASS nie jest oceną języka. Wybierz i udokumentuj `KEEP_E2B`, `PROMOTE_QWEN` lub `INSUFFICIENT_EVIDENCE`, z uwzględnieniem cold/warm, CPU/GPU, RAM i bezpieczeństwa. **E2B default nie zmieniaj do decyzji.**

Potem dopiero G4 (Skills reagujące per-turn), G5 dialog tekstowo-głosowy, G6 INCOMING/OUTGOING wyłącznie przy rzeczywistym grancie, G7 UX. Zamknij checkpoint + roadmap + handoff w repo. Nie używaj prawdziwych numerów/transkryptów w zadaniach, nie testuj Call bez konkretnej autoryzacji, respektuj STOP/własność Call i freeze Samsung RX/TX.
