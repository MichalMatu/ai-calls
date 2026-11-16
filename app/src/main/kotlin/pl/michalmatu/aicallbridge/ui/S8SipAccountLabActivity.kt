package pl.michalmatu.aicallbridge.ui

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Bundle
import android.telecom.TelecomManager
import android.text.InputType
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ScrollView
import android.widget.TextView
import java.io.IOException
import java.util.Arrays
import java.util.concurrent.Executors
import pl.michalmatu.aicallbridge.R
import pl.michalmatu.aicallbridge.session.S8SipsOptionsProtocol
import pl.michalmatu.aicallbridge.session.S8SipsRegisterDraft
import pl.michalmatu.aicallbridge.session.S8SipsTlsRegisterClient

/**
 * Manual, non-exported SIPS registrar lab.
 *
 * All account data remains in-memory, never logged or persisted. Password is
 * taken as mutable char[] and erased from the input immediately. The user
 * must tap REGISTER for every network attempt. Back/STOP closes the TCP socket
 * immediately. No auto-login, redirects, INVITE or audio transfer.
 */
class S8SipAccountLabActivity : UiChromeActivity(AppTab.SETTINGS) {
    override fun toolbarTitle(): String = getString(R.string.s8_sip_lab_title)

    private val worker = Executors.newSingleThreadExecutor()
    private var attempt: S8SipsTlsRegisterClient.Attempt? = null
    private var runToken = 0L
    private lateinit var registrar: EditText
    private lateinit var contact: EditText
    private lateinit var user: EditText
    private lateinit var password: EditText
    private lateinit var status: TextView
    private lateinit var register: Button
    private lateinit var stop: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val body = UiDesign.content(this)
        body.addView(UiDesign.heading(this, getString(R.string.s8_sip_lab_title)),
            UiDesign.spaceAfter(this))
        body.addView(UiDesign.caption(this, getString(R.string.s8_sip_lab_explanation)),
            UiDesign.spaceAfter(this))

        val card = UiDesign.card(this)
        registrar = field(R.string.s8_sip_lab_registrar, false)
        contact = field(R.string.s8_sip_lab_contact, false)
        user = field(R.string.s8_sip_lab_user, false)
        password = field(R.string.s8_sip_lab_password, true)
        card.addView(registrar, UiDesign.spaceAfter(this))
        card.addView(contact, UiDesign.spaceAfter(this))
        card.addView(user, UiDesign.spaceAfter(this))
        card.addView(password, UiDesign.spaceAfter(this))
        status = UiDesign.status(this, getString(R.string.s8_sip_lab_idle)).apply {
            tag = "s8-sip-registration-status"
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        card.addView(status, UiDesign.spaceAfter(this))
        register = UiDesign.action(
            this, getString(R.string.s8_sip_lab_register), UiActionTone.PRIMARY
        ) { startRegisterOnce() }.apply { tag = "s8-sip-register-once" }
        stop = UiDesign.action(
            this, getString(R.string.s8_sip_lab_stop), UiActionTone.REVOCATION
        ) { stopRegistration() }.apply {
            tag = "s8-sip-register-stop"
            isEnabled = false
        }
        card.addView(register, UiDesign.spaceAfter(this))
        card.addView(stop)
        body.addView(card)
        setContentView(ScrollView(this).apply { addView(body) })
    }

    private fun field(label: Int, secret: Boolean): EditText =
        EditText(this).apply {
            hint = getString(label)
            contentDescription = getString(label)
            setSingleLine(true)
            isSaveEnabled = false
            importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO
            inputType = InputType.TYPE_CLASS_TEXT or (if (secret) {
                InputType.TYPE_TEXT_VARIATION_PASSWORD
            } else {
                InputType.TYPE_TEXT_VARIATION_URI
            })
            if (secret) tag = "s8-sip-secret-input"
        }

    private fun startRegisterOnce() {
        if (attempt != null) return
        if (checkSelfPermission(Manifest.permission.READ_PHONE_STATE) !=
            PackageManager.PERMISSION_GRANTED) {
            status.text = getString(R.string.s8_sip_lab_permission)
            requestPermissions(arrayOf(Manifest.permission.READ_PHONE_STATE), 8109)
            return
        }
        if (!isPhoneIdle()) {
            status.text = getString(R.string.s8_sip_lab_blocked)
            return
        }
        val secret = CharArray(password.text.length) { password.text[it] }
        password.text.clear() // Immediately remove the credential from the UI.
        val config = try {
            val sips = S8SipsOptionsProtocol.parseEndpoint(registrar.text.toString().trim())
            val contactEndpoint = S8SipsOptionsProtocol.parseEndpoint(contact.text.toString().trim())
            S8SipsRegisterDraft.Config(sips, user.text.toString().trim(), contactEndpoint, 300)
        } catch (_: RuntimeException) {
            Arrays.fill(secret, Char.MIN_VALUE)
            status.text = getString(R.string.s8_sip_lab_invalid)
            return
        }
        if (secret.isEmpty()) {
            Arrays.fill(secret, Char.MIN_VALUE)
            status.text = getString(R.string.s8_sip_lab_invalid)
            return
        }
        val once = S8SipsTlsRegisterClient.Attempt()
        attempt = once
        val token = ++runToken
        register.isEnabled = false
        stop.isEnabled = true
        status.text = getString(R.string.s8_sip_lab_running)

        worker.execute {
            val result = try {
                Result.success(once.run(config, secret, 4500, true).acceptedExpiresSeconds())
            } catch (_: IllegalArgumentException) {
                Result.invalid()
            } catch (_: SecurityException) {
                Result.invalid()
            } catch (_: IOException) {
                Result.failure()
            } catch (_: RuntimeException) {
                Result.failure()
            } finally {
                Arrays.fill(secret, Char.MIN_VALUE)
                once.close()
            }
            runOnUiThread {
                if (isFinishing || isDestroyed || attempt !== once || runToken != token) {
                    return@runOnUiThread
                }
                attempt = null
                register.isEnabled = true
                stop.isEnabled = false
                status.text = when {
                    result.acceptedSeconds != null ->
                        getString(R.string.s8_sip_lab_success, result.acceptedSeconds)
                    result.invalid -> getString(R.string.s8_sip_lab_invalid)
                    else -> getString(R.string.s8_sip_lab_failed)
                }
            }
        }
    }

    private data class Result(val acceptedSeconds: Int?, val invalid: Boolean) {
        companion object {
            fun success(seconds: Int) = Result(seconds, false)
            fun invalid() = Result(null, true)
            fun failure() = Result(null, false)
        }
    }

    private fun stopRegistration() {
        val current = attempt ?: return
        attempt = null
        ++runToken
        current.close()
        register.isEnabled = true
        stop.isEnabled = false
        status.text = getString(R.string.s8_sip_lab_stopped)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 8109 && !isFinishing && !isDestroyed) {
            // Do not send any SIP data immediately after granting permission.
            // An additional explicit REGISTER tap is always required.
            status.text = getString(
                if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED)
                    R.string.s8_sip_lab_idle else R.string.s8_sip_lab_blocked
            )
        }
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
        stopRegistration()
        super.onStop()
    }

    override fun onDestroy() {
        stopRegistration()
        worker.shutdownNow()
        super.onDestroy()
    }
}
