package com.example.kapsama20

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

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

        val url = "$endpoint/rest/v1/odevler?select=id,baslik,video_url,sube,odev_turu&order=id.desc"
        try {
            val connection = URL(url).openConnection() as HttpURLConnection
            try {
                connection.requestMethod = "GET"
                connection.connectTimeout = 15_000
                connection.readTimeout = 15_000
                connection.setRequestProperty("apikey", key)
                if (key.startsWith("eyJ")) connection.setRequestProperty("Authorization", "Bearer $key")
                val code = connection.responseCode
                if (code !in 200..299) {
                    return@withContext HomeworkResult(cached, true, "Ödevler alınamadı (HTTP $code).")
                }
                val json = connection.inputStream.bufferedReader().use { it.readText() }
                val rows = parse(json)
                preferences.edit().putString(CACHE_KEY, json).apply()
                HomeworkResult(rows, false)
            } finally {
                connection.disconnect()
            }
        } catch (_: IOException) {
            HomeworkResult(cached, true, "Bağlantı yok; kayıtlı ödevler gösteriliyor.")
        } catch (_: org.json.JSONException) {
            HomeworkResult(cached, true, "Ödev verisi okunamadı.")
        }
    }

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
