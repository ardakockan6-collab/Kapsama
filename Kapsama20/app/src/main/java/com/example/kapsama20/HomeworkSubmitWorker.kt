package com.example.kapsama20

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class HomeworkSubmitWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val endpoint = BuildConfig.SUPABASE_URL.trimEnd('/')
        val key = BuildConfig.SUPABASE_ANON_KEY
        val homeworkId = inputData.getLong(KEY_HOMEWORK_ID, -1L)
        val studentId = inputData.getString(KEY_STUDENT_ID).orEmpty()
        if (!endpoint.startsWith("https://") || key.isBlank() || homeworkId < 0 || studentId.isBlank()) {
            return@withContext Result.failure(workDataOf("error" to "Ödev gönderim bilgileri eksik."))
        }

        try {
            val encodedStudent = URLEncoder.encode(studentId, Charsets.UTF_8.name())
            val filter = "odev_id=eq.$homeworkId&ogrenci_id=eq.$encodedStudent"
            val exists = request(
                url = "$endpoint/rest/v1/odev_durumu?select=odev_id&$filter",
                key = key,
                method = "GET"
            ).let { response -> response.code in 200..299 && JSONArray(response.body).length() > 0 }

            val response = if (exists) {
                request(
                    url = "$endpoint/rest/v1/odev_durumu?$filter",
                    key = key,
                    method = "PATCH",
                    body = JSONObject().put("acildi", true).toString()
                )
            } else {
                request(
                    url = "$endpoint/rest/v1/odev_durumu",
                    key = key,
                    method = "POST",
                    body = JSONObject()
                        .put("odev_id", homeworkId)
                        .put("ogrenci_id", studentId)
                        .put("acildi", true)
                        .toString()
                )
            }

            when (response.code) {
                in 200..299 -> Result.success()
                408, 429, in 500..599 -> Result.retry()
                else -> Result.failure(workDataOf("error" to "Ödev gönderilemedi (HTTP ${response.code})."))
            }
        } catch (_: IOException) {
            Result.retry()
        } catch (_: Exception) {
            Result.failure(workDataOf("error" to "Ödev gönderilemedi."))
        }
    }

    private fun request(url: String, key: String, method: String, body: String? = null): Response {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.connectTimeout = 15_000
            connection.readTimeout = 15_000
            connection.setRequestProperty("apikey", key)
            if (key.startsWith("eyJ")) connection.setRequestProperty("Authorization", "Bearer $key")
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.setRequestProperty("Prefer", "return=minimal")
            if (body != null) {
                connection.doOutput = true
                connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            return Response(code, stream?.bufferedReader()?.use { it.readText() }.orEmpty())
        } finally {
            connection.disconnect()
        }
    }

    private data class Response(val code: Int, val body: String)

    companion object {
        const val KEY_HOMEWORK_ID = "homework_id"
        const val KEY_STUDENT_ID = "student_id"
    }
}
