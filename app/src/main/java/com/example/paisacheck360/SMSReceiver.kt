package com.example.paisacheck360

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Telephony
import android.util.Log
import androidx.core.content.ContextCompat

class SMSReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        Log.d("SecureConnect-SMS", "Raw SMS Broadcast Received")

        if (intent.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            for (sms in messages) {
                val sender = sms.displayOriginatingAddress ?: "Unknown"
                val body = sms.displayMessageBody ?: ""

                Log.d("SecureConnect-SMS", "Processing message from: $sender")

                if (isScam(body)) {
                    Log.w("SecureConnect-SMS", "🚨 SCAM KEYWORDS DETECTED: $body")

                    val popupIntent = Intent(context, ScamPopupService::class.java).apply {
                        putExtra("sender", sender)
                        putExtra("body", body)
                        putExtra("risk", "High") // Receiver defaults to High for keyword hits
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }

                    // Must use Foreground Service to bypass background restrictions
                    ContextCompat.startForegroundService(context, popupIntent)
                }
            }
        }
    }

    private fun isScam(msg: String): Boolean {
        val lower = msg.lowercase()
        // Expanded keyword list for better detection
        val keywords = listOf("win", "bank", "otp", "kyc", "blocked", "suspend", "loan", "click", "gift", "verify")
        return keywords.any { lower.contains(it) }
    }
}