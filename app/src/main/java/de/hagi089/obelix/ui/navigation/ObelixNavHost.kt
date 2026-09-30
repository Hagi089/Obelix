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
import de.hagi089.obelix.data.auth.AuthUser
import de.hagi089.obelix.data.files.FileRef
import de.hagi089.obelix.ui.screens.SectionNotAvailableScreen
import de.hagi089.obelix.ui.screens.SettingsScreen
import de.hagi089.obelix.ui.finance.BookingFormScreen
import de.hagi089.obelix.ui.finance.BookingFormViewModel
import de.hagi089.obelix.ui.finance.FinanceScreen
import de.hagi089.obelix.ui.finance.FinanceViewModel
import de.hagi089.obelix.ui.finance.ReceiptScreen
import de.hagi089.obelix.ui.finance.ReceiptViewModel
import de.hagi089.obelix.ui.importing.ImportScreen
import de.hagi089.obelix.ui.importing.ImportViewModel
import de.hagi089.obelix.ui.planned.PlannedFormScreen
import de.hagi089.obelix.ui.planned.PlannedFormViewModel
import de.hagi089.obelix.ui.planned.PlannedListScreen
import de.hagi089.obelix.ui.planned.PlannedListViewModel
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
        composable<CalendarRoute> { SectionNotAvailableScreen() }
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
        composable<TasksRoute> { SectionNotAvailableScreen() }
        composable<CampsitesRoute> { SectionNotAvailableScreen() }
        composable<DocumentsRoute> { SectionNotAvailableScreen() }
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
