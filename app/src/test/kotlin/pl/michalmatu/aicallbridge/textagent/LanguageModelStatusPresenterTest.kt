package pl.michalmatu.aicallbridge.textagent

import org.junit.Assert.*
import org.junit.Test
import pl.michalmatu.aicallbridge.audioengines.DialogueModelId

class LanguageModelStatusPresenterTest {
    private val a = DialogueModelId.GEMMA_4_E2B
    private val b = DialogueModelId.GEMMA_4_E4B
    private val entries = LanguageModelCatalog.entries

    private fun render(
        ready: Set<DialogueModelId> = emptySet(),
        selected: DialogueModelId? = null,
        transfer: TransferStatus = TransferStatus(),
        previous: TransferStatus? = null,
    ) = LanguageModelStatusPresenter.present(entries, selected, ready, transfer, previous)

    @Test fun absentModelsAreNotShownAsReadyOrSelectable() {
        val screen = render(selected = a)
        assertEquals(entries.size, screen.rows.size)
        for (row in screen.rows) {
            assertEquals(LanguageModelMeaning.NOT_INSTALLED, row.meaning)
            assertFalse(row.active)
            assertTrue(row.showDownload)
            assertTrue(row.showImport)
            assertFalse(row.showSelect)
            assertTrue(row.actionsEnabled)
            assertFalse(row.showProgress)
            assertEquals(0, row.progressPercent)
        }
        assertFalse(screen.showCancel)
    }

    @Test fun selectionAloneIsNotInstallationEvidence() {
        val screen = render(ready = setOf(b), selected = a)
        val e2b = screen.rows.single { it.id == a }
        val e4b = screen.rows.single { it.id == b }
        assertEquals(LanguageModelMeaning.NOT_INSTALLED, e2b.meaning)
        assertEquals(LanguageModelMeaning.READY, e4b.meaning)
        assertTrue(e4b.showSelect)
        assertFalse(e4b.showDownload)
        assertFalse(e4b.showImport)
        assertFalse(e4b.active)
    }

    @Test fun onlyInstalledSelectedModelIsHighlightedAsActive() {
        val screen = render(ready = setOf(a, b), selected = a)
        val chosen = screen.rows.single { it.id == a }
        assertEquals(LanguageModelMeaning.ACTIVE, chosen.meaning)
        assertTrue(chosen.active)
        assertFalse(chosen.showSelect)
        assertFalse(chosen.showDownload)
        assertTrue(screen.rows.single { it.id == b }.showSelect)
    }

    @Test fun busyTransferDisablesChoicesWithoutChangingStoredSelection() {
        val bytes = checkNotNull(LanguageModelCatalog[b].spec.expectedBytes) / 2
        val screen = render(setOf(a), a, TransferStatus(b, true, bytes, "transferring"))
        assertTrue(screen.showCancel)
        assertEquals("transferring", screen.newMessage)
        val chosen = screen.rows.single { it.id == a }
        val receiving = screen.rows.single { it.id == b }
        assertEquals(LanguageModelMeaning.ACTIVE, chosen.meaning)
        assertFalse(chosen.actionsEnabled)
        assertFalse(receiving.actionsEnabled)
        assertTrue(receiving.showProgress)
        assertEquals(50, receiving.progressPercent)
        assertFalse(chosen.showProgress)
        assertEquals(0, chosen.progressPercent)
    }

    @Test fun progressIsBoundedForNegativeAndOversizeByteCounts() {
        val n = checkNotNull(LanguageModelCatalog[b].spec.expectedBytes)
        assertEquals(0, render(transfer = TransferStatus(b, true, -5L)).rows.single { it.id == b }.progressPercent)
        assertEquals(100, render(transfer = TransferStatus(b, true, Long.MAX_VALUE)).rows.single { it.id == b }.progressPercent)
        assertEquals(0, render(transfer = TransferStatus(a, true, n)).rows.single { it.id == b }.progressPercent)
    }

    @Test fun transferFeedbackEmitsOnChangeOnlyAndPersistsAfterCompletion() {
        val before = TransferStatus(a, true, 2L, "working")
        assertEquals("working", render(transfer = before).newMessage)
        assertNull(render(transfer = before, previous = before).newMessage)
        val done = TransferStatus(a, false, 3L, "done")
        assertEquals("done", render(transfer = done, previous = before).newMessage)
        assertFalse(render(transfer = done, previous = before).showCancel)
        assertNull(render(transfer = TransferStatus(), previous = done).newMessage)
    }

    @Test fun rowsAreDetachedImmutableSnapshots() {
        val screen = render(ready = setOf(a), selected = a)
        assertEquals(entries.map { it.id }, screen.rows.map { it.id })
        @Suppress("UNCHECKED_CAST")
        val mutable = screen.rows as MutableList<LanguageModelRowState>
        try {
            mutable.clear()
            fail("snapshot_mutable")
        } catch (_: UnsupportedOperationException) {}
        assertEquals(entries.size, screen.rows.size)
    }
}
