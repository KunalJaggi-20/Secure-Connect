package com.example.paisacheck360

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue

class CallPopupService : Service() {

    private val TAG = "CallPopupService"
    private lateinit var windowManager: WindowManager
    private var popupView: View? = null
    private var isPopupVisible = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        startInForeground()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val number = intent?.getStringExtra("callerNumber") ?: return START_NOT_STICKY
        val isFeedbackMode = intent.getBooleanExtra("isFeedbackMode", false)

        Log.d(TAG, "Incoming call check for: $number | Feedback mode: $isFeedbackMode")

        // 1. Intelligent Shield: Check if caller is in user's contacts
        val contactName = FraudCallDetector.getContactName(this, number)
        if (contactName != null) {
            Log.d(TAG, "Contact verified for $number ($contactName). Shield active: no popup needed.")
            stopSelf()
            return START_NOT_STICKY
        }

        // 2. Perform immediate on-device AI & Heuristic Analysis
        val assessment = FraudCallDetector.analyze(this, number)

        if (!isPopupVisible) {
            // Display overlay immediately with on-device intelligence
            showOverlay(assessment, isFeedbackMode)

            // Asynchronously augment with crowdsourced database reports
            fetchGlobalReportsAndUpdate(number, assessment)
        }

        return START_NOT_STICKY
    }

    private fun showOverlay(assessment: CallRiskAssessment, isFeedback: Boolean) {
        if (!Settings.canDrawOverlays(this)) {
            Log.w(TAG, "SYSTEM_ALERT_WINDOW permission not granted. Cannot draw overlay.")
            stopSelf()
            return
        }

        try {
            val inflater = getSystemService(LAYOUT_INFLATER_SERVICE) as LayoutInflater
            val view = inflater.inflate(R.layout.activity_call_feedback, null)
            popupView = view

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
                gravity = Gravity.CENTER
            }

            // Bind views from layout
            val txtTitle = view.findViewById<TextView>(R.id.txtTitle)
            val txtNumber = view.findViewById<TextView>(R.id.txtNumber)
            val scamWarningCard = view.findViewById<View>(R.id.scamWarningCard)
            val txtScamTitle = view.findViewById<TextView>(R.id.txtScamTitle)
            val txtScamSub = view.findViewById<TextView>(R.id.txtScamSub)
            val txtQuestion = view.findViewById<TextView>(R.id.txtQuestion)
            val btnScam = view.findViewById<View>(R.id.btnScam)
            val btnSafe = view.findViewById<View>(R.id.btnSafe)
            val btnOk = view.findViewById<View>(R.id.btnOk)
            val txtDismiss = view.findViewById<View>(R.id.txtDismiss)

            txtNumber.text = assessment.rawNumber

            if (assessment.isScam || assessment.riskLevel != RiskLevel.LOW) {
                // High risk / scam detected by on-device model
                txtTitle?.text = "🚨 Scam Call Detected!"
                scamWarningCard?.visibility = View.VISIBLE
                txtScamTitle?.text = assessment.callerTag
                txtScamSub?.text = assessment.reasons.firstOrNull() ?: "High-risk calling pattern identified"

                btnOk?.visibility = View.VISIBLE
                btnScam?.visibility = View.VISIBLE
                btnSafe?.visibility = View.VISIBLE
                txtQuestion?.text = "Flagged as ${assessment.category}. Mark status:"
                txtQuestion?.visibility = View.VISIBLE
            } else {
                // Unknown neutral number
                txtTitle?.text = "Caller ID"
                scamWarningCard?.visibility = View.GONE
                txtQuestion?.text = "Unverified Number • Is this call legitimate?"
                txtQuestion?.visibility = View.VISIBLE
                btnScam?.visibility = View.VISIBLE
                btnSafe?.visibility = View.VISIBLE
                btnOk?.visibility = View.GONE
            }

            // Click Handlers
            btnScam?.setOnClickListener {
                Log.d(TAG, "User tagged $assessment.rawNumber as SCAM")
                saveFeedback(assessment.rawNumber, "Scam")
                removeOverlayAndStop()
            }

            btnSafe?.setOnClickListener {
                Log.d(TAG, "User tagged $assessment.rawNumber as SAFE")
                saveFeedback(assessment.rawNumber, "Safe")
                removeOverlayAndStop()
            }

            btnOk?.setOnClickListener {
                removeOverlayAndStop()
            }

            txtDismiss?.setOnClickListener {
                removeOverlayAndStop()
            }

            windowManager.addView(view, params)
            isPopupVisible = true
            Log.d(TAG, "Caller ID overlay presented successfully for ${assessment.rawNumber}")

        } catch (e: Exception) {
            Log.e(TAG, "Overlay error: ${e.message}", e)
            stopSelf()
        }
    }

    private fun fetchGlobalReportsAndUpdate(number: String, initialAssessment: CallRiskAssessment) {
        val safeKey = number.replace(Regex("[.#$\\[\\]]"), "_")
        val globalRef = FirebaseDatabase.getInstance()
            .reference
            .child("global_scam_reports")
            .child(safeKey)

        globalRef.get().addOnSuccessListener { snapshot ->
            val scamCount = snapshot.child("scam_count").getValue(Long::class.java) ?: 0L
            if (scamCount > 0 && isPopupVisible) {
                popupView?.let { view ->
                    val scamWarningCard = view.findViewById<View>(R.id.scamWarningCard)
                    val txtScamTitle = view.findViewById<TextView>(R.id.txtScamTitle)
                    val txtScamSub = view.findViewById<TextView>(R.id.txtScamSub)
                    val txtTitle = view.findViewById<TextView>(R.id.txtTitle)

                    txtTitle?.text = "🚨 Confirmed Scam Call!"
                    scamWarningCard?.visibility = View.VISIBLE
                    txtScamTitle?.text = "🚨 Reported Fraud ($scamCount reports)"
                    txtScamSub?.text = "Reported by $scamCount Secure Connect members • ${initialAssessment.callerTag}"
                }
            }
        }.addOnFailureListener {
            Log.d(TAG, "Crowdsourced lookup offline or failed")
        }
    }

    private fun saveFeedback(number: String, status: String) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: "anonymous_user"
        val safeKey = number.replace(Regex("[.#$\\[\\]]"), "_")
        val rootRef = FirebaseDatabase.getInstance().reference

        // 1. Save to User Feedback Log
        val userLog = mapOf(
            "number" to number,
            "status" to status,
            "timestamp" to System.currentTimeMillis()
        )
        rootRef.child("users").child(userId).child("call_feedback").child(safeKey).setValue(userLog)

        // 2. Global Crowdsourced Increments
        if (status == "Scam") {
            val globalUpdate = mapOf(
                "scam_count" to ServerValue.increment(1),
                "last_reported" to ServerValue.TIMESTAMP,
                "number" to number
            )
            rootRef.child("global_scam_reports").child(safeKey).updateChildren(globalUpdate)
        }
    }

    private fun removeOverlayAndStop() {
        if (isPopupVisible && popupView != null) {
            try {
                windowManager.removeView(popupView)
            } catch (e: Exception) {
                Log.e(TAG, "Error removing view: ${e.message}")
            }
            isPopupVisible = false
            popupView = null
        }
        stopSelf()
    }

    private fun startInForeground() {
        val channelId = "CallPopupChannel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Caller ID & Scam Protection",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Secure Connect Caller ID")
            .setContentText("Intelligent Call Protection Active")
            .setSmallIcon(R.mipmap.ic_launcher)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(2, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(2, notification)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        removeOverlayAndStop()
    }
}