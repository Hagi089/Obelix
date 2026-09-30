package de.hagi089.obelix

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.MemoryCacheSettings
import de.hagi089.obelix.core.network.ConnectivityNetworkMonitor
import de.hagi089.obelix.core.network.NetworkMonitor
import de.hagi089.obelix.data.auth.AuthRepository
import de.hagi089.obelix.data.auth.FirebaseAuthRepository

/**
 * Einfache manuelle Dependency Injection (bewusst ohne Hilt, siehe docs/PROJEKTPLAN.md).
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    val networkMonitor: NetworkMonitor = ConnectivityNetworkMonitor(appContext)

    /** true, wenn google-services.json beim Build vorhanden war und Firebase initialisiert ist. */
    val isFirebaseConfigured: Boolean = FirebaseApp.getApps(appContext).isNotEmpty()

    val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }

    val authRepository: AuthRepository by lazy { FirebaseAuthRepository(auth) }

    val firestore: FirebaseFirestore by lazy {
        FirebaseFirestore.getInstance().apply {
            // Keine dauerhafte Offline-Persistenz: Änderungen gelten erst nach Server-Bestätigung
            // als gespeichert (Anforderung 8).
            firestoreSettings = FirebaseFirestoreSettings.Builder()
                .setLocalCacheSettings(MemoryCacheSettings.newBuilder().build())
                .build()
        }
    }
}
