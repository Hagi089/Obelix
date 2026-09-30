package de.hagi089.obelix.ui.finance

import android.net.Uri
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.hagi089.obelix.core.error.AppError
import de.hagi089.obelix.core.error.AppException
import de.hagi089.obelix.R
import de.hagi089.obelix.core.money.Money
import de.hagi089.obelix.data.files.FileReadException
import de.hagi089.obelix.data.files.LocalFileReader
import de.hagi089.obelix.data.files.NewFile
import de.hagi089.obelix.data.finance.Booking
import de.hagi089.obelix.data.finance.BookingInput
import de.hagi089.obelix.data.finance.BookingType
import de.hagi089.obelix.data.finance.BookingValidator
import de.hagi089.obelix.data.finance.Category
import de.hagi089.obelix.data.finance.CategoryRepository
import de.hagi089.obelix.data.finance.FinanceRepository
import de.hagi089.obelix.data.finance.ReceiptChange
import de.hagi089.obelix.data.finance.Settlement
import de.hagi089.obelix.data.user.UserProfile
import de.hagi089.obelix.data.user.UserRepository
import java.time.LocalDate
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BookingFormState(
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val loadError: AppError? = null,
    val saveError: AppError? = null,
    /** Bei „Bearbeiten": die gespeicherte Buchung. */
    val existing: Booking? = null,
    val categories: List<Category> = emptyList(),
    val users: List<UserProfile> = emptyList(),
    val type: BookingType = BookingType.EXPENSE,
    val date: String = LocalDate.now().toString(),
    val amountText: String = "",
    val categoryId: String? = null,
    val paidByUid: String? = null,
    val settlement: Settlement = Settlement.OPEN,
    val description: String = "",
    val comment: String = "",
    @param:StringRes val amountError: Int? = null,
    @param:StringRes val dateError: Int? = null,
    @param:StringRes val categoryError: Int? = null,
    @param:StringRes val payerError: Int? = null,
    @param:StringRes val descriptionError: Int? = null,
    @param:StringRes val commentError: Int? = null,
    /** Neu gewählter Beleg (fertig aufbereitet), wird erst beim Speichern übertragen. */
    val pendingReceipt: NewFile? = null,
    /** Der vorhandene Beleg soll beim Speichern entfernt werden. */
    val removeReceipt: Boolean = false,
    /** Eine gewählte Datei wird gerade gelesen und verkleinert. */
    val isReadingReceipt: Boolean = false,
    @param:StringRes val receiptError: Int? = null,
    /** true, sobald gespeichert/gelöscht wurde: der Bildschirm schließt sich. */
    val finished: Boolean = false,
) {
    val isEdit: Boolean get() = existing != null

    /** Was beim Speichern mit dem Beleg geschieht (Einnahmen haben keinen Beleg). */
    val receiptChange: ReceiptChange
        get() = when {
            type == BookingType.INCOME -> if (existing?.receipt != null) ReceiptChange.Remove else ReceiptChange.Keep
            pendingReceipt != null -> ReceiptChange.Replace(pendingReceipt)
            removeReceipt -> ReceiptChange.Remove
            else -> ReceiptChange.Keep
        }
}

/** Formular zum Anlegen und Bearbeiten einer Buchung (bookingId = null: neu). */
class BookingFormViewModel(
    private val bookingId: String?,
    private val uid: String,
    private val finance: FinanceRepository,
    private val categories: CategoryRepository,
    private val users: UserRepository,
    private val fileReader: LocalFileReader,
) : ViewModel() {

    private val _state = MutableStateFlow(BookingFormState(paidByUid = uid))
    val state: StateFlow<BookingFormState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.update { it.copy(isLoading = true, loadError = null) }
        viewModelScope.launch {
            val (cats, userList, booking) = coroutineScope {
                val c = async { categories.loadAll() }
                val u = async { users.loadUsers() }
                val b = async { bookingId?.let { finance.get(it) } }
                Triple(c.await(), u.await(), b.await())
            }
            val failure = cats.exceptionOrNull() ?: userList.exceptionOrNull() ?: booking?.exceptionOrNull()
            val existing = booking?.getOrNull()
            when {
                failure != null -> _state.update { it.copy(isLoading = false, loadError = errorOf(failure)) }
                bookingId != null && existing == null ->
                    _state.update { it.copy(isLoading = false, loadError = AppError.NOT_FOUND) }
                else -> _state.update { current ->
                    val base = current.copy(isLoading = false, categories = cats.getOrThrow(), users = userList.getOrThrow())
                    if (existing == null) {
                        base
                    } else {
                        base.copy(
                            existing = existing,
                            type = existing.type,
                            date = existing.date,
                            amountText = Money.formatInput(existing.amountCents),
                            categoryId = existing.categoryId,
                            paidByUid = existing.paidByUid,
                            settlement = existing.settlement,
                            description = existing.description,
                            comment = existing.comment,
                        )
                    }
                }
            }
        }
    }

    fun setType(type: BookingType) = _state.update {
        val settlement = when {
            type == BookingType.INCOME -> Settlement.SETTLED
            it.type == BookingType.INCOME -> Settlement.OPEN
            else -> it.settlement
        }
        it.copy(type = type, settlement = settlement, payerError = null)
    }

    fun setDate(iso: String) = _state.update { it.copy(date = iso, dateError = null) }
    fun setAmount(text: String) = _state.update { it.copy(amountText = text, amountError = null) }
    fun setCategory(id: String?) = _state.update { it.copy(categoryId = id, categoryError = null) }
    fun setPayer(uid: String?) = _state.update { it.copy(paidByUid = uid, payerError = null) }
    fun setSettlement(settlement: Settlement) = _state.update { it.copy(settlement = settlement) }
    fun setDescription(text: String) = _state.update { it.copy(description = text, descriptionError = null) }
    fun setComment(text: String) = _state.update { it.copy(comment = text, commentError = null) }

    /** Der Benutzer hat eine Datei gewählt: lesen, Bild verkleinern, prüfen. Erst „Speichern" überträgt sie. */
    fun attachReceipt(uri: Uri) {
        if (_state.value.isSaving || _state.value.isReadingReceipt) return
        _state.update { it.copy(isReadingReceipt = true, receiptError = null) }
        viewModelScope.launch {
            val result = fileReader.read(uri)
            _state.update { current ->
                result.fold(
                    onSuccess = { file -> current.copy(isReadingReceipt = false, pendingReceipt = file, removeReceipt = false) },
                    onFailure = { error ->
                        val message = (error as? FileReadException)?.messageRes ?: R.string.error_file_unreadable
                        current.copy(isReadingReceipt = false, receiptError = message)
                    },
                )
            }
        }
    }

    /** Verwirft die neu gewählte Datei (der vorhandene Beleg bleibt). */
    fun discardPendingReceipt() = _state.update { it.copy(pendingReceipt = null, receiptError = null) }

    /** Markiert den vorhandenen Beleg zum Entfernen beim Speichern bzw. macht das rückgängig. */
    fun setRemoveReceipt(remove: Boolean) = _state.update { it.copy(removeReceipt = remove, pendingReceipt = null, receiptError = null) }

    fun save() {
        val s = _state.value
        if (s.isLoading || s.isSaving || s.isReadingReceipt) return
        val errors = s.copy(
            amountError = BookingValidator.amount(s.amountText),
            dateError = BookingValidator.date(s.date),
            categoryError = BookingValidator.category(s.categoryId),
            payerError = BookingValidator.payer(s.type, s.paidByUid),
            descriptionError = BookingValidator.description(s.description),
            commentError = BookingValidator.comment(s.comment),
            saveError = null,
        )
        val hasErrors = listOf(
            errors.amountError, errors.dateError, errors.categoryError,
            errors.payerError, errors.descriptionError, errors.commentError,
        ).any { it != null }
        if (hasErrors) {
            _state.value = errors
            return
        }
        val input = BookingInput(
            type = s.type,
            date = s.date,
            amountCents = requireNotNull(Money.parse(s.amountText)),
            categoryId = requireNotNull(s.categoryId),
            paidByUid = s.paidByUid?.takeIf { it.isNotBlank() },
            settlement = if (s.type == BookingType.INCOME) Settlement.SETTLED else s.settlement,
            description = s.description.trim(),
            comment = s.comment.trim(),
        )
        run(errors) {
            val existing = s.existing
            if (existing == null) {
                finance.create(input, uid, s.pendingReceipt.takeIf { s.type == BookingType.EXPENSE })
            } else {
                finance.update(existing.id, input, existing.settlement, uid, s.receiptChange)
            }
        }
    }

    /** Aktion „Erstattet" (OPEN → SETTLED). */
    fun markSettled() {
        val existing = _state.value.existing ?: return
        run(_state.value) { finance.markSettled(existing.id, uid) }
    }

    fun delete() {
        val existing = _state.value.existing ?: return
        run(_state.value) { finance.delete(existing.id, existing.plannedExpenseId, uid) }
    }

    private fun run(base: BookingFormState, block: suspend () -> Result<Unit>) {
        if (_state.value.isSaving) return
        _state.value = base.copy(isSaving = true, saveError = null)
        viewModelScope.launch {
            val error = block().exceptionOrNull()
            _state.update {
                if (error == null) it.copy(isSaving = false, finished = true) else it.copy(isSaving = false, saveError = errorOf(error))
            }
        }
    }

    private fun errorOf(throwable: Throwable): AppError = (throwable as? AppException)?.error ?: AppError.UNKNOWN
}
