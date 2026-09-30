package de.hagi089.obelix.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Euro
import androidx.compose.material.icons.filled.Place
import androidx.compose.ui.graphics.vector.ImageVector
import de.hagi089.obelix.R
import kotlinx.serialization.Serializable

// Typsichere Routen (Navigation Compose)
@Serializable data object DashboardRoute
@Serializable data object CalendarRoute
@Serializable data object FinanceRoute
@Serializable data object TasksRoute
@Serializable data object CampsitesRoute
@Serializable data object DocumentsRoute
@Serializable data object SettingsRoute

/** Formular für eine Buchung; bookingId = null legt eine neue an. */
@Serializable data class BookingFormRoute(val bookingId: String? = null)

/** Formular eines Kalendereintrags; entryId = null legt einen neuen an. */
@Serializable data class CalendarFormRoute(val entryId: String? = null)

/** Formular einer Auffälligkeit; repairId = null legt eine neue an. */
@Serializable data class RepairFormRoute(val repairId: String? = null)

/** Liste der geplanten Ausgaben (aus dem Finanzbereich). */
@Serializable data object PlannedListRoute

/** Formular einer geplanten Ausgabe; plannedId = null legt eine neue an. */
@Serializable data class PlannedFormRoute(val plannedId: String? = null)

/** Anzeige eines Belegs (aus dem Buchungsformular). Die Angaben der Datei stehen in der Route. */
@Serializable data class ReceiptRoute(val fileId: String, val name: String, val contentType: String, val sizeBytes: Long)

/** Einmaliger Excel-Import (nur ADMIN, aus den Einstellungen). */
@Serializable data object ImportRoute

/** Die sechs Hauptbereiche der Navigationsleiste (Entscheidung vom 30.09.2026). */
enum class TopLevelDestination(
    val route: Any,
    val icon: ImageVector,
    @param:StringRes val labelRes: Int,
) {
    DASHBOARD(DashboardRoute, Icons.Filled.Dashboard, R.string.nav_dashboard),
    CALENDAR(CalendarRoute, Icons.Filled.CalendarMonth, R.string.nav_calendar),
    FINANCE(FinanceRoute, Icons.Filled.Euro, R.string.nav_finance),
    TASKS(TasksRoute, Icons.Filled.Build, R.string.nav_tasks),
    CAMPSITES(CampsitesRoute, Icons.Filled.Place, R.string.nav_campsites),
    DOCUMENTS(DocumentsRoute, Icons.Filled.Description, R.string.nav_documents),
}
