package com.example.paisacheck360

import android.Manifest
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
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
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class MainActivity : AppCompatActivity() {

    private val TAG = "SecureConnect-Main"
    private lateinit var auth: FirebaseAuth
    private lateinit var callerIdCard: CardView
    private lateinit var appScanCard: CardView
    private lateinit var linkGuardCard: CardView
    private lateinit var wifiGuardCard: CardView
    private lateinit var smsGuardCard: CardView
    private lateinit var logsCard: CardView
    private lateinit var threatsCard: CardView
    private lateinit var alertsCard: CardView
    private lateinit var threatCountText: TextView
    private lateinit var alertCountText: TextView
    private lateinit var profileBtn: ImageView
    private lateinit var shieldIcon: ImageView

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        perms.forEach { (perm, isGranted) ->
            Log.d(TAG, "Permission: $perm | Granted: $isGranted")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        Log.d(TAG, "--- MainActivity Launched ---")

        auth = FirebaseAuth.getInstance()
        bindViews()
        setupClickListeners()

        // Request standard core permissions smoothly
        checkAndRequestPermissions()

        Handler(Looper.getMainLooper()).postDelayed({
            animateShield()
        }, 150)
    }

    override fun onResume() {
        super.onResume()
        updateDashboardStats()
    }

    private fun bindViews() {
        profileBtn = findViewById(R.id.profileBtn)
        shieldIcon = findViewById(R.id.shieldIcon)
        appScanCard = findViewById(R.id.appScanCard)
        linkGuardCard = findViewById(R.id.linkGuardCard)
        callerIdCard = findViewById(R.id.callerIdCard)
        smsGuardCard = findViewById(R.id.smsGuardCard)
        wifiGuardCard = findViewById(R.id.wifiGuardCard)
        logsCard = findViewById(R.id.logsCard)
        threatsCard = findViewById(R.id.threatsCard)
        alertsCard = findViewById(R.id.alertsCard)
        threatCountText = findViewById(R.id.threatCountText)
        alertCountText = findViewById(R.id.alertCountText)
    }

    private fun setupClickListeners() {
        profileBtn.setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }

        callerIdCard.setOnClickListener {
            animateCardClick(callerIdCard)
            startActivity(Intent(this, FraudCallSummaryActivity::class.java))
        }

        smsGuardCard.setOnClickListener {
            animateCardClick(smsGuardCard)
            startActivity(Intent(this, SmsSummaryActivity::class.java))
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

        logsCard.setOnClickListener {
            animateCardClick(logsCard)
            startActivity(Intent(this, LogsActivity::class.java))
        }

        threatsCard.setOnClickListener {
            animateCardClick(threatsCard)
            startActivity(Intent(this, SmsSummaryActivity::class.java))
        }

        alertsCard.setOnClickListener {
            animateCardClick(alertsCard)
            startActivity(Intent(this, LogsActivity::class.java))
        }
    }

    private fun updateDashboardStats() {
        try {
            val prefs = getSharedPreferences("SecureConnectPrefs", Context.MODE_PRIVATE)
            val json = prefs.getString("scam_logs", "[]")
            val type = object : TypeToken<List<ScamMessage>>() {}.type
            val stored: List<ScamMessage>? = try {
                Gson().fromJson(json, type)
            } catch (e: Exception) {
                null
            }

            val threats = stored?.count {
                it?.riskLevel?.equals("Critical", true) == true ||
                it?.riskLevel?.equals("High", true) == true
            } ?: 0

            val alerts = stored?.size ?: 0

            threatCountText.text = threats.toString()
            alertCountText.text = alerts.toString()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun checkAndRequestPermissions() {
        val perms = mutableListOf(
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_SMS,
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.ACCESS_FINE_LOCATION
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val missing = perms.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isNotEmpty()) {
            requestPermissionLauncher.launch(missing.toTypedArray())
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