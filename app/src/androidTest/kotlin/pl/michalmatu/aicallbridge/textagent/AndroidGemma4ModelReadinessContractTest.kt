package pl.michalmatu.aicallbridge.textagent

import android.content.Context
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Physical no-call contract for the cheap app-owned Gemma readiness boundary. */
@RunWith(AndroidJUnit4::class)
class AndroidGemma4ModelReadinessContractTest {
    @Test
    fun appOwnedGemmaModelIsReadyWithoutStartingInferenceOrMedia() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        val readiness = AndroidGemma4ModelReadiness.check(context)

        assertEquals(Gemma4ModelReadinessState.READY, readiness.state)
        assertNull(readiness.reason)
        assertTrue(readiness.file.isFile)
        assertTrue(readiness.file.canRead())
        assertEquals(Gemma4ModelCatalog.GEMMA_4_E2B_IT.expectedBytes, readiness.file.length())
        val proof = "state=${readiness.state.name} bytes=${readiness.file.length()} reason=none"
        println("GEMMA_READINESS $proof")
        Log.i("GemmaReadinessProof", proof)
    }
}
