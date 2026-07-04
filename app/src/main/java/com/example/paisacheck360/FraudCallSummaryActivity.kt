package com.example.paisacheck360

import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.database.*

class FraudCallSummaryActivity : AppCompatActivity() {

    // These variables must be initialized after setContentView
    private lateinit var callListContainer: LinearLayout
    private lateinit var btnScanNow: Button
    private lateinit var txtScamsFlagged: TextView
    private lateinit var db: DatabaseReference

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.fraud_call_detector)

        // FIXED: Binding the IDs from XML to Kotlin variables
        callListContainer = findViewById(R.id.callListContainer)
        btnScanNow = findViewById(R.id.btn_scan_now)
        txtScamsFlagged = findViewById(R.id.txt_scams_flagged)

        val androidID = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID) ?: "guest"

        // Firebase path from your current configuration
        db = FirebaseDatabase.getInstance("https://sbtest-9acea-default-rtdb.firebaseio.com")
            .reference.child("users").child(androidID).child("call_feedback")

        btnScanNow.setOnClickListener {
            Toast.makeText(this, "Scanning for fraud...", Toast.LENGTH_SHORT).show()
            loadRealData()
        }

        loadRealData()
    }

    private fun loadRealData() {
        db.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                callListContainer.removeAllViews()
                var scamCount = 0

                if (!snapshot.exists()) {
                    txtScamsFlagged.text = "0 Scams Flagged"
                    return
                }

                for (child in snapshot.children) {
                    val number = child.key ?: "Unknown"
                    val status = child.child("status").getValue(String::class.java) ?: "Unknown"
                    val time = child.child("timestamp").getValue(String::class.java) ?: ""

                    if (status.lowercase() == "scam") scamCount++
                    addCallView(number, status, time)
                }
                txtScamsFlagged.text = "$scamCount Scams Flagged"
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("FirebaseError", error.message)
            }
        })
    }

    private fun addCallView(number: String, status: String, time: String) {
        val inflater = LayoutInflater.from(this)
        // Fallback to simple_list_item_2 if you don't have a custom card layout yet
        val itemView = inflater.inflate(android.R.layout.simple_list_item_2, callListContainer, false)

        val text1 = itemView.findViewById<TextView>(android.R.id.text1)
        val text2 = itemView.findViewById<TextView>(android.R.id.text2)

        text1.text = "📞 $number"
        text2.text = "Status: $status | $time"

        if (status.lowercase() == "scam") {
            text1.setTextColor(Color.RED)
            text2.text = "🚨 High Risk Fraud"
        } else {
            text1.setTextColor(Color.parseColor("#2E7D32"))
        }

        callListContainer.addView(itemView)
    }
}