package de.hagi089.obelix.data.auth

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.UserProfileChangeRequest
import de.hagi089.obelix.core.error.AppError
import de.hagi089.obelix.core.error.AppException
import de.hagi089.obelix.core.error.ErrorMapper
import de.hagi089.obelix.core.util.await
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/** Angemeldeter Benutzer (nur das, was die App aus Firebase Auth braucht). */
data class AuthUser(val uid: String, val email: String?, val displayName: String?)

/** Alle Operationen liefern bei Fehlern eine [AppException] mit verständlicher Fehlerart. */
interface AuthRepository {
    /** Emittiert sofort den aktuellen Zustand und danach jede Änderung (null = abgemeldet). */
    val authState: Flow<AuthUser?>

    /** Aktueller Stand direkt aus Firebase Auth (der Anzeigename ist nach der Registrierung sofort gesetzt). */
    val currentUser: AuthUser?

    suspend fun signIn(email: String, password: String): Result<Unit>
    suspend fun register(name: String, email: String, password: String): Result<Unit>
    suspend fun sendPasswordReset(email: String): Result<Unit>
    /** Löscht das angemeldete Konto (z. B. wenn bei der Registrierung der Zugangscode ungültig war). */
    suspend fun deleteAccount(): Result<Unit>

    fun signOut()
}

class FirebaseAuthRepository(private val auth: FirebaseAuth) : AuthRepository {

    override val authState: Flow<AuthUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            trySend(firebaseAuth.currentUser?.let { AuthUser(it.uid, it.email, it.displayName) })
        }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    override val currentUser: AuthUser?
        get() = auth.currentUser?.let { AuthUser(it.uid, it.email, it.displayName) }

    override suspend fun signIn(email: String, password: String): Result<Unit> = call {
        try {
            auth.signInWithEmailAndPassword(email.trim(), password).await()
        } catch (e: FirebaseAuthInvalidUserException) {
            // Kein Hinweis, ob es die E-Mail-Adresse gibt: gleiche Meldung wie bei falschem Passwort.
            throw AppException(AppError.INVALID_CREDENTIALS, e)
        }
    }

    override suspend fun register(name: String, email: String, password: String): Result<Unit> = call {
        val result = auth.createUserWithEmailAndPassword(email.trim(), password).await()
        // Der Anzeigename wird später (Phase 3) zusätzlich im Haushalt gespeichert. Scheitert das
        // Setzen hier, ist das Konto trotzdem angelegt und der Benutzer angemeldet.
        try {
            val request = UserProfileChangeRequest.Builder().setDisplayName(name.trim()).build()
            result.user?.updateProfile(request)?.await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ErrorMapper.map(e)
        }
    }

    override suspend fun sendPasswordReset(email: String): Result<Unit> = call {
        auth.sendPasswordResetEmail(email.trim()).await()
    }

    override suspend fun deleteAccount(): Result<Unit> = call {
        auth.currentUser?.delete()?.await()
    }

    override fun signOut() = auth.signOut()

    private suspend fun call(block: suspend () -> Unit): Result<Unit> = try {
        block()
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(AppException(ErrorMapper.map(e), e))
    }
}
