package com.example.paisacheck360

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.content.ContextCompat

class CallReceiver : BroadcastReceiver() {

    private val TAG = "SecureConnect-Call"

    override fun onReceive(context: Context, intent: Intent) {
        val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
        val incomingNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)

        val serviceIntent = Intent(context, CallPopupService::class.java)

        when (state) {
            TelephonyManager.EXTRA_STATE_RINGING -> {
                val callerNumber = if (!incomingNumber.isNullOrBlank()) incomingNumber else "Unknown"
                Log.d(TAG, "🔔 RINGING: Incoming scam check for $callerNumber")

                serviceIntent.putExtra("callerNumber", callerNumber)
                serviceIntent.putExtra("isRinging", true)
                serviceIntent.putExtra("isFeedbackMode", false)

                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        ContextCompat.startForegroundService(context, serviceIntent)
                    } else {
                        context.startService(serviceIntent)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to start CallPopupService: ${e.message}")
                }
            }
            TelephonyManager.EXTRA_STATE_OFFHOOK, TelephonyManager.EXTRA_STATE_IDLE -> {
                Log.d(TAG, "Call state $state: Dismissing overlay")
                try {
                    context.stopService(serviceIntent)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to stop CallPopupService: ${e.message}")
                }
            }
        }
    }
}