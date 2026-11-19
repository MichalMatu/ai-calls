package pl.michalmatu.aicallbridge.session

import org.junit.Assert.*
import org.junit.Test

class LocalAdbGsmPolicyTest {
    @Test fun onlyNumericBoundedPorts() {
        assertEquals(37111, LocalAdbGsmPolicy.port("37111"))
        for (value in listOf("", "0", "65536", "999999", "12;id", " 37111", "1.2.3", "-1"))
            assertNull(LocalAdbGsmPolicy.port(value))
    }
    @Test fun codeMustBeSixDigits() {
        assertTrue(LocalAdbGsmPolicy.pairingCode("019283"))
        for (value in listOf("12345", "1234567", "a23456", " 123456", "1234\n6"))
            assertFalse(LocalAdbGsmPolicy.pairingCode(value))
    }
    @Test fun requiresExactPhoneAndShell() {
        assertTrue(LocalAdbGsmPolicy.deviceApproved("SM-S906B", "16", "1", "2000"))
        assertFalse(LocalAdbGsmPolicy.deviceApproved("SM-G991B", "16", "1", "2000"))
        assertFalse(LocalAdbGsmPolicy.deviceApproved("SM-S906B", "16", "1", "10156"))
        assertFalse(LocalAdbGsmPolicy.deviceApproved("SM-S906B", "15", "1", "2000"))
    }
    @Test fun failClosedWhenCallUnknownOrBusy() {
        assertTrue(LocalAdbGsmPolicy.callIdle("mCallState=0", "Actual mode = MODE_NORMAL"))
        assertFalse(LocalAdbGsmPolicy.callIdle("", "Actual mode = MODE_NORMAL"))
        assertFalse(LocalAdbGsmPolicy.callIdle("mCallState=2", "Actual mode = MODE_NORMAL"))
        assertFalse(LocalAdbGsmPolicy.callIdle("mCallState=0", "Actual mode = MODE_IN_CALL"))
    }
    @Test fun bootstrapCommandIsFixedAndCannotAcceptUserInput() {
        assertTrue(LocalAdbGsmPolicy.START.contains("GsmShellBinderMediaHost"))
        assertTrue(LocalAdbGsmPolicy.START.contains("app_process"))
        assertFalse(LocalAdbGsmPolicy.START.contains("su -c"))
        assertFalse(LocalAdbGsmPolicy.START.contains("pm install"))
        assertFalse(LocalAdbGsmPolicy.START.contains("shizuku"))
        assertEquals("127.0.0.1", LocalAdbGsmPolicy.LOOPBACK)
        assertTrue(LocalAdbGsmPolicy.hostRegistered("Result: Bundle[{host_registered=true}]"))
        assertFalse(LocalAdbGsmPolicy.hostRegistered("Result: Bundle[{host_registered=false}]"))
    }
}
