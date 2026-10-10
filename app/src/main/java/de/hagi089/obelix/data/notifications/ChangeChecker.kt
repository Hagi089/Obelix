package de.hagi089.obelix.data.notifications

/** Liest Änderungen und Benutzernamen (Firestore: [FirestoreChangeSource]). */
interface ChangeSource {
    suspend fun loadSince(since: Stamp): Result<List<ChangeRecord>>
    suspend fun loadNames(): Result<Map<String, String>>
}

/** Lokaler Stand je Benutzer und Schalter (Gerät: [ChangeNotificationPreference]). */
interface ChangeStateStore {
    fun isEnabled(): Boolean
    fun watermark(uid: String): Stamp?
    fun setWatermark(uid: String, stamp: Stamp)
    fun clearWatermark(uid: String)
}

/** Zeigt Benachrichtigungen (Gerät: [AndroidChangePoster]). */
interface ChangePoster {
    /** false, wenn Android Benachrichtigungen nicht erlaubt (Berechtigung fehlt oder vom Benutzer abgeschaltet). */
    fun canPost(): Boolean
    fun post(area: ChangeArea, changes: List<PersonChange>)
}

enum class CheckOutcome { NO_USER, DISABLED, NOT_ALLOWED, STARTED, FAILED, NOTHING_NEW, POSTED }

/**
 * Eine Prüfung (läuft im Hintergrund etwa einmal pro Stunde, siehe ChangeCheckWorker). Liest nur, schreibt nie in
 * Firestore; das Speichern in der App ist davon unberührt.
 */
class ChangeChecker(
    private val source: ChangeSource,
    private val store: ChangeStateStore,
    private val poster: ChangePoster,
    private val now: () -> Stamp,
) {
    suspend fun run(uid: String?): CheckOutcome {
        if (uid == null) return CheckOutcome.NO_USER
        if (!store.isEnabled()) {
            // Beim Wiedereinschalten neu beginnen statt die gesamte Zwischenzeit zu melden.
            store.clearWatermark(uid)
            return CheckOutcome.DISABLED
        }
        if (!poster.canPost()) {
            store.clearWatermark(uid)
            return CheckOutcome.NOT_ALLOWED
        }
        val since = store.watermark(uid) ?: run {
            // Erster Lauf: nur den Startpunkt setzen, keine alte Historie melden.
            store.setWatermark(uid, now())
            return CheckOutcome.STARTED
        }
        // Bei einem Fehler bleibt der Stand stehen; die nächste Prüfung holt alles nach.
        val records = source.loadSince(since).getOrElse { return CheckOutcome.FAILED }
        val changes = ChangeLogic.summarize(records, since, uid)
        if (changes.isNotEmpty()) {
            val names = source.loadNames().getOrDefault(emptyMap())
            changes.forEach { poster.post(it.area, ChangeLogic.personChanges(it, names)) }
        }
        store.setWatermark(uid, ChangeLogic.watermark(records, since))
        return if (changes.isEmpty()) CheckOutcome.NOTHING_NEW else CheckOutcome.POSTED
    }
}
