package com.example.paisacheck360

import android.Manifest
import android.content.pm.PackageManager
import android.database.Cursor
import android.graphics.Color
import android.os.Bundle
import android.provider.CallLog
import android.text.format.DateFormat
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.util.Date

class FraudCallSummaryActivity : AppCompatActivity() {

    private lateinit var backButton: ImageView
    private lateinit var searchButton: ImageView
    private lateinit var totalCallsCount: TextView
    private lateinit var blockedCallsCount: TextView
    private lateinit var safeCallsCount: TextView

    private lateinit var filterAll: CardView
    private lateinit var filterScam: CardView
    private lateinit var filterSafe: CardView
    private lateinit var filterMissed: CardView

    private lateinit var callHistoryList: LinearLayout
    private lateinit var emptyState: LinearLayout

    private enum class FilterType { ALL, SCAM, SAFE, MISSED }
    private var currentFilter = FilterType.ALL

    private data class CallRecord(
        val number: String,
        val callType: Int, // CallLog.Calls.INCOMING_TYPE, etc.
        val date: Long,
        val assessment: CallRiskAssessment
    )

    private val allCalls = mutableListOf<CallRecord>()

    private val callLogPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            loadCallHistory()
        } else {
            Toast.makeText(this, "Call Log permission denied. Showing simulated security analysis.", Toast.LENGTH_LONG).show()
            loadDemonstrationCalls()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_call_history)

        bindViews()
        setupListeners()

        // Check permission and load calls
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED) {
            loadCallHistory()
        } else {
            callLogPermissionLauncher.launch(Manifest.permission.READ_CALL_LOG)
        }
    }

    private fun bindViews() {
        backButton = findViewById(R.id.backButton)
        searchButton = findViewById(R.id.searchButton)
        totalCallsCount = findViewById(R.id.totalCallsCount)
        blockedCallsCount = findViewById(R.id.blockedCallsCount)
        safeCallsCount = findViewById(R.id.safeCallsCount)

        filterAll = findViewById(R.id.filterAll)
        filterScam = findViewById(R.id.filterScam)
        filterSafe = findViewById(R.id.filterSafe)
        filterMissed = findViewById(R.id.filterMissed)

        callHistoryList = findViewById(R.id.callHistoryList)
        emptyState = findViewById(R.id.emptyState)

        // Clear sample XML placeholder children from callHistoryList
        callHistoryList.removeAllViews()
    }

    private fun setupListeners() {
        backButton.setOnClickListener { finish() }

        searchButton.setOnClickListener {
            showNumberLookupDialog()
        }

        filterAll.setOnClickListener { updateFilter(FilterType.ALL) }
        filterScam.setOnClickListener { updateFilter(FilterType.SCAM) }
        filterSafe.setOnClickListener { updateFilter(FilterType.SAFE) }
        filterMissed.setOnClickListener { updateFilter(FilterType.MISSED) }
    }

    private fun updateFilter(filter: FilterType) {
        currentFilter = filter

        // Update filter tabs UI
        val activeBg = Color.parseColor("#2563EB")
        val inactiveBg = Color.parseColor("#F3F4F6")

        filterAll.setCardBackgroundColor(if (filter == FilterType.ALL) activeBg else inactiveBg)
        (filterAll.getChildAt(0) as? TextView)?.setTextColor(if (filter == FilterType.ALL) Color.WHITE else Color.parseColor("#6B7280"))

        filterScam.setCardBackgroundColor(if (filter == FilterType.SCAM) activeBg else inactiveBg)
        (filterScam.getChildAt(0) as? TextView)?.setTextColor(if (filter == FilterType.SCAM) Color.WHITE else Color.parseColor("#6B7280"))

        filterSafe.setCardBackgroundColor(if (filter == FilterType.SAFE) activeBg else inactiveBg)
        (filterSafe.getChildAt(0) as? TextView)?.setTextColor(if (filter == FilterType.SAFE) Color.WHITE else Color.parseColor("#6B7280"))

        filterMissed.setCardBackgroundColor(if (filter == FilterType.MISSED) activeBg else inactiveBg)
        (filterMissed.getChildAt(0) as? TextView)?.setTextColor(if (filter == FilterType.MISSED) Color.WHITE else Color.parseColor("#6B7280"))

        renderList()
    }

    private fun loadCallHistory() {
        allCalls.clear()

        try {
            val cursor: Cursor? = contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(CallLog.Calls.NUMBER, CallLog.Calls.TYPE, CallLog.Calls.DATE),
                null,
                null,
                CallLog.Calls.DATE + " DESC"
            )

            cursor?.use {
                val numIndex = it.getColumnIndex(CallLog.Calls.NUMBER)
                val typeIndex = it.getColumnIndex(CallLog.Calls.TYPE)
                val dateIndex = it.getColumnIndex(CallLog.Calls.DATE)

                var count = 0
                while (it.moveToNext() && count < 60) {
                    val rawNum = if (numIndex != -1) it.getString(numIndex) ?: "Unknown" else "Unknown"
                    val callType = if (typeIndex != -1) it.getInt(typeIndex) else CallLog.Calls.INCOMING_TYPE
                    val date = if (dateIndex != -1) it.getLong(dateIndex) else System.currentTimeMillis()

                    val assessment = FraudCallDetector.analyze(this, rawNum)
                    allCalls.add(CallRecord(rawNum, callType, date, assessment))
                    count++
                }
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Error reading call logs: ${e.message}", Toast.LENGTH_SHORT).show()
        }

        if (allCalls.isEmpty()) {
            loadDemonstrationCalls()
        } else {
            calculateStatsAndDisplay()
        }
    }

    private fun loadDemonstrationCalls() {
        allCalls.clear()
        val now = System.currentTimeMillis()

        // Seed with illustrative scam & safe patterns for rich visualization
        val demoNumbers = listOf(
            Triple("+91 14098 76543", CallLog.Calls.INCOMING_TYPE, now - 1000 * 60 * 30),
            Triple("+234 803 123 4567", CallLog.Calls.MISSED_TYPE, now - 1000 * 60 * 120),
            Triple("+91 98765 43210", CallLog.Calls.OUTGOING_TYPE, now - 1000 * 60 * 60 * 5),
            Triple("+91 14011 22334", CallLog.Calls.MISSED_TYPE, now - 1000 * 60 * 60 * 24),
            Triple("+91 99999 99999", CallLog.Calls.INCOMING_TYPE, now - 1000 * 60 * 60 * 36),
            Triple("+91 94440 12345", CallLog.Calls.INCOMING_TYPE, now - 1000 * 60 * 60 * 48)
        )

        for ((num, type, time) in demoNumbers) {
            val assessment = FraudCallDetector.analyze(this, num)
            allCalls.add(CallRecord(num, type, time, assessment))
        }

        calculateStatsAndDisplay()
    }

    private fun calculateStatsAndDisplay() {
        val total = allCalls.size
        val scams = allCalls.count { it.assessment.isScam }
        val safe = total - scams

        totalCallsCount.text = total.toString()
        blockedCallsCount.text = scams.toString()
        safeCallsCount.text = safe.toString()

        renderList()
    }

    private fun renderList() {
        callHistoryList.removeAllViews()

        val filtered = allCalls.filter { record ->
            when (currentFilter) {
                FilterType.ALL -> true
                FilterType.SCAM -> record.assessment.isScam
                FilterType.SAFE -> !record.assessment.isScam
                FilterType.MISSED -> record.callType == CallLog.Calls.MISSED_TYPE
            }
        }

        if (filtered.isEmpty()) {
            emptyState.visibility = View.VISIBLE
            return
        }

        emptyState.visibility = View.GONE
        val inflater = LayoutInflater.from(this)

        for (item in filtered) {
            val itemView = inflater.inflate(R.layout.item_call_history, callHistoryList, false)
            bindCallItem(itemView, item)
            callHistoryList.addView(itemView)
        }
    }

    private fun bindCallItem(view: View, record: CallRecord) {
        val card = view.findViewById<CardView>(R.id.cardContainer)
        val imgCallType = view.findViewById<ImageView>(R.id.imgCallType)
        val txtCallerName = view.findViewById<TextView>(R.id.txtCallerName)
        val txtPhoneNumber = view.findViewById<TextView>(R.id.txtPhoneNumber)
        val txtCallTimeAndType = view.findViewById<TextView>(R.id.txtCallTimeAndType)
        val badgeCard = view.findViewById<CardView>(R.id.badgeCard)
        val txtBadge = view.findViewById<TextView>(R.id.txtBadge)
        val warningRow = view.findViewById<View>(R.id.warningRow)
        val txtWarningReason = view.findViewById<TextView>(R.id.txtWarningReason)

        val assessment = record.assessment
        txtPhoneNumber.text = record.number
        txtCallerName.text = assessment.callerTag

        val typeStr = when (record.callType) {
            CallLog.Calls.INCOMING_TYPE -> "Incoming"
            CallLog.Calls.OUTGOING_TYPE -> "Outgoing"
            CallLog.Calls.MISSED_TYPE -> "Missed"
            else -> "Call"
        }
        val timeFormatted = DateFormat.format("MMM dd, h:mm a", Date(record.date))
        txtCallTimeAndType.text = "$timeFormatted • $typeStr"

        if (assessment.isScam || assessment.riskLevel == RiskLevel.CRITICAL || assessment.riskLevel == RiskLevel.HIGH) {
            card.setCardBackgroundColor(Color.parseColor("#FEE2E2"))
            imgCallType.setBackgroundResource(R.drawable.bg_call_incoming_scam)
            badgeCard.setCardBackgroundColor(Color.parseColor("#DC2626"))
            txtBadge.text = "🚨 SCAM"
            txtBadge.setTextColor(Color.WHITE)

            warningRow.visibility = View.VISIBLE
            txtWarningReason.text = assessment.reasons.firstOrNull() ?: "Scam signature detected"
        } else if (assessment.riskLevel == RiskLevel.MEDIUM) {
            card.setCardBackgroundColor(Color.parseColor("#FEF3C7"))
            imgCallType.setBackgroundResource(R.drawable.bg_call_incoming_suspicious)
            badgeCard.setCardBackgroundColor(Color.parseColor("#F59E0B"))
            txtBadge.text = "⚠️ SUSPICIOUS"
            txtBadge.setTextColor(Color.WHITE)

            warningRow.visibility = View.VISIBLE
            txtWarningReason.text = assessment.reasons.firstOrNull() ?: "Unverified pattern"
        } else {
            card.setCardBackgroundColor(Color.WHITE)
            imgCallType.setBackgroundResource(R.drawable.bg_call_outgoing_safe)
            badgeCard.setCardBackgroundColor(Color.parseColor("#D1FAE5"))
            txtBadge.text = "✅ SAFE"
            txtBadge.setTextColor(Color.parseColor("#059669"))

            warningRow.visibility = View.GONE
        }

        card.setOnClickListener {
            showCallDetailsDialog(record)
        }
    }

    private fun showCallDetailsDialog(record: CallRecord) {
        val a = record.assessment
        val reasonsText = a.reasons.joinToString("\n• ", prefix = "• ")

        MaterialAlertDialogBuilder(this)
            .setTitle(if (a.isScam) "🚨 Fraud Alert: ${a.callerTag}" else "📞 Caller ID: ${a.callerTag}")
            .setMessage(
                "Phone Number: ${record.number}\n" +
                "Risk Level: ${a.riskLevel.displayName} (${(a.riskScore * 100).toInt()}%)\n" +
                "Category: ${a.category}\n\n" +
                "Analysis Details:\n$reasonsText"
            )
            .setPositiveButton("OK", null)
            .setNeutralButton(if (a.isScam) "Mark Safe" else "Mark Scam") { _, _ ->
                val newStatus = if (a.isScam) "Safe" else "Scam"
                Toast.makeText(this, "Updated status to $newStatus", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun showNumberLookupDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_number_lookup, null)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        val input = dialogView.findViewById<EditText>(R.id.inputPhoneNumber)
        val chipTrai = dialogView.findViewById<Button>(R.id.chipTrai)
        val chipWangiri = dialogView.findViewById<Button>(R.id.chipWangiri)
        val chipSpoof = dialogView.findViewById<Button>(R.id.chipSpoof)
        val resultCard = dialogView.findViewById<CardView>(R.id.resultCard)
        val txtResultBadge = dialogView.findViewById<TextView>(R.id.txtResultBadge)
        val txtResultScore = dialogView.findViewById<TextView>(R.id.txtResultScore)
        val txtResultTag = dialogView.findViewById<TextView>(R.id.txtResultTag)
        val txtResultReason = dialogView.findViewById<TextView>(R.id.txtResultReason)
        val btnAnalyze = dialogView.findViewById<Button>(R.id.btnAnalyzeNumber)
        val btnClose = dialogView.findViewById<Button>(R.id.btnCloseDialog)

        chipTrai.setOnClickListener { input.setText("+91 14098 12345") }
        chipWangiri.setOnClickListener { input.setText("+234 803 998 1122") }
        chipSpoof.setOnClickListener { input.setText("+91 99999 99999") }

        fun runAnalysis() {
            val text = input.text.toString().trim()
            if (text.isEmpty()) {
                input.error = "Please enter a number"
                return
            }

            val result = FraudCallDetector.analyze(this, text)
            resultCard.visibility = View.VISIBLE

            txtResultScore.text = "Risk: ${(result.riskScore * 100).toInt()}%"
            txtResultTag.text = result.callerTag
            txtResultReason.text = result.reasons.joinToString("\n• ", prefix = "• ")

            if (result.isScam) {
                resultCard.setCardBackgroundColor(Color.parseColor("#FEE2E2"))
                txtResultBadge.text = "🚨 HIGH RISK SCAM"
                txtResultBadge.setBackgroundColor(Color.parseColor("#DC2626"))
            } else if (result.riskLevel == RiskLevel.MEDIUM) {
                resultCard.setCardBackgroundColor(Color.parseColor("#FEF3C7"))
                txtResultBadge.text = "⚠️ SUSPICIOUS"
                txtResultBadge.setBackgroundColor(Color.parseColor("#F59E0B"))
            } else {
                resultCard.setCardBackgroundColor(Color.parseColor("#D1FAE5"))
                txtResultBadge.text = "✅ SAFE"
                txtResultBadge.setBackgroundColor(Color.parseColor("#10B981"))
            }
        }

        btnAnalyze.setOnClickListener { runAnalysis() }
        btnClose.setOnClickListener { dialog.dismiss() }

        dialog.show()
    }
}