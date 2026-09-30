package de.hagi089.obelix.core.error

import android.util.Log
import androidx.annotation.StringRes
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
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
    INVALID_CREDENTIALS(R.string.error_invalid_credentials),
    EMAIL_IN_USE(R.string.error_email_in_use),
    WEAK_PASSWORD(R.string.error_weak_password),
    TOO_MANY_REQUESTS(R.string.error_too_many_requests),
    INVALID_ACCESS_CODE(R.string.error_access_code_invalid),
    /** Der Stand hat sich zwischenzeitlich geändert (z. B. Planung wurde schon gekauft). */
    CONFLICT(R.string.error_conflict),
    /** Eine gespeicherte Datei ist unvollständig (Stücke fehlen oder passen nicht zu den Metadaten). */
    FILE_CORRUPT(R.string.error_file_corrupt),
    UNKNOWN(R.string.error_unknown),
}

/** Bereits abgebildeter Fehler, der durch die Schichten gereicht wird. */
class AppException(val error: AppError, cause: Throwable? = null) : Exception(error.name, cause)

object ErrorMapper {

    private const val TAG = "Obelix"

    fun map(throwable: Throwable): AppError {
        val error = classify(throwable)
        Log.w(TAG, "Fehler abgebildet auf $error", throwable)
        return error
    }

    internal fun classify(throwable: Throwable): AppError = when (throwable) {
        is AppException -> throwable.error
        is FirebaseNetworkException, is IOException -> AppError.NETWORK
        is FirebaseTooManyRequestsException -> AppError.TOO_MANY_REQUESTS
        // Reihenfolge wichtig: WeakPassword ist eine Unterart von InvalidCredentials.
        is FirebaseAuthWeakPasswordException -> AppError.WEAK_PASSWORD
        is FirebaseAuthInvalidCredentialsException -> AppError.INVALID_CREDENTIALS
        is FirebaseAuthUserCollisionException -> AppError.EMAIL_IN_USE
        is FirebaseAuthInvalidUserException -> AppError.UNAUTHENTICATED
        is FirebaseFirestoreException -> mapFirestore(throwable.code)
        // Ein bereits abgebildeter Fehler kann von einer Bibliothek (z. B. Transaktion) umhüllt worden sein.
        else -> (throwable.cause as? AppException)?.error ?: AppError.UNKNOWN
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
