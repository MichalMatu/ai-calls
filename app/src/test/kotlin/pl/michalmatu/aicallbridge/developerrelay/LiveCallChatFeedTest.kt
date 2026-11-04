package pl.michalmatu.aicallbridge.developerrelay

import org.junit.Assert.*
import org.junit.Test

class LiveCallChatFeedTest {
    @Test fun messagesAndTransmissionStatus() {
        val id = LiveCallChatFeed.begin()
        LiveCallChatFeed.heard(id, 1, "Tekst rozpoznany")
        LiveCallChatFeed.replied(id, 1, "Tekst odpowiedzi")
        assertEquals(2, LiveCallChatFeed.snapshot().lines.size)
        assertNull(LiveCallChatFeed.snapshot().lines[1].txStatus)
        LiveCallChatFeed.tx(id, 1, true)
        assertEquals("TX: wysłano lokalnie", LiveCallChatFeed.snapshot().lines[1].txStatus)
        LiveCallChatFeed.finish(id, "owned_call_ended")
        assertFalse(LiveCallChatFeed.snapshot().active)
    }

    @Test fun staleUpdatesDoNotEnterNextSession() {
        val first = LiveCallChatFeed.begin()
        LiveCallChatFeed.finish(first, "operator_stopped")
        val second = LiveCallChatFeed.begin()
        LiveCallChatFeed.heard(first, 1, "Stara sesja")
        LiveCallChatFeed.heard(second, 1, "Nowa sesja")
        assertEquals(1, LiveCallChatFeed.snapshot().lines.size)
        assertEquals("Nowa sesja", LiveCallChatFeed.snapshot().lines[0].text)
        LiveCallChatFeed.finish(second, null)
    }

    @Test fun clearIsOnlyAViewOperation() {
        val id = LiveCallChatFeed.begin()
        LiveCallChatFeed.heard(id, 1, "Przykład")
        LiveCallChatFeed.clearVisibleHistory()
        assertTrue(LiveCallChatFeed.snapshot().active)
        assertTrue(LiveCallChatFeed.snapshot().lines.isEmpty())
        LiveCallChatFeed.finish(id, null)
    }
}
