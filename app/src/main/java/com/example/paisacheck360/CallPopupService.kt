package com.example.paisacheck360

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.provider.ContactsContract
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

        // 1. Intelligent Shield: Ignore calls from your own contact list
        if (isNumberInContacts(this, number)) {
            Log.d("SecureConnect-UI", "Contact found for $number. No popup shown.")
            stopSelf()
            return START_NOT_STICKY
        }

        if (!isPopupVisible) {
            if (isFeedbackMode) {
                // Post-call state: show feedback options
                showOverlay(number, 0L, true)
            } else {
                // Ringing state: check global DB for known threats
                fetchGlobalDataAndShowWarning(number)
            }
        }
        return START_NOT_STICKY
    }

    private fun fetchGlobalDataAndShowWarning(number: String) {
        val safeKey = number.replace(Regex("[.#$\\[\\]]"), "_")
        val globalRef = FirebaseDatabase.getInstance().reference.child("global_scam_reports").child(safeKey)

        globalRef.get().addOnSuccessListener { snapshot ->
            val scamCount = snapshot.child("scam_count").getValue(Long::class.java) ?: 0L
            if (scamCount > 0) {
                showOverlay(number, scamCount, false)
            } else {
                // Not a known scam yet. Service stops and waits for post-call feedback trigger.
                stopSelf()
            }
        }.addOnFailureListener { stopSelf() }
    }

    private fun showOverlay(number: String, scamCount: Long, isFeedback: Boolean) {
        if (!Settings.canDrawOverlays(this)) return

        try {
            val inflater = getSystemService(LAYOUT_INFLATER_SERVICE) as LayoutInflater
            popupView = inflater.inflate(R.layout.activity_call_feedback, null)

            // Essential Window Flags for interactive overlays
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                else
                    WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                PixelFormat.TRANSLUCENT
            ).apply { gravity = Gravity.CENTER }

            popupView?.let { view ->
                // Bind all elements from your refined XML
                val txtNumber = view.findViewById<TextView>(R.id.txtNumber)
                val scamWarningCard = view.findViewById<View>(R.id.scamWarningCard)
                val txtScamSub = view.findViewById<TextView>(R.id.txtScamSub)
                val txtQuestion = view.findViewById<TextView>(R.id.txtQuestion)
                val btnScam = view.findViewById<View>(R.id.btnScam)
                val btnSafe = view.findViewById<View>(R.id.btnSafe)
                val btnOk = view.findViewById<View>(R.id.btnOk)
                val txtDismiss = view.findViewById<View>(R.id.txtDismiss)

                txtNumber.text = number

                if (!isFeedback && scamCount > 0) {
                    // --- REAL-TIME WARNING UI ---
                    scamWarningCard.visibility = View.VISIBLE
                    btnOk.visibility = View.VISIBLE
                    btnScam.visibility = View.GONE
                    btnSafe.visibility = View.GONE
                    txtQuestion.visibility = View.GONE
                    txtScamSub.text = "Reported by $scamCount community members"
                    Log.d("SecureConnect-UI", "Displaying Real-time Warning")
                } else {
                    // --- POST-CALL FEEDBACK UI ---
                    scamWarningCard.visibility = View.GONE
                    btnOk.visibility = View.GONE
                    btnScam.visibility = View.VISIBLE
                    btnSafe.visibility = View.VISIBLE
                    txtQuestion.visibility = View.VISIBLE
                    Log.d("SecureConnect-UI", "Displaying Post-call Feedback")
                }

                // Click Listeners (Enabled by XML clickable="true")
                btnScam.setOnClickListener {
                    Log.d("SecureConnect-UI", "Scam Button Clicked")
                    saveFeedback(number, "Scam")
                    stopSelf()
                }

                btnSafe.setOnClickListener {
                    Log.d("SecureConnect-UI", "Safe Button Clicked")
                    saveFeedback(number, "Safe")
                    stopSelf()
                }

                btnOk.setOnClickListener { stopSelf() }
                txtDismiss.setOnClickListener { stopSelf() }

                windowManager.addView(view, params)
                isPopupVisible = true
            }
        } catch (e: Exception) {
            Log.e("CallPopupService", "Overlay UI Error: ${e.message}")
        }
    }

    private fun saveFeedback(number: String, status: String) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: "anonymous_user"
        val safeKey = number.replace(Regex("[.#$\\[\\]]"), "_")
        val rootRef = FirebaseDatabase.getInstance().reference

        // 1. Save to User History
        val userLog = mapOf("status" to status, "timestamp" to System.currentTimeMillis())
        rootRef.child("users").child(userId).child("call_feedback").child(safeKey).setValue(userLog)

        // 2. Atomic Global Increment
        if (status == "Scam") {
            val globalUpdate = mapOf(
                "scam_count" to ServerValue.increment(1),
                "last_reported" to ServerValue.TIMESTAMP
            )
            rootRef.child("global_scam_reports").child(safeKey).updateChildren(globalUpdate)
        }
    }

    private fun isNumberInContacts(context: Context, number: String): Boolean {
        val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
        val projection = arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME)
        return try {
            context.contentResolver.query(uri, projection, null, null, null)?.use {
                it.moveToFirst()
            } ?: false
        } catch (e: Exception) {
            false
        }
    }

    private fun startInForeground() {
        val channelId = "CallPopupChannel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Security", NotificationManager.IMPORTANCE_LOW)
            val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Secure Connect")
            .setContentText("Intelligent Call Protection Active")
            .setSmallIcon(R.mipmap.ic_launcher).build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(2, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(2, notification)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isPopupVisible) {
            try {
                windowManager.removeView(popupView)
                isPopupVisible = false
            } catch (e: Exception) {
                Log.e("CallPopupService", "Error removing view: ${e.message}")
            }
        }
    }
}