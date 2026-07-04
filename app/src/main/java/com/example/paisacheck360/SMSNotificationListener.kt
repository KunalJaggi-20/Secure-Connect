package com.example.paisacheck360

import android.content.Intent
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class SMSNotificationListener : NotificationListenerService() {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .build()

    private val apiUrl = "https://backend-k0ri.onrender.com/predict"

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val pkg = sbn?.packageName ?: return
        val extras = sbn.notification.extras
        val title = extras.getString("android.title") ?: "Unknown"
        val text = extras.getCharSequence("android.text")?.toString() ?: ""

        // 1. Target Messaging Apps
        val targetApps = listOf("messaging", "sms", "whatsapp", "telegram", "mms")
        val isTarget = targetApps.any { pkg.contains(it, ignoreCase = true) }

        if (isTarget && text.isNotBlank()) {
            val risk = analyzeLocalRisk(text)

            if (risk != "Low") {
                Log.d("SecureConnect-Notif", "🚨 Risk Detected ($risk): $text")

                // Log to Firebase for Dashboard
                saveAlertToFirebase(title, text, risk)

                // Trigger UI Popup
                triggerPopup(title, text, risk)

                // Secondary AI check
                runApiCheck(title, text)
            }
        }
    }

    private fun analyzeLocalRisk(msg: String): String {
        val m = msg.lowercase()
        val highRisk = listOf("loan", "kyc", "blocked", "otp", "verify", "pan", "electricity", "bank")
        val medRisk = listOf("win", "congrats", "offer", "prize", "gift", "http", "www")

        return when {
            highRisk.any { m.contains(it) } -> "High"
            medRisk.any { m.contains(it) } -> "Medium"
            else -> "Low"
        }
    }

    private fun triggerPopup(sender: String, message: String, risk: String) {
        val intent = Intent(this, ScamPopupService::class.java).apply {
            putExtra("sender", sender)
            putExtra("body", message)
            putExtra("risk", risk)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        ContextCompat.startForegroundService(this, intent)
    }

    private fun saveAlertToFirebase(sender: String, message: String, risk: String) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val db = FirebaseDatabase.getInstance().reference.child("users").child(userId).child("alerts")

        val alertData = mapOf(
            "sender" to sender,
            "body" to message,
            "riskLevel" to risk,
            "timestamp" to System.currentTimeMillis()
        )
        db.push().setValue(alertData)
    }

    private fun runApiCheck(sender: String, message: String) {
        val json = JSONObject().apply { put("message", message) }
        val body = RequestBody.create("application/json".toMediaTypeOrNull(), json.toString())
        val req = Request.Builder().url(apiUrl).post(body).build()

        client.newCall(req).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("SecureConnect-API", "API Failure")
            }

            override fun onResponse(call: Call, response: Response) {
                val res = response.body?.string()
                try {
                    if (JSONObject(res ?: "{}").optBoolean("is_scam", false)) {
                        triggerPopup(sender, message, "High")
                    }
                } catch (e: Exception) { }
            }
        })
    }
}