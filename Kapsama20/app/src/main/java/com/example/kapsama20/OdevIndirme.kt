package com.example.kapsama20

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Ödev videoları Android'in kotasız saydığı ağda iner; evde internetsiz izlenir.
 * İndirme bitince odev_durumu.indirildi = true yazılır; video açılınca acildi = true.
 */
object OdevDosyalari {
    const val ETIKET = "odev-indir"

    fun dosya(context: Context, odevId: Long): File =
        File(File(context.filesDir, "odevler").apply { mkdirs() }, "odev_$odevId.mp4")

    fun indirildiMi(context: Context, odevId: Long): Boolean =
        dosya(context, odevId).let { it.exists() && it.length() > 0 }

    /** Sadece Supabase Storage'daki dosyalar indirilir (arama sonucu gibi sayfa linkleri değil). */
    fun indirilebilir(url: String?): Boolean = runCatching {
        val uri = java.net.URI(url ?: return false)
        uri.scheme == "https" && uri.host == java.net.URI(BuildConfig.SUPABASE_URL).host &&
            uri.rawPath.startsWith("/storage/v1/object/public/odevler/") && uri.userInfo == null
    }.getOrDefault(false)

    fun planla(context: Context, studentId: String, odev: Homework) {
        val url = odev.videoUrl ?: return
        if (!indirilebilir(url)) return
        if (indirildiMi(context, odev.id)) {
            durumPlanla(context, studentId, odev.id, "indirildi")
            return
        }
        val istek = OneTimeWorkRequestBuilder<OdevIndirWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.UNMETERED).build())
            .setInputData(workDataOf(
                OdevIndirWorker.KEY_ODEV to odev.id,
                OdevIndirWorker.KEY_URL to url,
                OdevIndirWorker.KEY_OGRENCI to studentId
            ))
            .addTag(ETIKET)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork("$ETIKET-$studentId-${odev.id}", ExistingWorkPolicy.KEEP, istek)
    }

    /** Sadece mesaj ödevi: içerik ödev listesiyle zaten telefona indi; bir kez "indirildi" yazılır. */
    suspend fun mesajAlindi(context: Context, studentId: String, odevId: Long) {
        durumPlanla(context, studentId, odevId, "indirildi")
    }

    suspend fun acildi(context: Context, studentId: String, odevId: Long) {
        durumPlanla(context, studentId, odevId, "acildi")
    }

    private fun durumPlanla(context: Context, studentId: String, odevId: Long, field: String) {
        val work = OneTimeWorkRequestBuilder<DurumSyncWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setInputData(workDataOf("student_id" to studentId, "odev_id" to odevId, "kind" to field))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork("$field-$studentId-$odevId", ExistingWorkPolicy.KEEP, work)
    }

    fun durum(studentId: String, odevId: Long): JSONObject =
        JSONObject().put("ogrenci_id", studentId).put("odev_id", odevId)
}

class OdevIndirWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val odevId = inputData.getLong(KEY_ODEV, -1L)
        val url = inputData.getString(KEY_URL)
        val ogrenci = inputData.getString(KEY_OGRENCI)
        if (odevId < 0 || url.isNullOrBlank() || ogrenci.isNullOrBlank() || !OdevDosyalari.indirilebilir(url)) {
            return@withContext Result.failure(workDataOf("error" to "İndirme bilgileri eksik."))
        }
        if (!AuthRepository.isCurrentStudent(applicationContext, ogrenci)) return@withContext Result.retry()
        val hedef = OdevDosyalari.dosya(applicationContext, odevId)
        if (!(hedef.exists() && hedef.length() > 0)) {
            val gecici = File(hedef.parentFile, hedef.name + ".$id.part")
            try {
                val c = URL(url).openConnection() as HttpURLConnection
                try {
                    c.connectTimeout = 15_000
                    c.readTimeout = 60_000
                    val kod = c.responseCode
                    if (kod !in 200..299) {
                        return@withContext if (kod == 408 || kod == 429 || kod in 500..599) Result.retry()
                        else Result.failure(workDataOf("error" to "Video indirilemedi (HTTP $kod)."))
                    }
                    c.inputStream.use { girdi -> gecici.outputStream().use { cikti -> girdi.copyTo(cikti, 64 * 1024) } }
                } finally {
                    c.disconnect()
                }
                if (gecici.length() == 0L || !gecici.renameTo(hedef)) {
                    gecici.delete()
                    return@withContext Result.retry()
                }
            } catch (_: IOException) {
                gecici.delete()
                return@withContext Result.retry()
            }
        }
        val kod = SupabaseYaz.upsert(applicationContext, "odev_durumu",
            OdevDosyalari.durum(ogrenci, odevId).put("indirildi", true))
        when (kod) {
            in 200..299 -> Result.success()
            0, 401, 408, 409, 429, in 500..599 -> Result.retry()
            else -> Result.failure(workDataOf("error" to "İndirildi bilgisi kaydedilemedi (HTTP $kod)."))
        }
    }

    companion object {
        const val KEY_ODEV = "odev_id"
        const val KEY_URL = "url"
        const val KEY_OGRENCI = "ogrenci_id"
    }
}
