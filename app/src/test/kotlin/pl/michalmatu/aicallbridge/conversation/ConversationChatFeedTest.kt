package pl.michalmatu.aicallbridge.conversation


import org.junit.Assert.*
import org.junit.Test

class ConversationChatFeedTest {
    @Test fun messagesAndTransmissionStatus() {
        val id = ConversationChatFeed.begin()
        ConversationChatFeed.heard(id, 1, "Tekst rozpoznany")
        ConversationChatFeed.replied(id, 1, "Tekst odpowiedzi")
        assertEquals(2, ConversationChatFeed.snapshot().lines.size)
        assertNull(ConversationChatFeed.snapshot().lines[1].txStatus)
        ConversationChatFeed.tx(id, 1, true)
        assertEquals("TX: wysłano lokalnie", ConversationChatFeed.snapshot().lines[1].txStatus)
        ConversationChatFeed.finish(id, "owned_call_ended")
        assertFalse(ConversationChatFeed.snapshot().active)
    }

    @Test fun staleUpdatesDoNotEnterNextSession() {
        val first = ConversationChatFeed.begin()
        ConversationChatFeed.finish(first, "operator_stopped")
        val second = ConversationChatFeed.begin()
        ConversationChatFeed.heard(first, 1, "Stara sesja")
        ConversationChatFeed.heard(second, 1, "Nowa sesja")
        assertEquals(1, ConversationChatFeed.snapshot().lines.size)
        assertEquals("Nowa sesja", ConversationChatFeed.snapshot().lines[0].text)
        ConversationChatFeed.finish(second, null)
    }

    @Test fun clearIsOnlyAViewOperation() {
        val id = ConversationChatFeed.begin()
        ConversationChatFeed.heard(id, 1, "Przykład")
        ConversationChatFeed.clearVisibleHistory()
        assertTrue(ConversationChatFeed.snapshot().active)
        assertTrue(ConversationChatFeed.snapshot().lines.isEmpty())
        ConversationChatFeed.finish(id, null)
    }
    @Test fun openingGreetingIsVisibleWithTxBeforeFirstCounterpartTurn() {
        val token=ConversationChatFeed.begin()
        ConversationChatFeed.replied(token,0,"Dzień dobry, tu wirtualna asystentka.",ConversationChatFeed.ResponseKind.SCRIPT_ASSISTED)
        ConversationChatFeed.tx(token,0,true)
        ConversationChatFeed.heard(token,1,"Chcę zostawić wiadomość.")
        val lines=ConversationChatFeed.snapshot().lines
        assertEquals(2,lines.size)
        assertEquals(0,lines.first().turn)
        assertEquals(ConversationChatFeed.ResponseKind.SCRIPT_ASSISTED,lines.first().responseKind)
        assertEquals("TX: wysłano lokalnie",lines.first().txStatus)
        ConversationChatFeed.finish(token,null)
    }

}
