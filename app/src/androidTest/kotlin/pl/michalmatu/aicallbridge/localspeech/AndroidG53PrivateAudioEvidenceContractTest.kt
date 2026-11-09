package pl.michalmatu.aicallbridge.localspeech

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import android.content.Context
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import pl.michalmatu.aicallbridge.conversation.SkillTurnRoutingDecision
import pl.michalmatu.aicallbridge.conversation.SkillLoadingMode

/** Writer contract only: no model inference, microphone, Telecom, or personal data. */
@RunWith(AndroidJUnit4::class)
class AndroidG53PrivateAudioEvidenceContractTest {
    @Test fun privateWavAndManifestContainOnlyExplicitOptInSyntheticTurn() {
        val context: Context = ApplicationProvider.getApplicationContext()
        // Reuse the ordinary per-turn Skill router decision contract through a real decision.
        val snapshot = pl.michalmatu.aicallbridge.conversation.ConversationSkillLibrary
            .defaults().snapshot("natural")
        val router = pl.michalmatu.aicallbridge.conversation.ConversationSkillTurnRouter(
            snapshot,
            pl.michalmatu.aicallbridge.conversation.SkillLoadingMode.CONTEXTUAL,
            pl.michalmatu.aicallbridge.conversation.ConversationDirection.INCOMING,
            "Przyjmij krótką wiadomość", "",
        )
        val decision: SkillTurnRoutingDecision = router.observeUserTurn("Przyjmij wiadomość")
        val wavPcm = ByteArray(6400) { index -> (index % 13).toByte() }
        val saved = G53PrivateAudioEvidence.save(context, "GEMMA_4_E2B",
            "ANDROID_STT", "ANDROID_TTS_PL",
            listOf(G53CapturedAudioTurn("Fikcyjny głos", "Fikcyjny głos",
                "Potwierdzenie testowe", wavPcm, wavPcm, 100, 250, 320)),
            listOf(decision))
        try {
            assertTrue(saved.canonicalPath.startsWith(
                File(context.noBackupFilesDir, "g53-synthetic-audio").canonicalPath +
                    File.separator))
            assertEquals(3, saved.listFiles().orEmpty().size)
            val json = JSONObject(File(saved, "manifest.json").readText())
            assertEquals("g53-synthetic-audio-review-v1", json.getString("schema"))
            assertTrue(json.getBoolean("no_telecom"))
            assertEquals("CONTEXTUAL", json.getString("skills_loading_mode"))
            assertTrue(json.getJSONArray("turns").getJSONObject(0).getBoolean("routing_observed"))
            assertEquals(1, json.getJSONArray("turns").length())
            val first = json.getJSONArray("turns").getJSONObject(0)
            assertEquals("Fikcyjny głos", first.getString("stt_final"))
            for (filename in listOf("turn-01-rx.wav", "turn-01-tx.wav")) {
                val bytes = File(saved, filename).readBytes()
                assertEquals(44 + wavPcm.size, bytes.size)
                assertEquals("RIFF", String(bytes, 0, 4, Charsets.US_ASCII))
                assertEquals("WAVE", String(bytes, 8, 4, Charsets.US_ASCII))
                assertEquals(16000, ByteBuffer.wrap(bytes, 24, 4)
                    .order(ByteOrder.LITTLE_ENDIAN).int)
                assertTrue(bytes.copyOfRange(44, bytes.size).contentEquals(wavPcm))
            }
        } finally {
            saved.listFiles().orEmpty().forEach { it.delete() }
            saved.delete()
        }
    }
    @Test fun allActivePrivateWriterDoesNotInventContextualRouting() {
        val context: Context = ApplicationProvider.getApplicationContext()
        val pcm = ByteArray(6400) { i -> (i % 17).toByte() }
        val saved = G53PrivateAudioEvidence.save(
            context, "GEMMA_4_E2B", "ANDROID_STT", "ANDROID_TTS_PL",
            listOf(G53CapturedAudioTurn("Tylko fikcyjny test", "Tylko fikcyjny test",
                "Odpowiedź syntetyczna", pcm, pcm, 300, 400, 600)),
            emptyList(), SkillLoadingMode.ALL_ACTIVE,
        )
        try {
            val manifest = JSONObject(File(saved, "manifest.json").readText())
            assertEquals("ALL_ACTIVE", manifest.getString("skills_loading_mode"))
            val first = manifest.getJSONArray("turns").getJSONObject(0)
            assertFalse(first.getBoolean("routing_observed"))
            assertEquals(0, first.getJSONArray("activated_skill_ids").length())
            assertEquals(0, first.getJSONArray("deactivated_skill_ids").length())
        } finally {
            saved.listFiles().orEmpty().forEach { it.delete() }
            saved.delete()
        }
    }

}
