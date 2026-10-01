package de.hagi089.obelix.data.user

import android.util.Log
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.Transaction
import de.hagi089.obelix.core.error.AppError
import de.hagi089.obelix.core.error.AppException
import de.hagi089.obelix.core.error.ErrorMapper
import de.hagi089.obelix.core.util.await
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

/**
 * Benutzer, Rollen und Zugangscode. Alle Operationen liefern bei Fehlern eine [AppException].
 * Die Berechtigungen setzen die Firestore-Regeln durch (firebase/firestore.rules).
 */
interface UserRepository {
    /** null, wenn es zu diesem Konto noch kein Benutzerdokument gibt (Zugangscode noch nicht eingelöst). */
    suspend fun loadProfile(uid: String): Result<UserProfile?>

    /** Legt das Benutzerdokument an. Ein falscher Code wird vom Server abgelehnt: [AppError.INVALID_ACCESS_CODE]. */
    suspend fun register(uid: String, displayName: String, accessCode: String): Result<Unit>

    suspend fun loadUsers(): Result<List<UserProfile>>

    /** Nur ADMIN (Regeln). */
    suspend fun setRole(uid: String, role: Role): Result<Unit>

    /** Nur ADMIN (Regeln). Das Konto in Firebase Auth bleibt bestehen, hat aber keinen Datenzugriff mehr. */
    suspend fun removeUser(uid: String): Result<Unit>

    /** Nur ADMIN (Regeln). null = noch kein Code angelegt. */
    suspend fun loadAccessCode(): Result<String?>

    /** Nur ADMIN (Regeln). Der bisherige Code wird sofort ungültig. */
    suspend fun renewAccessCode(adminUid: String): Result<String>
}

class FirestoreUserRepository(private val db: FirebaseFirestore) : UserRepository {

    private val users get() = db.collection(USERS)
    private val accessDoc get() = db.collection(CONFIG).document(ACCESS)

    override suspend fun loadProfile(uid: String): Result<UserProfile?> = call {
        val snapshot = users.document(uid).get(Source.SERVER).await()
        removeStoredAccessCode(snapshot)
        snapshot.toProfile()
    }

    /**
     * Die Registrierung muss den Zugangscode mitschicken (die Regeln vergleichen ihn). Weil alle Benutzer die
     * Benutzerdokumente lesen dürfen, der aktuelle Code aber nur für den ADMIN sichtbar sein soll, entfernt die App
     * das Feld gleich beim nächsten Laden des eigenen Profils (direkt nach der Registrierung und bei älteren Konten
     * beim ersten Start dieser Version). Nebenbei, ohne zu warten: Scheitert es, wird es beim nächsten Laden erneut
     * versucht; die Anmeldung hängt nie davon ab.
     */
    private fun removeStoredAccessCode(snapshot: DocumentSnapshot) {
        if (!snapshot.exists() || !snapshot.contains(ACCESS_CODE_FIELD)) return
        snapshot.reference.update(ACCESS_CODE_FIELD, FieldValue.delete())
            .addOnFailureListener { Log.w(TAG, "Zugangscode im Benutzerdokument konnte nicht entfernt werden", it) }
    }

    override suspend fun register(uid: String, displayName: String, accessCode: String): Result<Unit> = call {
        try {
            write { tx ->
                tx.set(
                    users.document(uid),
                    mapOf(
                        "displayName" to displayName,
                        "role" to Role.MEMBER.name,
                        ACCESS_CODE_FIELD to accessCode,
                        "createdAt" to FieldValue.serverTimestamp(),
                    ),
                )
            }
        } catch (e: FirebaseFirestoreException) {
            // Die Regeln lehnen einen falschen oder erneuerten Code mit „Berechtigung fehlt" ab.
            if (e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                throw AppException(AppError.INVALID_ACCESS_CODE, e)
            }
            throw e
        }
    }

    override suspend fun loadUsers(): Result<List<UserProfile>> = call {
        users.get(Source.SERVER).await().documents
            .mapNotNull { it.toProfile() }
            .sortedBy { it.displayName.lowercase() }
    }

    override suspend fun setRole(uid: String, role: Role): Result<Unit> = call {
        write { tx -> tx.update(users.document(uid), "role", role.name) }
    }

    override suspend fun removeUser(uid: String): Result<Unit> = call {
        write { tx -> tx.delete(users.document(uid)) }
    }

    override suspend fun loadAccessCode(): Result<String?> = call {
        accessDoc.get(Source.SERVER).await().getString("code")
    }

    override suspend fun renewAccessCode(adminUid: String): Result<String> = call {
        val code = AccessCode.generate()
        write { tx ->
            tx.set(
                accessDoc,
                mapOf("code" to code, "updatedBy" to adminUid, "updatedAt" to FieldValue.serverTimestamp()),
            )
        }
        code
    }

    /**
     * Schreibt in einer Transaktion. Anders als ein einfacher Schreibvorgang braucht sie den Server
     * und meldet sonst einen Fehler, statt lokal „erfolgreich" zu sein (Anforderung 8).
     */
    private suspend fun write(block: (Transaction) -> Unit) {
        db.runTransaction(Transaction.Function<Unit> { tx -> block(tx) }).await()
    }

    private suspend fun <T> call(block: suspend () -> T): Result<T> = try {
        Result.success(withTimeout(TIMEOUT_MS) { block() })
    } catch (e: TimeoutCancellationException) {
        Result.failure(AppException(AppError.NETWORK, e))
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(AppException(ErrorMapper.map(e), e))
    }

    private fun DocumentSnapshot.toProfile(): UserProfile? {
        if (!exists()) return null
        return UserProfile(
            uid = id,
            displayName = getString("displayName") ?: return null,
            role = Role.from(getString("role")) ?: return null,
        )
    }

    private companion object {
        const val USERS = "users"
        const val CONFIG = "config"
        const val ACCESS = "access"
        const val TIMEOUT_MS = 20_000L
        const val ACCESS_CODE_FIELD = "accessCode"
        const val TAG = "Obelix"
    }
}
