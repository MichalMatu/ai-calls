package pl.michalmatu.aicallbridge.campaign

import android.os.Handler
import android.os.Looper
import android.telecom.Call
import pl.michalmatu.aicallbridge.conversationcore.OutgoingIvrDigitPort
import pl.michalmatu.aicallbridge.conversationcore.OutgoingIvrDigitAttempt
import pl.michalmatu.aicallbridge.conversationcore.OutgoingIvrDtmfRequest
import pl.michalmatu.aicallbridge.conversationcore.OutgoingIvrDtmfToneDelivery
import pl.michalmatu.aicallbridge.conversationcore.OutgoingIvrToneHardware
import pl.michalmatu.aicallbridge.conversationcore.OutgoingIvrToneStopScheduler

/**
 * Exact app-owned outgoing Call only; never searches device-wide foreground calls.
 * Telecom requires playDtmfTone/stopDtmfTone on the same Call, sequentially.
 * This port is not a grant: the owner must supply a current scoped authorization.
 */
internal class AndroidTelecomDtmfDigitPort(
    exactCall: () -> Call?,
    active: () -> Boolean,
    authorized: (OutgoingIvrDtmfRequest) -> Boolean,
) : OutgoingIvrDigitPort, AutoCloseable {
    private val handler = Handler(Looper.getMainLooper())
    private var toneCall: Call? = null
    private val delivery = OutgoingIvrDtmfToneDelivery(
        active = active,
        authorized = authorized,
        hardware = object : OutgoingIvrToneHardware {
            override fun play(digit: Char) {
                val call = exactCall() ?: error("owned_call_missing")
                check(call.state == Call.STATE_ACTIVE) { "owned_call_not_active" }
                toneCall = call
                call.playDtmfTone(digit)
            }

            override fun stop() {
                val call = toneCall ?: return
                // Keep exact Call on stop failure; close() can retry safely.
                call.stopDtmfTone()
                toneCall = null
            }
        },
        scheduler = OutgoingIvrToneStopScheduler { delayMs, stop ->
            handler.postDelayed(Runnable { stop() }, delayMs)
        },
    )

    override fun dispatch(request: OutgoingIvrDtmfRequest): Boolean =
        dispatchAttempt(request) == OutgoingIvrDigitAttempt.STARTED

    override fun dispatchAttempt(request: OutgoingIvrDtmfRequest): OutgoingIvrDigitAttempt =
        if (Looper.myLooper() == Looper.getMainLooper()) delivery.dispatchAttempt(request)
        else OutgoingIvrDigitAttempt.NOT_STARTED // No Telecom call on wrong thread.

    override fun close() {
        if (Looper.myLooper() == Looper.getMainLooper()) delivery.close()
        else handler.post { delivery.close() }
    }
}
