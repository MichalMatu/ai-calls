package pl.michalmatu.aicallbridge.developerrelay

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Bundle
import android.telecom.TelecomManager
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import pl.michalmatu.aicallbridge.AndroidLiveCallReadiness
import pl.michalmatu.aicallbridge.textagent.AndroidGemma4ModelReadiness
import pl.michalmatu.aicallbridge.textagent.Gemma4ModelReadinessState

/**
 * Minimum operator-supervised call product. NEVER dials, changes the default
 * dialer, changes an account, discloses identity, or disconnects a cellular call.
 * It only attaches the frozen Shizuku RX/TX path to a call the operator already
 * started and executes up to three reviewed, effect-free dialogue turns.
 */
class SupervisedCallPilotActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var startButton: Button
    private lateinit var stopButton: Button
    private lateinit var gemmaMode: CheckBox
    private lateinit var modelStatus: TextView
    private var returnedFromDialer = false
    private val allowed = AtomicBoolean(false)
    private var session: ChatRelayLiveCallProbe.SessionControl? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        status = TextView(this).apply {
            textSize = 16f
            setTextIsSelectable(true)
            text = "Gotowy. Rozmowę rozpoczynasz i kończysz samodzielnie."
        }
        startButton = Button(this).apply {
            text = "Uruchom AI w trwającej rozmowie"
            setOnClickListener { confirmAttach() }
        }
        stopButton = Button(this).apply {
            text = "STOP AI — wróć do rozmowy ręcznej"
            isEnabled = false
            setOnClickListener { stopAudio() }
        }

        gemmaMode = CheckBox(this).apply {
            text = "Rozmowa z Gemmą 4 (lokalnie)"
            // The audio script works without the 2.59 GB model; prefer it until ready.
            isChecked = AndroidGemma4ModelReadiness.check(this@SupervisedCallPilotActivity).state ==
                Gemma4ModelReadinessState.READY
        }
        modelStatus = TextView(this).apply { textSize = 14f }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
            addView(TextView(this@SupervisedCallPilotActivity).apply {
                text = "Pierwsze połączenie — nadzorowane"
                textSize = 22f
            })
            addView(TextView(this@SupervisedCallPilotActivity).apply {
                text = "1. W zwykłym dialerze zadzwoń samodzielnie na uzgodniony numer.\n" +
                    "2. Podczas rozmowy wróć tutaj przez ostatnie aplikacje.\n" +
                    "3. Potwierdź włączenie AI: STT → Gemma/skrypt → TTS → Shizuku.\n" +
                    "4. STOP wyłącza wyłącznie AI, nie rozłącza telefonu.\n\n" +
                    "Bez danych osobowych, CLIR i zmian usług."
            })
            addView(Button(this@SupervisedCallPilotActivity).apply {
                text = "Otwórz zwykły dialer (bez wybierania numeru)"
                setOnClickListener {
                    returnedFromDialer = true
                    runCatching { startActivity(Intent(Intent.ACTION_DIAL)) }
                        .onFailure {
                            returnedFromDialer = false
                            status.text = "Nie można otworzyć dialera."
                        }
                }
            })
            addView(gemmaMode)
            addView(modelStatus)
            addView(Button(this@SupervisedCallPilotActivity).apply {
                text = "Pobierz lub importuj Gemmę na ekranie głównym"
                setOnClickListener { finish() }
            })
            addView(startButton)
            addView(stopButton)
            addView(status)
        }
        setContentView(ScrollView(this).apply { addView(content) })
    }

    override fun onResume() {
        super.onResume()
        if (!::modelStatus.isInitialized) return
        refreshModelStatus()
        if (returnedFromDialer && !allowed.get()) {
            returnedFromDialer = false
            if (preflight() == null) {
                status.text = "Rozmowa wykryta. Potwierdź, aby uruchomić AI."
                confirmAttach()
            } else {
                status.text = "Wrócono z dialera. Jeśli rozmowa trwa, " +
                    "dotknij «Uruchom AI w trwającej rozmowie»."
            }
        }
    }

    private fun refreshModelStatus() {
        val readiness = AndroidGemma4ModelReadiness.check(this)
        modelStatus.text = when (readiness.state) {
            Gemma4ModelReadinessState.READY -> "Gemma 4: gotowa do lokalnego dialogu."
            Gemma4ModelReadinessState.MISSING ->
                "Gemma 4: niepobrana (2,59 GB). Pobierz ją na ekranie głównym albo odznacz tryb Gemma."
            Gemma4ModelReadinessState.INVALID ->
                "Gemma 4: nieprawidłowa (${readiness.reason}). Pobierz lub zaimportuj ponownie."
        }
    }

    private fun preflight(): SupervisedCallPilotFailure? {
        val mic = checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        val phone = checkSelfPermission(Manifest.permission.READ_PHONE_STATE) ==
            PackageManager.PERMISSION_GRANTED
        val ready = runCatching { AndroidLiveCallReadiness.evaluate(this).ready }.getOrDefault(false)
        val telecom = getSystemService(TelecomManager::class.java)
        val active = if (phone) {
            runCatching { telecom?.isInCall == true }.getOrDefault(false)
        } else false
        val inCallAudio = getSystemService(AudioManager::class.java)?.mode ==
            AudioManager.MODE_IN_CALL
        return SupervisedCallPilotPolicy.check(mic, phone, ready, active, inCallAudio)
    }

    private fun confirmAttach() {
        if (allowed.get()) {
            status.text = "Sesja AI już działa."
            return
        }
        when (preflight()) {
            SupervisedCallPilotFailure.PERMISSION_REQUIRED -> {
                status.text = "Potrzebne uprawnienia mikrofonu i stanu telefonu."
                requestPermissions(
                    arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.READ_PHONE_STATE),
                    REQUEST_PERMISSIONS,
                )
                return
            }
            SupervisedCallPilotFailure.SHIZUKU_NOT_READY -> {
                status.text = "Shizuku niedostępne. Uruchom Shizuku i sprawdź uprawnienia AI Calls."
                return
            }
            SupervisedCallPilotFailure.NO_ACTIVE_CELLULAR_CALL -> {
                status.text = "Najpierw rozpocznij rozmowę w zwykłym dialerze."
                return
            }
            SupervisedCallPilotFailure.AUDIO_ROUTE_NOT_READY -> {
                status.text = "Tor rozmowy nie jest aktywny. Spróbuj podczas trwającego połączenia."
                return
            }
            null -> Unit
        }
        if (gemmaMode.isChecked &&
            AndroidGemma4ModelReadiness.check(this).state != Gemma4ModelReadinessState.READY
        ) {
            AlertDialog.Builder(this)
                .setTitle("Brakuje modelu Gemma 4")
                .setMessage("Aby rozmawiać z modelem, pobierz 2,59 GB na ekranie głównym. " +
                    "Możesz też przetestować samo audio w trybie skryptowym.")
                .setPositiveButton("Pobierz Gemmę") { _, _ -> finish() }
                .setNeutralButton("Użyj skryptu") { _, _ ->
                    gemmaMode.isChecked = false
                    confirmAttach()
                }
                .setNegativeButton("Anuluj", null)
                .show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Uruchomić AI w rozmowie?")
            .setMessage(
                "Potwierdź, że to Twoja świadomie rozpoczęta rozmowa testowa. " +
                    "AI wyśle maksymalnie trzy krótkie odpowiedzi " +
                    "w trybie ${if (gemmaMode.isChecked) "Gemma 4" else "skryptowym"}. " +
                    "Nie wybierze numeru, nie zmieni usług i nie rozłączy połączenia. " +
                    "STOP natychmiast wyłącza audio AI.",
            )
            .setPositiveButton("Uruchom test") { _, _ -> startPilot() }
            .setNegativeButton("Anuluj", null)
            .show()
    }

    private fun startPilot() {
        if (preflight() != null || !allowed.compareAndSet(false, true)) {
            status.text = "Warunki rozmowy zmieniły się. Test nie został uruchomiony."
            return
        }
        startButton.isEnabled = false
        stopButton.isEnabled = true
        status.text = "Nasłuch STT → ${if (gemmaMode.isChecked) "Gemma 4" else "skrypt"} → TTS. " +
            "AI odpowiada na usłyszaną wypowiedź. STOP wyłącza tylko AI."
        val sessionId = "pilot-" + UUID.randomUUID().toString()
        try {
            session = ChatRelayLiveCallProbe.run(
                context = this,
                sessionId = sessionId,
                maxTurns = 3,
                phoneDisclosureAuthorized = false,
                supervisorRelayEnabled = false,
                readOnlySupervised = true,
                supervisedGemmaEnabled = gemmaMode.isChecked,
                liveAuthorization = {
                    allowed.get() && preflight() == null
                },
            ) { report ->
                allowed.set(false)
                session = null
                startButton.isEnabled = true
                stopButton.isEnabled = false
                val success = report.lineSequence().any {
                    it == "chat_relay_live_call_success=true"
                }
                val reason = report.lineSequence()
                    .firstOrNull { it.startsWith("failure_reason=") }
                    ?.substringAfter('=')
                status.text = if (success) {
                    "Test audio zakończony. Połączenie pozostaje pod Twoją kontrolą."
                } else {
                    "AI zatrzymane: ${reason ?: "sprawdź raport"}. " +
                        "Połączenie możesz kontynuować ręcznie."
                }
            }
        } catch (error: Throwable) {
            allowed.set(false)
            session = null
            startButton.isEnabled = true
            stopButton.isEnabled = false
            status.text = "Nie udało się uruchomić audio AI: ${error.javaClass.simpleName}"
        }
    }

    private fun stopAudio() {
        allowed.set(false)
        session?.stopAudio()
        stopButton.isEnabled = false
        status.text = "Zatrzymuję audio AI. Połączenie telefoniczne pozostaje aktywne."
    }

    override fun onStop() {
        // A foreground UI is our operator supervision; no unattended audio on Home/dialer.
        if (allowed.get()) stopAudio()
        super.onStop()
    }

    override fun onDestroy() {
        if (allowed.get() || session != null) stopAudio()
        super.onDestroy()
    }

    companion object {
        private const val REQUEST_PERMISSIONS = 7003
    }
}
