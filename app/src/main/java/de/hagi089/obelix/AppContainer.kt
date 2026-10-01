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
import de.hagi089.obelix.data.auth.RegistrationHandoff
import de.hagi089.obelix.data.backup.BackupRepository
import de.hagi089.obelix.data.backup.FirestoreBackupRepository
import de.hagi089.obelix.data.calendar.CalendarRepository
import de.hagi089.obelix.data.campsites.CampsiteRepository
import de.hagi089.obelix.data.campsites.FirestoreCampsiteRepository
import de.hagi089.obelix.data.campsites.FusedLocationProvider
import de.hagi089.obelix.data.campsites.LocationProvider
import de.hagi089.obelix.data.calendar.FirestoreCalendarRepository
import de.hagi089.obelix.data.documents.DocumentRepository
import de.hagi089.obelix.data.documents.FirestoreDocumentRepository
import de.hagi089.obelix.data.files.AndroidFileReader
import de.hagi089.obelix.data.files.FileStore
import de.hagi089.obelix.data.files.FirestoreFileStore
import de.hagi089.obelix.data.files.LocalFileReader
import de.hagi089.obelix.data.files.PhotoCameraCache
import de.hagi089.obelix.data.files.ReceiptCache
import de.hagi089.obelix.data.finance.CategoryRepository
import de.hagi089.obelix.data.finance.FinanceRepository
import de.hagi089.obelix.data.finance.FirestoreCategoryRepository
import de.hagi089.obelix.data.finance.FirestoreFinanceRepository
import de.hagi089.obelix.data.planned.FirestorePlannedExpenseRepository
import de.hagi089.obelix.data.planned.PlannedExpenseRepository
import de.hagi089.obelix.data.repairs.FirestoreRepairRepository
import de.hagi089.obelix.data.repairs.RepairRepository
import de.hagi089.obelix.data.settings.ThemePreference
import de.hagi089.obelix.data.user.FirestoreUserRepository
import de.hagi089.obelix.data.user.UserRepository

/**
 * Einfache manuelle Dependency Injection (bewusst ohne Hilt, siehe docs/PROJEKTPLAN.md).
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    val networkMonitor: NetworkMonitor = ConnectivityNetworkMonitor(appContext)

    /** Lokale Wahl Hell-/Dunkelmodus. */
    val themePreference = ThemePreference(appContext)

    /** true, wenn google-services.json beim Build vorhanden war und Firebase initialisiert ist. */
    val isFirebaseConfigured: Boolean = FirebaseApp.getApps(appContext).isNotEmpty()

    val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }

    val authRepository: AuthRepository by lazy { FirebaseAuthRepository(auth) }

    val repairRepository: RepairRepository by lazy { FirestoreRepairRepository(firestore) }
    val userRepository: UserRepository by lazy { FirestoreUserRepository(firestore) }

    /** Dateiablage (Belege; später Stellplatzfotos und Dokumente). */
    val fileStore: FileStore by lazy { FirestoreFileStore(firestore) }

    /** Liest vom Benutzer gewählte Dateien und verkleinert Bilder. */
    val localFileReader: LocalFileReader by lazy { AndroidFileReader(appContext) }

    /** Zwischenspeicher für PDF-Belege, die in einer externen App geöffnet werden. */
    val receiptCache: ReceiptCache by lazy { ReceiptCache(appContext) }

    val financeRepository: FinanceRepository by lazy { FirestoreFinanceRepository(firestore, fileStore) }

    val categoryRepository: CategoryRepository by lazy { FirestoreCategoryRepository(firestore) }

    /** Backup des gesamten Datenbestands als ZIP (nur ADMIN in den Einstellungen). */
    val backupRepository: BackupRepository by lazy { FirestoreBackupRepository(firestore, fileStore) }

    val plannedExpenseRepository: PlannedExpenseRepository by lazy { FirestorePlannedExpenseRepository(firestore) }

    val calendarRepository: CalendarRepository by lazy { FirestoreCalendarRepository(firestore) }

    val campsiteRepository: CampsiteRepository by lazy { FirestoreCampsiteRepository(firestore, fileStore) }

    val documentRepository: DocumentRepository by lazy { FirestoreDocumentRepository(firestore, fileStore) }

    /** Einmalige Standortbestimmung (nur auf Knopfdruck). */
    val locationProvider: LocationProvider by lazy { FusedLocationProvider(appContext) }

    /** Zielordner für Kameraaufnahmen (Stellplatzfotos). */
    val photoCameraCache: PhotoCameraCache by lazy { PhotoCameraCache(appContext) }

    /** Übergabe von Code und Name zwischen Registrierungsformular und Codebildschirm. */
    val registrationHandoff = RegistrationHandoff()

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
