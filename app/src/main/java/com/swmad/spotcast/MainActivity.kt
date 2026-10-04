package com.swmad.spotcast

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.swmad.spotcast.ui.navigation.Navigation
import com.swmad.spotcast.ui.theme.SpotCastTheme
import com.swmad.spotcast.ui.theme.ThemeMode
import com.swmad.spotcast.ui.theme.ThemeViewModel

// The app has one activity. All screens are composables in the navigation graph.
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            // The owner of this view model is the activity, so the choice holds for every screen
            val themeViewModel: ThemeViewModel = viewModel()
            val darkTheme = when (themeViewModel.mode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }

            // The clock and icons in the status bar have to change with the theme,
            // otherwise they are dark on a dark background
            DisposableEffect(darkTheme) {
                val style = if (darkTheme) {
                    SystemBarStyle.dark(Color.TRANSPARENT)
                } else {
                    SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose {}
            }

            SpotCastTheme(darkTheme = darkTheme) {
                Navigation(
                    themeMode = themeViewModel.mode,
                    onThemeModeChange = { themeViewModel.select(it) }
                )
            }
        }
    }
}
