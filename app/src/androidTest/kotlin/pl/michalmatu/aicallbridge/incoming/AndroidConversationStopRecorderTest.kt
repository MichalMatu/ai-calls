package pl.michalmatu.aicallbridge.incoming

import android.content.ContextWrapper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import pl.michalmatu.aicallbridge.conversation.rxInterruptionCode
import pl.michalmatu.aicallbridge.conversationcore.ConversationStopReason
import pl.michalmatu.aicallbridge.conversationcore.ConversationTermination
import pl.michalmatu.aicallbridge.developerrelay.CallSessionEvidenceRecorder

@RunWith(AndroidJUnit4::class)
class AndroidConversationStopRecorderTest {
    @Test fun allTypedTerminationsWriteInterruptedRxAndCompleteSafely() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(context.cacheDir, "rx-cause-test-" + UUID.randomUUID()).apply { mkdirs() }
        val isolated = object : ContextWrapper(context) {
            override fun getFilesDir(): File = root
        }
        val cases = ConversationStopReason.entries.map { ConversationTermination.Stopped(it) } +
            listOf(ConversationTermination.Completed, ConversationTermination.Failed("synthetic_failure"))
        try {
            for (termination in cases) {
                val recorder = checkNotNull(CallSessionEvidenceRecorder.create(isolated))
                val reason = termination.rxInterruptionCode()
                recorder.recordInterruptedRx(1, 250L, true, 80L, reason)
                recorder.finish(termination.code)
                val completed = File(recorder.directory, "COMPLETE")
                val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3)
                while (!completed.isFile && System.nanoTime() < deadline) Thread.sleep(10)
                assertTrue("incomplete recorder for " + reason, completed.isFile)
                val events = File(recorder.directory, "timeline.jsonl").readLines()
                    .filter { it.isNotBlank() }.map(::JSONObject)
                val interruption = events.single { it.optString("type") == "RX_INTERRUPTED" }
                assertEquals(reason, interruption.getString("reason_code"))
                val end = events.single { it.optString("type") == "END" }
                assertEquals(termination.code, end.getString("reason_code"))
            }
        } finally { root.deleteRecursively() }
    }
}
