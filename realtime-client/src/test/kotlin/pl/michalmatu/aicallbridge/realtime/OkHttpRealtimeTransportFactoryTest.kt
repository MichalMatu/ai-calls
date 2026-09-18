package pl.michalmatu.aicallbridge.realtime

import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test

class OkHttpRealtimeTransportFactoryTest {
    @Test
    fun createsFreshWebSocketTransportPerSessionGeneration() {
        val factory = OkHttpRealtimeTransportFactory()

        val first = factory.create()
        val second = factory.create()

        assertTrue(first is RealtimeWebSocketTransport)
        assertTrue(second is RealtimeWebSocketTransport)
        assertNotSame(first, second)
    }
}
