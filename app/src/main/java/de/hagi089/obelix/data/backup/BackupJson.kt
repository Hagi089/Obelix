package de.hagi089.obelix.data.backup

import com.google.firebase.Timestamp
import com.google.firebase.firestore.Blob
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.GeoPoint
import java.time.Instant
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Ein gelesenes Firestore-Dokument, noch unabhängig vom Firestore-Client (damit die Archivierung testbar bleibt). */
class BackupDocument(val collection: String, val id: String, val fields: Map<String, Any?>)

/** Wandelt Firestore-Werte in JSON um. Die Ausgabe ist deterministisch (Felder nach Name sortiert). */
object BackupJson {

    /** `{"id": "...", "data": {...}}`; Felder aus [BackupCollections.excludedFields] fehlen. */
    fun document(doc: BackupDocument): JsonObject {
        val dropped = BackupCollections.excludedFields(doc.collection)
        val data = doc.fields
            .filterKeys { it !in dropped }
            .toSortedMap()
            .mapValues { value(it.value) }
        return JsonObject(mapOf("id" to JsonPrimitive(doc.id), "data" to JsonObject(data)))
    }

    fun value(raw: Any?): JsonElement = when (raw) {
        null -> JsonNull
        is String -> JsonPrimitive(raw)
        is Boolean -> JsonPrimitive(raw)
        is Number -> JsonPrimitive(raw)
        // Zeitpunkte als ISO-8601 in UTC, z. B. 2026-10-01T12:00:00Z.
        is Timestamp -> JsonPrimitive(Instant.ofEpochSecond(raw.seconds, raw.nanoseconds.toLong()).toString())
        is GeoPoint -> JsonObject(mapOf("latitude" to JsonPrimitive(raw.latitude), "longitude" to JsonPrimitive(raw.longitude)))
        is DocumentReference -> JsonPrimitive(raw.path)
        // Binärdaten stehen nie in den gesicherten Dokumenten (Dateien liegen in files/…/chunks); der Platzhalter
        // verhindert nur einen Absturz, falls das Datenmodell einmal anders wird.
        is Blob -> JsonPrimitive("<Binärdaten, ${raw.toBytes().size} Byte>")
        is Map<*, *> -> JsonObject(raw.entries.associate { it.key.toString() to value(it.value) })
        is Iterable<*> -> JsonArray(raw.map(::value))
        else -> JsonPrimitive(raw.toString())
    }
}
