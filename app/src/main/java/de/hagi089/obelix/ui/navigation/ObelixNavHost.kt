package de.hagi089.obelix.ui.navigation

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import de.hagi089.obelix.AppContainer
import de.hagi089.obelix.R
import de.hagi089.obelix.data.auth.AuthUser
import de.hagi089.obelix.data.files.FileRef
import de.hagi089.obelix.ui.calendar.CalendarFormScreen
import de.hagi089.obelix.ui.calendar.CalendarFormViewModel
import de.hagi089.obelix.ui.calendar.CalendarScreen
import de.hagi089.obelix.ui.calendar.CalendarViewModel
import de.hagi089.obelix.ui.screens.SectionNotAvailableScreen
import de.hagi089.obelix.ui.screens.SettingsScreen
import de.hagi089.obelix.ui.finance.BookingFormScreen
import de.hagi089.obelix.ui.finance.BookingFormViewModel
import de.hagi089.obelix.ui.finance.FinanceScreen
import de.hagi089.obelix.ui.finance.FinanceViewModel
import de.hagi089.obelix.ui.documents.DocumentFormScreen
import de.hagi089.obelix.ui.documents.DocumentFormViewModel
import de.hagi089.obelix.ui.documents.DocumentListScreen
import de.hagi089.obelix.ui.documents.DocumentListViewModel
import de.hagi089.obelix.ui.finance.ReceiptScreen
import de.hagi089.obelix.ui.finance.ReceiptViewModel
import de.hagi089.obelix.ui.importing.ImportScreen
import de.hagi089.obelix.ui.importing.ImportViewModel
import de.hagi089.obelix.ui.planned.PlannedFormScreen
import de.hagi089.obelix.ui.planned.PlannedFormViewModel
import de.hagi089.obelix.ui.planned.PlannedListScreen
import de.hagi089.obelix.ui.planned.PlannedListViewModel
import de.hagi089.obelix.data.campsites.GeoFormat
import de.hagi089.obelix.ui.campsites.CampsiteFormScreen
import de.hagi089.obelix.ui.campsites.CampsiteFormViewModel
import de.hagi089.obelix.ui.campsites.CampsiteListScreen
import de.hagi089.obelix.ui.campsites.CampsiteListViewModel
import de.hagi089.obelix.ui.repairs.RepairFormScreen
import de.hagi089.obelix.ui.repairs.RepairFormViewModel
import de.hagi089.obelix.ui.repairs.RepairListScreen
import de.hagi089.obelix.ui.repairs.RepairListViewModel
import de.hagi089.obelix.ui.settings.CategoriesViewModel
import de.hagi089.obelix.ui.settings.SettingsViewModel

@Composable
fun ObelixNavHost(
    navController: NavHostController,
    user: AuthUser,
    container: AppContainer,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = DashboardRoute,
        modifier = modifier,
    ) {
        // Die Bereiche werden in den jeweiligen Phasen umgesetzt (docs/PROJEKTPLAN.md).
        composable<DashboardRoute> { SectionNotAvailableScreen() }
        composable<CalendarRoute> {
            val calendarViewModel: CalendarViewModel = viewModel(
                factory = viewModelFactory { initializer { CalendarViewModel(container.calendarRepository, container.userRepository) } },
            )
            CalendarScreen(
                viewModel = calendarViewModel,
                onAdd = { navController.navigate(CalendarFormRoute()) },
                onOpen = { id -> navController.navigate(CalendarFormRoute(entryId = id)) },
            )
        }
        composable<CalendarFormRoute> { entry ->
            val route = entry.toRoute<CalendarFormRoute>()
            val calendarFormViewModel: CalendarFormViewModel = viewModel(
                factory = viewModelFactory {
                    initializer {
                        CalendarFormViewModel(
                            entryId = route.entryId,
                            uid = user.uid,
                            calendar = container.calendarRepository,
                            users = container.userRepository,
                        )
                    }
                },
            )
            CalendarFormScreen(viewModel = calendarFormViewModel, onFinished = { navController.navigateUp() })
        }
        composable<FinanceRoute> {
            val financeViewModel: FinanceViewModel = viewModel(
                factory = viewModelFactory {
                    initializer { FinanceViewModel(container.financeRepository, container.categoryRepository, container.userRepository) }
                },
            )
            FinanceScreen(
                viewModel = financeViewModel,
                onAdd = { navController.navigate(BookingFormRoute()) },
                onOpen = { id -> navController.navigate(BookingFormRoute(bookingId = id)) },
                onOpenPlanned = { navController.navigate(PlannedListRoute) },
            )
        }
        composable<PlannedListRoute> {
            val plannedListViewModel: PlannedListViewModel = viewModel(
                factory = viewModelFactory {
                    initializer { PlannedListViewModel(container.plannedExpenseRepository, container.userRepository) }
                },
            )
            PlannedListScreen(
                viewModel = plannedListViewModel,
                onAdd = { navController.navigate(PlannedFormRoute()) },
                onOpen = { id -> navController.navigate(PlannedFormRoute(plannedId = id)) },
            )
        }
        composable<PlannedFormRoute> { entry ->
            val route = entry.toRoute<PlannedFormRoute>()
            val plannedFormViewModel: PlannedFormViewModel = viewModel(
                factory = viewModelFactory {
                    initializer {
                        PlannedFormViewModel(
                            plannedId = route.plannedId,
                            uid = user.uid,
                            planned = container.plannedExpenseRepository,
                            finance = container.financeRepository,
                            categories = container.categoryRepository,
                            users = container.userRepository,
                        )
                    }
                },
            )
            PlannedFormScreen(
                viewModel = plannedFormViewModel,
                onFinished = { navController.navigateUp() },
                onOpenBooking = { id -> navController.navigate(BookingFormRoute(bookingId = id)) },
            )
        }
        composable<BookingFormRoute> { entry ->
            val route = entry.toRoute<BookingFormRoute>()
            val formViewModel: BookingFormViewModel = viewModel(
                factory = viewModelFactory {
                    initializer {
                        BookingFormViewModel(
                            bookingId = route.bookingId,
                            uid = user.uid,
                            finance = container.financeRepository,
                            categories = container.categoryRepository,
                            users = container.userRepository,
                            fileReader = container.localFileReader,
                        )
                    }
                },
            )
            BookingFormScreen(
                viewModel = formViewModel,
                onFinished = { navController.navigateUp() },
                onViewReceipt = { ref ->
                    navController.navigate(ReceiptRoute(ref.fileId, ref.name, ref.contentType, ref.sizeBytes))
                },
            )
        }
        composable<ReceiptRoute> { entry ->
            val route = entry.toRoute<ReceiptRoute>()
            val ref = FileRef(route.fileId, route.name, route.contentType, route.sizeBytes)
            val receiptViewModel: ReceiptViewModel = viewModel(
                factory = viewModelFactory {
                    initializer { ReceiptViewModel(ref, container.fileStore, container.receiptCache) }
                },
            )
            ReceiptScreen(viewModel = receiptViewModel, ref = ref)
        }
        composable<ImportRoute> {
            val importViewModel: ImportViewModel = viewModel(
                factory = viewModelFactory {
                    initializer {
                        ImportViewModel(user.uid, container.financeRepository, container.categoryRepository, container.userRepository)
                    }
                },
            )
            ImportScreen(viewModel = importViewModel)
        }
        composable<TasksRoute> {
            val repairListViewModel: RepairListViewModel = viewModel(
                factory = viewModelFactory {
                    initializer { RepairListViewModel(container.repairRepository, container.userRepository) }
                },
            )
            RepairListScreen(
                viewModel = repairListViewModel,
                onAdd = { navController.navigate(RepairFormRoute()) },
                onOpen = { id -> navController.navigate(RepairFormRoute(repairId = id)) },
            )
        }
        composable<RepairFormRoute> { entry ->
            val route = entry.toRoute<RepairFormRoute>()
            val repairFormViewModel: RepairFormViewModel = viewModel(
                factory = viewModelFactory {
                    initializer { RepairFormViewModel(repairId = route.repairId, uid = user.uid, repairs = container.repairRepository) }
                },
            )
            RepairFormScreen(viewModel = repairFormViewModel, onFinished = { navController.navigateUp() })
        }
        composable<CampsitesRoute> {
            val campsiteListViewModel: CampsiteListViewModel = viewModel(
                factory = viewModelFactory {
                    initializer {
                        CampsiteListViewModel(container.campsiteRepository, container.userRepository, container.locationProvider)
                    }
                },
            )
            CampsiteListScreen(
                viewModel = campsiteListViewModel,
                onLocated = { position -> navController.navigate(CampsiteFormRoute(position = GeoFormat.encode(position))) },
                onOpen = { id -> navController.navigate(CampsiteFormRoute(campsiteId = id)) },
            )
        }
        composable<CampsiteFormRoute> { entry ->
            val route = entry.toRoute<CampsiteFormRoute>()
            val campsiteFormViewModel: CampsiteFormViewModel = viewModel(
                factory = viewModelFactory {
                    initializer {
                        CampsiteFormViewModel(
                            campsiteId = route.campsiteId,
                            initialPosition = GeoFormat.decode(route.position),
                            uid = user.uid,
                            campsites = container.campsiteRepository,
                            fileReader = container.localFileReader,
                        )
                    }
                },
            )
            CampsiteFormScreen(
                viewModel = campsiteFormViewModel,
                newCaptureUri = container.photoCameraCache::newCaptureUri,
                onFinished = { navController.navigateUp() },
            )
        }
        composable<DocumentsRoute> {
            val documentListViewModel: DocumentListViewModel = viewModel(
                factory = viewModelFactory {
                    initializer { DocumentListViewModel(container.documentRepository, container.userRepository) }
                },
            )
            DocumentListScreen(
                viewModel = documentListViewModel,
                onAdd = { navController.navigate(DocumentFormRoute()) },
                onOpen = { id -> navController.navigate(DocumentFormRoute(documentId = id)) },
            )
        }
        composable<DocumentFormRoute> { entry ->
            val route = entry.toRoute<DocumentFormRoute>()
            val documentFormViewModel: DocumentFormViewModel = viewModel(
                factory = viewModelFactory {
                    initializer {
                        DocumentFormViewModel(
                            documentId = route.documentId,
                            uid = user.uid,
                            documents = container.documentRepository,
                            fileReader = container.localFileReader,
                        )
                    }
                },
            )
            DocumentFormScreen(
                viewModel = documentFormViewModel,
                onFinished = { navController.navigateUp() },
                onOpenFile = { ref ->
                    navController.navigate(DocumentViewRoute(ref.fileId, ref.name, ref.contentType, ref.sizeBytes))
                },
            )
        }
        composable<DocumentViewRoute> { entry ->
            val route = entry.toRoute<DocumentViewRoute>()
            val ref = FileRef(route.fileId, route.name, route.contentType, route.sizeBytes)
            val documentViewModel: ReceiptViewModel = viewModel(
                factory = viewModelFactory {
                    initializer { ReceiptViewModel(ref, container.fileStore, container.receiptCache) }
                },
            )
            ReceiptScreen(
                viewModel = documentViewModel,
                ref = ref,
                loadingRes = R.string.document_loading,
                imageDescriptionRes = R.string.document_image_description,
                pdfInfoRes = R.string.document_pdf_info,
            )
        }
        composable<SettingsRoute> {
            val settingsViewModel: SettingsViewModel = viewModel(
                factory = viewModelFactory { initializer { SettingsViewModel(container.userRepository, user.uid) } },
            )
            val categoriesViewModel: CategoriesViewModel = viewModel(
                factory = viewModelFactory { initializer { CategoriesViewModel(container.categoryRepository, user.uid) } },
            )
            val chosenDark by container.themePreference.darkMode.collectAsStateWithLifecycle()
            val isDark = chosenDark ?: isSystemInDarkTheme()
            SettingsScreen(
                viewModel = settingsViewModel,
                categoriesViewModel = categoriesViewModel,
                user = user,
                onSignOut = onSignOut,
                onOpenImport = { navController.navigate(ImportRoute) },
                isDark = isDark,
                onToggleTheme = { container.themePreference.setDarkMode(!isDark) },
            )
        }
    }
}

/** Wechselt zwischen Hauptbereichen, ohne den Back-Stack aufzublähen. */
fun NavHostController.navigateToTopLevel(destination: TopLevelDestination) {
    navigate(destination.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
