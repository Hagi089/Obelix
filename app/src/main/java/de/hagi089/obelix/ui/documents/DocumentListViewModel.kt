package de.hagi089.obelix.ui.documents

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.hagi089.obelix.core.error.AppError
import de.hagi089.obelix.core.error.AppException
import de.hagi089.obelix.data.documents.Document
import de.hagi089.obelix.data.documents.DocumentCategory
import de.hagi089.obelix.data.documents.DocumentLogic
import de.hagi089.obelix.data.documents.DocumentRepository
import de.hagi089.obelix.data.user.UserProfile
import de.hagi089.obelix.data.user.UserRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DocumentListState(
    val isLoading: Boolean = true,
    /** true, sobald Dokumente und Benutzer einmal vollständig vom Server geladen wurden. */
    val hasLoaded: Boolean = false,
    val documents: List<Document> = emptyList(),
    val users: List<UserProfile> = emptyList(),
    /** Filter: null = alle Kategorien. */
    val category: DocumentCategory? = null,
    val error: AppError? = null,
) {
    val visible: List<Document> get() = DocumentLogic.filtered(documents, category)
    fun userName(uid: String?): String? = users.firstOrNull { it.uid == uid }?.displayName
}

/** Liste der Dokumente. Lädt einmalig je Öffnen nur die Angaben, nie die Dateien (Anforderung 37). */
class DocumentListViewModel(
    private val documents: DocumentRepository,
    private val users: UserRepository,
    /** Nur für Tests: ein eigener Bereich statt [viewModelScope] (der braucht auf der JVM den Android-Hauptthread). */
    scopeOverride: CoroutineScope? = null,
) : ViewModel() {

    private val scope: CoroutineScope = scopeOverride ?: viewModelScope

    private val _state = MutableStateFlow(DocumentListState())
    val state: StateFlow<DocumentListState> = _state.asStateFlow()

    fun refresh() {
        _state.update { it.copy(isLoading = true, error = null) }
        scope.launch {
            val (list, userList) = coroutineScope {
                val d = async { documents.loadAll() }
                val u = async { users.loadUsers() }
                d.await() to u.await()
            }
            val failure = list.exceptionOrNull() ?: userList.exceptionOrNull()
            if (failure != null) {
                // Bisher geladene Daten bleiben sichtbar; nie eine „leere Liste“ statt eines Fehlers.
                _state.update { it.copy(isLoading = false, error = (failure as? AppException)?.error ?: AppError.UNKNOWN) }
            } else {
                _state.update {
                    it.copy(isLoading = false, hasLoaded = true, documents = list.getOrThrow(), users = userList.getOrThrow(), error = null)
                }
            }
        }
    }

    fun setCategory(category: DocumentCategory?) = _state.update { it.copy(category = category) }
}
