package com.swmad.spotcast.integration.auth

import com.swmad.spotcast.domain.AuthResult
import com.swmad.spotcast.domain.Email
import com.swmad.spotcast.domain.Password
import com.swmad.spotcast.domain.User

interface AuthService {
    // The user that is signed in on this device, or null
    val currentUser: User?

    suspend fun signUp(email: Email, password: Password): AuthResult
    suspend fun signIn(email: Email, password: Password): AuthResult
    fun signOut()
}
