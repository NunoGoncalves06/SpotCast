package com.swmad.spotcast.ui.theme

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel

enum class ThemeMode(val label: String) {
    SYSTEM("System"),
    LIGHT("Light"),
    DARK("Dark")
}

// Holds the theme the user picked. The choice is a setting of this device, not of the
// account, so it is kept in SharedPreferences and not in Firestore.
// AndroidViewModel gives access to the application, which is needed to open the preferences.
class ThemeViewModel(application: Application) : AndroidViewModel(application) {

    private val preferences =
        application.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    var mode by mutableStateOf(load())
        private set

    fun select(newMode: ThemeMode) {
        mode = newMode
        preferences.edit().putString(KEY_MODE, newMode.name).apply()
    }

    private fun load(): ThemeMode {
        val stored = preferences.getString(KEY_MODE, null)
        return ThemeMode.entries.firstOrNull { it.name == stored } ?: ThemeMode.SYSTEM
    }

    companion object {
        private const val PREFERENCES_NAME = "spotcast_settings"
        private const val KEY_MODE = "theme_mode"
    }
}
