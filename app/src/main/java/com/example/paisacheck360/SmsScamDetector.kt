package com.example.paisacheck360

import android.util.Patterns
import java.util.Locale
import java.util.regex.Pattern

/**
 * High-performance on-device SMS & Message Scam Detection Engine.
 * Evaluates semantic coercion, financial urgency, phishing URLs,
 * known Indian telecom fraud patterns, and brand impersonation.
 */
object SmsScamDetector {

    data class SmsAnalysisResult(
        val riskLevel: String, // "CRITICAL", "HIGH", "MEDIUM", "SAFE"
        val riskScore: Int,    // 0 to 100
        val category: String,
        val matchedTriggers: List<String>,
        val detectedUrls: List<String>,
        val explanation: String,
        val advice: String
    )

    @Volatile
    private var lastAlertText: String = ""
    @Volatile
    private var lastAlertTimestamp: Long = 0L

    @Synchronized
    fun shouldTriggerPopup(message: String): Boolean {
        val now = System.currentTimeMillis()
        if (message.trim().equals(lastAlertText.trim(), ignoreCase = true) && (now - lastAlertTimestamp) < 6000L) {
            return false
        }
        lastAlertText = message.trim()
        lastAlertTimestamp = now
        return true
    }

    // Common Indian Financial and Telecom Scam Triggers
    private val KYC_BANKING_TRIGGERS = listOf(
        "kyc", "pan card", "aadhaar", "pan update", "account suspended", "account blocked",
        "debit card block", "credit card limit", "unauthorized transaction", "yono",
        "netbanking", "verify account", "account deactivated", "dormant account",
        "sim block", "sim deactivate", "trai notice"
    )

    private val UTILITY_FRAUD_TRIGGERS = listOf(
        "electricity bill", "power disconnect", "power cut", "electricity office",
        "tonight at 9:30", "tonight at 10", "power supply will be disconnected",
        "update electricity", "contact electricity officer"
    )

    private val REWARD_UPI_TRIGGERS = listOf(
        "kbc", "lottery", "congratulations you won", "claim reward", "cashback",
        "scratch card", "won 25,00,000", "won 25 lakh", "lucky draw", "claim prize",
        "send rs 1", "receive 50000", "spin and win"
    )

    private val JOB_TASK_TRIGGERS = listOf(
        "part time job", "work from home", "daily 3000", "daily 5000", "youtube like",
        "telegram task", "earn per day", "hotel review job"
    )

    private val URGENCY_TRIGGERS = listOf(
        "immediately", "within 24 hours", "urgent action", "final notice",
        "otherwise your", "legal action", "police complaint", "penalty of"
    )

    private val SUSPICIOUS_URL_SHORTENERS = listOf(
        "bit.ly", "tinyurl.com", "is.gd", "cutt.ly", "t.me", "wa.me", "ngrok.io",
        "rb.gy", "shorturl.at", "ow.ly", "rebrand.ly", "page.link"
    )

    fun analyze(message: String, sender: String = ""): SmsAnalysisResult {
        if (message.isBlank()) {
            return SmsAnalysisResult(
                riskLevel = "SAFE",
                riskScore = 0,
                category = "Empty Message",
                matchedTriggers = emptyList(),
                detectedUrls = emptyList(),
                explanation = "No content found to analyze.",
                advice = "No action needed."
            )
        }

        val lowerMsg = message.lowercase(Locale.ROOT)
        val lowerSender = sender.lowercase(Locale.ROOT)
        val matchedTriggers = mutableListOf<String>()
        var score = 0

        // 1. Extract URLs
        val detectedUrls = extractUrls(message)
        var hasShortenedUrl = false
        var hasApkLink = false
        var hasIpUrl = false

        for (url in detectedUrls) {
            val lowerUrl = url.lowercase(Locale.ROOT)
            if (lowerUrl.contains(".apk")) {
                hasApkLink = true
                score += 45
                matchedTriggers.add("Malicious APK Download Link")
            }
            if (SUSPICIOUS_URL_SHORTENERS.any { lowerUrl.contains(it) }) {
                hasShortenedUrl = true
                score += 30
                matchedTriggers.add("Masked/Shortened URL")
            }
            // Check IP based URLs (e.g. http://192.168... or http://45.33...)
            if (Pattern.compile("https?://\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}").matcher(lowerUrl).find()) {
                hasIpUrl = true
                score += 40
                matchedTriggers.add("Direct IP Address Link")
            }
        }

        // 2. Check Category Triggers
        var isKycBanking = false
        for (trigger in KYC_BANKING_TRIGGERS) {
            if (lowerMsg.contains(trigger)) {
                matchedTriggers.add(trigger)
                score += 25
                isKycBanking = true
            }
        }

        var isUtilityScam = false
        for (trigger in UTILITY_FRAUD_TRIGGERS) {
            if (lowerMsg.contains(trigger)) {
                matchedTriggers.add(trigger)
                score += 35
                isUtilityScam = true
            }
        }

        var isRewardLottery = false
        for (trigger in REWARD_UPI_TRIGGERS) {
            if (lowerMsg.contains(trigger)) {
                matchedTriggers.add(trigger)
                score += 30
                isRewardLottery = true
            }
        }

        var isJobScam = false
        for (trigger in JOB_TASK_TRIGGERS) {
            if (lowerMsg.contains(trigger)) {
                matchedTriggers.add(trigger)
                score += 25
                isJobScam = true
            }
        }

        // 3. Urgency & Coercion Multiplier
        var hasUrgency = false
        for (trigger in URGENCY_TRIGGERS) {
            if (lowerMsg.contains(trigger)) {
                matchedTriggers.add(trigger)
                score += 15
                hasUrgency = true
            }
        }

        // 4. Sender Analysis (e.g., standard 10-digit mobile number pretending to be a bank)
        val isPersonalNumberSender = sender.isNotBlank() && sender.matches(Regex("^[+]?[0-9]{10,13}$"))
        if (isPersonalNumberSender && (isKycBanking || isUtilityScam)) {
            score += 25
            matchedTriggers.add("Sender is an unauthorized mobile number (impersonation)")
        }

        // Bound score to 0..100
        val finalScore = score.coerceIn(0, 100)

        // Categorize Risk
        val (level, category, explanation, advice) = when {
            finalScore >= 65 || hasApkLink || (isKycBanking && detectedUrls.isNotEmpty()) || (isUtilityScam && hasUrgency) -> {
                Quad(
                    "CRITICAL",
                    if (isUtilityScam) "Electricity Disconnection Scam"
                    else if (isKycBanking) "Bank / KYC Phishing Scam"
                    else if (hasApkLink) "Malicious APK Dropper Scam"
                    else "High-Risk Fraud Attempt",
                    "This message uses high-pressure intimidation or deceptive links to steal bank credentials or install spyware.",
                    "DO NOT click any links, do not share OTP, and do not call the sender. Block and report immediately."
                )
            }
            finalScore >= 40 || isRewardLottery || isJobScam || hasShortenedUrl -> {
                Quad(
                    "HIGH",
                    if (isRewardLottery) "Fake Lottery / Cashback Scam"
                    else if (isJobScam) "Part-time Task / Telegram Scam"
                    else "Suspicious Promotion / Phishing",
                    "This message promotes fraudulent schemes or unverified links designed to deceive users.",
                    "Do not transfer money or enter UPI PINs. Legitimate banks/companies never ask for advance payments."
                )
            }
            finalScore >= 20 || detectedUrls.isNotEmpty() -> {
                Quad(
                    "MEDIUM",
                    "Unverified Promotional SMS",
                    "Message contains unverified commercial links or offers from unknown senders.",
                    "Verify the sender's official credentials before taking any action."
                )
            }
            else -> {
                Quad(
                    "SAFE",
                    "Safe Communication",
                    "No suspicious coercion, malicious URLs, or banking fraud patterns were detected.",
                    "Standard precautions apply."
                )
            }
        }

        return SmsAnalysisResult(
            riskLevel = level,
            riskScore = finalScore,
            category = category,
            matchedTriggers = matchedTriggers.distinct(),
            detectedUrls = detectedUrls,
            explanation = explanation,
            advice = advice
        )
    }

    private fun extractUrls(text: String): List<String> {
        val urls = mutableListOf<String>()
        val matcher = Patterns.WEB_URL.matcher(text)
        while (matcher.find()) {
            val url = matcher.group()
            if (url != null && url.isNotBlank()) {
                urls.add(url.trim())
            }
        }
        return urls
    }

    private data class Quad(val level: String, val category: String, val explanation: String, val advice: String)
}
