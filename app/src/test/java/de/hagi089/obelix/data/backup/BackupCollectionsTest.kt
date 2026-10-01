package de.hagi089.obelix.data.backup

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupCollectionsTest {

    private fun rulesText(): String {
        // Unit-Tests laufen im Ordner des Moduls „app“.
        val file = listOf("../firebase/firestore.rules", "firebase/firestore.rules").map(::File).firstOrNull { it.exists() }
        assertTrue("firebase/firestore.rules nicht gefunden (Arbeitsordner: ${File(".").absolutePath})", file != null)
        return file!!.readText()
    }

    /**
     * Wächter: Kommt in den Regeln eine neue Sammlung hinzu, muss sie ins Backup (oder hier bewusst ausgenommen
     * werden). Sonst würde das Backup sie stillschweigend auslassen.
     */
    @Test
    fun everyCollectionInTheRulesIsBackedUp_orDeliberatelyExcluded() {
        val inRules = Regex("""match\s+/([A-Za-z]+)/""").findAll(rulesText()).map { it.groupValues[1] }.toSet()
        // databases = Wurzel der Regeln; config = nur der Zugangscode (Geheimnis); files/chunks = Dateien über ihre Verweise.
        val deliberatelyExcluded = setOf("databases", "config", "files", "chunks")
        assertEquals(inRules - deliberatelyExcluded, BackupCollections.ALL.toSet())
    }

    @Test
    fun collectionsAreUnique() {
        assertEquals(BackupCollections.ALL.size, BackupCollections.ALL.toSet().size)
    }
}
