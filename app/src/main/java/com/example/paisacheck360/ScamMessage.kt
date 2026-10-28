package com.example.paisacheck360

// Simple data class to hold scam SMS info with full null-safety
data class ScamMessage(
    val sender: String? = "Unknown",
    val text: String? = "",
    val date: String? = "",
    val riskLevel: String? = "Safe"
)
