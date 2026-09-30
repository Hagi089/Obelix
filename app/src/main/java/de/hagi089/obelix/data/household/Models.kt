package de.hagi089.obelix.data.household

/** Rolle eines Mitglieds im Haushalt. Serverseitig durch die Firestore-Regeln abgesichert. */
enum class Role {
    ADMIN,
    MEMBER,
    ;

    companion object {
        fun from(value: String?): Role? = entries.firstOrNull { it.name == value }
    }
}

/** Eine der zwei Parteien des Haushalts. Die IDs sind fest: [PARTY_A] und [PARTY_B]. */
data class Party(val id: String, val name: String)

const val PARTY_A = "A"
const val PARTY_B = "B"

data class Household(
    val id: String,
    val name: String,
    val parties: List<Party>,
    /** Aktiver gemeinsamer Zugangscode; null, solange der ADMIN noch keinen erzeugt hat. */
    val inviteCode: String?,
)

data class Member(val uid: String, val role: Role, val partyId: String, val displayName: String)

/** Haushalt und Mitgliedschaft des angemeldeten Benutzers. */
data class Membership(val household: Household, val member: Member) {
    val isAdmin: Boolean get() = member.role == Role.ADMIN

    fun partyName(partyId: String): String =
        household.parties.firstOrNull { it.id == partyId }?.name ?: partyId
}

enum class InviteType { JOIN, CREATE_HOUSEHOLD }

/** Ergebnis der Code-Prüfung: was der Code erlaubt (Beitritt oder Haushalt anlegen). */
data class InviteInfo(
    val code: String,
    val type: InviteType,
    val householdId: String?,
    val parties: List<Party>,
)
