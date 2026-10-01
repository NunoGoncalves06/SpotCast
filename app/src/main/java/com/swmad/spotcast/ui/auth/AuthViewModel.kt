package com.swmad.spotcast.ui.auth

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.swmad.spotcast.domain.AuthResult
import com.swmad.spotcast.domain.Email
import com.swmad.spotcast.domain.Password
import com.swmad.spotcast.domain.User
import com.swmad.spotcast.integration.auth.AuthFireBaseService
import com.swmad.spotcast.integration.auth.AuthService
import kotlinx.coroutines.launch

// Holds the logged-in state for the whole app. It is created in Navigation,
// so its owner is the activity and it survives navigation between screens.
class AuthViewModel(
    private val authService: AuthService = AuthFireBaseService()
) : ViewModel() {

    // Firebase remembers the user between app starts
    var user by mutableStateOf<User?>(authService.currentUser)
        private set

    var isLoading by mutableStateOf(false)
        private set

    var error by mutableStateOf<String?>(null)
        private set

    fun signIn(email: Email, password: Password) {
        authenticate { authService.signIn(email, password) }
    }

    fun signUp(email: Email, password: Password) {
        authenticate { authService.signUp(email, password) }
    }

    fun signOut() {
        authService.signOut()
        user = null
    }

    fun clearError() {
        error = null
    }

    private fun authenticate(call: suspend () -> AuthResult) {
        viewModelScope.launch {
            isLoading = true
            error = null
            val res = call()
            if (res.isOk()) {
                user = res.user
            } else {
                error = res.message ?: "Something went wrong. Try again."
            }
            isLoading = false
        }
    }
}
