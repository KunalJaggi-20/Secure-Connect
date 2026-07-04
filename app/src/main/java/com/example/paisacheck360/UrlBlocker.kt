package com.example.paisacheck360

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast

object UrlBlocker {

    private val dangerMap = mutableMapOf<String, UrlAnalyzer.AnalysisResult>()

    fun registerIncomingUrl(result: UrlAnalyzer.AnalysisResult) {
        dangerMap[result.url] = result
        Log.d("UrlBlocker", "Registered URL for protection: ${result.url}")
    }

    fun openUrlWithProtection(context: Context, url: String) {
        // 1. Basic Validation to prevent crashes
        if (url.isBlank()) {
            Toast.makeText(context, "URL is empty", Toast.LENGTH_SHORT).show()
            return
        }

        // Ensure URL starts with a protocol so Intent(ACTION_VIEW) doesn't crash
        val formattedUrl = if (!url.startsWith("http://") && !url.startsWith("https://")) {
            "http://$url"
        } else {
            url
        }

        val result = dangerMap[url] ?: UrlAnalyzer.analyzeUrl(formattedUrl)

        when (result.level) {
            UrlAnalyzer.RiskLevel.DANGEROUS, UrlAnalyzer.RiskLevel.SUSPICIOUS -> {
                // 2. Block and show the Warning Screen
                val i = Intent(context, BlockedUrlActivity::class.java).apply {
                    putExtra("url", formattedUrl)
                    putStringArrayListExtra("reasons", ArrayList(result.reasons))
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(i)
            }

            UrlAnalyzer.RiskLevel.SAFE -> {
                // 3. Open safely in browser
                try {
                    val i = Intent(Intent.ACTION_VIEW, Uri.parse(formattedUrl)).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(i)
                } catch (e: Exception) {
                    Log.e("UrlBlocker", "Could not open browser", e)
                    Toast.makeText(context, "No browser found to open link", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}