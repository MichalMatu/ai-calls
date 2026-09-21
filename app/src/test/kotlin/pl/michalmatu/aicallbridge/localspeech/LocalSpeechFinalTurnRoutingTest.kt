package pl.michalmatu.aicallbridge.localspeech

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import pl.michalmatu.aicallbridge.textagent.TextCallFinalTurnRoute

class LocalSpeechFinalTurnRoutingTest {
    @Test
    fun `missing selector preserves ordinary generate route`() {
        val route = selectLocalSpeechFinalTurnRoute("zwykły finalny tekst", null)

        assertSame(TextCallFinalTurnRoute.Generate, route)
    }

    @Test
    fun `optional selector receives exact final transcript and chooses neutral route`() {
        var seenTranscript: String? = null
        val selector = LocalSpeechFinalTurnRouteSelector { transcript ->
            seenTranscript = transcript
            TextCallFinalTurnRoute.Candidate("dokładny kandydat")
        }

        val route = selectLocalSpeechFinalTurnRoute("dokładny finalny tekst", selector)

        assertEquals("dokładny finalny tekst", seenTranscript)
        assertEquals(TextCallFinalTurnRoute.Candidate("dokładny kandydat"), route)
    }
}
