package pl.michalmatu.aicallbridge.incoming

import org.junit.Assert.*
import org.junit.Test

class PhoneAccountLabelMaskTest {
    @Test fun retainsOperatorAndShortSimIdentifier() {
        assertEquals("Orange SIM 1", maskPhoneAccountLabel("Orange SIM 1"))
    }

    @Test fun hidesAllButLastFourPhoneDigits() {
        val label = maskPhoneAccountLabel("Orange SIM 1: +48 123 456 789")
        assertTrue(label.startsWith("Orange SIM 1: "))
        assertTrue(label.endsWith("6789"))
        assertFalse(label.contains("123"))
        assertFalse(label.contains("456"))
        assertEquals(4, label.substringAfter(':').count { it.isDigit() })
    }

    @Test fun leavesLastFourAccountDigitsForRecognition() {
        val label = maskPhoneAccountLabel("SIM 2 (123456789012345)")
        assertTrue(label.contains("2345"))
        assertFalse(label.contains("1234567"))
        assertEquals(4, label.count { it.isDigit() } - 1) // SIM index is not a phone number.
    }
}
