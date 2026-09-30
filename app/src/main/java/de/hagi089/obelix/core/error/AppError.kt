package de.hagi089.obelix.core.error

import android.util.Log
import androidx.annotation.StringRes
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.firestore.FirebaseFirestoreException
import de.hagi089.obelix.R
import java.io.IOException

/**
 * Fachliche Fehlerarten mit verständlicher deutscher Meldung (Anforderung 40).
 * Technische Details gehen nur ins Log, nie in die Oberfläche.
 */
enum class AppError(@param:StringRes val messageRes: Int) {
    NETWORK(R.string.error_network),
    PERMISSION_DENIED(R.string.error_permission_denied),
    NOT_FOUND(R.string.error_not_found),
    UNAUTHENTICATED(R.string.error_unauthenticated),
    UNAVAILABLE(R.string.error_unavailable),
    UNKNOWN(R.string.error_unknown),
}

object ErrorMapper {

    private const val TAG = "Obelix"

    fun map(throwable: Throwable): AppError {
        val error = when (throwable) {
            is FirebaseNetworkException, is IOException -> AppError.NETWORK
            is FirebaseAuthInvalidUserException -> AppError.UNAUTHENTICATED
            is FirebaseFirestoreException -> mapFirestore(throwable.code)
            else -> AppError.UNKNOWN
        }
        Log.w(TAG, "Fehler abgebildet auf $error", throwable)
        return error
    }

    internal fun mapFirestore(code: FirebaseFirestoreException.Code): AppError = when (code) {
        FirebaseFirestoreException.Code.PERMISSION_DENIED -> AppError.PERMISSION_DENIED
        FirebaseFirestoreException.Code.NOT_FOUND -> AppError.NOT_FOUND
        FirebaseFirestoreException.Code.UNAUTHENTICATED -> AppError.UNAUTHENTICATED
        FirebaseFirestoreException.Code.UNAVAILABLE,
        FirebaseFirestoreException.Code.DEADLINE_EXCEEDED,
        -> AppError.UNAVAILABLE
        else -> AppError.UNKNOWN
    }
}
