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

    @Test fun overlappingFinalFragmentsDoNotRepeatSpeech() {
        val speech = SpeechRecognitionSegments()
        speech.add("proszę umówić")
        speech.add("umówić wizytę")
        speech.add("wizytę na piątek")
        assertEquals("proszę umówić wizytę na piątek", speech.resolve(""))
    }

    @Test fun repeatedStreamingFinalResultsDoNotDuplicateRecognizedWords() {
        val speech = SpeechRecognitionSegments()
        speech.add("dzień dobry")
        speech.add("dzień dobry")
        speech.add("dzień dobry chciałbym")
        speech.add("chciałbym wizytę")
        speech.add("dzień dobry")
        assertEquals("dzień dobry chciałbym wizytę", speech.resolve(""))
    }

    @Test fun correctedFinalUtteranceReplacesEarlierHypothesisWithoutDuplicatingIt() {
        val speech = SpeechRecognitionSegments()
        speech.add("Dzień dobry chciałbym wizytę")
        speech.add("na piątek")
        assertEquals("Dzień dobry, chcę wizytę na piątek.",
            speech.resolve("Dzień dobry, chcę wizytę na piątek."))
    }

    @Test fun sameGreetingWithDifferentEndingCannotDiscardCompletedAppointmentContext() {
        val speech = SpeechRecognitionSegments()
        speech.add("Dzień dobry chciałbym umówić wizytę")
        speech.add("na piątek")
        assertEquals(
            "Dzień dobry chciałbym umówić wizytę na piątek Dzień dobry, czy są wolne miejsca",
            speech.resolve("Dzień dobry, czy są wolne miejsca"),
        )
    }

    @Test fun correctedCompleteResultWithSharedBeginningAndEndingStillWins() {
        val speech = SpeechRecognitionSegments()
        speech.add("Dzień dobry chciałbym wizytę")
        speech.add("na piątek")
        assertEquals(
            "Dzień dobry, chcę wizytę na piątek.",
            speech.resolve("Dzień dobry, chcę wizytę na piątek."),
        )
    }

    @Test fun clippedFinalWithSameOpeningAndEndingPreservesConfirmedConditions() {
        val speech = SpeechRecognitionSegments()
        speech.add("Dzień dobry chciałbym umówić wizytę")
        speech.add("na piątek bez dodatkowych opłat")
        assertEquals(
            "Dzień dobry chciałbym umówić wizytę na piątek bez dodatkowych opłat",
            speech.resolve("Dzień dobry bez dodatkowych opłat"),
        )
    }

    @Test fun shortCorrectionOfOneWordStillReplacesWholeUtterance() {
        val speech = SpeechRecognitionSegments()
        speech.add("Dzień dobry chciałbym wizytę")
        speech.add("na piątek")
        assertEquals(
            "Dzień dobry wizytę na piątek",
            speech.resolve("Dzień dobry wizytę na piątek"),
        )
    }

    @Test fun differentOpeningIsAppendedAsSeparateFinalFragment() {
        val speech = SpeechRecognitionSegments()
        speech.add("Dzień dobry chciałbym wizytę")
        assertEquals("Dzień dobry chciałbym wizytę czy jest termin",
            speech.resolve("czy jest termin"))
    }

    @Test fun clearDiscardsPreviousCallSegments() {
        val speech = SpeechRecognitionSegments()
        speech.add("Wrażliwy tekst")
        speech.clear()
        assertEquals("Nowa wypowiedź", speech.resolve("Nowa wypowiedź"))
    }
}
