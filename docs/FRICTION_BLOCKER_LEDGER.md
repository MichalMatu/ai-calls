# AI Calls — Rejestr zbędnych blokerów (weryfikować przed każdą zmianą)

Właściciel wymaga produktu o minimalnym tarciu. Agent ma zakaz samodzielnego dodawania zbędnych limitów, kliknięć, dialogów i wymagania manualnego przekazywania danych. Po zauważeniu regresji natychmiast dodaje wpis z plikiem, wpływem, poprawką i testem. `OPEN` oznacza faktyczny nierozwiązany problem, nie zgodę na jego ignorowanie.

| ID | Problem | Stan | Dalsze działanie |
| --- | --- | --- | --- |
| F-001 | Arbitralny limit pięciu połączeń (`maxAttempts`, `ATTEMPT_LIMIT_REACHED`) | REMOVED | Zmiana `87c7d903`; testy polityki autoryzacji |
| F-002 | Osobny przycisk, dialog oraz 10-minutowe uzbrojenie zapisu RX/STT/TTS/TX po już zaakceptowanej kampanii | OPEN — oceniany | Przeprojektować bez nadmiarowych kliknięć; zachować zgodność z obowiązkami dotyczącymi rozmów i prywatności. Nie usuwać ochrony przez obejście blokady narzędzia |
| F-003 | Potrzeba ręcznego eksportowania nagrań do analizy | OPEN | Zbudować wygodny przepływ przeglądu i przekazania plików bez publikowania cudzych wypowiedzi i sekretów w GitHub/task-results |
| F-004 | Niezależny od celu, sztywny limit liczby tur `MAX_TURNS=10` | OPEN — audyt | Zastąpić świadomym zakończeniem rzeczywistego zadania i diagnostyką długich sesji |
| F-005 | Limity retencji zapisów 3 sesji / 24 MB sesja | OPEN — audyt | Rozdzielić arbitralną utratę dowodów od niezbędnej kontroli miejsca; testy braku utraty danych |
| F-006 | Mylenie zakończenia rozmowy przez drugą stronę z wygaśnięciem zgody | FIXED IN CODE | `owned_call_ended`; potwierdzony REMOTE w fizycznym Telecom, potrzebna diagnostyka następnej rozmowy |

**Nigdy nie wpisuj fikcyjnego PASS.** Nie usuwaj realnych warunków: zakresu zlecenia, uprawnień platformy, możliwości STOP, prawa osób trzecich, oddzielenia własnej sesji, bezpieczeństwa danych ani zakazu obchodzenia odmów narzędzi. Rozwiązuj zbędny UX przy zachowaniu tych granic.
