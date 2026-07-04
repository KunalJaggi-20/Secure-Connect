package com.example.paisacheck360

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

data class FakeNewsResult(
    val ok: Boolean,
    val isFakeNews: Boolean,
    val confidence: Double,
    val category: String?,
    val reason: String?
)

object FakeNewsApi {

    // EMULATOR -> backend on PC
    private const val BASE_URL = "http://10.0.2.2:3000"

    private val client = OkHttpClient()

    suspend fun checkSmsForFakeNews(smsText: String): FakeNewsResult =
        withContext(Dispatchers.IO) {
            try {
                val json = JSONObject()
                json.put("smsText", smsText)

                val body = json.toString()
                    .toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())

                val request = Request.Builder()
                    .url("$BASE_URL/check-sms-fake-news")
                    .post(body)
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        return@withContext FakeNewsResult(
                            ok = false,
                            isFakeNews = false,
                            confidence = 0.0,
                            category = null,
                            reason = "HTTP ${response.code}"
                        )
                    }

                    val resp = response.body?.string() ?: "{}"
                    val obj = JSONObject(resp)

                    FakeNewsResult(
                        ok = obj.optBoolean("ok", false),
                        isFakeNews = obj.optBoolean("is_fake_news", false),
                        confidence = obj.optDouble("confidence", 0.0),
                        category = obj.optString("category", null),
                        reason = obj.optString("reason", null)
                    )
                }
            } catch (e: Exception) {
                FakeNewsResult(false, false, 0.0, null, e.message)
            }
        }
}
