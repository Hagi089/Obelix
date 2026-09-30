package de.hagi089.obelix.ui.finance

import androidx.annotation.StringRes
import de.hagi089.obelix.R
import de.hagi089.obelix.data.finance.Settlement
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val DAY_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy")

/** yyyy-MM-dd → dd.MM.yyyy (bei ungültiger Eingabe unverändert). */
fun formatDay(iso: String): String = try {
    LocalDate.parse(iso).format(DAY_FORMAT)
} catch (e: java.time.format.DateTimeParseException) {
    iso
}

@StringRes
fun settlementLabel(settlement: Settlement): Int = when (settlement) {
    Settlement.OPEN -> R.string.settlement_open
    Settlement.SETTLED -> R.string.settlement_settled
    Settlement.SPONSORED -> R.string.settlement_sponsored
}
