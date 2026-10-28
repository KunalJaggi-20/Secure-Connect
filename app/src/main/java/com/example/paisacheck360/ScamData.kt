package com.example.paisacheck360

data class ScamData(
    val sender: String = "Unknown",
    val body: String = "",
    val risk: String = "Safe",
    val timestamp: Long = System.currentTimeMillis()
)
