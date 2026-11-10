package pl.michalmatu.aicallbridge.campaign

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppointmentSimSelectionPolicyTest {
    @Test fun preservesUserSelectionAcrossRoleOrPermissionReturn() {
        assertEquals(1, AppointmentSimSelectionPolicy.index(listOf(10, 20), 20, 10))
    }

    @Test fun missingSelectedSimFailsClosedInsteadOfChoosingAnother() {
        assertNull(AppointmentSimSelectionPolicy.index(listOf(10), 20, 10))
    }

    @Test fun initialSelectionUsesSavedOrFirstAvailableSim() {
        assertEquals(0, AppointmentSimSelectionPolicy.index(listOf(10, 20), null, 10))
        assertEquals(0, AppointmentSimSelectionPolicy.index(listOf(10, 20), null, null))
        assertNull(AppointmentSimSelectionPolicy.index(emptyList(), null, 10))
    }
}
