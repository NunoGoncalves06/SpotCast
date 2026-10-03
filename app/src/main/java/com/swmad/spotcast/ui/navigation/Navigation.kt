package com.swmad.spotcast.ui.navigation

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
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
import androidx.lifecycle.viewmodel.compose.viewModel
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
    BottomItem(Routes.SPOTS, "Spots", Icons.AutoMirrored.Filled.List),
    BottomItem(Routes.MAP, "Map", Icons.Filled.Place),
    BottomItem(Routes.PROFILE, "Profile", Icons.Filled.Person)
)

@Composable
fun Navigation(authViewModel: AuthViewModel = viewModel()) {
    // The nav controller lives at the top of the hierarchy. Screens only get callbacks.
    val controller = rememberNavController()
    val backStackEntry by controller.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val user = authViewModel.user

    // A user that is already signed in goes straight to the spots
    val startDestination = remember {
        if (authViewModel.user != null) Routes.SPOTS else Routes.SIGN_IN
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
                                    // Keeps only the spots list below the selected tab,
                                    // so the back button always leads to the list
                                    popUpTo(Routes.SPOTS)
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
            composable(route = Routes.SPOTS) {
                SpotsScreen(
                    onAddSpot = { controller.navigate(Routes.ADD_SPOT) },
                    onOpenSpot = { spotId -> controller.navigate(Routes.details(spotId)) }
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
            composable(route = Routes.MAP) {
                MapScreen(
                    onOpenSpot = { spotId -> controller.navigate(Routes.details(spotId)) }
                )
            }
            composable(route = Routes.PROFILE) {
                ProfileScreen(
                    email = user?.email?.value ?: "",
                    onSignOut = { authViewModel.signOut() }
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
            controller.navigate(Routes.SPOTS) {
                popUpTo(controller.graph.id) { inclusive = true }
            }
        }
    }
}
