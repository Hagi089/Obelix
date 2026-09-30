package de.hagi089.obelix.ui.planned

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.hagi089.obelix.core.error.AppError
import de.hagi089.obelix.core.error.AppException
import de.hagi089.obelix.core.money.Money
import de.hagi089.obelix.data.finance.Booking
import de.hagi089.obelix.data.finance.BookingType
import de.hagi089.obelix.data.finance.BookingValidator
import de.hagi089.obelix.data.finance.Category
import de.hagi089.obelix.data.finance.CategoryRepository
import de.hagi089.obelix.data.finance.FinanceRepository
import de.hagi089.obelix.data.finance.Settlement
import de.hagi089.obelix.data.planned.PlannedExpense
import de.hagi089.obelix.data.planned.PlannedExpenseRepository
import de.hagi089.obelix.data.planned.PlannedInput
import de.hagi089.obelix.data.planned.PlannedStatus
import de.hagi089.obelix.data.planned.PlannedValidator
import de.hagi089.obelix.data.planned.Priority
import de.hagi089.obelix.data.planned.PurchaseInput
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

/** Eingaben des Dialogs „Gekauft“. */
data class PurchaseDraft(
    val amountText: String,
    val date: String,
    val paidByUid: String?,
    val categoryId: String? = null,
    val settlement: Settlement = Settlement.OPEN,
    @param:StringRes val amountError: Int? = null,
    @param:StringRes val dateError: Int? = null,
    @param:StringRes val categoryError: Int? = null,
    @param:StringRes val payerError: Int? = null,
)

data class PlannedFormState(
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val loadError: AppError? = null,
    val saveError: AppError? = null,
    /** Bei „Bearbeiten“: die gespeicherte Planung. */
    val existing: PlannedExpense? = null,
    /** Bei gekaufter Planung: die entstandene Buchung (null, wenn es sie nicht mehr gibt). */
    val purchasedBooking: Booking? = null,
    val categories: List<Category> = emptyList(),
    val users: List<UserProfile> = emptyList(),
    val title: String = "",
    val amountText: String = "",
    val date: String = LocalDate.now().toString(),
    val priority: Priority? = null,
    val link: String = "",
    val comment: String = "",
    @param:StringRes val titleError: Int? = null,
    @param:StringRes val amountError: Int? = null,
    @param:StringRes val dateError: Int? = null,
    @param:StringRes val linkError: Int? = null,
    @param:StringRes val commentError: Int? = null,
    /** Nicht null, solange der Dialog „Gekauft“ offen ist. */
    val purchase: PurchaseDraft? = null,
    /** true, sobald gespeichert, gekauft oder gelöscht wurde: der Bildschirm schließt sich. */
    val finished: Boolean = false,
) {
    val isEdit: Boolean get() = existing != null
    val isPurchased: Boolean get() = existing?.status == PlannedStatus.PURCHASED
}

/** Formular zum Anlegen, Bearbeiten, Kaufen und Löschen einer geplanten Ausgabe (plannedId = null: neu). */
class PlannedFormViewModel(
    private val plannedId: String?,
    private val uid: String,
    private val planned: PlannedExpenseRepository,
    private val finance: FinanceRepository,
    private val categories: CategoryRepository,
    private val users: UserRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(PlannedFormState())
    val state: StateFlow<PlannedFormState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.update { it.copy(isLoading = true, loadError = null) }
        viewModelScope.launch {
            val (cats, userList, plan) = coroutineScope {
                val c = async { categories.loadAll() }
                val u = async { users.loadUsers() }
                val p = async { plannedId?.let { planned.get(it) } }
                Triple(c.await(), u.await(), p.await())
            }
            val failure = cats.exceptionOrNull() ?: userList.exceptionOrNull() ?: plan?.exceptionOrNull()
            val existing = plan?.getOrNull()
            if (failure != null) {
                _state.update { it.copy(isLoading = false, loadError = errorOf(failure)) }
                return@launch
            }
            if (plannedId != null && existing == null) {
                _state.update { it.copy(isLoading = false, loadError = AppError.NOT_FOUND) }
                return@launch
            }
            // Gekaufte Planung: die entstandene Buchung nachladen, um den tatsächlichen Betrag zu zeigen.
            var booking: Booking? = null
            val bookingId = existing?.purchasedTransactionId
            if (existing?.status == PlannedStatus.PURCHASED && bookingId != null) {
                val loaded = finance.get(bookingId)
                loaded.exceptionOrNull()?.let { error ->
                    _state.update { it.copy(isLoading = false, loadError = errorOf(error)) }
                    return@launch
                }
                booking = loaded.getOrNull()
            }
            _state.update { current ->
                val base = current.copy(isLoading = false, categories = cats.getOrThrow(), users = userList.getOrThrow())
                if (existing == null) {
                    base
                } else {
                    base.copy(
                        existing = existing,
                        purchasedBooking = booking,
                        title = existing.title,
                        amountText = Money.formatInput(existing.estimatedAmountCents),
                        date = existing.plannedDate,
                        priority = existing.priority,
                        link = existing.link.orEmpty(),
                        comment = existing.comment,
                    )
                }
            }
        }
    }

    fun setTitle(text: String) = _state.update { it.copy(title = text, titleError = null) }
    fun setAmount(text: String) = _state.update { it.copy(amountText = text, amountError = null) }
    fun setDate(iso: String) = _state.update { it.copy(date = iso, dateError = null) }
    fun setPriority(priority: Priority?) = _state.update { it.copy(priority = priority) }
    fun setLink(text: String) = _state.update { it.copy(link = text, linkError = null) }
    fun setComment(text: String) = _state.update { it.copy(comment = text, commentError = null) }

    fun save() {
        val s = _state.value
        if (s.isLoading || s.isSaving || s.isPurchased) return
        val errors = s.copy(
            titleError = PlannedValidator.title(s.title),
            amountError = BookingValidator.amount(s.amountText),
            dateError = BookingValidator.date(s.date),
            linkError = PlannedValidator.link(s.link),
            commentError = BookingValidator.comment(s.comment),
            saveError = null,
        )
        val hasErrors = listOf(errors.titleError, errors.amountError, errors.dateError, errors.linkError, errors.commentError).any { it != null }
        if (hasErrors) {
            _state.value = errors
            return
        }
        val input = PlannedInput(
            title = s.title.trim(),
            estimatedAmountCents = requireNotNull(Money.parse(s.amountText)),
            plannedDate = s.date,
            priority = s.priority,
            link = s.link.trim().takeIf { it.isNotEmpty() },
            comment = s.comment.trim(),
        )
        run(errors) {
            val existing = s.existing
            if (existing == null) planned.create(input, uid) else planned.update(existing.id, input, uid)
        }
    }

    // ---------- Kauf ----------

    /** Öffnet den Dialog „Gekauft“ mit der Schätzung als Vorschlag für den tatsächlichen Betrag. */
    fun startPurchase() {
        val s = _state.value
        val existing = s.existing ?: return
        if (s.isSaving || existing.status != PlannedStatus.PLANNED) return
        _state.value = s.copy(
            saveError = null,
            purchase = PurchaseDraft(
                amountText = Money.formatInput(existing.estimatedAmountCents),
                date = LocalDate.now().toString(),
                paidByUid = uid,
            ),
        )
    }

    fun cancelPurchase() = _state.update { if (it.isSaving) it else it.copy(purchase = null, saveError = null) }

    private fun updateDraft(block: (PurchaseDraft) -> PurchaseDraft) =
        _state.update { s -> s.purchase?.let { s.copy(purchase = block(it)) } ?: s }

    fun setPurchaseAmount(text: String) = updateDraft { it.copy(amountText = text, amountError = null) }
    fun setPurchaseDate(iso: String) = updateDraft { it.copy(date = iso, dateError = null) }
    fun setPurchaseCategory(id: String?) = updateDraft { it.copy(categoryId = id, categoryError = null) }
    fun setPurchasePayer(uid: String?) = updateDraft { it.copy(paidByUid = uid, payerError = null) }
    fun setPurchaseSettlement(settlement: Settlement) = updateDraft { it.copy(settlement = settlement) }

    /** Legt die echte Ausgabe mit dem tatsächlichen Betrag an und setzt die Planung auf GEKAUFT (eine Transaktion). */
    fun confirmPurchase() {
        val s = _state.value
        val draft = s.purchase ?: return
        val existing = s.existing ?: return
        if (s.isSaving) return
        val checked = draft.copy(
            amountError = BookingValidator.amount(draft.amountText),
            dateError = BookingValidator.date(draft.date),
            categoryError = BookingValidator.category(draft.categoryId),
            payerError = BookingValidator.payer(BookingType.EXPENSE, draft.paidByUid),
        )
        if (listOf(checked.amountError, checked.dateError, checked.categoryError, checked.payerError).any { it != null }) {
            _state.value = s.copy(purchase = checked, saveError = null)
            return
        }
        val input = PurchaseInput(
            actualAmountCents = requireNotNull(Money.parse(draft.amountText)),
            date = draft.date,
            paidByUid = requireNotNull(draft.paidByUid),
            categoryId = requireNotNull(draft.categoryId),
            settlement = draft.settlement,
        )
        run(s.copy(purchase = checked)) { planned.purchase(existing.id, input, uid) }
    }

    fun delete() {
        val existing = _state.value.existing ?: return
        run(_state.value) { planned.delete(existing.id) }
    }

    private fun run(base: PlannedFormState, block: suspend () -> Result<Unit>) {
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
