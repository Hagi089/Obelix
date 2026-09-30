package de.hagi089.obelix.data.finance.importing

import de.hagi089.obelix.core.money.Money
import de.hagi089.obelix.data.finance.Booking
import de.hagi089.obelix.data.finance.BookingInput
import de.hagi089.obelix.data.finance.BookingType
import de.hagi089.obelix.data.finance.BookingValidator
import de.hagi089.obelix.data.finance.FinanceCalculator
import de.hagi089.obelix.data.finance.FinanceSummary
import de.hagi089.obelix.data.finance.Settlement
import java.time.LocalDate
import java.time.format.DateTimeParseException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Einmaliger Import der Excel-Buchungen (Phase 4). Die Datei `obelix-import.json` enthält private Daten und
 * liegt nie im Repository. Sie wird aus der Excel erzeugt (Beträge auf Cent gerundet, Status und Zahler
 * zugeordnet) und enthält die erwarteten Kontrollwerte, gegen die die App vor dem Import prüft.
 */
@Serializable
data class ImportFile(
    val version: Int,
    /** Die Zahler-Bezeichnungen der Excel, z. B. zwei Stück. Jeder wird beim Import einem Benutzer zugeordnet. */
    val payers: List<String>,
    val categories: List<String>,
    val bookings: List<ImportBooking>,
    val skipped: List<ImportSkipped> = emptyList(),
    val expected: ImportExpected,
)

@Serializable
data class ImportBooking(
    /** Zeile in der Excel; wird zu `importRef` = „xl-<Zeile>". */
    val row: Int,
    val date: String,
    val type: String,
    val amountCents: Long,
    val category: String,
    val payer: String,
    val settlement: String,
    val description: String,
    val comment: String = "",
) {
    val importRef: String get() = "xl-$row"
}

@Serializable
data class ImportSkipped(val row: Int, val description: String, val reason: String)

@Serializable
data class ImportExpected(
    val count: Int,
    val incomeCents: Long,
    val expenseCents: Long,
    val balanceCents: Long,
    /** Offene Forderungen je Zahler-Bezeichnung. */
    val openCents: Map<String, Long>,
    val afterSettlementCents: Long,
)

/** Die Importdatei ist unbrauchbar. [reason] ist eine deutsche Erklärung für die Anzeige. */
class ImportFileException(val reason: String) : Exception(reason)

object ImportPlanner {

    const val SUPPORTED_VERSION = 1

    private val json = Json { ignoreUnknownKeys = true }

    /** Liest und prüft die Datei. Prüft jede Buchung wie das Formular (Betrag, Datum, Länge, Status). */
    fun parse(text: String): Result<ImportFile> = try {
        val file = json.decodeFromString<ImportFile>(text)
        validate(file)
        Result.success(file)
    } catch (e: ImportFileException) {
        Result.failure(e)
    } catch (e: IllegalArgumentException) {
        // Auch SerializationException (Unterart von IllegalArgumentException).
        Result.failure(ImportFileException("Die Datei hat nicht das erwartete Format."))
    }

    private fun validate(file: ImportFile) {
        fun fail(message: String): Nothing = throw ImportFileException(message)
        if (file.version != SUPPORTED_VERSION) fail("Nicht unterstützte Dateiversion ${file.version}.")
        if (file.payers.isEmpty() || file.payers.any { it.isBlank() } || file.payers.toSet().size != file.payers.size) {
            fail("Die Zahler-Liste ist leer oder enthält Duplikate.")
        }
        if (file.categories.any { BookingValidator.categoryName(it) != null } ||
            file.categories.map { it.lowercase() }.toSet().size != file.categories.size
        ) {
            fail("Die Kategorien-Liste ist ungültig oder enthält Duplikate.")
        }
        if (file.bookings.map { it.row }.toSet().size != file.bookings.size) fail("Zeilennummern kommen doppelt vor.")
        file.bookings.forEach { b ->
            val where = "Zeile ${b.row}"
            val type = BookingType.from(b.type) ?: fail("$where: unbekannte Art „${b.type}“.")
            val settlement = Settlement.from(b.settlement) ?: fail("$where: unbekannter Status „${b.settlement}“.")
            if (b.amountCents <= 0 || b.amountCents > Money.MAX_CENTS) fail("$where: ungültiger Betrag.")
            try {
                LocalDate.parse(b.date)
            } catch (e: DateTimeParseException) {
                fail("$where: ungültiges Datum „${b.date}“.")
            }
            if (b.category !in file.categories) fail("$where: unbekannte Kategorie „${b.category}“.")
            if (b.payer !in file.payers) fail("$where: unbekannter Zahler „${b.payer}“.")
            if (BookingValidator.description(b.description) != null) fail("$where: ungültige Beschreibung.")
            if (BookingValidator.comment(b.comment) != null) fail("$where: Kommentar zu lang.")
            if (type == BookingType.INCOME && settlement != Settlement.SETTLED) fail("$where: Einnahmen müssen „beglichen“ sein.")
        }
    }

    /** Kennzahlen, wie die App sie nach dem Import zeigen würde; Zahler sind hier noch die Excel-Bezeichnungen. */
    fun controlValues(file: ImportFile): FinanceSummary = FinanceCalculator.summarize(
        file.bookings.map {
            Booking(
                id = it.importRef,
                type = BookingType.from(it.type)!!,
                date = it.date,
                amountCents = it.amountCents,
                categoryId = it.category,
                paidByUid = it.payer,
                settlement = Settlement.from(it.settlement)!!,
                description = it.description,
                comment = it.comment,
                importRef = it.importRef,
            )
        },
    )

    /** Abweichungen zwischen den erwarteten Kontrollwerten der Datei und dem Nachgerechneten. Leer = alles stimmt. */
    fun mismatches(file: ImportFile): List<String> {
        val actual = controlValues(file)
        val e = file.expected
        return buildList {
            fun check(label: String, expected: Long, actual: Long) {
                if (expected != actual) add("$label: erwartet ${Money.format(expected)}, berechnet ${Money.format(actual)}")
            }
            if (e.count != file.bookings.size) add("Anzahl: erwartet ${e.count}, in der Datei ${file.bookings.size}")
            check("Einnahmen", e.incomeCents, actual.incomeTotalCents)
            check("Ausgaben", e.expenseCents, actual.expenseTotalCents)
            check("Kontostand", e.balanceCents, actual.balanceCents)
            check("Kontostand nach Begleichung", e.afterSettlementCents, actual.afterSettlementCents)
            file.payers.forEach { payer ->
                check("Offene Forderung $payer", e.openCents[payer] ?: 0L, actual.openByPayerCents[payer] ?: 0L)
            }
        }
    }

    /**
     * Wandelt die Datei in Buchungen um. [payerToUid] ordnet jede Zahler-Bezeichnung einem Benutzer zu,
     * [categoryIds] jeden Kategorienamen der Kategorie-ID in Firestore.
     */
    fun toInputs(file: ImportFile, payerToUid: Map<String, String>, categoryIds: Map<String, String>): List<BookingInput> =
        file.bookings.map {
            BookingInput(
                type = BookingType.from(it.type)!!,
                date = it.date,
                amountCents = it.amountCents,
                categoryId = requireNotNull(categoryIds[it.category]) { "Kategorie ${it.category} fehlt" },
                paidByUid = requireNotNull(payerToUid[it.payer]) { "Zahler ${it.payer} nicht zugeordnet" },
                settlement = Settlement.from(it.settlement)!!,
                description = it.description.trim(),
                comment = it.comment.trim(),
                importRef = it.importRef,
            )
        }
}
