package com.swmad.spotcast

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.swmad.spotcast.ui.navigation.Navigation
import com.swmad.spotcast.ui.theme.SpotCastTheme

// The app has one activity. All screens are composables in the navigation graph.
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SpotCastTheme {
                Navigation()
            }
        }
    }
}
