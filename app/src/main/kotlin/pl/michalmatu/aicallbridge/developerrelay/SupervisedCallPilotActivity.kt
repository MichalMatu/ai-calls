package pl.michalmatu.aicallbridge.developerrelay

import pl.michalmatu.aicallbridge.conversation.ConversationPreparation

import pl.michalmatu.aicallbridge.conversation.SupervisedConversationScenario

import pl.michalmatu.aicallbridge.conversation.AndroidConversationRuntime

import pl.michalmatu.aicallbridge.audioengines.AudioEngineProfile
import pl.michalmatu.aicallbridge.audioengines.ConversationRunConfiguration

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
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
    private var session: AndroidConversationRuntime.SessionControl? = null
    private val routeHandler = Handler(Looper.getMainLooper())
    private var pendingRouteProbe: Runnable? = null

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
                    "3. Dotknij START: STT → Gemma/skrypt → TTS → Shizuku.\n" +
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
            when (preflight()) {
                null -> status.text =
                    "Rozmowa wykryta. Dotknij «Uruchom AI w trwającej rozmowie»."
                SupervisedCallPilotFailure.AUDIO_ROUTE_NOT_READY -> status.text =
                    "Rozmowa wykryta, lecz tor audio nie jest gotowy. " +
                        "Dotknij START po przywróceniu toru audio."
                else -> status.text = "Wrócono z dialera. Jeśli rozmowa trwa, " +
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

    private fun preflight(): SupervisedCallPilotFailure? =
        SupervisedCallReadinessDiagnostics.capture(this).failure

    private fun cancelRouteProbe() {
        pendingRouteProbe?.let(routeHandler::removeCallbacks)
        pendingRouteProbe = null
    }

    /** Retry an Android routing transition only; never override the frozen media gate. */
    private fun awaitAudioRoute() {
        if (pendingRouteProbe != null) return
        status.text = "Rozmowa wykryta. Sprawdzam tor audio Androida (do 3 s)…"
        val start = SystemClock.elapsedRealtime()
        val probe = object : Runnable {
            override fun run() {
                if (isFinishing || isDestroyed) {
                    pendingRouteProbe = null
                    return
                }
                val snapshot = SupervisedCallReadinessDiagnostics.capture(this@SupervisedCallPilotActivity)
                val failure = snapshot.failure
                if (failure == null) {
                    pendingRouteProbe = null
                    SupervisedCallReadinessDiagnostics.write(this@SupervisedCallPilotActivity, snapshot)
                    confirmAttach() // Reuse the original START tap after the OS route stabilizes.
                } else if (SupervisedCallPilotPolicy.shouldAwaitAudioRoute(
                        failure, SystemClock.elapsedRealtime() - start)) {
                    routeHandler.postDelayed(this, 300L)
                } else {
                    pendingRouteProbe = null
                    val saved = SupervisedCallReadinessDiagnostics.write(
                        this@SupervisedCallPilotActivity, snapshot)
                    if (failure == SupervisedCallPilotFailure.AUDIO_ROUTE_NOT_READY) {
                        status.text = "Połączenie wykryte, lecz tryb audio to ${snapshot.audioModeLabel}, " +
                            "nie MODE_IN_CALL. Bezpiecznie nie uruchomiono AI. " +
                            if (saved) "Zapisano diagnostykę dla Local Agenta."
                            else "Nie udało się zapisać diagnostyki."
                    } else {
                        confirmAttach() // Displays the current permission/call error without a dial.
                    }
                }
            }
        }
        pendingRouteProbe = probe
        routeHandler.postDelayed(probe, 250L)
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
                awaitAudioRoute()
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
        // The user's START tap is the approval for this effect-free pilot.
        startPilot(gemmaMode.isChecked)
    }

    private fun startPilot(approvedGemmaMode: Boolean) {
        cancelRouteProbe()
        if (approvedGemmaMode &&
            AndroidGemma4ModelReadiness.check(this).state != Gemma4ModelReadinessState.READY
        ) {
            status.text = "Model Gemma 4 nie jest już gotowy. Nie uruchomiono audio."
            return
        }
        if (preflight() != null || !allowed.compareAndSet(false, true)) {
            status.text = "Warunki rozmowy zmieniły się. Test nie został uruchomiony."
            return
        }
        startButton.isEnabled = false
        stopButton.isEnabled = true
        status.text = "Nasłuch STT → ${if (approvedGemmaMode) "Gemma 4" else "skrypt"} → TTS. " +
            "AI odpowiada na usłyszaną wypowiedź. STOP wyłącza tylko AI."
        val sessionId = "pilot-" + UUID.randomUUID().toString()
        try {
            session = AndroidConversationRuntime.run(
                context = this,
                sessionId = sessionId,
                runConfiguration = ConversationRunConfiguration.ScriptAssisted(AudioEngineProfile.STABLE, ConversationPreparation.supervised()),
                maxTurns = 3,
                scenario = SupervisedConversationScenario(this, approvedGemmaMode),
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
        cancelRouteProbe()
        // A foreground UI is our operator supervision; no unattended audio on Home/dialer.
        if (allowed.get()) stopAudio()
        super.onStop()
    }

    override fun onDestroy() {
        cancelRouteProbe()
        if (allowed.get() || session != null) stopAudio()
        super.onDestroy()
    }

    companion object {
        private const val REQUEST_PERMISSIONS = 7003
    }
}
