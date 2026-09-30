package de.hagi089.obelix.ui.importing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.hagi089.obelix.core.error.AppError
import de.hagi089.obelix.core.error.AppException
import de.hagi089.obelix.data.finance.CategoryRepository
import de.hagi089.obelix.data.finance.FinanceRepository
import de.hagi089.obelix.data.finance.FinanceSummary
import de.hagi089.obelix.data.finance.importing.ImportFile
import de.hagi089.obelix.data.finance.importing.ImportFileException
import de.hagi089.obelix.data.finance.importing.ImportPlanner
import de.hagi089.obelix.data.user.UserProfile
import de.hagi089.obelix.data.user.UserRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ImportUiState(
    val isLoading: Boolean = true,
    val loadError: AppError? = null,
    val users: List<UserProfile> = emptyList(),
    /** `importRef` aller bereits importierten Buchungen (Import lässt sich wiederholen, ohne doppelt zu buchen). */
    val existingRefs: Set<String> = emptySet(),
    val file: ImportFile? = null,
    /** Kennzahlen aus der Datei, wie die App sie nach dem Import zeigen würde. */
    val controlValues: FinanceSummary? = null,
    val mismatches: List<String> = emptyList(),
    val fileError: String? = null,
    /** Excel-Zahler-Bezeichnung → uid des Benutzers. */
    val payerMapping: Map<String, String> = emptyMap(),
    val isImporting: Boolean = false,
    val progress: Int = 0,
    val importError: AppError? = null,
    /** Anzahl neu importierter Buchungen; null = noch nicht importiert. */
    val importedCount: Int? = null,
) {
    val pendingCount: Int get() = file?.bookings?.count { it.importRef !in existingRefs } ?: 0
    val mappingComplete: Boolean
        get() = file != null &&
            file.payers.all { payerMapping[it] != null } &&
            file.payers.map { payerMapping[it] }.toSet().size == file.payers.size
    val canImport: Boolean
        get() = file != null && mismatches.isEmpty() && mappingComplete && pendingCount > 0 && !isImporting && importedCount == null
}

/**
 * Einmaliger Excel-Import (nur ADMIN, siehe docs/PROJEKTPLAN.md, Phase 4). Die Datei wird geprüft, die Kontrollwerte
 * werden vor dem Schreiben mit den erwarteten verglichen; geschrieben wird erst nach ausdrücklicher Bestätigung.
 */
class ImportViewModel(
    private val uid: String,
    private val finance: FinanceRepository,
    private val categories: CategoryRepository,
    private val users: UserRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ImportUiState())
    val state: StateFlow<ImportUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.update { it.copy(isLoading = true, loadError = null) }
        viewModelScope.launch {
            val (userList, bookings) = coroutineScope {
                val u = async { users.loadUsers() }
                val b = async { finance.loadAll() }
                u.await() to b.await()
            }
            val failure = userList.exceptionOrNull() ?: bookings.exceptionOrNull()
            _state.update {
                if (failure != null) {
                    it.copy(isLoading = false, loadError = errorOf(failure))
                } else {
                    it.copy(
                        isLoading = false,
                        users = userList.getOrThrow(),
                        existingRefs = bookings.getOrThrow().mapNotNull { b -> b.importRef }.toSet(),
                    )
                }
            }
        }
    }

    /** Wird mit dem Text der gewählten Datei aufgerufen. */
    fun onFileText(text: String) {
        val parsed = ImportPlanner.parse(text)
        _state.update {
            val file = parsed.getOrNull()
            if (file == null) {
                it.copy(
                    file = null,
                    controlValues = null,
                    mismatches = emptyList(),
                    fileError = (parsed.exceptionOrNull() as? ImportFileException)?.reason ?: "Die Datei konnte nicht gelesen werden.",
                    payerMapping = emptyMap(),
                    importedCount = null,
                    importError = null,
                )
            } else {
                it.copy(
                    file = file,
                    controlValues = ImportPlanner.controlValues(file),
                    mismatches = ImportPlanner.mismatches(file),
                    fileError = null,
                    payerMapping = emptyMap(),
                    importedCount = null,
                    importError = null,
                )
            }
        }
    }

    fun onFileUnreadable() = _state.update { it.copy(fileError = "Die Datei konnte nicht gelesen werden.") }

    fun mapPayer(payer: String, userUid: String?) = _state.update {
        it.copy(payerMapping = if (userUid == null) it.payerMapping - payer else it.payerMapping + (payer to userUid))
    }

    fun startImport() {
        val s = _state.value
        val file = s.file ?: return
        if (!s.canImport) return
        _state.update { it.copy(isImporting = true, progress = 0, importError = null) }
        viewModelScope.launch {
            val ensured = categories.ensure(file.categories, uid)
            val categoryIds = ensured.getOrNull()
            if (categoryIds == null) {
                _state.update { it.copy(isImporting = false, importError = errorOf(ensured.exceptionOrNull())) }
                return@launch
            }
            val inputs = ImportPlanner.toInputs(file, s.payerMapping, categoryIds)
                .filter { it.importRef !in s.existingRefs }
            val result = finance.importBookings(inputs, uid) { done -> _state.update { it.copy(progress = done) } }
            val count = result.getOrNull()
            _state.update {
                if (count != null) {
                    it.copy(isImporting = false, importedCount = count)
                } else {
                    it.copy(isImporting = false, importError = errorOf(result.exceptionOrNull()))
                }
            }
            // Nach einem Fehler steht fest, was bereits importiert ist (der Import lässt sich fortsetzen).
            if (count == null) load()
        }
    }

    private fun errorOf(throwable: Throwable?): AppError = (throwable as? AppException)?.error ?: AppError.UNKNOWN
}
