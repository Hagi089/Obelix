package de.hagi089.obelix.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import de.hagi089.obelix.AppContainer
import de.hagi089.obelix.R
import de.hagi089.obelix.data.auth.AuthUser
import de.hagi089.obelix.ui.auth.AuthScreens
import de.hagi089.obelix.ui.auth.AuthViewModel
import de.hagi089.obelix.ui.components.OfflineBanner
import de.hagi089.obelix.ui.navigation.BookingFormRoute
import de.hagi089.obelix.ui.navigation.ImportRoute
import de.hagi089.obelix.ui.navigation.ObelixNavHost
import de.hagi089.obelix.ui.navigation.SettingsRoute
import de.hagi089.obelix.ui.navigation.TopLevelDestination
import de.hagi089.obelix.ui.navigation.navigateToTopLevel
import de.hagi089.obelix.ui.onboarding.AccessCodeScreen
import de.hagi089.obelix.ui.onboarding.AccessCodeViewModel

@Composable
fun ObelixApp(container: AppContainer) {
    if (!container.isFirebaseConfigured) {
        FirebaseMissingScreen()
        return
    }
    val isOnline by container.networkMonitor.isOnline.collectAsStateWithLifecycle(initialValue = true)
    val sessionViewModel: SessionViewModel = viewModel(
        factory = viewModelFactory { initializer { SessionViewModel(container.authRepository, container.userRepository) } },
    )
    val session by sessionViewModel.session.collectAsStateWithLifecycle()

    when (val current = session) {
        SessionState.Loading -> LoadingScreen()
        SessionState.SignedOut -> {
            val authViewModel: AuthViewModel = viewModel(
                factory = viewModelFactory { initializer { AuthViewModel(container.authRepository, container.registrationHandoff) } },
            )
            AuthScreens(viewModel = authViewModel, isOnline = isOnline)
        }
        is SessionState.SignedIn -> when (val profile = current.profile) {
            ProfileState.Loading -> LoadingScreen()
            ProfileState.None -> {
                val codeViewModel: AccessCodeViewModel = viewModel(
                    key = "access-code-${current.user.uid}",
                    factory = viewModelFactory {
                        initializer {
                            AccessCodeViewModel(
                                container.userRepository,
                                container.authRepository,
                                container.registrationHandoff,
                                current.user.uid,
                            )
                        }
                    },
                )
                AccessCodeScreen(
                    viewModel = codeViewModel,
                    isOnline = isOnline,
                    onFinished = sessionViewModel::reloadProfile,
                    onSignOut = sessionViewModel::signOut,
                )
            }
            is ProfileState.Failed -> ProfileErrorScreen(
                error = profile.error,
                isOnline = isOnline,
                onRetry = sessionViewModel::reloadProfile,
                onSignOut = sessionViewModel::signOut,
            )
            is ProfileState.Ready -> ObelixMainScaffold(
                user = current.user,
                container = container,
                isOnline = isOnline,
                onSignOut = sessionViewModel::signOut,
            )
        }
    }
}

@Composable
private fun LoadingScreen() {
    Surface(modifier = Modifier.fillMaxSize()) {
        Box(contentAlignment = Alignment.Center) { CircularProgressIndicator() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ObelixMainScaffold(user: AuthUser, container: AppContainer, isOnline: Boolean, onSignOut: () -> Unit) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val currentTopLevel = TopLevelDestination.entries.firstOrNull { currentDestination.isIn(it) }
    // Alles außer den sechs Hauptbereichen (Einstellungen, Buchungsformular, Import) ist ein Unterbildschirm mit Zurück-Pfeil.
    val isSubScreen = currentDestination != null && currentTopLevel == null

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            TopLevelDestination.entries.forEach { destination ->
                item(
                    selected = currentDestination.isIn(destination),
                    onClick = { navController.navigateToTopLevel(destination) },
                    // Nur Symbole (Beschriftungen brachen um); der Name bleibt für TalkBack als Beschreibung erhalten.
                    icon = { Icon(imageVector = destination.icon, contentDescription = stringResource(destination.labelRes)) },
                    label = null,
                )
            }
        },
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        val titleRes = when {
                            currentDestination?.hasRoute(SettingsRoute::class) == true -> R.string.nav_settings
                            currentDestination?.hasRoute(BookingFormRoute::class) == true -> R.string.finance_form_title
                            currentDestination?.hasRoute(ImportRoute::class) == true -> R.string.import_title
                            currentTopLevel != null -> currentTopLevel.labelRes
                            else -> R.string.app_name
                        }
                        Text(stringResource(titleRes))
                    },
                    navigationIcon = {
                        if (isSubScreen) {
                            IconButton(onClick = { navController.navigateUp() }) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = stringResource(R.string.action_back),
                                )
                            }
                        }
                    },
                    actions = {
                        if (!isSubScreen) {
                            IconButton(onClick = { navController.navigate(SettingsRoute) { launchSingleTop = true } }) {
                                Icon(
                                    imageVector = Icons.Filled.Settings,
                                    contentDescription = stringResource(R.string.action_open_settings),
                                )
                            }
                        }
                    },
                )
            },
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .consumeWindowInsets(innerPadding),
            ) {
                if (!isOnline) OfflineBanner()
                ObelixNavHost(
                    navController = navController,
                    user = user,
                    container = container,
                    onSignOut = onSignOut,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

private fun NavDestination?.isIn(destination: TopLevelDestination): Boolean =
    this?.hierarchy?.any { it.hasRoute(destination.route::class) } == true

/** Wird angezeigt, wenn der Build ohne google-services.json erstellt wurde. */
@Composable
private fun FirebaseMissingScreen() {
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(imageVector = Icons.Filled.CloudOff, contentDescription = null)
            Text(
                text = stringResource(R.string.firebase_missing_title),
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(R.string.firebase_missing_text),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
        }
    }
}
