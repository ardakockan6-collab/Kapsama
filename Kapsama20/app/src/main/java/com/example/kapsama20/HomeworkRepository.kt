package com.example.kapsama20

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class Homework(
    val id: Long,
    val title: String,
    val videoUrl: String?,
    val type: String?,
    val section: String?
)

data class HomeworkResult(
    val items: List<Homework>,
    val fromCache: Boolean,
    val message: String? = null
)

object HomeworkRepository {
    private const val CACHE_KEY = "homework_json"

    suspend fun load(context: Context): HomeworkResult = withContext(Dispatchers.IO) {
        val preferences = context.getSharedPreferences("homework", Context.MODE_PRIVATE)
        val cached = runCatching { parse(preferences.getString(CACHE_KEY, null)) }.getOrDefault(emptyList())
        val endpoint = BuildConfig.SUPABASE_URL.trimEnd('/')
        val key = BuildConfig.SUPABASE_ANON_KEY
        if (!endpoint.startsWith("https://") || key.isBlank()) {
            return@withContext HomeworkResult(cached, true, "Supabase ayarı eksik.")
        }
        var accessToken = AuthRepository.currentAccessToken(context)
            ?: return@withContext HomeworkResult(cached, true, "Oturum yenilenemedi; yeniden giriş yapın.")

        val url = "$endpoint/rest/v1/odevler?select=id,baslik,video_url,sube,odev_turu&order=id.desc"
        try {
            var response = get(url, key, accessToken)
            if (response.code == 401) {
                accessToken = AuthRepository.currentAccessToken(context, forceRefresh = true)
                    ?: return@withContext HomeworkResult(cached, true, "Oturum süresi doldu; yeniden giriş yapın.")
                response = get(url, key, accessToken)
            }
            if (response.code !in 200..299) {
                return@withContext HomeworkResult(cached, true, "Ödevler alınamadı (HTTP ${response.code}).")
            }
            val rows = parse(response.body)
            preferences.edit().putString(CACHE_KEY, response.body).apply()
            HomeworkResult(rows, false)
        } catch (_: IOException) {
            HomeworkResult(cached, true, "Bağlantı yok; kayıtlı ödevler gösteriliyor.")
        } catch (_: org.json.JSONException) {
            HomeworkResult(cached, true, "Ödev verisi okunamadı.")
        }
    }

    suspend fun loadSubmittedIds(context: Context, studentId: String): Set<Long> = withContext(Dispatchers.IO) {
        val endpoint = BuildConfig.SUPABASE_URL.trimEnd('/')
        val key = BuildConfig.SUPABASE_ANON_KEY
        if (!endpoint.startsWith("https://") || key.isBlank()) return@withContext emptySet()
        var accessToken = AuthRepository.currentAccessToken(context) ?: return@withContext emptySet()
        val encodedStudent = URLEncoder.encode(studentId, Charsets.UTF_8.name())
        val url = "$endpoint/rest/v1/odev_durumu?select=odev_id&ogrenci_id=eq.$encodedStudent&acildi=eq.true"
        try {
            var response = get(url, key, accessToken)
            if (response.code == 401) {
                accessToken = AuthRepository.currentAccessToken(context, forceRefresh = true) ?: return@withContext emptySet()
                response = get(url, key, accessToken)
            }
            if (response.code !in 200..299) return@withContext emptySet()
            val rows = JSONArray(response.body)
            buildSet { for (index in 0 until rows.length()) add(rows.getJSONObject(index).getLong("odev_id")) }
        } catch (_: Exception) {
            emptySet()
        }
    }

    suspend fun loadStudentGrade(context: Context, studentId: String): Int? = withContext(Dispatchers.IO) {
        val endpoint = BuildConfig.SUPABASE_URL.trimEnd('/')
        val key = BuildConfig.SUPABASE_ANON_KEY
        if (!endpoint.startsWith("https://") || key.isBlank()) return@withContext null
        var accessToken = AuthRepository.currentAccessToken(context) ?: return@withContext null
        val encodedStudent = URLEncoder.encode(studentId, Charsets.UTF_8.name())
        val url = "$endpoint/rest/v1/ogrenci_sinif?select=sinif&ogrenci_id=eq.$encodedStudent"
        try {
            var response = get(url, key, accessToken)
            if (response.code == 401) {
                accessToken = AuthRepository.currentAccessToken(context, forceRefresh = true)
                    ?: return@withContext null
                response = get(url, key, accessToken)
            }
            if (response.code !in 200..299) return@withContext null
            val rows = JSONArray(response.body)
            if (rows.length() == 0) null else rows.getJSONObject(0).optInt("sinif").takeIf { it in 1..4 }
        } catch (_: Exception) {
            null
        }
    }

    private fun get(url: String, key: String, accessToken: String): HttpResponse {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 15_000
            connection.readTimeout = 15_000
            connection.setRequestProperty("apikey", key)
            connection.setRequestProperty("Authorization", "Bearer $accessToken")
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            return HttpResponse(code, stream?.bufferedReader()?.use { it.readText() }.orEmpty())
        } finally {
            connection.disconnect()
        }
    }

    private data class HttpResponse(val code: Int, val body: String)

    private fun parse(json: String?): List<Homework> {
        if (json.isNullOrBlank()) return emptyList()
        val rows = JSONArray(json)
        return buildList {
            for (index in 0 until rows.length()) {
                val row = rows.getJSONObject(index)
                add(
                    Homework(
                        id = row.getLong("id"),
                        title = row.optString("baslik").ifBlank { "Başlıksız ödev" },
                        videoUrl = row.optionalText("video_url"),
                        type = row.optionalText("odev_turu"),
                        section = row.optionalText("sube")
                    )
                )
            }
        }
    }

    private fun JSONObject.optionalText(name: String): String? =
        if (isNull(name)) null else optString(name).trim().takeIf { it.isNotEmpty() }
}
