package pl.michalmatu.aicallbridge.presentation.modelvoice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelVoiceVerificationControllerTest {
    private val state = ModelVoiceVerificationController()
    private fun start(
        attempt: String = "stamp", force: Boolean = false, audio: Boolean = false,
        ready: Boolean = true, permission: () -> Boolean = { true },
        safe: () -> Boolean = { true },
    ) = state.begin(attempt, force, audio, ready, permission, safe) { it.name }

    private fun blocked(reason: ModelVoiceVerificationController.BlockedReason, permission: Boolean = false) =
        ModelVoiceVerificationController.Decision.Blocked(reason, permission)

    @Test fun startIsVisibleButDoesNotMeanSuccess() {
        assertEquals(ModelVoiceVerificationController.Decision.Start, start())
        assertTrue(state.checking)
        assertNull(state.checkResult)
        assertEquals("stamp", state.lastAttempt)
    }

    @Test fun simultaneousCheckIsIgnoredEvenOnForcedTap() {
        start()
        assertEquals(ModelVoiceVerificationController.Decision.Skip,
            start("other", true, permission = { error("must not query") }))
        assertEquals("stamp", state.lastAttempt)
    }

    @Test fun automaticRepeatSkipsButForcedRetryStartsAgain() {
        start()
        state.complete("passed")
        assertEquals(ModelVoiceVerificationController.Decision.Skip, start())
        assertEquals("passed", state.checkResult)
        assertEquals(ModelVoiceVerificationController.Decision.Start, start(force = true))
        assertNull(state.checkResult)
    }

    @Test fun missingAudioVoiceBlocksBeforePermissionAndCallSafety() {
        assertEquals(blocked(ModelVoiceVerificationController.BlockedReason.VOICES_UNAVAILABLE),
            start(force = true, audio = true, ready = false,
                permission = { error("not reached") }, safe = { error("not reached") }))
        assertFalse(state.checking)
        assertEquals("VOICES_UNAVAILABLE", state.checkResult)
    }

    @Test fun automaticBlockNeverRecordsOrRequests() {
        assertEquals(blocked(ModelVoiceVerificationController.BlockedReason.VOICES_UNAVAILABLE),
            start(audio = true, ready = false))
        assertNull(state.lastAttempt)
        assertNull(state.checkResult)
    }

    @Test fun manualPermissionDenialDisplaysReasonAndRequiresSecondTap() {
        assertEquals(blocked(ModelVoiceVerificationController.BlockedReason.PHONE_PERMISSION, true),
            start(force = true, permission = { false }, safe = { error("not reached") }))
        assertEquals("PHONE_PERMISSION", state.checkResult)
        assertFalse(state.checking)
        assertEquals(ModelVoiceVerificationController.Decision.Start, start(force = true))
    }

    @Test fun implicitPermissionDenialHasNoSideEffects() {
        assertEquals(blocked(ModelVoiceVerificationController.BlockedReason.PHONE_PERMISSION),
            start(permission = { false }))
        assertNull(state.checkResult)
        assertNull(state.lastAttempt)
    }

    @Test fun unsafeCallStateBlocksNativeProbe() {
        assertEquals(blocked(ModelVoiceVerificationController.BlockedReason.CALL_UNSAFE),
            start(force = true, safe = { false }))
        assertEquals("CALL_UNSAFE", state.checkResult)
        assertFalse(state.checking)
    }

    @Test fun interruptionLeavesReasonButDoesNotCreatePass() {
        start()
        state.pause { "interrupted" }
        assertFalse(state.checking)
        assertEquals("interrupted", state.checkResult)
        state.pause { error("not checking") }
        assertEquals("interrupted", state.checkResult)
    }

    @Test fun profileChangeForgetsAttemptAndResult() {
        start()
        state.complete("pass")
        state.reset()
        assertNull(state.lastAttempt)
        assertNull(state.checkResult)
        assertFalse(state.checking)
    }

    @Test fun permissionBlockPrecedesCallSafetyCheck() {
        assertEquals(blocked(ModelVoiceVerificationController.BlockedReason.PHONE_PERMISSION, true),
            start(force = true, permission = { false }, safe = { error("not reached") }))
    }

    @Test fun implicitBlockPreservesPriorResultAndAttempt() {
        start()
        state.complete("prior-pass")
        assertEquals(blocked(ModelVoiceVerificationController.BlockedReason.PHONE_PERMISSION),
            start("different", permission = { false }))
        assertEquals("prior-pass", state.checkResult)
        assertEquals("stamp", state.lastAttempt)
    }
}
