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
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import pl.michalmatu.aicallbridge.AndroidLiveCallReadiness

/**
 * Minimum operator-supervised call product. NEVER dials, changes the default
 * dialer, changes an account, discloses identity, or disconnects a cellular call.
 * It only attaches the frozen Shizuku RX/TX path to a call the operator already
 * started and executes up to three local, reviewed audio-script turns.
 */
class SupervisedCallPilotActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var startButton: Button
    private lateinit var stopButton: Button
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

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
            addView(TextView(this@SupervisedCallPilotActivity).apply {
                text = "Pierwsze połączenie — nadzorowane"
                textSize = 22f
            })
            addView(TextView(this@SupervisedCallPilotActivity).apply {
                text = "1. Otwórz dialer i samodzielnie połącz się z uzgodnionym numerem.\n" +
                    "2. Wróć do AI Calls i uruchom test.\n" +
                    "3. AI odpowie na maksymalnie trzy proste wypowiedzi przez Shizuku.\n" +
                    "4. STOP wyłącza WYŁĄCZNIE audio AI. Połączenie nadal trwa.\n\n" +
                    "To test dźwięku, bez Gemmy, danych osobowych, CLIR i zmian usług."
            })
            addView(Button(this@SupervisedCallPilotActivity).apply {
                text = "Otwórz zwykły dialer (bez wybierania numeru)"
                setOnClickListener {
                    runCatching { startActivity(Intent(Intent.ACTION_DIAL)) }
                        .onFailure { status.text = "Nie można otworzyć dialera." }
                }
            })
            addView(startButton)
            addView(stopButton)
            addView(status)
        }
        setContentView(ScrollView(this).apply { addView(content) })
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
        AlertDialog.Builder(this)
            .setTitle("Rozpocząć test audio?")
            .setMessage(
                "Potwierdź, że to Twoja świadomie rozpoczęta rozmowa testowa. " +
                    "AI wyśle maksymalnie trzy krótkie, neutralne odpowiedzi. " +
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
        status.text = "Test AI uruchomiony. Mów do rozmówcy. STOP wyłącza tylko AI."
        val sessionId = "pilot-" + UUID.randomUUID().toString()
        try {
            session = ChatRelayLiveCallProbe.run(
                context = this,
                sessionId = sessionId,
                maxTurns = 3,
                phoneDisclosureAuthorized = false,
                supervisorRelayEnabled = false,
                readOnlySupervised = true,
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
