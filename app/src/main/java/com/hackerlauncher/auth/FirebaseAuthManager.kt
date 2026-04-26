package com.hackerlauncher.auth

import android.app.Activity
import android.content.Intent
import android.util.Log
import com.firebase.ui.auth.AuthUI
import com.firebase.ui.auth.IdpResponse
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser

class FirebaseAuthManager {

    companion object {
        private const val TAG = "FirebaseAuthManager"
        const val RC_SIGN_IN = 1001

        fun createSignInIntent(): Intent {
            val providers = listOf(
                AuthUI.IdpConfig.GoogleBuilder().build(),
                AuthUI.IdpConfig.GitHubBuilder().build()
            )
            return AuthUI.getInstance()
                .createSignInIntentBuilder()
                .setAvailableProviders(providers)
                .setTheme(com.hackerlauncher.R.style.Theme_HackerLauncher)
                .setLogo(com.hackerlauncher.R.drawable.ic_terminal)
                .setIsSmartLockEnabled(false)
                .build()
        }

        fun getCurrentUser(): FirebaseUser? {
            return FirebaseAuth.getInstance().currentUser
        }

        fun isLoggedIn(): Boolean {
            return FirebaseAuth.getInstance().currentUser != null
        }

        fun getUserName(): String {
            return FirebaseAuth.getInstance().currentUser?.displayName ?: "Hacker"
        }

        fun getUserEmail(): String {
            return FirebaseAuth.getInstance().currentUser?.email ?: "unknown@hack.er"
        }

        fun getProvider(): String {
            val user = FirebaseAuth.getInstance().currentUser
            val providerData = user?.providerData
            return if (providerData != null && providerData.size > 1) {
                providerData[1].providerId ?: "unknown"
            } else "unknown"
        }

        fun signOut() {
            FirebaseAuth.getInstance().signOut()
        }

        fun handleSignInResult(requestCode: Int, resultCode: Int, data: Intent?): AuthResult {
            if (requestCode == RC_SIGN_IN) {
                val response = IdpResponse.fromResultIntent(data)
                return when {
                    resultCode == Activity.RESULT_OK -> {
                        val user = getCurrentUser()
                        AuthResult(
                            success = true,
                            userName = user?.displayName ?: "Hacker",
                            userEmail = user?.email ?: "",
                            provider = getProvider(),
                            error = null
                        )
                    }
                    response == null -> AuthResult(success = false, error = "Sign in cancelled")
                    else -> AuthResult(success = false, error = response.error?.message ?: "Unknown error")
                }
            }
            return AuthResult(success = false, error = "Invalid request code")
        }
    }

    data class AuthResult(
        val success: Boolean,
        val userName: String = "",
        val userEmail: String = "",
        val provider: String = "",
        val error: String?
    )
}
