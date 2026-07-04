package com.example.paisacheck360

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.content.ContextCompat

class CallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
        val number = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)

        val serviceIntent = Intent(context, CallPopupService::class.java)

        when (state) {
            TelephonyManager.EXTRA_STATE_RINGING -> {
                // REAL-TIME TRIGGER: Show popup while ringing
                if (number != null) {
                    Log.d("SecureConnect-Call", "🔔 RINGING: Incoming scam check for $number")
                    serviceIntent.putExtra("callerNumber", number)
                    serviceIntent.putExtra("isRinging", true)
                    ContextCompat.startForegroundService(context, serviceIntent)
                }
            }
            TelephonyManager.EXTRA_STATE_OFFHOOK, TelephonyManager.EXTRA_STATE_IDLE -> {
                // CLEANUP: Remove popup when call is answered or missed
                Log.d("SecureConnect-Call", "Call state $state: Removing popup")
                context.stopService(serviceIntent)
            }
        }
    }
}