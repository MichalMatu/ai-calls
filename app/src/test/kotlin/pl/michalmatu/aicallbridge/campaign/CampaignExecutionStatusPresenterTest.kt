package pl.michalmatu.aicallbridge.campaign

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CampaignExecutionStatusPresenterTest {
    private val pl = Locale.forLanguageTag("pl-PL")
    private val en = Locale.forLanguageTag("en-US")

    @Test fun missingExecutionIsNotLive() {
        val state = CampaignExecutionStatusPresenter.present(null, pl)
        assertEquals(CampaignExecutionMeaning.NO_RECORD, state.meaning)
        assertEquals(CampaignExecutionTone.NEUTRAL, state.tone)
        assertTrue(state.summary.contains("Brak zapisu"))
        assertEquals(state, CampaignExecutionStatusPresenter.present("  ", pl))
    }

    @Test fun aRequestedDisconnectIsNotConfirmed() {
        val state = CampaignExecutionStatusPresenter.present(
            "Campaign call ended without accepted success; cleanup=targeted_disconnect_requested", pl)
        assertEquals(CampaignExecutionMeaning.REQUESTED, state.meaning)
        assertEquals(CampaignExecutionTone.CAUTION, state.tone)
        assertTrue(state.summary.contains("Wysłano polecenie"))
        assertFalse(state.summary.contains("Potwierdzono zakończenie"))
    }

    @Test fun anExplicitConfirmationIsDistinctFromRequest() {
        val state = CampaignExecutionStatusPresenter.present(
            "targeted_disconnect_confirmed", en)
        assertEquals(CampaignExecutionMeaning.CONFIRMED, state.meaning)
        assertEquals(CampaignExecutionTone.SUCCESS, state.tone)
        assertTrue(state.summary.contains("Confirmed"))
    }

    @Test fun failedCleanupWinsOverRequestAndCannotBeConfirmed() {
        for (code in listOf("targeted_disconnect_failed", "targeted_disconnect_exception")) {
            val state = CampaignExecutionStatusPresenter.present(
                "cleanup=targeted_disconnect_requested; cleanup=$code", en)
            assertEquals(CampaignExecutionMeaning.FAILED, state.meaning)
            assertEquals(CampaignExecutionTone.CAUTION, state.tone)
        }
    }

    @Test fun unrelatedHistoryAndCarrierClaimAreUnverified() {
        for (raw in listOf(
            "Starting user-requested task",
            "Carrier reports CLIR activation, independent network verification pending; cleanup=unknown",
            "cleanup=not_targeted_disconnect_requested_fake",
        )) {
            assertEquals(CampaignExecutionMeaning.UNVERIFIED,
                CampaignExecutionStatusPresenter.present(raw, en).meaning)
        }
    }

    @Test fun polishAndEnglishAreLocalizable() {
        val polish = CampaignExecutionStatusPresenter.present(
            "targeted_disconnect_requested", pl)
        val english = CampaignExecutionStatusPresenter.present(
            "targeted_disconnect_requested", en)
        assertTrue(polish.summary.contains("Wysłano"))
        assertTrue(english.summary.contains("Requested"))
        assertFalse(polish.summary == english.summary)
    }
}
