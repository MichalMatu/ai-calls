package pl.michalmatu.aicallbridge.session

import org.junit.Assert.assertEquals
import org.junit.Test

class LocalAdbGsmStatusTest {
    @Test fun missingIdentityIsNotPairedEvenIfDebuggingIsOn() {
        assertEquals(LocalAdbGsmStatus.AdbState.NOT_CONFIGURED,
            LocalAdbGsmStatus.resolve(false, 1, true))
    }

    @Test fun storedIdentityDoesNotProveActivePairing() {
        assertEquals(LocalAdbGsmStatus.AdbState.KEY_STORED_OFFLINE,
            LocalAdbGsmStatus.resolve(true, 1, false))
    }

    @Test fun wirelessDebuggingDisabledDoesNotClaimConnected() {
        assertEquals(LocalAdbGsmStatus.AdbState.DEBUGGING_DISABLED,
            LocalAdbGsmStatus.resolve(true, 0, true))
    }

    @Test fun authenticatedShellConnectionConfirmsAdb() {
        assertEquals(LocalAdbGsmStatus.AdbState.CONNECTED,
            LocalAdbGsmStatus.resolve(true, 1, true))
    }

    @Test fun unknownWirelessSettingFailsClosedWithoutAuthentication() {
        assertEquals(LocalAdbGsmStatus.AdbState.KEY_STORED_OFFLINE,
            LocalAdbGsmStatus.resolve(true, -1, false))
    }
}
