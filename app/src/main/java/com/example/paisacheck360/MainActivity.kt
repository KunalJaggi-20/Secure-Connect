package com.example.paisacheck360

import android.Manifest
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.animation.OvershootInterpolator
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth

class MainActivity : AppCompatActivity() {

    private val TAG = "SecureConnect-Main"
    private lateinit var auth: FirebaseAuth
    private lateinit var callerIdCard: CardView
    private lateinit var appScanCard: CardView
    private lateinit var linkGuardCard: CardView
    private lateinit var wifiGuardCard: CardView
    private lateinit var profileBtn: ImageView
    private lateinit var shieldIcon: ImageView

    // Permission Launcher with Diagnostic Logging
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        perms.forEach { (perm, isGranted) ->
            Log.d(TAG, "Permission: $perm | Granted: $isGranted")
        }
        if (perms[Manifest.permission.RECEIVE_SMS] == true) {
            Toast.makeText(this, "SMS Security Active ✅", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        Log.d(TAG, "--- MainActivity Launched ---")

        auth = FirebaseAuth.getInstance()
        bindViews()
        setupClickListeners()

        // 1. Request standard permissions
        checkAndRequestPermissions()

        // 2. NEW: Request Overlay Permission (Required for Call & Scam Popups)
        checkOverlayPermission()

        Handler(Looper.getMainLooper()).postDelayed({
            animateShield()
        }, 100)
    }

    private fun checkOverlayPermission() {
        if (!Settings.canDrawOverlays(this)) {
            Log.w(TAG, "Overlay permission missing. Redirecting user...")

            // Show a toast so the user knows why they are being sent to settings
            Toast.makeText(this, "Enable 'Display over other apps' for Scam Alerts", Toast.LENGTH_LONG).show()

            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
        } else {
            Log.d(TAG, "Overlay permission already granted ✅")
        }
    }

    private fun checkAndRequestPermissions() {
        val perms = mutableListOf(
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_SMS,
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.ACCESS_FINE_LOCATION
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val missing = perms.filter {
            val status = ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
            Log.d(TAG, "Checking $it: ${if (status) "OK" else "MISSING"}")
            !status
        }

        if (missing.isNotEmpty()) {
            Log.w(TAG, "Requesting missing permissions: $missing")
            requestPermissionLauncher.launch(missing.toTypedArray())
        } else {
            Log.d(TAG, "All core security permissions are already granted")
        }
    }

    private fun bindViews() {
        profileBtn = findViewById(R.id.profileBtn)
        shieldIcon = findViewById(R.id.shieldIcon)
        appScanCard = findViewById(R.id.appScanCard)
        linkGuardCard = findViewById(R.id.linkGuardCard)
        callerIdCard = findViewById(R.id.callerIdCard)
        wifiGuardCard = findViewById(R.id.wifiGuardCard)
    }

    private fun setupClickListeners() {
        profileBtn.setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }

        callerIdCard.setOnClickListener {
            animateCardClick(callerIdCard)
            startActivity(Intent(this, FraudCallSummaryActivity::class.java))
        }

        appScanCard.setOnClickListener {
            animateCardClick(appScanCard)
            startActivity(Intent(this, AppRiskScannerActivity::class.java))
        }

        wifiGuardCard.setOnClickListener {
            animateCardClick(wifiGuardCard)
            startActivity(Intent(this, WiFiGuardActivity::class.java))
        }

        linkGuardCard.setOnClickListener {
            animateCardClick(linkGuardCard)
            startActivity(Intent(this, LinkScannerActivity::class.java))
        }
    }

    private fun animateShield() {
        val scaleX = ObjectAnimator.ofFloat(shieldIcon, "scaleX", 0.5f, 1f)
        val scaleY = ObjectAnimator.ofFloat(shieldIcon, "scaleY", 0.5f, 1f)
        val animatorSet = AnimatorSet()
        animatorSet.playTogether(scaleX, scaleY)
        animatorSet.duration = 800
        animatorSet.interpolator = OvershootInterpolator()
        animatorSet.start()
    }

    private fun animateCardClick(card: CardView) {
        card.animate().scaleX(0.95f).scaleY(0.95f).setDuration(100).withEndAction {
            card.animate().scaleX(1f).scaleY(1f).setDuration(100).start()
        }.start()
    }
}