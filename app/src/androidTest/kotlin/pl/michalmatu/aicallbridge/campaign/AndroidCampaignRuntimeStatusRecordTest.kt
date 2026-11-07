package pl.michalmatu.aicallbridge.campaign

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Isolated preference name: never alters actual campaign logs or phone state. */
@RunWith(AndroidJUnit4::class)
class AndroidCampaignRuntimeStatusRecordTest {
    @Test fun timestampAndMessageAreAtomicAndLegacyFormatReads() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val prefName = "status_history_test_" + UUID.randomUUID().toString()
        val pref = ctx.getSharedPreferences(prefName, android.content.Context.MODE_PRIVATE)
        try {
            val store = CampaignRuntimeStatusStore(ctx, prefName)
            assertNull(store.loadRecord())
            store.save("synthetic_request", 123456789L)
            assertEquals("synthetic_request", store.load())
            assertEquals(123456789L, store.loadRecord()?.recordedAtEpochMs)
            store.save("synthetic_finished", 123456790L)
            assertEquals("synthetic_finished", store.loadRecord()?.message)
            assertEquals(123456790L, store.loadRecord()?.recordedAtEpochMs)
            // Existing installations have text but no timestamp. Do not invent one.
            pref.edit().remove("recorded_at_epoch_ms").commit()
            assertNull(store.loadRecord()?.recordedAtEpochMs)
            assertEquals("synthetic_finished", store.loadRecord()?.message)
            assertThrows(IllegalArgumentException::class.java) {
                store.save("invalid_timestamp", 0L)
            }
            assertEquals("synthetic_finished", store.load())
        } finally {
            pref.edit().clear().commit()
        }
    }
}
