package pl.michalmatu.aicallbridge.ui

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Bundle
import android.telecom.TelecomManager
import android.view.View
import android.widget.Button
import android.widget.ScrollView
import android.widget.TextView
import java.util.concurrent.Executors
import pl.michalmatu.aicallbridge.R
import pl.michalmatu.aicallbridge.session.S8VoipSixTurnLab

/**
 * Visible, manually triggered S8 no-Shizuku demo. App-owned synthetic RTP only:
 * no carrier, microphone, SIP credentials, Gemini, STT/TTS, GSM or external host.
 * Unlike DUMP-only probes this can be used without ADB or developer options.
 */
class S8VoipLabActivity : UiChromeActivity(AppTab.SETTINGS) {
    override fun toolbarTitle(): String = getString(R.string.s8_voip_lab_title)

    private val worker = Executors.newSingleThreadExecutor()
    private var lab: S8VoipSixTurnLab? = null
    private var runToken: Long = 0
    private lateinit var status: TextView
    private lateinit var details: TextView
    private lateinit var startButton: Button
    private lateinit var stopButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val content = UiDesign.content(this)
        content.addView(UiDesign.heading(this, getString(R.string.s8_voip_lab_title)),
            UiDesign.spaceAfter(this))
        content.addView(UiDesign.caption(this, getString(R.string.s8_voip_lab_explanation)),
            UiDesign.spaceAfter(this, UiDesign.GAP_CARD))

        val card = UiDesign.card(this).apply { tag = "s8-voip-lab-card" }
        status = UiDesign.status(this, getString(R.string.s8_voip_lab_idle)).apply {
            tag = "s8-voip-lab-status"
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        details = UiDesign.caption(this, getString(R.string.s8_voip_lab_no_calls)).apply {
            tag = "s8-voip-lab-results"
        }
        card.addView(status, UiDesign.spaceAfter(this))
        card.addView(details, UiDesign.spaceAfter(this, UiDesign.GAP_CARD))

        startButton = UiDesign.action(
            this, getString(R.string.s8_voip_lab_start), UiActionTone.PRIMARY
        ) { beginSyntheticSession() }.apply { tag = "s8-voip-lab-start" }
        stopButton = UiDesign.action(
            this, getString(R.string.s8_voip_lab_stop), UiActionTone.REVOCATION
        ) { stopSyntheticSession() }.apply {
            tag = "s8-voip-lab-stop"
            isEnabled = false
        }
        card.addView(startButton, UiDesign.spaceAfter(this))
        card.addView(stopButton)
        content.addView(card)
        setContentView(ScrollView(this).apply { addView(content) })
    }

    private fun beginSyntheticSession() {
        if (lab != null) return
        if (!isPhoneIdle()) {
            status.text = getString(R.string.s8_voip_lab_blocked)
            return
        }
        val created = S8VoipSixTurnLab()
        lab = created
        val token = ++runToken
        startButton.isEnabled = false
        stopButton.isEnabled = true
        status.text = getString(R.string.s8_voip_lab_running)
        details.text = getString(R.string.s8_voip_lab_no_calls)

        worker.execute {
            val outcome = runCatching { created.run() }
            created.close()
            runOnUiThread {
                if (isDestroyed || isFinishing || lab !== created || runToken != token) {
                    return@runOnUiThread
                }
                lab = null
                startButton.isEnabled = true
                stopButton.isEnabled = false
                status.text = outcome.fold(
                    onSuccess = {
                        getString(if (it.passed()) R.string.s8_voip_lab_pass
                                  else R.string.s8_voip_lab_stopped)
                    },
                    onFailure = { getString(R.string.s8_voip_lab_failed) },
                )
                details.text = outcome.fold(
                    onSuccess = {
                        getString(R.string.s8_voip_lab_counters,
                            it.phasesCompleted(), it.rxFrames(), it.txFrames(), it.stopped())
                    },
                    onFailure = { getString(R.string.s8_voip_lab_failed_details) },
                )
            }
        }
    }

    private fun stopSyntheticSession() {
        val active = lab ?: return
        lab = null
        active.close() // Closes app-owned UDP and coordinator; no Telecom action.
        startButton.isEnabled = true
        stopButton.isEnabled = false
        status.text = getString(R.string.s8_voip_lab_stopped)
    }

    private fun isPhoneIdle(): Boolean {
        if (checkSelfPermission(Manifest.permission.READ_PHONE_STATE) !=
            PackageManager.PERMISSION_GRANTED) return false
        return runCatching {
            val telecom = getSystemService(TelecomManager::class.java)
            val audio = getSystemService(AudioManager::class.java)
            telecom != null && !telecom.isInCall && !telecom.isInManagedCall &&
                audio != null && audio.mode != AudioManager.MODE_IN_CALL &&
                audio.mode != AudioManager.MODE_IN_COMMUNICATION
        }.getOrDefault(false)
    }

    override fun onStop() {
        // Navigating away is TAKE OVER: leave no media/background lab running.
        stopSyntheticSession()
        super.onStop()
    }

    override fun onDestroy() {
        stopSyntheticSession()
        worker.shutdownNow()
        super.onDestroy()
    }
}
