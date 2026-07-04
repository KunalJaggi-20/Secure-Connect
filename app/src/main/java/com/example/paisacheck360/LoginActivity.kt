package com.example.paisacheck360

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

class LoginActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth

    private lateinit var emailInput: EditText
    private lateinit var passwordInput: EditText
    private lateinit var loginBtn: Button
    private lateinit var registerBtn: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        auth = FirebaseAuth.getInstance()

        // Auto login
        if (auth.currentUser != null) {
            goMain()
            return
        }

        emailInput = findViewById(R.id.emailInput)
        passwordInput = findViewById(R.id.passwordInput)
        loginBtn = findViewById(R.id.loginBtn)
        registerBtn = findViewById(R.id.registerBtn)

        loginBtn.setOnClickListener { loginUser() }
        registerBtn.setOnClickListener { registerUser() }
    }

    // ---------------- LOGIN ----------------
    private fun loginUser() {
        val email = emailInput.text.toString().trim()
        val password = passwordInput.text.toString().trim()

        if (email.isEmpty() || password.isEmpty()) {
            toast("Enter email & password")
            return
        }

        auth.signInWithEmailAndPassword(email, password)
            .addOnSuccessListener {
                toast("Login successful")
                goMain()
            }
            .addOnFailureListener {
                toast("Login failed: ${it.message}")
            }
    }

    // ---------------- REGISTER ----------------
    private fun registerUser() {
        val email = emailInput.text.toString().trim()
        val password = passwordInput.text.toString().trim()

        if (email.isEmpty() || password.isEmpty()) {
            toast("Enter email & password")
            return
        }

        if (password.length < 6) {
            toast("Password must be at least 6 characters")
            return
        }

        auth.createUserWithEmailAndPassword(email, password)
            .addOnSuccessListener {
                createUserProfile()
                toast("Registration successful")
                goMain()
            }
            .addOnFailureListener {
                toast("Register failed: ${it.message}")
            }
    }

    // ---------------- SAVE SID PROFILE ----------------
    private fun createUserProfile() {
        val user = auth.currentUser ?: return
        val sid = user.uid   // 🔥 THIS IS YOUR SECURE ID

        val profile = mapOf(
            "sid" to sid,
            "email" to user.email,
            "created_at" to System.currentTimeMillis()
        )

        FirebaseDatabase.getInstance().reference
            .child("users")
            .child(sid)
            .setValue(profile)
    }

    private fun goMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }
}
