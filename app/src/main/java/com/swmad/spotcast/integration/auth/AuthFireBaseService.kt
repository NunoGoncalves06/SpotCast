package com.swmad.spotcast.integration.auth

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.swmad.spotcast.domain.AuthResult
import com.swmad.spotcast.domain.Email
import com.swmad.spotcast.domain.Password
import com.swmad.spotcast.domain.Status
import com.swmad.spotcast.domain.User
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await

class AuthFireBaseService : AuthService {

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()

    companion object {
        private const val TAG = "AUTH SERVICE"
    }

    override val currentUser: User?
        get() = auth.currentUser?.toUser()

    override suspend fun signUp(email: Email, password: Password): AuthResult {
        return try {
            val result =
                auth.createUserWithEmailAndPassword(email.value, password.value).await().user
                    ?: return AuthResult(null, Status.ERROR, "The account could not be created")
            val user = result.toUser() ?: return AuthResult(null, Status.ERROR, "The account has no e-mail")
            AuthResult(user, Status.OK)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.v(TAG, e.toString())
            AuthResult(null, Status.ERROR, e.localizedMessage)
        }
    }

    override suspend fun signIn(email: Email, password: Password): AuthResult {
        return try {
            val result =
                auth.signInWithEmailAndPassword(email.value, password.value).await().user
                    ?: return AuthResult(null, Status.ERROR, "Sign in failed")
            val user = result.toUser() ?: return AuthResult(null, Status.ERROR, "The account has no e-mail")
            AuthResult(user, Status.OK)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.v(TAG, e.toString())
            AuthResult(null, Status.ERROR, e.localizedMessage)
        }
    }

    override fun signOut() {
        auth.signOut()
    }

    private fun FirebaseUser.toUser(): User? = email?.let { User(uid, Email(it)) }
}
