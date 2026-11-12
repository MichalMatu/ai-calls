package pl.michalmatu.aicallbridge.localspeech

import org.junit.Assert.assertEquals
import org.junit.Test

class SpeechRecognitionSegmentsTest {
    @Test fun finalOnlyKeepsOriginalTranscript() {
        assertEquals("Dzień dobry", SpeechRecognitionSegments().resolve("  Dzień dobry  "))
    }

    @Test fun segmentedSessionRetainsAllFragmentsWhenFinalResultIsBlank() {
        val speech = SpeechRecognitionSegments()
        speech.add("Dzień dobry")
        speech.add("chciałbym umówić wizytę")
        assertEquals("Dzień dobry chciałbym umówić wizytę", speech.resolve(""))
    }

    @Test fun finalFragmentDoesNotDiscardEarlierAppointmentContext() {
        val speech = SpeechRecognitionSegments()
        speech.add("Dzień dobry")
        speech.add("chciałbym umówić wizytę")
        assertEquals("Dzień dobry chciałbym umówić wizytę na piątek",
            speech.resolve("wizytę na piątek"))
    }

    @Test fun completeCorrectedFinalResultWinsInsteadOfDuplicatingIt() {
        val speech = SpeechRecognitionSegments()
        speech.add("dzień dobry")
        speech.add("chciałbym termin")
        assertEquals("Dzień dobry, chciałbym termin w piątek.",
            speech.resolve("Dzień dobry, chciałbym termin w piątek."))
    }

    @Test fun cumulativeAndDuplicateSegmentCallbacksDoNotRepeatWords() {
        val speech = SpeechRecognitionSegments()
        speech.add("dzień dobry")
        speech.add("dzień dobry chciałbym wizytę")
        speech.add("dzień dobry chciałbym wizytę")
        assertEquals("dzień dobry chciałbym wizytę", speech.resolve("chciałbym wizytę"))
    }

    @Test fun clearDiscardsPreviousCallSegments() {
        val speech = SpeechRecognitionSegments()
        speech.add("Wrażliwy tekst")
        speech.clear()
        assertEquals("Nowa wypowiedź", speech.resolve("Nowa wypowiedź"))
    }
}
