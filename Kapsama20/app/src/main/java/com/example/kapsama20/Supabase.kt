package com.example.kapsama20

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import kotlin.math.roundToInt

/** Supabase tablosuna "varsa güncelle, yoksa ekle" (birincil anahtara göre). HTTP kodunu döner; 0 = bağlantı yok. */
object SupabaseYaz {
    suspend fun upsert(context: Context, tablo: String, govde: JSONObject): Int = withContext(Dispatchers.IO) {
        val endpoint = BuildConfig.SUPABASE_URL.trimEnd('/')
        val key = BuildConfig.SUPABASE_ANON_KEY
        if (!endpoint.startsWith("https://") || key.isBlank()) return@withContext 0
        if (!AuthRepository.isCurrentStudent(context, govde.optString("ogrenci_id"))) return@withContext 409
        var token = AuthRepository.currentAccessToken(context) ?: return@withContext 401
        try {
            var kod = gonder("$endpoint/rest/v1/$tablo", key, token, govde)
            if (kod == 401) {
                token = AuthRepository.currentAccessToken(context, forceRefresh = true) ?: return@withContext 401
                kod = gonder("$endpoint/rest/v1/$tablo", key, token, govde)
            }
            kod
        } catch (_: IOException) {
            0
        }
    }

    private fun gonder(url: String, key: String, token: String, govde: JSONObject): Int {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.requestMethod = "POST"
            c.doOutput = true
            c.connectTimeout = 15_000
            c.readTimeout = 15_000
            c.setRequestProperty("apikey", key)
            c.setRequestProperty("Authorization", "Bearer $token")
            c.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            c.setRequestProperty("Prefer", "resolution=merge-duplicates,return=minimal")
            c.outputStream.use { it.write(govde.toString().toByteArray(Charsets.UTF_8)) }
            val kod = c.responseCode
            (if (kod in 200..299) c.inputStream else c.errorStream)?.use { it.readBytes() }
            return kod
        } finally {
            c.disconnect()
        }
    }
}

/** Öğrencinin bildirdiği aylık mobil veri kotası. Telefonda saklanır ve öğretmene gönderilir. */
object KotaTercihi {
    val SECENEKLER = listOf("1 GB", "2 GB", "4 GB", "8 GB", "16 GB", "32 GB", "Sınırsız")

    private fun prefs(context: Context) = context.getSharedPreferences("kota", Context.MODE_PRIVATE)

    fun oku(context: Context, studentId: String): String? = prefs(context).getString("kota_$studentId", null)

    private fun gb(secim: String?): Double? = secim?.removeSuffix(" GB")?.trim()?.toDoubleOrNull()

    /** 2 GB ve altı: arka plan hız testi mobil veriyle yapılmaz. */
    fun dusukMu(context: Context, studentId: String): Boolean = gb(oku(context, studentId))?.let { it <= 2.0 } ?: false

    /** Bir dosyanın aylık kotanın yüzde kaçı olduğu; kota bilinmiyor ya da sınırsızsa null. */
    fun yuzde(secim: String?, boyutMb: Double): Int? {
        val g = gb(secim) ?: return null
        return (boyutMb / (g * 1024) * 100).roundToInt()
    }

    fun kaydet(context: Context, studentId: String, secim: String) {
        require(secim in SECENEKLER)
        prefs(context).edit().putString("kota_$studentId", secim).apply()
        val work = androidx.work.OneTimeWorkRequestBuilder<DurumSyncWorker>()
            .setConstraints(androidx.work.Constraints.Builder().setRequiredNetworkType(androidx.work.NetworkType.CONNECTED).build())
            .setInputData(androidx.work.workDataOf("student_id" to studentId, "kind" to "kota"))
            .build()
        androidx.work.WorkManager.getInstance(context).enqueueUniqueWork(
            "kota-$studentId", androidx.work.ExistingWorkPolicy.REPLACE, work)
    }

    fun govde(context: Context, studentId: String): JSONObject? {
        val secim = oku(context, studentId) ?: return null
        val g = gb(secim)
        return JSONObject()
            .put("ogrenci_id", studentId)
            .put("kota_gb", g ?: JSONObject.NULL)
            .put("sinirsiz", g == null)
            .put("guncelleme", Instant.now().toString())
    }
}

/** Çevrimdışı açılma/indirilme/kota bildirimlerini kalıcı kuyruktan gönderir. */
class DurumSyncWorker(context: Context, params: androidx.work.WorkerParameters) : androidx.work.CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val studentId = inputData.getString("student_id") ?: return Result.failure()
        val kind = inputData.getString("kind") ?: return Result.failure()
        val body = when (kind) {
            "kota" -> KotaTercihi.govde(applicationContext, studentId) ?: return Result.success()
            "acildi", "indirildi" -> OdevDosyalari.durum(studentId, inputData.getLong("odev_id", -1)).put(kind, true)
            else -> return Result.failure()
        }
        return when (SupabaseYaz.upsert(applicationContext, if (kind == "kota") "ogrenci_kota" else "odev_durumu", body)) {
            in 200..299 -> Result.success()
            0, 401, 408, 409, 429, in 500..599 -> Result.retry()
            else -> Result.failure()
        }
    }
}
