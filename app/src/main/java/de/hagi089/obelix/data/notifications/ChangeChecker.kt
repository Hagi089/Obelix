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

    /** Merkt sich Zeitpunkt und Ergebnis der letzten Prüfung (Anzeige in den Einstellungen, Hilfe beim Testen). */
    fun recordCheck(outcome: CheckOutcome)
}

/** Zeigt Benachrichtigungen (Gerät: [AndroidChangePoster]). */
interface ChangePoster {
    /** false, wenn Android Benachrichtigungen nicht erlaubt (Berechtigung fehlt oder vom Benutzer abgeschaltet). */
    fun canPost(): Boolean
    fun post(area: ChangeArea, changes: List<PersonChange>)
}

enum class CheckOutcome { NO_USER, DISABLED, NOT_ALLOWED, STARTED, FAILED, NOTHING_NEW, POSTED }

/**
 * Eine Prüfung (läuft im Hintergrund regelmäßig, siehe ChangeCheckWorker). Liest nur, schreibt nie in Firestore; das
 * Speichern in der App ist davon unberührt.
 */
class ChangeChecker(
    private val source: ChangeSource,
    private val store: ChangeStateStore,
    private val poster: ChangePoster,
    private val now: () -> Stamp,
) {
    suspend fun run(uid: String?): CheckOutcome = check(uid).also(store::recordCheck)

    /**
     * Setzt den Startpunkt, falls noch keiner besteht. Aufgerufen, sobald die App nach der Anmeldung die Prüfung
     * einplant: Änderungen ab diesem Moment werden gemeldet, auch wenn Android die erste Hintergrundprüfung verzögert.
     */
    fun ensureStart(uid: String) {
        if (store.watermark(uid) == null) store.setWatermark(uid, now())
    }

    private suspend fun check(uid: String?): CheckOutcome {
        if (uid == null) return CheckOutcome.NO_USER
        if (!store.isEnabled()) {
            // Beim Wiedereinschalten neu beginnen statt die gesamte Zwischenzeit zu melden.
            store.clearWatermark(uid)
            return CheckOutcome.DISABLED
        }
        // Fehler in Version 26: Hier wurde der Stand gelöscht. Die erste Prüfung läuft aber meist, während der
        // Erlaubnis-Dialog noch offen ist; dadurch gingen alle Änderungen bis zur übernächsten Prüfung verloren.
        // Jetzt bleibt der Stand; sobald Android Benachrichtigungen erlaubt, wird alles seitdem gemeldet.
        if (!poster.canPost()) return CheckOutcome.NOT_ALLOWED
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
