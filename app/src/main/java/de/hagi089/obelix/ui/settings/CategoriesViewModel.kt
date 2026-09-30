package de.hagi089.obelix.ui.settings

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.hagi089.obelix.core.error.AppError
import de.hagi089.obelix.core.error.AppException
import de.hagi089.obelix.R
import de.hagi089.obelix.data.finance.BookingValidator
import de.hagi089.obelix.data.finance.Category
import de.hagi089.obelix.data.finance.CategoryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CategoriesUiState(
    val isLoading: Boolean = true,
    val categories: List<Category> = emptyList(),
    val error: AppError? = null,
    @param:StringRes val nameError: Int? = null,
)

/** Kategorien in den Einstellungen: alle dürfen anlegen, nur ADMIN benennt um und (de)aktiviert (Regeln). */
class CategoriesViewModel(
    private val repository: CategoryRepository,
    private val uid: String,
) : ViewModel() {

    private val _state = MutableStateFlow(CategoriesUiState())
    val state: StateFlow<CategoriesUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch { load() }
    }

    fun clearNameError() = _state.update { it.copy(nameError = null) }

    /** Gibt true zurück, wenn die Eingabe angenommen wurde (Dialog darf schließen). */
    fun add(name: String): Boolean {
        val error = nameError(name, exceptId = null)
        if (error != null) {
            _state.update { it.copy(nameError = error) }
            return false
        }
        action { repository.add(name, uid) }
        return true
    }

    fun rename(category: Category, name: String): Boolean {
        val error = nameError(name, exceptId = category.id)
        if (error != null) {
            _state.update { it.copy(nameError = error) }
            return false
        }
        action { repository.update(category.id, name, category.active, uid) }
        return true
    }

    fun toggleActive(category: Category) = action { repository.update(category.id, category.name, !category.active, uid) }

    @StringRes
    private fun nameError(name: String, exceptId: String?): Int? {
        BookingValidator.categoryName(name)?.let { return it }
        val exists = _state.value.categories.any { it.id != exceptId && it.name.equals(name.trim(), ignoreCase = true) }
        return if (exists) R.string.error_category_name_exists else null
    }

    private fun action(block: suspend () -> Result<Unit>) {
        if (_state.value.isLoading) return
        _state.update { it.copy(isLoading = true, error = null, nameError = null) }
        viewModelScope.launch {
            val error = block().exceptionOrNull()?.let(::errorOf)
            // Danach neu vom Server laden: angezeigt wird nur, was der Server bestätigt hat.
            load(actionError = error)
        }
    }

    private suspend fun load(actionError: AppError? = null) {
        val result = repository.loadAll()
        _state.update {
            it.copy(
                isLoading = false,
                categories = result.getOrDefault(it.categories),
                error = actionError ?: result.exceptionOrNull()?.let(::errorOf),
            )
        }
    }

    private fun errorOf(throwable: Throwable): AppError = (throwable as? AppException)?.error ?: AppError.UNKNOWN
}
