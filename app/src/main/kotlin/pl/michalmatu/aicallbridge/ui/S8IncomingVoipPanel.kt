package pl.michalmatu.aicallbridge.ui

import android.content.Context
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import pl.michalmatu.aicallbridge.R
import pl.michalmatu.aicallbridge.session.S8IncomingVoipUiState

/**
 * Presentation-only incoming VoIP controls. A native verified SIP owner must
 * provide the current state and callbacks. The panel never owns credentials,
 * media, auto-answer or a simulated active call.
 */
internal class S8IncomingVoipPanel(
    private val context: Context,
    onAccept: () -> Unit,
    onReject: () -> Unit,
    onHangUp: () -> Unit,
) {
    val view: LinearLayout = UiDesign.card(context)
    private val status: TextView
    private val accept: Button
    private val reject: Button
    private val hangUp: Button

    init {
        view.addView(
            UiDesign.heading(context, context.getString(R.string.s8_sip_incoming_title)),
            UiDesign.spaceAfter(context),
        )
        status = UiDesign.status(
            context, context.getString(R.string.s8_sip_incoming_unavailable),
        ).apply { tag = "s8-sip-incoming-status" }
        view.addView(status, UiDesign.spaceAfter(context))
        accept = UiDesign.action(
            context, context.getString(R.string.s8_sip_incoming_accept),
            UiActionTone.PRIMARY, onAccept,
        ).apply { tag = "s8-sip-incoming-accept" }
        reject = UiDesign.action(
            context, context.getString(R.string.s8_sip_incoming_reject),
            UiActionTone.REVOCATION, onReject,
        ).apply { tag = "s8-sip-incoming-reject" }
        hangUp = UiDesign.action(
            context, context.getString(R.string.s8_sip_incoming_hangup),
            UiActionTone.REVOCATION, onHangUp,
        ).apply { tag = "s8-sip-incoming-hangup" }
        view.addView(accept, UiDesign.spaceAfter(context))
        view.addView(reject, UiDesign.spaceAfter(context))
        view.addView(hangUp)
        render(S8IncomingVoipUiState(
            "native_sip_unavailable", false, false, false, false,
        ))
    }

    /** Must be called on Android main thread using state from the call owner. */
    fun render(model: S8IncomingVoipUiState) {
        val message = when (model.statusCode()) {
            "waiting" -> R.string.s8_sip_incoming_waiting
            "ringing" -> R.string.s8_sip_incoming_ringing
            "negotiating" -> R.string.s8_sip_incoming_negotiating
            "connected" -> R.string.s8_sip_incoming_connected
            "security_failure" -> R.string.s8_sip_incoming_security_failure
            "ended" -> R.string.s8_sip_incoming_ended
            "failed" -> R.string.s8_sip_incoming_failed
            else -> R.string.s8_sip_incoming_unavailable
        }
        status.text = context.getString(message)
        accept.isEnabled = model.canAccept()
        reject.isEnabled = model.canReject()
        hangUp.isEnabled = model.canHangUp()
        status.contentDescription = context.getString(message)
    }
}
