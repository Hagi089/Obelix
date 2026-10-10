package de.hagi089.obelix

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.hagi089.obelix.data.notifications.ChangeArea
import de.hagi089.obelix.notifications.AndroidChangePoster
import de.hagi089.obelix.ui.ObelixApp
import de.hagi089.obelix.ui.theme.ObelixTheme
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {

    /** Bereich, der nach dem Antippen einer Benachrichtigung geöffnet werden soll (einmalig, danach null). */
    private val openArea = MutableStateFlow<ChangeArea?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Nur beim ersten Start, nicht nach Drehen des Bildschirms erneut springen.
        if (savedInstanceState == null) openArea.value = AndroidChangePoster.areaFrom(intent)
        val container = (application as ObelixApplication).container
        setContent {
            val area by openArea.collectAsStateWithLifecycle()
            val chosen by container.themePreference.darkMode.collectAsStateWithLifecycle()
            val dark = chosen ?: isSystemInDarkTheme()
            // Symbole der Systemleisten passend zur gewählten (nicht zur System-)Darstellung.
            DisposableEffect(dark) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                    navigationBarStyle = SystemBarStyle.auto(
                        Color.argb(0xE6, 0xFF, 0xFF, 0xFF),
                        Color.argb(0x80, 0x1B, 0x1B, 0x1B),
                    ) { dark },
                )
                onDispose { }
            }
            ObelixTheme(darkTheme = dark) {
                ObelixApp(container, openArea = area, onAreaOpened = { openArea.value = null })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        AndroidChangePoster.areaFrom(intent)?.let { openArea.value = it }
    }
}
