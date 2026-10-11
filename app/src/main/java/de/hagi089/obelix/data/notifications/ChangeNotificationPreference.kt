package de.hagi089.obelix.data.notifications

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Lokale Einstellungen der Änderungs-Benachrichtigungen (nur auf diesem Gerät, kein Firestore, nicht im Backup):
 * Schalter (Standard: an), ob schon einmal um die Erlaubnis gefragt wurde, und der zuletzt geprüfte Stand je Benutzer.
 */
class ChangeNotificationPreference(context: Context) : ChangeStateStore {

    private val prefs = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    private val _enabled = MutableStateFlow(prefs.getBoolean(KEY_ENABLED, true))
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    fun setEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply()
        _enabled.value = enabled
    }

    val permissionAsked: Boolean get() = prefs.getBoolean(KEY_PERMISSION_ASKED, false)

    fun markPermissionAsked() {
        prefs.edit().putBoolean(KEY_PERMISSION_ASKED, true).apply()
    }

    override fun isEnabled(): Boolean = prefs.getBoolean(KEY_ENABLED, true)

    override fun watermark(uid: String): Stamp? = Stamp.decode(prefs.getString(KEY_WATERMARK + uid, null))

    override fun setWatermark(uid: String, stamp: Stamp) {
        prefs.edit().putString(KEY_WATERMARK + uid, stamp.encode()).apply()
    }

    override fun clearWatermark(uid: String) {
        prefs.edit().remove(KEY_WATERMARK + uid).apply()
    }

    override fun recordCheck(outcome: CheckOutcome) {
        prefs.edit()
            .putLong(KEY_LAST_CHECK_AT, System.currentTimeMillis())
            .putString(KEY_LAST_CHECK_OUTCOME, outcome.name)
            .apply()
    }

    /** Letzte Prüfung (Gerätezeit in Millisekunden, Ergebnis) oder null, wenn noch keine lief. */
    fun lastCheck(): Pair<Long, CheckOutcome>? {
        val at = prefs.getLong(KEY_LAST_CHECK_AT, 0L).takeIf { it > 0L } ?: return null
        val outcome = prefs.getString(KEY_LAST_CHECK_OUTCOME, null)
            ?.let { name -> CheckOutcome.entries.firstOrNull { it.name == name } } ?: return null
        return at to outcome
    }

    /**
     * Beim Abmelden und beim Ausschalten: Nach der nächsten Anmeldung bzw. dem Einschalten beginnt die Prüfung neu
     * (keine Meldungen über die Zwischenzeit).
     */
    fun clearAllWatermarks() {
        val editor = prefs.edit()
        prefs.all.keys.filter { it.startsWith(KEY_WATERMARK) }.forEach { editor.remove(it) }
        editor.apply()
    }

    private companion object {
        const val FILE = "obelix_change_notifications"
        const val KEY_ENABLED = "enabled"
        const val KEY_PERMISSION_ASKED = "permission_asked"
        const val KEY_WATERMARK = "watermark_"
        const val KEY_LAST_CHECK_AT = "last_check_at"
        const val KEY_LAST_CHECK_OUTCOME = "last_check_outcome"
    }
}
