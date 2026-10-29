package com.swmad.spotcast.ui.navigation

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.swmad.spotcast.ui.auth.AuthViewModel
import com.swmad.spotcast.ui.auth.SignInScreen
import com.swmad.spotcast.ui.auth.SignUpScreen
import com.swmad.spotcast.ui.details.DetailsScreen
import com.swmad.spotcast.ui.map.MapScreen
import com.swmad.spotcast.ui.profile.ProfileScreen
import com.swmad.spotcast.ui.spots.AddSpotScreen
import com.swmad.spotcast.ui.spots.SpotsScreen
import com.swmad.spotcast.ui.spots.SpotsViewModel
import com.swmad.spotcast.ui.theme.ThemeMode

object Routes {
    const val SIGN_IN = "signIn"
    const val SIGN_UP = "signUp"
    const val SPOTS = "spots"
    const val ADD_SPOT = "addSpot"
    const val MAP = "map"
    const val PROFILE = "profile"

    const val SPOT_ID = "spotId"
    const val DETAILS = "details/{$SPOT_ID}"

    fun details(spotId: String) = "details/$spotId"
}

private data class BottomItem(val route: String, val label: String, val icon: ImageVector)

private val bottomItems = listOf(
    BottomItem(Routes.MAP, "Today", Icons.Filled.Map),
    BottomItem(Routes.SPOTS, "Spots", Icons.AutoMirrored.Filled.List),
    BottomItem(Routes.PROFILE, "Profile", Icons.Filled.Person)
)

// Today, Spots and Profile show the same spots, so they share one SpotsViewModel: one Firestore listener, one
// forecast per spot, and the chosen activity stays when the user changes tab. It belongs to the Today entry,
// which stays below the tabs and is removed with the rest of the back stack at sign out, so the spots of one
// user are never shown to the next.
@Composable
private fun sharedSpotsViewModel(controller: NavHostController, entry: NavBackStackEntry): SpotsViewModel {
    val owner = remember(entry) { controller.getBackStackEntry(Routes.MAP) }
    // Looked up once: asking a destroyed entry for its view model store throws while the sign-out animation runs
    return remember(owner) { ViewModelProvider(owner)[SpotsViewModel::class.java] }
}

@Composable
fun Navigation(
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    authViewModel: AuthViewModel = viewModel()
) {
    // The nav controller lives at the top of the hierarchy. Screens only get callbacks.
    val controller = rememberNavController()
    val backStackEntry by controller.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val user = authViewModel.user

    // A user that is already signed in goes straight to today's map
    val startDestination = remember {
        if (authViewModel.user != null) Routes.MAP else Routes.SIGN_IN
    }

    Scaffold(
        // Every screen has its own Scaffold that handles the system bars
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (bottomItems.any { it.route == currentRoute }) {
                NavigationBar {
                    bottomItems.forEach { item ->
                        NavigationBarItem(
                            selected = currentRoute == item.route,
                            onClick = {
                                controller.navigate(item.route) {
                                    // Keeps only the map below the selected tab,
                                    // so the back button always leads to the map
                                    popUpTo(Routes.MAP)
                                    launchSingleTop = true
                                }
                            },
                            icon = { Icon(item.icon, contentDescription = null) },
                            label = { Text(item.label) }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = controller,
            startDestination = startDestination,
            modifier = Modifier
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
        ) {
            composable(route = Routes.SIGN_IN) {
                SignInScreen(
                    isLoading = authViewModel.isLoading,
                    error = authViewModel.error,
                    onSignIn = { email, password -> authViewModel.signIn(email, password) },
                    onGoToSignUp = {
                        authViewModel.clearError()
                        controller.navigate(Routes.SIGN_UP)
                    }
                )
            }
            composable(route = Routes.SIGN_UP) {
                SignUpScreen(
                    isLoading = authViewModel.isLoading,
                    error = authViewModel.error,
                    onSignUp = { email, password -> authViewModel.signUp(email, password) },
                    onGoToSignIn = {
                        authViewModel.clearError()
                        controller.popBackStack()
                    }
                )
            }
            composable(route = Routes.SPOTS) { entry ->
                SpotsScreen(
                    onAddSpot = { controller.navigate(Routes.ADD_SPOT) },
                    onOpenSpot = { spotId -> controller.navigate(Routes.details(spotId)) },
                    viewModel = sharedSpotsViewModel(controller, entry)
                )
            }
            composable(route = Routes.ADD_SPOT) {
                AddSpotScreen(
                    onDone = { controller.popBackStack() },
                    onBack = { controller.popBackStack() }
                )
            }
            // Navigation with an argument: the details depend on the id of the spot
            composable(
                route = Routes.DETAILS,
                arguments = listOf(navArgument(Routes.SPOT_ID) { type = NavType.StringType })
            ) { entry ->
                val spotId = entry.arguments?.getString(Routes.SPOT_ID) ?: return@composable
                DetailsScreen(
                    spotId = spotId,
                    onBack = { controller.popBackStack() },
                    onDeleted = { controller.popBackStack() }
                )
            }
            composable(route = Routes.MAP) { entry ->
                MapScreen(
                    onOpenSpot = { spotId -> controller.navigate(Routes.details(spotId)) },
                    onAddSpot = { controller.navigate(Routes.ADD_SPOT) },
                    viewModel = sharedSpotsViewModel(controller, entry)
                )
            }
            composable(route = Routes.PROFILE) { entry ->
                ProfileScreen(
                    email = user?.email?.value ?: "",
                    themeMode = themeMode,
                    onThemeModeChange = onThemeModeChange,
                    onSignOut = { authViewModel.signOut() },
                    viewModel = sharedSpotsViewModel(controller, entry)
                )
            }
        }
    }

    // Reacts to the logged-in state. The whole back stack is cleared in both directions,
    // so back never returns to a screen of the other state, and the view models of a
    // signed-out user are thrown away.
    LaunchedEffect(user) {
        val route = controller.currentDestination?.route
        val onAuthScreen = route == Routes.SIGN_IN || route == Routes.SIGN_UP
        if (user == null && !onAuthScreen) {
            controller.navigate(Routes.SIGN_IN) {
                popUpTo(controller.graph.id) { inclusive = true }
            }
        } else if (user != null && onAuthScreen) {
            controller.navigate(Routes.MAP) {
                popUpTo(controller.graph.id) { inclusive = true }
            }
        }
    }
}
