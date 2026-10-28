package com.example.paisacheck360

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

class ScamPopupService : Service() {

    private val TAG = "ScamPopupService"
    private lateinit var windowManager: WindowManager
    private var popupView: View? = null
    private var isPopupVisible = false
    private val mainHandler = Handler(Looper.getMainLooper())
    private var autoDismissRunnable: Runnable? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
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
            showOverlay(sender, body, risk, category, explanation, advice)
        } else {
            Log.w(TAG, "Overlay permission missing, showing heads-up notification fallback.")
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

    private fun showOverlay(
        sender: String,
        message: String,
        risk: String,
        category: String,
        explanation: String,
        advice: String
    ) {
        try {
            // If already showing a popup, remove previous view without stopping the service
            if (isPopupVisible && popupView != null) {
                try {
                    windowManager.removeView(popupView)
                } catch (e: Exception) {
                    Log.d(TAG, "Cleanup old view: ${e.message}")
                }
                popupView = null
                isPopupVisible = false
            }

            // Inflate professional XML layout
            val inflater = getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
            val view = inflater.inflate(R.layout.layout_sms_scam_popup, null)
            popupView = view

            val headerBanner = view.findViewById<View>(R.id.headerBanner)
            val txtRiskBadge = view.findViewById<TextView>(R.id.txtScamRiskBadge)
            val txtCategory = view.findViewById<TextView>(R.id.txtScamCategory)
            val txtSender = view.findViewById<TextView>(R.id.txtScamSender)
            val txtBody = view.findViewById<TextView>(R.id.txtScamBody)
            val txtAdvice = view.findViewById<TextView>(R.id.txtScamAdvice)
            val btnDismiss = view.findViewById<Button>(R.id.btnDismiss)
            val btnReport = view.findViewById<Button>(R.id.btnReport)
            val btnDismissTop = view.findViewById<ImageView>(R.id.btnDismissTop)

            // Setup Header color and Risk Badge
            val headerColor = when (risk.uppercase()) {
                "CRITICAL" -> "#DC2626"
                "HIGH" -> "#EA580C"
                "MEDIUM" -> "#D97706"
                else -> "#16A34A"
            }
            headerBanner?.setBackgroundColor(Color.parseColor(headerColor))
            txtRiskBadge?.text = "🚨 FRAUD ALERT: ${risk.uppercase()} RISK"

            txtCategory?.text = category
            txtSender?.text = "From: $sender"
            txtBody?.text = message
            txtAdvice?.text = advice

            // Button Click Handlers
            btnDismiss?.setOnClickListener {
                removeOverlayAndStop()
            }

            btnDismissTop?.setOnClickListener {
                removeOverlayAndStop()
            }

            btnReport?.setOnClickListener {
                saveUserAction("reported", sender, message, risk)
                removeOverlayAndStop()
            }

            val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                layoutType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                y = 80
            }

            windowManager.addView(view, params)
            isPopupVisible = true
            Log.d(TAG, "✅ SMS Scam popup overlay presented successfully for $sender")

            // Auto-dismiss after 60 seconds of inactivity
            autoDismissRunnable?.let { mainHandler.removeCallbacks(it) }
            autoDismissRunnable = Runnable {
                Log.d(TAG, "Auto-dismissing popup overlay after timeout")
                removeOverlayAndStop()
            }
            mainHandler.postDelayed(autoDismissRunnable!!, 60000)

        } catch (e: Exception) {
            Log.e(TAG, "Overlay presentation failed: ${e.message}", e)
            showHeadsUpNotification(sender, message, risk, category)
            removeOverlayAndStop()
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

    private fun removeOverlayAndStop() {
        autoDismissRunnable?.let { mainHandler.removeCallbacks(it) }
        if (isPopupVisible && popupView != null) {
            try {
                windowManager.removeView(popupView)
                Log.d(TAG, "Popup view dismissed and removed from WindowManager")
            } catch (e: Exception) {
                Log.e(TAG, "Error removing view: ${e.message}")
            }
            popupView = null
            isPopupVisible = false
        }
        stopSelf()
    }

    private fun saveUserAction(action: String, sender: String, message: String, risk: String) {
        try {
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
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onDestroy() {
        autoDismissRunnable?.let { mainHandler.removeCallbacks(it) }
        if (isPopupVisible && popupView != null) {
            try {
                windowManager.removeView(popupView)
            } catch (e: Exception) {}
            popupView = null
            isPopupVisible = false
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}