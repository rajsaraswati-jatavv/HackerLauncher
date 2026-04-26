package com.hackerlauncher.auth

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.hackerlauncher.MainActivity
import com.hackerlauncher.R

class LoginActivity : AppCompatActivity() {

    private lateinit var tvStatus: TextView
    private lateinit var btnGoogle: Button
    private lateinit var btnGithub: Button
    private lateinit var btnSkip: Button

    // FIX: Use Activity Result API instead of deprecated onActivityResult
    private val signInLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val authResult = FirebaseAuthManager.handleSignInResult(
            FirebaseAuthManager.RC_SIGN_IN,
            result.resultCode,
            result.data
        )
        if (authResult.success) {
            Toast.makeText(this, "Welcome, ${authResult.userName}!", Toast.LENGTH_SHORT).show()
            navigateToMain()
        } else {
            tvStatus.text = authResult.error ?: "Sign in failed"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        tvStatus = findViewById(R.id.tvLoginStatus)
        btnGoogle = findViewById(R.id.btnGoogleLogin)
        btnGithub = findViewById(R.id.btnGithubLogin)
        btnSkip = findViewById(R.id.btnSkipLogin)

        // Check if already logged in
        if (FirebaseAuthManager.isLoggedIn()) {
            navigateToMain()
            return
        }

        btnGoogle.setOnClickListener {
            startFirebaseAuth()
        }

        btnGithub.setOnClickListener {
            startFirebaseAuth()
        }

        btnSkip.setOnClickListener {
            tvStatus.text = "Continuing as guest..."
            navigateToMain()
        }
    }

    private fun startFirebaseAuth() {
        tvStatus.text = "Opening sign-in..."
        val signInIntent = FirebaseAuthManager.createSignInIntent()
        signInLauncher.launch(signInIntent)
    }

    private fun navigateToMain() {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}
