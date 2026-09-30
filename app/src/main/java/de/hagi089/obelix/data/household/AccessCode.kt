package de.hagi089.obelix.data.household

import java.security.SecureRandom

/**
 * Zugangscode: 16 Zeichen ohne leicht verwechselbare (I, L, O, 0, 1), rund 79 Bit.
 * Muss zum Format in firebase/firestore.rules (validCodeFormat) passen.
 */
object AccessCode {
    const val LENGTH = 16
    private const val ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"

    /** Entfernt Leerzeichen und Bindestriche und macht Großbuchstaben (Eingabe darf formatiert sein). */
    fun normalize(input: String): String = input.filter { it.isLetterOrDigit() }.uppercase()

    fun isValid(normalized: String): Boolean =
        normalized.length == LENGTH && normalized.all { it in ALPHABET }

    fun generate(random: SecureRandom = SecureRandom()): String =
        buildString { repeat(LENGTH) { append(ALPHABET[random.nextInt(ALPHABET.length)]) } }

    /** Anzeige in Vierergruppen: XXXX-XXXX-XXXX-XXXX. */
    fun format(code: String): String = code.chunked(4).joinToString("-")
}
