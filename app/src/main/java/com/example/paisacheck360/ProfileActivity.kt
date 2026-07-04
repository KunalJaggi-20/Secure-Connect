package com.example.paisacheck360

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth

class ProfileActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        // 1. Check Firebase Auth session
        val auth = FirebaseAuth.getInstance()
        val user = auth.currentUser

        if (user == null) {
            // If not logged in, redirect to Login
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        // 2. Initialize Views
        val nameTextView = findViewById<TextView>(R.id.nameTextView)
        val logoutButton = findViewById<Button>(R.id.logoutButton)

        // 3. Get Device Secure ID
        val androidID = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ANDROID_ID
        )

        // 4. Display ID (or user info)
        nameTextView.text = "Secure ID: $androidID"

        // 5. Logout Logic
        logoutButton.setOnClickListener {
            auth.signOut()
            val intent = Intent(this, LoginActivity::class.java)
            // Clear backstack so user can't go back to profile
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }
    }
}