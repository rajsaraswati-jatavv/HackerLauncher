package com.hackerlauncher.auth

import android.app.Activity
import android.content.Intent
import android.util.Log

/**
 * Firebase-safe Auth Manager.
 * All Firebase calls wrapped in try-catch to prevent crashes
 * when google-services.json is placeholder.
 */
class FirebaseAuthManager {

    companion object {
        private const val TAG = "FirebaseAuthManager"
        const val RC_SIGN_IN = 1001

        private var firebaseAvailable: Boolean? = null

        private fun isFirebaseAvailable(): Boolean {
            if (firebaseAvailable != null) return firebaseAvailable!!
            return try {
                Class.forName("com.google.firebase.auth.FirebaseAuth")
                val instance = com.google.firebase.auth.FirebaseAuth.getInstance()
                // Verify the instance is usable (not from placeholder config)
                instance.app.name // This will throw if FirebaseApp is misconfigured
                firebaseAvailable = true
                true
            } catch (e: Exception) {
                Log.w(TAG, "Firebase not available: ${e.message}")
                firebaseAvailable = false
                false
            }
        }

        fun createSignInIntent(): Intent? {
            return try {
                if (!isFirebaseAvailable()) return null
                val providers = listOf(
                    com.firebase.ui.auth.AuthUI.IdpConfig.GoogleBuilder().build(),
                    com.firebase.ui.auth.AuthUI.IdpConfig.GitHubBuilder().build()
                )
                com.firebase.ui.auth.AuthUI.getInstance()
                    .createSignInIntentBuilder()
                    .setAvailableProviders(providers)
                    .setTheme(com.hackerlauncher.R.style.Theme_HackerLauncher)
                    .setLogo(com.hackerlauncher.R.drawable.ic_terminal)
                    .setIsSmartLockEnabled(false)
                    .build()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create sign-in intent: ${e.message}")
                null
            }
        }

        fun getCurrentUser(): com.google.firebase.auth.FirebaseUser? {
            return try {
                if (!isFirebaseAvailable()) null
                else com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get current user: ${e.message}")
                null
            }
        }

        fun isLoggedIn(): Boolean {
            return try {
                if (!isFirebaseAvailable()) false
                else com.google.firebase.auth.FirebaseAuth.getInstance().currentUser != null
            } catch (e: Exception) {
                Log.e(TAG, "Failed to check login status: ${e.message}")
                false
            }
        }

        fun getUserName(): String {
            return try {
                if (!isFirebaseAvailable()) "Hacker"
                else com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.displayName ?: "Hacker"
            } catch (e: Exception) {
                "Hacker"
            }
        }

        fun getUserEmail(): String {
            return try {
                if (!isFirebaseAvailable()) "unknown@hack.er"
                else com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.email ?: "unknown@hack.er"
            } catch (e: Exception) {
                "unknown@hack.er"
            }
        }

        fun getProvider(): String {
            return try {
                if (!isFirebaseAvailable()) return "unknown"
                val user = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
                val providerData = user?.providerData
                if (providerData != null && providerData.size > 1) {
                    providerData[1].providerId ?: "unknown"
                } else "unknown"
            } catch (e: Exception) {
                "unknown"
            }
        }

        fun signOut() {
            try {
                if (isFirebaseAvailable()) {
                    com.google.firebase.auth.FirebaseAuth.getInstance().signOut()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to sign out: ${e.message}")
            }
        }

        fun handleSignInResult(requestCode: Int, resultCode: Int, data: Intent?): AuthResult {
            if (requestCode == RC_SIGN_IN) {
                return try {
                    val response = com.firebase.ui.auth.IdpResponse.fromResultIntent(data)
                    when {
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
                } catch (e: Exception) {
                    AuthResult(success = false, error = e.message ?: "Sign in error")
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
