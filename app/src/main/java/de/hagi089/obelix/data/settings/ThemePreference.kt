package de.hagi089.obelix.data.settings

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Lokale Einstellung Hell-/Dunkelmodus (nur auf diesem Gerät, kein Firestore).
 * `null` = noch nie gewählt, dann gilt die Systemeinstellung.
 */
class ThemePreference(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    private val _darkMode = MutableStateFlow(read())
    val darkMode: StateFlow<Boolean?> = _darkMode.asStateFlow()

    fun setDarkMode(dark: Boolean) {
        prefs.edit().putBoolean(KEY_DARK, dark).apply()
        _darkMode.value = dark
    }

    private fun read(): Boolean? = if (prefs.contains(KEY_DARK)) prefs.getBoolean(KEY_DARK, false) else null

    private companion object {
        const val FILE = "obelix_settings"
        const val KEY_DARK = "dark_mode"
    }
}
