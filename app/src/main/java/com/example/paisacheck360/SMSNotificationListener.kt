package com.example.paisacheck360

import android.content.Context
import android.content.Intent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SMSNotificationListener : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val pkg = sbn?.packageName ?: return
        val extras = sbn.notification?.extras ?: return
        val title = extras.getString("android.title") ?: extras.getCharSequence("android.title")?.toString() ?: "Unknown"
        val text = extras.getCharSequence("android.text")?.toString() ?: ""

        // Target all SMS, Messaging, WhatsApp, Telegram, and RCS apps
        val targetApps = listOf("messaging", "sms", "whatsapp", "telegram", "mms", "signal", "viber")
        val isTarget = targetApps.any { pkg.contains(it, ignoreCase = true) }

        if (isTarget && text.isNotBlank()) {
            val analysis = SmsScamDetector.analyze(text, title)

            if (analysis.riskLevel != "SAFE") {
                Log.w("SecureConnect-Notif", "🚨 Notification Risk Detected: [${analysis.riskLevel}] ${analysis.category} | App: $pkg")

                // Save locally
                saveScamLocally(title, text, analysis.riskLevel)

                // Save to Firebase for Dashboard
                saveAlertToFirebase(title, text, analysis)

                // Trigger UI Popup (debounced so it doesn't double-fire with SMSReceiver)
                if (SmsScamDetector.shouldTriggerPopup(text)) {
                    triggerPopup(title, text, analysis)
                }
            }
        }
    }

    private fun triggerPopup(sender: String, message: String, analysis: SmsScamDetector.SmsAnalysisResult) {
        val intent = Intent(this, ScamPopupService::class.java).apply {
            putExtra("sender", sender)
            putExtra("body", message)
            putExtra("risk", analysis.riskLevel)
            putExtra("category", analysis.category)
            putExtra("explanation", analysis.explanation)
            putExtra("advice", analysis.advice)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        ContextCompat.startForegroundService(this, intent)
    }

    private fun saveScamLocally(sender: String, body: String, risk: String) {
        try {
            val prefs = getSharedPreferences("SecureConnectPrefs", Context.MODE_PRIVATE)
            val gson = Gson()
            val existingJson = prefs.getString("scam_logs", "[]")
            val type = object : TypeToken<MutableList<ScamMessage>>() {}.type
            val list: MutableList<ScamMessage> = gson.fromJson(existingJson, type) ?: mutableListOf()

            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            val dateStr = sdf.format(Date())

            list.add(0, ScamMessage(sender = sender, text = body, date = dateStr, riskLevel = risk))
            prefs.edit().putString("scam_logs", gson.toJson(list.take(100))).apply()
            ScamStorage.addScamLog(this, body, risk)
        } catch (e: Exception) {
            Log.e("SecureConnect-Notif", "Error saving locally: ${e.message}")
        }
    }

    private fun saveAlertToFirebase(sender: String, message: String, analysis: SmsScamDetector.SmsAnalysisResult) {
        try {
            val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
            val db = FirebaseDatabase.getInstance().reference.child("users").child(userId).child("alerts")

            val alertData = mapOf(
                "sender" to sender,
                "body" to message,
                "riskLevel" to analysis.riskLevel,
                "category" to analysis.category,
                "score" to analysis.riskScore,
                "timestamp" to System.currentTimeMillis()
            )
            db.push().setValue(alertData)
        } catch (e: Exception) {
            Log.e("SecureConnect-Notif", "Firebase error: ${e.message}")
        }
    }
}