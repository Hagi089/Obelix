package de.hagi089.obelix.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import de.hagi089.obelix.AppContainer
import de.hagi089.obelix.data.auth.AuthUser
import de.hagi089.obelix.ui.screens.SectionNotAvailableScreen
import de.hagi089.obelix.ui.screens.SettingsScreen
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
        composable<FinanceRoute> { SectionNotAvailableScreen() }
        composable<TasksRoute> { SectionNotAvailableScreen() }
        composable<CampsitesRoute> { SectionNotAvailableScreen() }
        composable<DocumentsRoute> { SectionNotAvailableScreen() }
        composable<SettingsRoute> {
            val settingsViewModel: SettingsViewModel = viewModel(
                factory = viewModelFactory { initializer { SettingsViewModel(container.userRepository, user.uid) } },
            )
            SettingsScreen(viewModel = settingsViewModel, user = user, onSignOut = onSignOut)
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
