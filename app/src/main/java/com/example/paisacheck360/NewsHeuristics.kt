package com.example.paisacheck360

object NewsHeuristics {

    fun looksLikeNewsOrForward(message: String): Boolean {
        val text = message.lowercase()

        val newsWords = listOf(
            "breaking",
            "news",
            "viral",
            "whatsapp",
            "forward this",
            "share with everyone",
            "urgent",
            "alert",
            "government has announced",
            "govt has announced",
            "pm has declared",
            "modi ji",
            "new scheme",
            "subsidy",
            "free",
            "ration",
            "petrol price",
            "scholarship",
            "reservation",
            "lockdown",
            "curfew",
            "section 144",
            "big announcement"
        )

        return newsWords.any { text.contains(it) }
    }
}
