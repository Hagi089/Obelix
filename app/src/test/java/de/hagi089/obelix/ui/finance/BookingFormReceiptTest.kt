package de.hagi089.obelix.ui.finance

import de.hagi089.obelix.data.files.FileLimits
import de.hagi089.obelix.data.files.FileRef
import de.hagi089.obelix.data.files.NewFile
import de.hagi089.obelix.data.finance.Booking
import de.hagi089.obelix.data.finance.BookingType
import de.hagi089.obelix.data.finance.ReceiptChange
import de.hagi089.obelix.data.finance.Settlement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** Was beim Speichern mit dem Beleg geschieht (BookingFormState.receiptChange). */
class BookingFormReceiptTest {

    private val saved = FileRef("f1", "alt.jpg", FileLimits.MIME_JPEG, 1000)
    private val newFile = NewFile("neu.pdf", FileLimits.MIME_PDF, byteArrayOf(1, 2, 3))

    private fun booking(receipt: FileRef?) = Booking(
        id = "b1", type = BookingType.EXPENSE, date = "2026-09-30", amountCents = 4700, categoryId = "c",
        paidByUid = "u", settlement = Settlement.OPEN, description = "Diesel", comment = "", importRef = null, receipt = receipt,
    )

    @Test
    fun nothingChanged_keepsTheReceipt() {
        assertEquals(ReceiptChange.Keep, BookingFormState().receiptChange)
        assertEquals(ReceiptChange.Keep, BookingFormState(existing = booking(saved)).receiptChange)
    }

    @Test
    fun newFile_replacesOrAdds() {
        val added = BookingFormState(pendingReceipt = newFile).receiptChange
        assertTrue(added is ReceiptChange.Replace)
        assertSame(newFile, (added as ReceiptChange.Replace).file)
        val replaced = BookingFormState(existing = booking(saved), pendingReceipt = newFile).receiptChange
        assertSame(newFile, (replaced as ReceiptChange.Replace).file)
    }

    @Test
    fun removeMark_removes() {
        assertEquals(ReceiptChange.Remove, BookingFormState(existing = booking(saved), removeReceipt = true).receiptChange)
    }

    @Test
    fun turningIntoIncome_removesAnExistingReceipt_neverKeepsOrUploads() {
        assertEquals(ReceiptChange.Remove, BookingFormState(existing = booking(saved), type = BookingType.INCOME).receiptChange)
        assertEquals(ReceiptChange.Keep, BookingFormState(existing = booking(null), type = BookingType.INCOME).receiptChange)
        // Ein noch nicht übertragener neuer Beleg wird bei einer Einnahme nie hochgeladen.
        assertEquals(ReceiptChange.Keep, BookingFormState(type = BookingType.INCOME, pendingReceipt = newFile).receiptChange)
        assertEquals(ReceiptChange.Remove, BookingFormState(existing = booking(saved), type = BookingType.INCOME, pendingReceipt = newFile).receiptChange)
    }
}
