package de.hagi089.obelix.core.util

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Transaction
import de.hagi089.obelix.core.error.AppError
import de.hagi089.obelix.core.error.AppException
import de.hagi089.obelix.core.error.ErrorMapper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

const val DEFAULT_TIMEOUT_MS = 20_000L

/** Führt einen Repository-Aufruf mit Zeitlimit aus und bildet Fehler auf [AppException] ab (Anforderung 40). */
suspend fun <T> repositoryCall(timeoutMs: Long = DEFAULT_TIMEOUT_MS, block: suspend () -> T): Result<T> = try {
    Result.success(withTimeout(timeoutMs) { block() })
} catch (e: TimeoutCancellationException) {
    Result.failure(AppException(AppError.NETWORK, e))
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    Result.failure(AppException(ErrorMapper.map(e), e))
}

/**
 * Schreibt in einer Transaktion. Anders als ein einfacher Schreibvorgang braucht sie den Server
 * und meldet sonst einen Fehler, statt lokal „erfolgreich" zu sein (Anforderung 8).
 */
suspend fun FirebaseFirestore.writeTransaction(block: (Transaction) -> Unit) {
    runTransaction(Transaction.Function<Unit> { tx -> block(tx) }).await()
}
