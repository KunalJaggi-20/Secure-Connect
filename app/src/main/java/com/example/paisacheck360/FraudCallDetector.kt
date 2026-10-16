package com.example.paisacheck360

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.ContactsContract
import android.util.Log
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.tasks.await

data class CallRiskAssessment(
    val rawNumber: String,
    val normalizedNumber: String,
    val riskScore: Float, // 0.0 (safe) to 1.0 (critical scam)
    val riskLevel: RiskLevel,
    val callerTag: String,
    val category: String,
    val reasons: List<String>,
    val isScam: Boolean,
    val contactName: String? = null
)

object FraudCallDetector {

    private const val TAG = "FraudCallDetector"

    // High-risk country codes known for Wangiri (one-ring callback) & international cyber fraud
    private val HIGH_RISK_COUNTRY_CODES = mapOf(
        "+234" to "Nigeria",
        "+92" to "Pakistan",
        "+232" to "Sierra Leone",
        "+880" to "Bangladesh",
        "+252" to "Somalia",
        "+387" to "Bosnia",
        "+247" to "Ascension Island",
        "+676" to "Tonga",
        "+674" to "Nauru",
        "+685" to "Samoa",
        "+216" to "Tunisia",
        "+225" to "Ivory Coast",
        "+254" to "Kenya",
        "+375" to "Belarus",
        "+371" to "Latvia",
        "+93" to "Afghanistan"
    )

    // Curated high-threat phone patterns & reported numbers
    private val KNOWN_SCAM_PREFIXES = listOf(
        "140",      // TRAI official telemarketing series in India
        "0140",
        "+91140",
        "91140"
    )

    // Recognized emergency & service short codes in India
    private val SAFE_SHORT_CODES = setOf("100", "101", "102", "108", "112", "1930", "1091", "139")

    /**
     * Clean and normalize phone number for consistent evaluation.
     */
    fun normalizeNumber(raw: String): String {
        return raw.trim()
            .replace(" ", "")
            .replace("-", "")
            .replace("(", "")
            .replace(")", "")
    }

    /**
     * Check if the number is in the user's contacts.
     * Returns the contact name if found, or null.
     */
    fun getContactName(context: Context?, rawNumber: String): String? {
        if (context == null || rawNumber.isBlank()) return null
        return try {
            val uri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(rawNumber)
            )
            val projection = arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME)
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME)
                    if (nameIndex != -1) cursor.getString(nameIndex) else null
                } else null
            }
        } catch (e: Exception) {
            Log.d(TAG, "Contact lookup skipped: ${e.message}")
            null
        }
    }

    /**
     * Instant on-device heuristic analysis of a phone number.
     * Guarantees 0ms response time while ringing.
     */
    fun analyze(
        context: Context?,
        rawNumber: String,
        crowdsourcedScamCount: Long = 0L
    ): CallRiskAssessment {
        val normalized = normalizeNumber(rawNumber)

        // 1. Check Contact List first
        val contactName = getContactName(context, rawNumber)
        if (contactName != null) {
            return CallRiskAssessment(
                rawNumber = rawNumber,
                normalizedNumber = normalized,
                riskScore = 0.0f,
                riskLevel = RiskLevel.LOW,
                callerTag = contactName,
                category = "Saved Contact",
                reasons = listOf("Verified contact in your phonebook"),
                isScam = false,
                contactName = contactName
            )
        }

        // 2. Check emergency numbers
        if (SAFE_SHORT_CODES.contains(normalized)) {
            return CallRiskAssessment(
                rawNumber = rawNumber,
                normalizedNumber = normalized,
                riskScore = 0.0f,
                riskLevel = RiskLevel.LOW,
                callerTag = "Official Emergency Service ($normalized)",
                category = "Emergency",
                reasons = listOf("Verified national emergency short-code"),
                isScam = false
            )
        }

        // 3. Private / Hidden numbers
        if (normalized.isBlank() ||
            normalized.equals("Unknown", ignoreCase = true) ||
            normalized.equals("Private", ignoreCase = true) ||
            normalized == "-1" || normalized == "-2"
        ) {
            return CallRiskAssessment(
                rawNumber = if (rawNumber.isBlank()) "Unknown Caller" else rawNumber,
                normalizedNumber = "PRIVATE",
                riskScore = 0.70f,
                riskLevel = RiskLevel.HIGH,
                callerTag = "Hidden / Private Number",
                category = "Masked Caller",
                reasons = listOf("Caller ID has been concealed or spoofed"),
                isScam = true
            )
        }

        val reasons = mutableListOf<String>()
        var score = 0.0f
        var tag = "Unknown Caller"
        var category = "Unverified"

        // 4. Crowdsourced reports from Firebase
        if (crowdsourcedScamCount > 0) {
            val boost = minOf(0.40f, crowdsourcedScamCount * 0.10f)
            score += 0.65f + boost
            tag = "Community Flagged Scam ($crowdsourcedScamCount reports)"
            category = "Reported Fraud"
            reasons.add("Flagged by $crowdsourcedScamCount Secure Connect community members")
        }

        // 5. TRAI 140 Series Commercial Telemarketing Detection
        val is140 = KNOWN_SCAM_PREFIXES.any { normalized.startsWith(it) }
        if (is140) {
            score = maxOf(score, 0.85f)
            tag = "Commercial Telemarketer (TRAI 140)"
            category = "Telemarketing"
            reasons.add("Official TRAI 140 commercial marketing allocation (Promotions/Loans/Cards)")
        }

        // 6. International Wangiri Scam Corridors
        for ((code, country) in HIGH_RISK_COUNTRY_CODES) {
            val strippedCode = code.removePrefix("+")
            if (normalized.startsWith(code) || (normalized.startsWith(strippedCode) && !normalized.startsWith("91"))) {
                score = maxOf(score, 0.95f)
                tag = "🚨 High-Risk Foreign Gateway ($country)"
                category = "International Scam"
                reasons.add("Originates from high-risk fraud corridor ($country). Wangiri callback threat.")
                break
            }
        }

        // 7. Indian Mobile Number Formatting & Spoofing Heuristics
        // Extract national number (remove +91, 91, or leading 0 if 11 digits)
        val nationalNumber = when {
            normalized.startsWith("+91") && normalized.length == 13 -> normalized.substring(3)
            normalized.startsWith("91") && normalized.length == 12 -> normalized.substring(2)
            normalized.startsWith("0") && normalized.length == 11 -> normalized.substring(1)
            else -> normalized
        }

        if (nationalNumber.length == 10 && nationalNumber.all { it.isDigit() }) {
            val firstDigit = nationalNumber.first()
            // Valid Indian mobiles start with 6, 7, 8, or 9
            if (firstDigit !in listOf('6', '7', '8', '9')) {
                score = maxOf(score, 0.80f)
                tag = "Spoofed / Invalid Indian Number"
                category = "VoIP Spoofing"
                reasons.add("Invalid starting digit '$firstDigit' for Indian mobile lines")
            }

            // Repeated digits check (e.g. 9999999999, 8888888888)
            val uniqueDigits = nationalNumber.toSet().size
            if (uniqueDigits <= 2) {
                score = maxOf(score, 0.85f)
                tag = "Spoofed Repetitive Number"
                category = "VoIP Spoofing"
                reasons.add("Highly repetitive pattern ($uniqueDigits unique digits) common in robocalls")
            }

            // Sequential numbers (e.g. 1234567890, 9876543210)
            if (nationalNumber == "1234567890" || nationalNumber == "9876543210" || nationalNumber == "0123456789") {
                score = maxOf(score, 0.90f)
                tag = "Fake Sequential Number"
                category = "VoIP Spoofing"
                reasons.add("Sequential dummy number generated by computer dialer")
            }
        } else if (normalized.length in 4..7 && !SAFE_SHORT_CODES.contains(normalized)) {
            // Short, non-emergency codes
            score = maxOf(score, 0.70f)
            tag = "Abnormal Short Number"
            category = "Promotional / IVR"
            reasons.add("Short non-carrier number, typical of automated promotional dialers")
        } else if (normalized.length > 14 && !normalized.startsWith("+")) {
            // Unusually long numbers without international indicator
            score = maxOf(score, 0.75f)
            tag = "Irregular Long Number"
            category = "VoIP Dialing"
            reasons.add("Irregular digit count typical of multi-hop VoIP routing")
        }

        // Final score capping and classification
        val finalScore = minOf(1.0f, score)
        val level = when {
            finalScore >= 0.80f -> RiskLevel.CRITICAL
            finalScore >= 0.50f -> RiskLevel.HIGH
            finalScore >= 0.30f -> RiskLevel.MEDIUM
            else -> RiskLevel.LOW
        }

        if (reasons.isEmpty()) {
            reasons.add("Standard phone format. No fraud signatures detected.")
            tag = "Unknown Caller"
            category = "Unverified"
        }

        return CallRiskAssessment(
            rawNumber = rawNumber,
            normalizedNumber = normalized,
            riskScore = finalScore,
            riskLevel = level,
            callerTag = tag,
            category = category,
            reasons = reasons,
            isScam = finalScore >= 0.50f
        )
    }

    /**
     * Async evaluation that checks both on-device rules and Firebase crowdsourced DB.
     */
    suspend fun analyzeWithFirebase(context: Context?, rawNumber: String): CallRiskAssessment {
        val safeKey = rawNumber.replace(Regex("[.#$\\[\\]]"), "_")
        var scamCount = 0L

        try {
            val snapshot = FirebaseDatabase.getInstance()
                .reference
                .child("global_scam_reports")
                .child(safeKey)
                .get()
                .await()

            scamCount = snapshot.child("scam_count").getValue(Long::class.java) ?: 0L
        } catch (e: Exception) {
            Log.d(TAG, "Firebase query skipped or offline: ${e.message}")
        }

        return analyze(context, rawNumber, scamCount)
    }
}
