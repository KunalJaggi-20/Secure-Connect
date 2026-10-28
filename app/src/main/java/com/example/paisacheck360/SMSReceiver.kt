package com.example.paisacheck360

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SMSReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        Log.d("SecureConnect-SMS", "Raw SMS Broadcast Received: ${intent.action}")

        if (intent.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            for (sms in messages) {
                val sender = sms.displayOriginatingAddress ?: "Unknown"
                val body = sms.displayMessageBody ?: ""

                Log.d("SecureConnect-SMS", "Processing message from: $sender")

                val analysis = SmsScamDetector.analyze(body, sender)

                if (analysis.riskLevel != "SAFE") {
                    Log.w("SecureConnect-SMS", "🚨 SCAM DETECTED: [${analysis.riskLevel}] ${analysis.category} - $body")

                    // 1. Save to local storage for History & Dashboard stats
                    saveScamLocally(context, sender, body, analysis.riskLevel)

                    // 2. Sync to Firebase real-time database
                    syncToFirebase(sender, body, analysis)

                    // 3. Launch floating HUD overlay / notification (debounced)
                    if (SmsScamDetector.shouldTriggerPopup(body)) {
                        val popupIntent = Intent(context, ScamPopupService::class.java).apply {
                            putExtra("sender", sender)
                            putExtra("body", body)
                            putExtra("risk", analysis.riskLevel)
                            putExtra("category", analysis.category)
                            putExtra("explanation", analysis.explanation)
                            putExtra("advice", analysis.advice)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }

                        ContextCompat.startForegroundService(context, popupIntent)
                    }
                } else {
                    Log.d("SecureConnect-SMS", "Message evaluated as SAFE.")
                }
            }
        }
    }

    private fun saveScamLocally(context: Context, sender: String, body: String, risk: String) {
        try {
            val prefs = context.getSharedPreferences("SecureConnectPrefs", Context.MODE_PRIVATE)
            val gson = Gson()
            val existingJson = prefs.getString("scam_logs", "[]")
            val type = object : TypeToken<MutableList<ScamMessage>>() {}.type
            val list: MutableList<ScamMessage> = gson.fromJson(existingJson, type) ?: mutableListOf()

            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            val dateStr = sdf.format(Date())

            list.add(0, ScamMessage(sender = sender, text = body, date = dateStr, riskLevel = risk))
            prefs.edit().putString("scam_logs", gson.toJson(list.take(100))).apply()

            // Also record in ScamStorage for backward compatibility
            ScamStorage.addScamLog(context, body, risk)
        } catch (e: Exception) {
            Log.e("SecureConnect-SMS", "Failed to save scam log locally: ${e.message}")
        }
    }

    private fun syncToFirebase(sender: String, body: String, analysis: SmsScamDetector.SmsAnalysisResult) {
        try {
            val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
            val db = FirebaseDatabase.getInstance().reference.child("users").child(userId).child("alerts")
            val alertData = mapOf(
                "sender" to sender,
                "body" to body,
                "riskLevel" to analysis.riskLevel,
                "category" to analysis.category,
                "score" to analysis.riskScore,
                "timestamp" to System.currentTimeMillis()
            )
            db.push().setValue(alertData)
        } catch (e: Exception) {
            Log.e("SecureConnect-SMS", "Firebase sync error: ${e.message}")
        }
    }
}