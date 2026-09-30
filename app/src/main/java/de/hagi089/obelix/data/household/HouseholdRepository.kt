package de.hagi089.obelix.data.household

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
 * Haushalt, Mitglieder und Zugangscodes. Alle Operationen liefern bei Fehlern eine [AppException].
 * Die Berechtigungen setzen die Firestore-Regeln durch (firebase/firestore.rules); diese Klasse
 * schreibt nur Daten, die die Regeln erlauben.
 */
interface HouseholdRepository {
    /** null, wenn der Benutzer noch keinem Haushalt angehört. */
    suspend fun loadMembership(uid: String): Result<Membership?>

    /** Prüft einen normalisierten Code. Ungültig, benutzt oder abgelaufen: [AppError.INVALID_ACCESS_CODE]. */
    suspend fun lookupCode(code: String): Result<InviteInfo>

    /** Beitritt mit dem gemeinsamen Haushalts-Code (Rolle MEMBER). */
    suspend fun join(uid: String, displayName: String, invite: InviteInfo, partyId: String): Result<Unit>

    /** Haushalt anlegen mit dem Start-Code; der Gründer wird ADMIN. */
    suspend fun createHousehold(
        uid: String,
        displayName: String,
        invite: InviteInfo,
        householdName: String,
        partyNames: List<String>,
        ownPartyId: String,
    ): Result<Unit>

    suspend fun loadMembers(householdId: String): Result<List<Member>>

    /** Nur ADMIN (Regeln). */
    suspend fun updateMember(householdId: String, memberUid: String, role: Role, partyId: String): Result<Unit>

    /** Entfernt das Mitglied samt Zeiger. Nur ADMIN (Regeln). */
    suspend fun removeMember(householdId: String, memberUid: String): Result<Unit>

    /** Erzeugt einen neuen gemeinsamen Code; der bisherige wird ungültig. Nur ADMIN (Regeln). */
    suspend fun renewInviteCode(uid: String, household: Household): Result<String>
}

class FirestoreHouseholdRepository(private val db: FirebaseFirestore) : HouseholdRepository {

    override suspend fun loadMembership(uid: String): Result<Membership?> = call {
        val userDoc = db.collection(USERS).document(uid).get(Source.SERVER).await()
        val householdId = userDoc.getString("householdId") ?: return@call null
        val householdRef = db.collection(HOUSEHOLDS).document(householdId)
        val householdDoc = householdRef.get(Source.SERVER).await()
        val memberDoc = householdRef.collection(MEMBERS).document(uid).get(Source.SERVER).await()
        val household = householdDoc.toHousehold() ?: return@call null
        val member = memberDoc.toMember() ?: return@call null
        Membership(household, member)
    }

    override suspend fun lookupCode(code: String): Result<InviteInfo> = call {
        val doc = db.collection(INVITES).document(code).get(Source.SERVER).await()
        if (!doc.exists()) throw AppException(AppError.INVALID_ACCESS_CODE)
        val type = InviteType.entries.firstOrNull { it.name == doc.getString("type") }
            ?: throw AppException(AppError.INVALID_ACCESS_CODE)
        if (type == InviteType.CREATE_HOUSEHOLD) {
            // Start-Code: einmalig und optional befristet (die Regeln prüfen dasselbe verbindlich).
            val used = doc.getString("usedBy") != null
            val expired = doc.getTimestamp("expiresAt")?.let { it.toDate().time < System.currentTimeMillis() } ?: false
            if (used || expired) throw AppException(AppError.INVALID_ACCESS_CODE)
        }
        InviteInfo(code, type, doc.getString("householdId"), parseParties(doc.get("parties")))
    }

    override suspend fun join(uid: String, displayName: String, invite: InviteInfo, partyId: String): Result<Unit> = call {
        val householdId = invite.householdId ?: throw AppException(AppError.INVALID_ACCESS_CODE)
        deniedMeansInvalidCode {
            write { tx ->
                tx.set(
                    db.collection(HOUSEHOLDS).document(householdId).collection(MEMBERS).document(uid),
                    mapOf(
                        "role" to Role.MEMBER.name,
                        "partyId" to partyId,
                        "displayName" to displayName,
                        "inviteCode" to invite.code,
                        "createdAt" to FieldValue.serverTimestamp(),
                    ),
                )
                tx.set(db.collection(USERS).document(uid), userPointer(householdId, displayName))
            }
        }
    }

    override suspend fun createHousehold(
        uid: String,
        displayName: String,
        invite: InviteInfo,
        householdName: String,
        partyNames: List<String>,
        ownPartyId: String,
    ): Result<Unit> = call {
        val householdRef = db.collection(HOUSEHOLDS).document() // zufällige ID
        deniedMeansInvalidCode {
            write { tx ->
                tx.set(
                    householdRef,
                    mapOf(
                        "name" to householdName,
                        "parties" to listOf(
                            mapOf("id" to PARTY_A, "name" to partyNames[0]),
                            mapOf("id" to PARTY_B, "name" to partyNames[1]),
                        ),
                        "inviteCode" to null,
                        "createdBy" to uid,
                        "createdAt" to FieldValue.serverTimestamp(),
                    ),
                )
                tx.set(
                    householdRef.collection(MEMBERS).document(uid),
                    mapOf(
                        "role" to Role.ADMIN.name,
                        "partyId" to ownPartyId,
                        "displayName" to displayName,
                        "inviteCode" to invite.code,
                        "createdAt" to FieldValue.serverTimestamp(),
                    ),
                )
                tx.set(db.collection(USERS).document(uid), userPointer(householdRef.id, displayName))
                tx.update(
                    db.collection(INVITES).document(invite.code),
                    mapOf(
                        "usedBy" to uid,
                        "usedAt" to FieldValue.serverTimestamp(),
                        "householdId" to householdRef.id,
                    ),
                )
            }
        }
    }

    override suspend fun loadMembers(householdId: String): Result<List<Member>> = call {
        db.collection(HOUSEHOLDS).document(householdId).collection(MEMBERS)
            .get(Source.SERVER).await()
            .documents.mapNotNull { it.toMember() }
            .sortedBy { it.displayName.lowercase() }
    }

    override suspend fun updateMember(householdId: String, memberUid: String, role: Role, partyId: String): Result<Unit> = call {
        write { tx ->
            tx.update(
                db.collection(HOUSEHOLDS).document(householdId).collection(MEMBERS).document(memberUid),
                mapOf("role" to role.name, "partyId" to partyId),
            )
        }
    }

    override suspend fun removeMember(householdId: String, memberUid: String): Result<Unit> = call {
        write { tx ->
            tx.delete(db.collection(HOUSEHOLDS).document(householdId).collection(MEMBERS).document(memberUid))
            tx.delete(db.collection(USERS).document(memberUid))
        }
    }

    override suspend fun renewInviteCode(uid: String, household: Household): Result<String> = call {
        val newCode = AccessCode.generate()
        write { tx ->
            tx.set(
                db.collection(INVITES).document(newCode),
                mapOf(
                    "type" to InviteType.JOIN.name,
                    "householdId" to household.id,
                    "parties" to household.parties.map { mapOf("id" to it.id, "name" to it.name) },
                    "createdBy" to uid,
                    "createdAt" to FieldValue.serverTimestamp(),
                ),
            )
            tx.update(db.collection(HOUSEHOLDS).document(household.id), "inviteCode", newCode)
            household.inviteCode?.let { tx.delete(db.collection(INVITES).document(it)) }
        }
        newCode
    }

    // ---------- Hilfen ----------

    /**
     * Schreibt mehrere Dokumente atomar in einer Transaktion. Anders als ein Batch braucht sie eine
     * Verbindung zum Server und meldet sonst einen Fehler, statt lokal „erfolgreich" zu sein
     * (Anforderung 8).
     */
    private suspend fun write(block: (Transaction) -> Unit) {
        db.runTransaction(Transaction.Function<Unit> { tx -> block(tx) }).await()
    }

    /** Die Regeln lehnen ungültige, widerrufene oder benutzte Codes mit „Berechtigung fehlt" ab. */
    private suspend fun deniedMeansInvalidCode(block: suspend () -> Unit) {
        try {
            block()
        } catch (e: FirebaseFirestoreException) {
            if (e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                throw AppException(AppError.INVALID_ACCESS_CODE, e)
            }
            throw e
        }
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

    private fun userPointer(householdId: String, displayName: String): Map<String, Any?> = mapOf(
        "householdId" to householdId,
        "displayName" to displayName,
        "createdAt" to FieldValue.serverTimestamp(),
    )

    private fun DocumentSnapshot.toHousehold(): Household? {
        if (!exists()) return null
        return Household(
            id = id,
            name = getString("name") ?: return null,
            parties = parseParties(get("parties")),
            inviteCode = getString("inviteCode"),
        )
    }

    private fun DocumentSnapshot.toMember(): Member? {
        if (!exists()) return null
        return Member(
            uid = id,
            role = Role.from(getString("role")) ?: return null,
            partyId = getString("partyId") ?: return null,
            displayName = getString("displayName") ?: return null,
        )
    }

    private fun parseParties(raw: Any?): List<Party> = (raw as? List<*>).orEmpty().mapNotNull { item ->
        val map = item as? Map<*, *> ?: return@mapNotNull null
        val id = map["id"] as? String ?: return@mapNotNull null
        Party(id, map["name"] as? String ?: id)
    }

    private companion object {
        const val USERS = "users"
        const val HOUSEHOLDS = "households"
        const val MEMBERS = "members"
        const val INVITES = "invites"
        const val TIMEOUT_MS = 20_000L
    }
}
