package de.hagi089.obelix

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import de.hagi089.obelix.ui.ObelixApp
import de.hagi089.obelix.ui.theme.ObelixTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val container = (application as ObelixApplication).container
        setContent {
            ObelixTheme {
                ObelixApp(container)
            }
        }
    }
}
