package de.hagi089.obelix.data.notifications

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import de.hagi089.obelix.core.util.await
import de.hagi089.obelix.core.util.repositoryCall
import de.hagi089.obelix.data.user.UserRepository

/**
 * Je Sammlung zwei Bereichsabfragen (`createdAt > Stand`, `updatedAt > Stand`), denn neue Einträge tragen nur
 * createdAt. Einfache Bereichsfilter brauchen keinen eigenen Index. Die Regeln erlauben das Lesen jedem
 * freigeschalteten Benutzer (`allow list: if isUser()`); es gibt keine Regeländerung.
 */
class FirestoreChangeSource(
    private val db: FirebaseFirestore,
    private val userRepository: UserRepository,
) : ChangeSource {

    // Zwölf Abfragen nacheinander im Hintergrund (oft mit kaltem Prozess): 20 s Standardgrenze ist zu knapp.
    override suspend fun loadSince(since: Stamp): Result<List<ChangeRecord>> = repositoryCall(TIMEOUT_MS) {
        val after = Timestamp(since.seconds, since.nanos)
        val byKey = linkedMapOf<Pair<String, String>, ChangeRecord>()
        CHANGE_COLLECTIONS.keys.forEach { collection ->
            FIELDS.forEach { field ->
                db.collection(collection)
                    .whereGreaterThan(field, after)
                    .get(Source.SERVER)
                    .await()
                    .documents
                    .forEach { doc -> byKey[collection to doc.id] = doc.toRecord(collection) }
            }
        }
        byKey.values.toList()
    }

    override suspend fun loadNames(): Result<Map<String, String>> =
        userRepository.loadUsers().map { users -> users.associate { it.uid to it.displayName } }

    private fun DocumentSnapshot.toRecord(collection: String) = ChangeRecord(
        collection = collection,
        id = id,
        createdAt = getTimestamp(CREATED_AT)?.toStamp(),
        createdBy = getString("createdBy"),
        updatedAt = getTimestamp(UPDATED_AT)?.toStamp(),
        updatedBy = getString("updatedBy"),
    )

    private fun Timestamp.toStamp() = Stamp(seconds, nanoseconds)

    private companion object {
        const val CREATED_AT = "createdAt"
        const val UPDATED_AT = "updatedAt"
        val FIELDS = listOf(CREATED_AT, UPDATED_AT)
        const val TIMEOUT_MS = 60_000L
    }
}
