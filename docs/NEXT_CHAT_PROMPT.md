# AI Calls — NOWY CZAT: domknij G3, nie uruchamiaj ponownie zakończonych etapów

Wznów projekt \`MichalMatu/ai-calls\` **autonomicznie** w trybie GitHub Connector + wskazany Mac Local Agent + ChatGPT Sandbox (bez Codex, Work, GitHub Actions również odczytu statusu). Repo \`main\`, bez nowego brancha/force-push, commity \`[skip ci]\`. **Nie używaj starego SHA jako HEAD**: pobierz aktualny HEAD i sprawdź czystość checkoutu przed zmianami.

**Obowiązkowe dokumenty:** \`AGENTS.md\`, \`README.md\`, \`docs/AI_CALLS_MASTER_EXECUTION_PLAN.md\`, \`docs/HANDOFF_NEXT_CHAT.md\`, \`docs/checkpoints/2026-11-09-g3-e2b-qwen35-pending-host-receipt-handoff.md\`, \`docs/ROADMAP.md\`, \`docs/ARCHITECTURE.md\`, \`BLOCKERS.md\`, \`docs/AUTONOMOUS_OPERATION_MODE.md\`, \`docs/SECURITY_PRIVACY.md\`. Dla Mac Host Ops również \`MichalMatu/local-agent\`: \`AGENTS.md\`, \`docs/HOST_CONTROL_PLANE.md\`, \`docs/CURRENT_HANDOFF.md\`.

## Fakty zamknięte
- G0 F-158 **24/24 native E2B/GPU tur PASS** na S22; semantic review szeroki nadal OPEN.
- G1 \`Qwen3.5-2B_int8.litertlm\` pinned \`f24013ba6844bd39be2d1f68e8d2421a77eed1f2\`, SHA-256 \`c2438cb9c050220e667366b4beeca4daac56e693b226420674f53fc40f007c49\`, 2 119 843 840 B, źródłowa licencja Apache-2.0; Mac source + S22 probe DONE.
- G2 **Mac pełna bramka PASS, rzeczywisty S22 Qwen3.5 CPU 3/3 tury PASS**, import modelu zweryfikowany, E2B domyślny i Call nietknięty. Cold 23 983 ms, warm 3 537 ms, fresh 15 758 ms, PSS 2 897 MiB po teście; tylko techniczny zakres.
- G3 kod macierzy 96 tur \`bf31c48\` Mac compile/full QA PASS, prywatny journal w \`2966967\` czeka na nowy build/host task. **Nie ma jeszcze wyniku 96 tur ani decyzji modelowej**.

## Jedyna pierwsza akcja
Odczytaj dokładnie \`MichalMatu/local-agent:agent-control\` → \`.agent/hosts/macbook-michal/supervisor/cfea4cc3-b54c-4b6d-b9a6-758d12733e98/task-results/aicalls-g3-e2b-qwen35-96turns-s22-20261109-v1.json\`, a potem (tylko jeśli potrzebne) task i **świeże** logi control-plane. Task opublikowano 2026-11-09 08:58:23 UTC, target \`macbook-michal/cfea4cc3-b54c-4b6d-b9a6-758d12733e98\`, S22 \`SM-S906B\`, serial \`RFCT70L7E8J\`. Ostatnio result **404**, stary status hosta \`idle\` pochodził z 08:23:50 UTC, a dostarczony log sięgał 09:27:24 UTC i nie zawierał task ID G3. Log miał \`ExecutionLeaseBusy\`, timeouty Git sync i busy runner; **brak dowodu przyczyny konkretnego tasku**.

Jeśli wynik jest DONE — odczytaj exit codes, 48 recordów/2 summary, 96 tur, CPU/GPU, cold/warm median/p95, JSON/tools, niezależność sesji, RAM, Call IDLE, prywatny raport 48 par; **nie publikuj dialogów**. Jeśli FAILED — rozdziel, czy zawiódł build, instrumentacja, czy sam eksport. Jeżeli test skończył się, ale eksport zawiódł, odzyskaj artefakt bez powtarzania wszystkich 96 tur. Jeśli wynik nadal 404 — diagnozuj świeże Mac logi/claims i kontrolny checkout; nie kasuj lease, nie restartuj na ślepo i **nie uruchamiaj drugiego G3**, dopóki nie udowodnisz braku bieżącego procesu.

Następnie oceniaj prywatną semantykę 8 scenariuszy (nie utożsamiaj trafienia słowa/JSON z naturalnością), rozdziel rzeczywiste CPU Qwen i GPU Gemma, odnotuj wynik \`KEEP_E2B\` / \`PROMOTE_QWEN\` / \`INSUFFICIENT_EVIDENCE\` w checkpointcie. **Dopiero po G3** przejdź do G4 per-turn Agent Skills, potem G5 pełne STT/LLM/TTS, G6 rzeczywiste Call w dokładnym grancie, G7 UX.

Pracuj samodzielnie bez żądania „kontynuuj”; po każdej zamkniętej bramce jeden zweryfikowany checkpoint i synchronizacja głównego planu/handoff/roadmap. Zachowaj własność Call, STOP, prywatność, Samsung RX/TX freeze i zero telefonów bez dokładnego aplikacyjnego upoważnienia.
