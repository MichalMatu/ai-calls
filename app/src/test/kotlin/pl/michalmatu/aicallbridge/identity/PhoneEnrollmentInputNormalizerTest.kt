package pl.michalmatu.aicallbridge.identity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PhoneEnrollmentInputNormalizerTest {
    @Test
    fun `normalizes supported phone formatting to digits`() {
        assertEquals(
            "48123456789",
            PhoneEnrollmentInputNormalizer.normalize("+48 (123) 456-789"),
        )
    }

    @Test
    fun `accepts exact digit length boundaries`() {
        assertEquals("1234567", PhoneEnrollmentInputNormalizer.normalize("1234567"))
        assertEquals(
            "123456789012345",
            PhoneEnrollmentInputNormalizer.normalize("123456789012345"),
        )
    }

    @Test
    fun `rejects unsupported characters and invalid lengths`() {
        assertNull(PhoneEnrollmentInputNormalizer.normalize("123456"))
        assertNull(PhoneEnrollmentInputNormalizer.normalize("1234567890123456"))
        assertNull(PhoneEnrollmentInputNormalizer.normalize("123ABC4567"))
        assertNull(PhoneEnrollmentInputNormalizer.normalize(""))
    }
}
