package com.example.paisacheck360

import android.app.*
import android.content.*
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import android.widget.*
import androidx.core.app.NotificationCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import java.util.regex.Pattern

class ScamPopupService : Service() {

    private var windowManager: WindowManager? = null
    private var popupView: LinearLayout? = null

    override fun onCreate() {
        super.onCreate()
        // ✅ CRITICAL: Register as a foreground service immediately
        startInForeground()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val sender = intent?.getStringExtra("sender") ?: "Unknown"
        val body = intent?.getStringExtra("body") ?: ""
        val risk = intent?.getStringExtra("risk") ?: "High" // Default to High for SMSReceiver
        val suggestions = intent?.getStringArrayListExtra("suggestedReplies") ?: arrayListOf("OK", "Got it")

        showPopup(sender, body, risk, suggestions)
        return START_NOT_STICKY
    }

    private fun startInForeground() {
        val channelId = "SmsScamChannel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "SMS Protection", NotificationManager.IMPORTANCE_LOW)
            val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Secure Connect")
            .setContentText("Scanning for SMS fraud...")
            .setSmallIcon(R.mipmap.ic_launcher)
            .build()

        // Match the service type declared in your Manifest
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(1, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, notification)
        }
    }

    private fun showPopup(sender: String, message: String, risk: String, suggestions: ArrayList<String>) {
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        // Remove existing popup if any
        removePopup()

        val headerColor = when (risk) {
            "High" -> "#FF5252"
            "Medium" -> "#FFD740"
            else -> "#4CAF50"
        }

        popupView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadius = 40f
            }
            setPadding(32, 32, 32, 32)
        }

        // --- Build Header ---
        val header = TextView(this).apply {
            text = "📩 Fraud Detection: $risk Risk"
            setBackgroundColor(Color.parseColor(headerColor))
            setTextColor(Color.WHITE)
            textSize = 16f
            gravity = Gravity.CENTER
            setPadding(20, 20, 20, 20)
        }
        popupView?.addView(header)

        // --- Message Content ---
        val content = TextView(this).apply {
            text = "From: $sender\n\n$message"
            textSize = 14f
            setTextColor(Color.BLACK)
            setPadding(10, 20, 10, 20)
        }
        popupView?.addView(content)

        // --- Actions ---
        val btnLayout = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }

        btnLayout.addView(createButton("Dismiss", "#616161") { removePopup() })
        btnLayout.addView(createButton("Report", "#D32F2F") {
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
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP
            y = 100
        }

        try {
            windowManager?.addView(popupView, params)
        } catch (e: Exception) {
            Log.e("ScamPopup", "Error adding overlay: ${e.message}")
        }
    }

    private fun createButton(txt: String, color: String, onClick: () -> Unit): Button {
        return Button(this).apply {
            text = txt
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor(color))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f).apply {
                setMargins(5, 5, 5, 5)
            }
            setOnClickListener { onClick() }
        }
    }

    private fun saveUserAction(action: String, sender: String, message: String, risk: String) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: "guest"
        val db = FirebaseDatabase.getInstance().getReference("users/$uid/userActions")
        val data = mapOf("action" to action, "sender" to sender, "body" to message, "risk" to risk, "time" to System.currentTimeMillis())
        db.push().setValue(data)
    }

    private fun removePopup() {
        if (popupView != null) {
            try {
                windowManager?.removeView(popupView)
            } catch (e: Exception) {}
            popupView = null
        }
        stopSelf()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}