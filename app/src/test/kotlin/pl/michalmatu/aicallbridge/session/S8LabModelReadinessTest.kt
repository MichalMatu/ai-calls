package pl.michalmatu.aicallbridge.session

import org.junit.Assert.assertEquals
import org.junit.Test
import pl.michalmatu.aicallbridge.textagent.Gemma4ModelReadinessState

/** No model file access, hashing, downloads or native initialization. */
class S8LabModelReadinessTest {
    @Test fun neverReportsRunnableModelFromCheapFileProbe() {
        assertEquals(S8LabModelReadiness.State.MISSING,
            S8LabModelReadiness.fromFileState(Gemma4ModelReadinessState.MISSING))
        assertEquals(S8LabModelReadiness.State.INVALID,
            S8LabModelReadiness.fromFileState(Gemma4ModelReadinessState.INVALID))
        assertEquals(S8LabModelReadiness.State.FILE_PRESENT_UNVERIFIED_INFERENCE,
            S8LabModelReadiness.fromFileState(Gemma4ModelReadinessState.READY))
    }
}
