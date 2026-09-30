package de.hagi089.obelix.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import de.hagi089.obelix.ui.screens.SectionNotAvailableScreen

@Composable
fun ObelixNavHost(navController: NavHostController, modifier: Modifier = Modifier) {
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
        composable<SettingsRoute> { SectionNotAvailableScreen() }
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
