package de.hagi089.obelix.core.money

import java.math.BigDecimal

/**
 * Geld wird immer in Cent (Long) gerechnet, nie als Double (Datenkorrektheit).
 * Eingabe und Anzeige sind deutsch: 1.234,56 €.
 */
object Money {

    /** Obergrenze je Buchung: 1.000.000,00 €. Muss zu firebase/firestore.rules (validBooking) passen. */
    const val MAX_CENTS = 100_000_000L

    private val WITH_COMMA = Regex("^(\\d{1,3}(\\.\\d{3})+|\\d+),\\d{1,2}$")
    private val THOUSANDS = Regex("^\\d{1,3}(\\.\\d{3})+$")
    private val PLAIN = Regex("^\\d+(\\.\\d{1,2})?$")

    /**
     * Liest einen Betrag aus der Eingabe: „12", „12,5", „12,50", „1.234,56", „12.50", „1.234", „12,50 €".
     * Höchstens zwei Nachkommastellen. Gibt null zurück, wenn die Eingabe kein Betrag ist.
     * Ob der Betrag größer als 0 und im erlaubten Bereich ist, prüft [BookingValidator].
     */
    fun parse(input: String): Long? {
        val s = input.trim().removeSuffix("€").filterNot { it.isWhitespace() || it == ' ' }
        if (s.isEmpty() || s.length > 15) return null
        val normalized = when {
            WITH_COMMA.matches(s) -> s.replace(".", "").replace(',', '.')
            PLAIN.matches(s) -> s
            THOUSANDS.matches(s) -> s.replace(".", "")
            else -> return null
        }
        return BigDecimal(normalized).movePointRight(2).longValueExact()
    }

    /** Anzeige mit Tausenderpunkt und Währung, z. B. „1.234,56 €" oder „−99,00 €". */
    fun format(cents: Long): String = (if (cents < 0) "−" else "") + plain(kotlin.math.abs(cents), grouping = true) + " €"

    /** Für Eingabefelder: „1234,56" (ohne Tausenderpunkt und Währung). */
    fun formatInput(cents: Long): String = plain(kotlin.math.abs(cents), grouping = false)

    private fun plain(absCents: Long, grouping: Boolean): String {
        val euros = (absCents / 100).toString()
        val shown = if (grouping) euros.reversed().chunked(3).joinToString(".").reversed() else euros
        return "$shown,${(absCents % 100).toString().padStart(2, '0')}"
    }
}
