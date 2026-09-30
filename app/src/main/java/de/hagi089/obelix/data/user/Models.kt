package de.hagi089.obelix.data.user

/** Rolle eines Benutzers. Serverseitig durch die Firestore-Regeln abgesichert. */
enum class Role {
    ADMIN,
    MEMBER,
    ;

    companion object {
        fun from(value: String?): Role? = entries.firstOrNull { it.name == value }
    }
}

/** Benutzer der App (Firestore users/{uid}). Der Name erscheint z. B. bei „Bezahlt von". */
data class UserProfile(val uid: String, val displayName: String, val role: Role) {
    val isAdmin: Boolean get() = role == Role.ADMIN
}
