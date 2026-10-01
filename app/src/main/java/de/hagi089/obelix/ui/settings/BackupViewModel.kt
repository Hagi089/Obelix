package de.hagi089.obelix.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.hagi089.obelix.core.error.AppError
import de.hagi089.obelix.core.error.AppException
import de.hagi089.obelix.data.backup.BackupProgress
import de.hagi089.obelix.data.backup.BackupRepository
import de.hagi089.obelix.data.backup.BackupSummary
import java.io.OutputStream
import java.time.Instant
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class BackupUiState(
    val isRunning: Boolean = false,
    val progress: BackupProgress? = null,
    /** Ergebnis des letzten Backups (auch ein unvollständiges, siehe [BackupSummary.isComplete]). */
    val result: BackupSummary? = null,
    val error: AppError? = null,
)

/**
 * Backup in den Einstellungen (nur ADMIN sichtbar). Der Speicherort kommt vom Android-Dateidialog; das ViewModel
 * bekommt nur eine Funktion, die den Ausgabestrom öffnet, und kennt weder Android noch Firestore.
 */
class BackupViewModel(
    private val repository: BackupRepository,
    private val clock: () -> Instant = Instant::now,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    scopeOverride: CoroutineScope? = null,
) : ViewModel() {

    private val scope: CoroutineScope = scopeOverride ?: viewModelScope

    private val _state = MutableStateFlow(BackupUiState())
    val state: StateFlow<BackupUiState> = _state.asStateFlow()

    /** Startet das Backup. Läuft schon eines, passiert nichts (kein zweites, paralleles Backup). */
    fun start(open: () -> OutputStream?) {
        if (_state.value.isRunning) return
        _state.value = BackupUiState(isRunning = true)
        scope.launch {
            // Das Öffnen kann bei Cloud-Speicherorten (z. B. Google Drive) dauern: nicht auf dem Hauptfaden.
            val out = withContext(ioDispatcher) { runCatching(open).getOrNull() }
            if (out == null) {
                _state.value = BackupUiState(error = AppError.STORAGE)
                return@launch
            }
            val result = repository.write(out, clock()) { progress -> _state.update { it.copy(progress = progress) } }
            _state.value = result.fold(
                onSuccess = { BackupUiState(result = it) },
                onFailure = { BackupUiState(error = (it as? AppException)?.error ?: AppError.UNKNOWN) },
            )
        }
    }
}
