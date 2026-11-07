package pl.michalmatu.aicallbridge.conversation

import android.Manifest
import android.content.pm.PackageManager
import android.telecom.TelecomManager
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.UUID
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import pl.michalmatu.aicallbridge.R
import pl.michalmatu.aicallbridge.developerrelay.CallSessionEvidenceRecorder
import pl.michalmatu.aicallbridge.developerrelay.CallSessionPcmWav

/** Physical off-call WAV player test. Only a synthetic silent 100ms WAV is created/deleted. */
@RunWith(AndroidJUnit4::class)
class AndroidArchivedChatAudioPlaybackTest {
    private fun findButton(view: View, predicate: (String) -> Boolean): Button? {
        if (view is Button && predicate(view.text.toString())) return view
        if (view is ViewGroup) for (i in 0 until view.childCount) {
            findButton(view.getChildAt(i), predicate)?.let { return it }
        }
        return null
    }

    private fun hasStatus(view: View, predicate: (String) -> Boolean): Boolean {
        if (view is TextView && predicate(view.text.toString())) return true
        if (view is ViewGroup) for (i in 0 until view.childCount) {
            if (hasStatus(view.getChildAt(i), predicate)) return true
        }
        return false
    }

    @Test
    fun syntheticSilentRxCanPlayFromArchivedChatWithoutAccessToAnyRealCallAudio() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertTrue("phone_state_permission_required",
            context.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED)
        val telecom = context.getSystemService(TelecomManager::class.java)
        assertTrue("call_must_be_idle", telecom != null && !telecom.isInCall)

        val root = File(context.filesDir, CallSessionEvidenceRecorder.ROOT)
        val directory = File(root, "9999999999999-qa-" + UUID.randomUUID().toString())
        assertTrue("synthetic_archive_create_failed", directory.mkdirs())
        try {
            CallSessionPcmWav.write(File(directory, "turn-01-rx.wav"), ByteArray(256000))
            File(directory, "timeline.jsonl").writeText(listOf(
                """{"type":"CONVERSATION","direction":"INCOMING","profile_id":"qa-synthetic","started_at_ms":9999999999999}""",
                """{"type":"COUNTERPART_STT","turn":1,"text":"SYNTHETIC_SILENCE_TEST","elapsed_ms":20}""",
                """{"type":"RX_PCM","turn":1,"file":"turn-01-rx.wav","elapsed_ms":40}""",
                """{"type":"END","elapsed_ms":120}"""
            ).joinToString("\n") + "\n")

            ActivityScenario.launch(ConversationHistoryActivity::class.java).use { scenario ->
                var opened = false
                for (attempt in 0 until 50) {
                    scenario.onActivity { activity ->
                        val link = activity.window.decorView.findViewWithTag<View>(
                            "history-open:" + directory.name
                        )
                        if (link != null) {
                            link.performClick()
                            opened = true
                        }
                    }
                    if (opened) break
                    Thread.sleep(100)
                }
                assertTrue("synthetic_history_session_not_listed", opened)

                var clicked = false
                for (attempt in 0 until 50) {
                    scenario.onActivity { activity ->
                        val bubble = activity.window.decorView.findViewWithTag<View>(
                            "history-audio-bubble:0"
                        )
                        if (bubble != null) {
                            assertTrue("bubble_is_not_clickable", bubble.isClickable)
                            assertTrue("large_play_buttons_should_be_removed",
                                findButton(activity.window.decorView) {
                                    it.contains(activity.getString(R.string.history_play_fragment))
                                } == null)
                            bubble.performClick()
                            clicked = true
                        }
                    }
                    if (clicked) break
                    Thread.sleep(100)
                }
                assertTrue("synthetic_chat_audio_bubble_not_found", clicked)

                var succeeded = false
                for (attempt in 0 until 40) {
                    scenario.onActivity { activity ->
                        succeeded = hasStatus(activity.window.decorView) {
                            it.contains(activity.getString(R.string.history_playing)) ||
                                it.contains(activity.getString(R.string.history_playback_finished))
                        }
                    }
                    if (succeeded) break
                    Thread.sleep(100)
                }
                assertTrue("synthetic_wav_player_failed", succeeded)
                // Private UX evidence: capture the chat-bubble playback state.
                var privateDir: File? = null
                scenario.onActivity { activity ->
                    privateDir = requireNotNull(activity.getExternalFilesDir(
                        "ui-audit-20261107-sprint6"))
                    assertTrue(requireNotNull(privateDir).isDirectory ||
                        requireNotNull(privateDir).mkdirs())
                }
                val image = requireNotNull(InstrumentationRegistry.getInstrumentation()
                    .uiAutomation.takeScreenshot())
                try {
                    val target = File(requireNotNull(privateDir), "history-audio-bubble-synthetic.jpg")
                    target.outputStream().use { stream ->
                        assertTrue(image.compress(android.graphics.Bitmap.CompressFormat.JPEG, 82, stream))
                    }
                    assertTrue(target.length() > 2000)
                } finally { image.recycle() }
                scenario.onActivity { activity ->
                    val bubble = requireNotNull(activity.window.decorView.findViewWithTag<View>(
                        "history-audio-bubble:0"))
                    val hint = requireNotNull(activity.window.decorView.findViewWithTag<TextView>(
                        "history-audio-bubble:0:hint"))
                    assertTrue("bubble_did_not_switch_to_stop",
                        hint.text.contains(activity.getString(R.string.history_audio_stop_hint)))
                    bubble.performClick()
                    assertTrue("bubble_second_tap_did_not_stop", hint.text.contains(
                        activity.getString(R.string.history_audio_play_hint)))
                }
            }
            println("S22_SYNTHETIC_OFFCALL_WAV_PLAYER=PASS")
        } finally {
            assertTrue("synthetic_archive_cleanup_failed", directory.deleteRecursively())
        }
        assertFalse("synthetic_file_still_exists", directory.exists())
        println("S22_SYNTHETIC_ARCHIVE_CLEANUP=PASS")
    }
}
