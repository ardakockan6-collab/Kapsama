package com.example.kapsama20

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import java.time.LocalDate
import java.time.LocalTime
import java.util.concurrent.TimeUnit

/**
 * Ödev saati ölçümü: okulda yapılan test, öğrencinin akşam evde ödev yaparkenki
 * bağlantısını göstermez (akşam hücre dolar). Bu iş saatte bir uyanır; 19:00–22:59
 * arasında günde bir kez sinyal + hız testi yapıp gönderir.
 * Kotası 2 GB veya altındaysa mobil veriyle hız testi yapılmaz (kotayı yemesin).
 */
class AksamOlcumWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val zorla = inputData.getBoolean(KEY_ZORLA, false)
        val saat = LocalTime.now().hour
        if (!zorla && saat !in AKSAM_BAS until AKSAM_BIT) return Result.success()

        val prefs = applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val bugun = LocalDate.now().toString()

        val studentId = applicationContext.getSharedPreferences("student_auth", Context.MODE_PRIVATE)
            .getString("student_id", null) ?: return Result.success()

        if (!zorla && prefs.getString("son_gun_$studentId", null) == bugun) return Result.success()

        // Arka planda hücre bilgisi erişilemeyebilir; koordinat okunmaz.
        val sinyal = runCatching { readRadio(applicationContext) }.getOrNull()
        val ag = agTuru(applicationContext)
        val dusukKota = ag != "wifi" && (KotaTercihi.oku(applicationContext, studentId) == null || KotaTercihi.dusukMu(applicationContext, studentId))
        val hiz = HizTesti.calistir(applicationContext, sadeceGecikme = dusukKota)

        if (sinyal == null && hiz.indirmeMbps == null && hiz.pingMs == null) {
            return if (runAttemptCount < 2) Result.retry() else Result.success()
        }
        val okuma = (sinyal ?: RadioReading(null, null, null, "Bilinmiyor")).copy(
            mbps = hiz.indirmeMbps, upMbps = hiz.yuklemeMbps, pingMs = hiz.pingMs, ag = hiz.ag
        )
        val tur = if (zorla) "odev_saati_deneme" else "odev_saati"
        WorkManager.getInstance(applicationContext).enqueue(olcumIsi(studentId, okuma, tur))
        if (!zorla) prefs.edit().putString("son_gun_$studentId", bugun).apply()
        return Result.success()
    }

    companion object {
        const val KEY_ZORLA = "zorla"
        const val AKSAM_BAS = 19
        const val AKSAM_BIT = 23
        private const val PREFS = "aksam_olcum"
        private const val AD = "aksam-olcum"

        /** Girişten sonra bir kez çağrılır; saatte bir kontrol eden periyodik işi kurar. */
        fun planla(context: Context) {
            val istek = PeriodicWorkRequestBuilder<AksamOlcumWorker>(1, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(AD, ExistingPeriodicWorkPolicy.KEEP, istek)
        }

        /** Demo ve test için: saat kontrolü olmadan hemen bir ödev saati ölçümü yapar. */
        fun simdiCalistir(context: Context) {
            val istek = OneTimeWorkRequestBuilder<AksamOlcumWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setInputData(workDataOf(KEY_ZORLA to true))
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork("$AD-simdi", ExistingWorkPolicy.KEEP, istek)
        }
    }
}
