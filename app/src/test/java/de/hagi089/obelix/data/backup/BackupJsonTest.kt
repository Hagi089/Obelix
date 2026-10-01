package de.hagi089.obelix.data.backup

import com.google.firebase.Timestamp
import com.google.firebase.firestore.GeoPoint
import java.time.Instant
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupJsonTest {

    @Test
    fun primitives_keepTheirType() {
        assertEquals(JsonNull, BackupJson.value(null))
        assertEquals(JsonPrimitive("Text"), BackupJson.value("Text"))
        assertEquals(JsonPrimitive(true), BackupJson.value(true))
        assertEquals(JsonPrimitive(12345L), BackupJson.value(12345L))
        assertEquals(JsonPrimitive(1.5), BackupJson.value(1.5))
    }

    @Test
    fun timestamp_isIsoInUtc() {
        val seconds = Instant.parse("2026-10-01T12:00:00Z").epochSecond
        assertEquals(JsonPrimitive("2026-10-01T12:00:00Z"), BackupJson.value(Timestamp(seconds, 0)))
    }

    @Test
    fun geoPoint_becomesObject() {
        val json = BackupJson.value(GeoPoint(48.5, 11.25)) as JsonObject
        assertEquals(JsonPrimitive(48.5), json["latitude"])
        assertEquals(JsonPrimitive(11.25), json["longitude"])
    }

    @Test
    fun nestedMapsAndLists_areConverted() {
        val json = BackupJson.value(
            mapOf("receipt" to mapOf("fileId" to "f1", "sizeBytes" to 10L), "photos" to listOf("a", "b")),
        ) as JsonObject
        assertEquals(JsonPrimitive("f1"), (json["receipt"] as JsonObject)["fileId"])
        assertEquals(JsonArray(listOf(JsonPrimitive("a"), JsonPrimitive("b"))), json["photos"])
    }

    @Test
    fun unknownType_fallsBackToText() {
        assertEquals(JsonPrimitive("abc"), BackupJson.value(StringBuilder("abc")))
    }

    @Test
    fun document_hasIdAndSortedData() {
        val doc = BackupJson.document(BackupDocument("transactions", "t1", mapOf("b" to 1L, "a" to 2L)))
        assertEquals(JsonPrimitive("t1"), doc["id"])
        assertEquals(listOf("a", "b"), (doc["data"] as JsonObject).keys.toList())
    }

    @Test
    fun users_neverContainTheAccessCode() {
        val doc = BackupJson.document(
            BackupDocument("users", "u1", mapOf("displayName" to "Anna", "role" to "ADMIN", "accessCode" to "ABCDEFGHJKMNPQRS")),
        )
        val data = doc["data"] as JsonObject
        assertFalse(data.containsKey("accessCode"))
        assertTrue(data.containsKey("displayName"))
        assertTrue(data.containsKey("role"))
    }

    @Test
    fun accessCodeIsOnlyDroppedFromUsers() {
        // Nur Benutzerdokumente tragen den Registrierungscode; in anderen Sammlungen wird nichts verschwiegen.
        val doc = BackupJson.document(BackupDocument("repairs", "r1", mapOf("accessCode" to "x")))
        assertTrue((doc["data"] as JsonObject).containsKey("accessCode"))
    }
}
