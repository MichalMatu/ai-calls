# Bezpieczeństwo i autoryzacja — kontrakty nienaruszalne

## Kto podejmuje decyzje
- Właściciel wydaje **konkretny** task/grant ze wskazaniem celu, zakresu, konta/SIM, dozwolonych danych i skutków. Jeden trwały scope pozwala na prawidłowe retry/IVR bez dodatkowego sztucznego dialogu; rozszerzenie zakresu wymaga nowej rzeczywistej autoryzacji.
- `CampaignAuthorizationGrant`/store egzekwują zgodność zadania i cofnięcie; `CallCommitmentGate` zużywa prawo jednorazowo dla konkretnego skutku. `IdentityVault` i `FactDisclosurePolicy` filtrują pole/destynację przed ujawnieniem. Model, prompt, Skill, transkrypt i dokumentacja **nie są** grantem.
- Nie oznaczaj rezerwacji/CLIR jako wykonanych na podstawie deklaracji AI lub samego wysłania TX. Oddziel requested/validated/dispatched/confirmed od niezależnego wyniku z systemu zewnętrznego.

## Telefonia i STOP
- **Tylko exact app-owned Android Call** może zostać odebrany, rozłączony lub użyty do TX przez AI. Brak globalnego `endCall`, KEYCODE_ENDCALL ani ogólnych `ACTION_CALL` bez związania z tym samym grantem i SIM.
- STOP/TAKE OVER unieważnia generację STT/inferencji/TTS i przyszłe audio, potem kończy zasoby; spóźnione callbacki nie wznawiają starej odpowiedzi. Człowiek przejmuje aktywne połączenie bez rozłączenia, AI wraca wyłącznie po świadomym resume tej samej sesji.
- Prawdziwe dial/IVR/instalacja/przejęcie tylko przy odpowiednim dokładnym upoważnieniu, rozpoznanym urządzeniu, uprawnieniach Androida i stanie IDLE. Jeżeli odczyt stanu telefonu niepewny, fizycznej operacji **nie wykonuj**.

## Prywatność
- Zapis audio jest opcjonalny, zależny od rzeczywistej zgody/ustawień i przepisów. Tekst zatwierdzonych wypowiedzi i STT jest automatyczną, prywatną historią autoryzowanej sesji, niezależną od zgody na WAV; należy stosować właściwe wymogi dotyczące informowania rozmówców i ochrony danych. Prywatne WAV/transkrypcje pozostają w katalogu app; eksport właściciela przez kontrolowany kanał USB i bez nadpisywania. Nie umieszczaj PII, numerów, sekretów, nagrań i prywatnych transkryptów w GitHub, logcat, taskach Local Agenta ani telemetrycznych wynikach.
- Nie twórz globalnego promptu zawierającego dane IdentityVault; tylko późne, typowane, dozwolone odczyty. Odsłuch prywatnego archiwum tylko poza połączeniem, po bezpiecznym rozwiązaniu ścieżki i z Android `READ_PHONE_STATE`.
- Nie używaj `FLAG_SECURE`/`windowSecure` do blokowania własnych screenshotów użytkownika; nie dodawaj automatycznego przesyłania screenshotów.

## Frozen S22 transport
Zmiana `privileged-helper`/Shizuku, Samsung `VOICE_DOWNLINK` i `CALL_ASSISTANT` TX, PCM pipes, shared RX/TX generation, watchdog, endpoint-loss i selective cleanup wymaga dowodu na **fizycznym** S22. Baseline 2D jest fizycznie sprawdzony (commit `59b0505537a53306acdab6a2a66ca6eed2b3f1c0`). Nie przebudowuj go przy porządkowaniu dokumentacji.

## Anti-friction nie usuwa zabezpieczeń
Nie dodawaj fikcyjnych limitów połączeń/tur i redundantnych promptów; zachowaj autoryzację, ochronę przed dublem, RAM/storage/protocol bounds, realne timeouty, uprawnienia, prywatność i zgodność prawną. Test regresji: `scripts/aicall_tools/diagnostics/audit_product_friction.py`.
