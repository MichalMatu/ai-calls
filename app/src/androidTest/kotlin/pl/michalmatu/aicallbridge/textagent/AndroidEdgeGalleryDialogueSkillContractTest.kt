package pl.michalmatu.aicallbridge.textagent

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import pl.michalmatu.aicallbridge.runtime.TextLlmProvider

/**
 * Physical S22 contract for the Edge Gallery/Gemma dialogue-skill path only.
 *
 * No telephony, microphone, STT, TTS or media session is started. The test supplies synthetic text
 * directly to the phone-local backend. Reaching a parsed decision proves Edge Gallery readiness,
 * exact expected model identity and the bounded JSON skill contract in one no-call path.
 */
@RunWith(AndroidJUnit4::class)
class AndroidEdgeGalleryDialogueSkillContractTest {
    @Test
    fun gemmaProducesBoundedSkillDecisionFromSyntheticTextWithoutCallMedia() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val decision = AtomicReference<DialogueSkillDecision?>()
        val callback = AtomicReference<String>("no_callback")
        val completed = CountDownLatch(1)
        val policy = DialogueSkillPolicy(
            allowedResponses = mapOf(
                DialogueSkillId.ASK_REPEAT to "Proszę powtórzyć.",
                DialogueSkillId.ASK_CLARIFY to "Proszę doprecyzować.",
                DialogueSkillId.ACKNOWLEDGE_NEUTRAL to "Rozumiem.",
            ),
            minimumConfidence = 0.72,
        )
        val backend = LocalDialogueSkillBackendFactory.create(
            context = context,
            provider = TextLlmProvider.EDGE_GALLERY,
            policy = policy,
            observer = DialogueSkillDecisionObserver { value -> decision.set(value) },
        )

        try {
            backend.generate(
                "Dzień dobry, chciałem tylko upewnić się, czy mnie słychać.",
                object : TextCallAgentBackend.Listener {
                    override fun onComplete(text: String) {
                        callback.set("complete:${text.take(120)}")
                        completed.countDown()
                    }

                    override fun onError(reason: String) {
                        callback.set("error:${reason.take(160)}")
                        completed.countDown()
                    }
                },
            )

            assertTrue(
                "Gemma callback timeout; callback=${callback.get()}",
                completed.await(80, TimeUnit.SECONDS),
            )
            val observed = decision.get()
            assertNotNull(
                "No parsed Gemma skill decision; callback=${callback.get()}",
                observed,
            )
            checkNotNull(observed)
            assertTrue(observed.skillId in policy.allowedSkills)
            assertTrue(observed.confidence in 0.0..1.0)
        } finally {
            backend.close()
        }
    }
}
