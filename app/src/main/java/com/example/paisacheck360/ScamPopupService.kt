package com.example.paisacheck360

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

class ScamPopupService : Service() {

    private val TAG = "ScamPopupService"
    private var windowManager: WindowManager? = null
    private var popupView: LinearLayout? = null

    override fun onCreate() {
        super.onCreate()
        startInForeground()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val sender = intent?.getStringExtra("sender") ?: "Unknown"
        val body = intent?.getStringExtra("body") ?: ""
        val risk = intent?.getStringExtra("risk") ?: "HIGH"
        val category = intent?.getStringExtra("category") ?: "Suspicious Message"
        val explanation = intent?.getStringExtra("explanation") ?: "Potential fraud pattern detected."
        val advice = intent?.getStringExtra("advice") ?: "Do not click links or share OTP."

        Log.d(TAG, "Displaying scam alert for $sender | Risk: $risk")

        if (Settings.canDrawOverlays(this)) {
            showPopup(sender, body, risk, category, explanation, advice)
        } else {
            Log.w(TAG, "Overlay permission not granted, showing high-priority notification.")
            showHeadsUpNotification(sender, body, risk, category)
        }

        return START_NOT_STICKY
    }

    private fun startInForeground() {
        val channelId = "SmsScamChannel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "SMS Protection", NotificationManager.IMPORTANCE_HIGH)
            val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Secure Connect Guard")
            .setContentText("Active SMS & phishing threat monitoring")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(101, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(101, notification)
        }
    }

    private fun showHeadsUpNotification(sender: String, message: String, risk: String, category: String) {
        val channelId = "SmsScamAlertsChannel"
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Fraud Alerts", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Immediate warnings for phishing & fraud SMS"
                enableVibration(true)
            }
            manager.createNotificationChannel(channel)
        }

        val openIntent = Intent(this, SmsSummaryActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val pendingIntent = PendingIntent.getActivity(this, 0, openIntent, PendingIntent.FLAG_IMMUTABLE)

        val notif = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("🚨 $risk FRAUD ALERT: $category")
            .setContentText("From $sender: $message")
            .setStyle(NotificationCompat.BigTextStyle().bigText("From: $sender\n\n$message\n\n⚠️ DO NOT click links or share banking credentials."))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        manager.notify(202, notif)
    }

    private fun showPopup(
        sender: String,
        message: String,
        risk: String,
        category: String,
        explanation: String,
        advice: String
    ) {
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        removePopup()

        val (accentColor, riskLabel) = when (risk.uppercase()) {
            "CRITICAL" -> Pair("#DC2626", "🔴 CRITICAL RISK")
            "HIGH" -> Pair("#EA580C", "🟠 HIGH RISK")
            "MEDIUM" -> Pair("#D97706", "🟡 MEDIUM RISK")
            else -> Pair("#16A34A", "🟢 SAFE")
        }

        popupView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadius = 36f
                setStroke(4, Color.parseColor(accentColor))
            }
            setPadding(32, 28, 32, 28)
            elevation = 20f
        }

        // --- Header Banner ---
        val header = TextView(this).apply {
            text = "🛡️ SECURE CONNECT: $riskLabel"
            setBackgroundColor(Color.parseColor(accentColor))
            setTextColor(Color.WHITE)
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(16, 14, 16, 14)
        }
        popupView?.addView(header)

        // --- Category Title ---
        val catTitle = TextView(this).apply {
            text = category
            setTextColor(Color.parseColor("#1F2937"))
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 16, 0, 4)
        }
        popupView?.addView(catTitle)

        // --- Sender & Body ---
        val senderView = TextView(this).apply {
            text = "Sender: $sender"
            setTextColor(Color.parseColor("#4B5563"))
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 2, 0, 8)
        }
        popupView?.addView(senderView)

        val messageBox = TextView(this).apply {
            text = "\"$message\""
            setTextColor(Color.parseColor("#111827"))
            textSize = 13f
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#F3F4F6"))
                cornerRadius = 16f
            }
            setPadding(20, 16, 20, 16)
        }
        popupView?.addView(messageBox)

        // --- Warning & Advice ---
        val adviceView = TextView(this).apply {
            text = "⚠️ $advice"
            setTextColor(Color.parseColor("#B91C1C"))
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 12, 0, 12)
        }
        popupView?.addView(adviceView)

        // --- Actions ---
        val btnLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 8, 0, 0)
        }

        btnLayout.addView(createButton("Dismiss", "#4B5563") { removePopup() })
        btnLayout.addView(createButton("🚨 Report Scam", "#DC2626") {
            saveUserAction("reported", sender, message, risk)
            removePopup()
        })

        popupView?.addView(btnLayout)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP
            y = 80
        }

        try {
            windowManager?.addView(popupView, params)
        } catch (e: Exception) {
            Log.e(TAG, "Error adding overlay: ${e.message}")
            showHeadsUpNotification(sender, message, risk, category)
        }
    }

    private fun createButton(txt: String, color: String, onClick: () -> Unit): Button {
        return Button(this).apply {
            text = txt
            setTextColor(Color.WHITE)
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            background = GradientDrawable().apply {
                setColor(Color.parseColor(color))
                cornerRadius = 18f
            }
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f).apply {
                setMargins(8, 0, 8, 0)
            }
            setOnClickListener { onClick() }
        }
    }

    private fun saveUserAction(action: String, sender: String, message: String, risk: String) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: "guest"
        val db = FirebaseDatabase.getInstance().getReference("users/$uid/userActions")
        val data = mapOf(
            "action" to action,
            "sender" to sender,
            "body" to message,
            "risk" to risk,
            "timestamp" to System.currentTimeMillis()
        )
        db.push().setValue(data)
    }

    private fun removePopup() {
        if (popupView != null) {
            try {
                windowManager?.removeView(popupView)
            } catch (e: Exception) {
                Log.e(TAG, "Error removing overlay: ${e.message}")
            }
            popupView = null
        }
        stopSelf()
    }

    override fun onDestroy() {
        removePopup()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}