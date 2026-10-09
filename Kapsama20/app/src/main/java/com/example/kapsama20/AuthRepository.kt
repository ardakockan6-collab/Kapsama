package com.example.kapsama20

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class AuthSession(
    val id: String,
    val name: String,
    val email: String,
    val accessToken: String,
    val refreshToken: String,
    val expiresAt: Long,
    val className: String,
    val studentNumber: String
)

object AuthRepository {
    private const val PREFS = "student_auth"

    suspend fun signIn(context: Context, email: String, password: String): AuthSession =
        withContext(Dispatchers.IO) {
            val endpoint = BuildConfig.SUPABASE_URL.trimEnd('/')
            val key = BuildConfig.SUPABASE_ANON_KEY
            require(endpoint.startsWith("https://") && key.isNotBlank()) { "Supabase ayarı eksik." }
            val json = post("$endpoint/auth/v1/token?grant_type=password", key, JSONObject()
                .put("email", email.trim()).put("password", password))
            sessionFrom(json).also { save(context, it) }
        }

    suspend fun restore(context: Context): AuthSession? = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val refresh = prefs.getString("refresh_token", null) ?: return@withContext null
        try {
            val endpoint = BuildConfig.SUPABASE_URL.trimEnd('/')
            val key = BuildConfig.SUPABASE_ANON_KEY
            val json = post("$endpoint/auth/v1/token?grant_type=refresh_token", key,
                JSONObject().put("refresh_token", refresh))
            sessionFrom(json).also { save(context, it) }
        } catch (e: AuthApiException) {
            if (e.statusCode in 400..499) {
                prefs.edit().clear().apply()
                null
            } else cachedSession(prefs, refresh)
        } catch (_: Exception) {
            cachedSession(prefs, refresh)
        }
    }

    fun signOut(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }

    suspend fun currentAccessToken(context: Context, forceRefresh: Boolean = false): String? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val cachedToken = prefs.getString("access_token", "").orEmpty()
        val expiresAt = prefs.getLong("expires_at", 0L)
        if (!forceRefresh && cachedToken.isNotBlank() && expiresAt > System.currentTimeMillis() / 1000 + 60) {
            return cachedToken
        }
        return restore(context)?.takeIf { it.expiresAt > System.currentTimeMillis() / 1000 }
            ?.accessToken?.takeIf { it.isNotBlank() }
    }

    private fun save(context: Context, session: AuthSession) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("refresh_token", session.refreshToken)
            .putString("access_token", session.accessToken)
            .putLong("expires_at", session.expiresAt)
            .putString("student_id", session.id)
            .putString("name", session.name)
            .putString("email", session.email)
            .putString("class_name", session.className)
            .putString("student_number", session.studentNumber)
            .apply()
    }

    private fun cachedSession(prefs: android.content.SharedPreferences, refresh: String): AuthSession? {
        val studentId = prefs.getString("student_id", null) ?: return null
        return AuthSession(
            id = studentId,
            name = prefs.getString("name", "Öğrenci") ?: "Öğrenci",
            email = prefs.getString("email", "") ?: "",
            accessToken = prefs.getString("access_token", "") ?: "",
            refreshToken = refresh,
            expiresAt = prefs.getLong("expires_at", 0L),
            className = prefs.getString("class_name", "Şube seçilmedi") ?: "Şube seçilmedi",
            studentNumber = prefs.getString("student_number", studentId) ?: studentId
        )
    }

    private fun sessionFrom(json: JSONObject): AuthSession {
        val user = json.getJSONObject("user")
        val metadata = user.optJSONObject("user_metadata") ?: JSONObject()
        val appMetadata = user.optJSONObject("app_metadata") ?: JSONObject()
        val studentId = appMetadata.optString("ogrenci_id").trim()
        require(studentId.isNotBlank()) {
            "Giriş hesabında app_metadata.ogrenci_id tanımlı değil. Öğrenci hesabını Supabase'de güncelleyin."
        }
        val email = user.optString("email")
        val name = metadata.optString("full_name").ifBlank {
            metadata.optString("name").ifBlank { email.ifBlank { "Öğrenci" } }
        }
        return AuthSession(
            id = studentId, name = name, email = email,
            accessToken = json.getString("access_token"),
            refreshToken = json.getString("refresh_token"),
            expiresAt = json.optLong("expires_at", System.currentTimeMillis() / 1000 + json.optLong("expires_in", 3600L)),
            className = metadata.optString("class_name").ifBlank { "Şube seçilmedi" },
            studentNumber = studentId
        )
    }

    private fun post(url: String, key: String, body: JSONObject): JSONObject {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 15_000
            connection.readTimeout = 15_000
            connection.doOutput = true
            connection.setRequestProperty("apikey", key)
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val response = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                val errorJson = runCatching { JSONObject(response) }.getOrNull()
                val message = errorJson?.optString("msg")?.ifBlank { null }
                    ?: errorJson?.optString("message")?.ifBlank { null }
                    ?: errorJson?.optString("error_description")?.ifBlank { null }
                    ?: "Supabase isteği başarısız (HTTP $code)."
                throw AuthApiException(code, message)
            }
            return JSONObject(response)
        } finally {
            connection.disconnect()
        }
    }

    private class AuthApiException(val statusCode: Int, message: String) : IllegalStateException(message)
}
