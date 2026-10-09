package com.example.kapsama20

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant

class UploadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val endpoint = BuildConfig.SUPABASE_URL.trimEnd('/')
        val key = BuildConfig.SUPABASE_ANON_KEY
        if (!endpoint.startsWith("https://") || key.isBlank()) {
            return@withContext Result.failure(workDataOf("error" to "Supabase bağlantısı ayarlanmadı."))
        }
        val studentId = inputData.getString(KEY_STUDENT_ID)
            ?: return@withContext Result.failure(workDataOf("error" to "Giriş yapan öğrenci bilgisi eksik."))
        var accessToken = AuthRepository.currentAccessToken(applicationContext)
            ?: return@withContext Result.retry()
        val body = JSONObject().apply {
            put("ogrenci_id", studentId)
            put("zaman", Instant.ofEpochMilli(inputData.getLong(KEY_TIME, 0L)).toString())
            put("enlem", 0.0)
            put("boylam", 0.0)
            put("hiz_mbps", inputData.getDouble("mbps", Double.NaN).takeIf { it.isFinite() } ?: JSONObject.NULL)
            for (name in listOf(KEY_RSRP, KEY_SINR, KEY_RSRQ)) {
                put(name, inputData.getInt(name, Int.MIN_VALUE).takeUnless { it == Int.MIN_VALUE } ?: JSONObject.NULL)
            }
        }
        try {
            var response = send(endpoint, key, accessToken, body)
            if (response.code == 401) {
                accessToken = AuthRepository.currentAccessToken(applicationContext, forceRefresh = true) ?: return@withContext Result.retry()
                response = send(endpoint, key, accessToken, body)
            }
            when (val code = response.code) {
                in 200..299 -> Result.success()
                408, 429, in 500..599 -> Result.retry()
                else -> {
                    val pgCode = try { JSONObject(response.body).optString("code") } catch (_: Exception) { "" }
                    Result.failure(workDataOf("error" to "Supabase HTTP $code ($pgCode): tablo alanlarını ve yazma izinlerini kontrol edin."))
                }
            }
        } catch (_: IOException) {
            Result.retry()
        }
    }

    private fun send(endpoint: String, key: String, accessToken: String, body: JSONObject): Response {
        val connection = URL("$endpoint/rest/v1/olcumler").openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 15_000
            connection.readTimeout = 15_000
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.setRequestProperty("apikey", key)
            connection.setRequestProperty("Authorization", "Bearer $accessToken")
            connection.setRequestProperty("Prefer", "return=minimal")
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            return Response(code, stream?.bufferedReader()?.use { it.readText().take(2048) }.orEmpty())
        } finally {
            connection.disconnect()
        }
    }

    private data class Response(val code: Int, val body: String)

    companion object {
        const val KEY_STUDENT_ID = "student_id"
        const val KEY_RSRP = "rsrp"
        const val KEY_SINR = "sinr"
        const val KEY_RSRQ = "rsrq"
        const val KEY_TIME = "time"
    }
}
