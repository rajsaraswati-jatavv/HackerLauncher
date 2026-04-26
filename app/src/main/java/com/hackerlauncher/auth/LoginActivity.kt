package com.hackerlauncher.auth

import android.app.Activity
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
        startActivityForResult(signInIntent, FirebaseAuthManager.RC_SIGN_IN)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val result = FirebaseAuthManager.handleSignInResult(requestCode, resultCode, data)
        if (result.success) {
            Toast.makeText(this, "Welcome, ${result.userName}!", Toast.LENGTH_SHORT).show()
            navigateToMain()
        } else {
            tvStatus.text = result.error ?: "Sign in failed"
        }
    }

    private fun navigateToMain() {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}
