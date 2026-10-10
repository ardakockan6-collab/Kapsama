package com.example.kapsama20

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.telephony.CellInfo
import android.telephony.CellInfoLte
import android.telephony.CellInfoNr
import android.telephony.CellSignalStrengthLte
import android.telephony.CellSignalStrengthNr
import android.telephony.TelephonyManager
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.workDataOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import kotlin.random.Random

/* ---------- Eşikler (panel ile aynı) ---------- */
const val CANLI_MIN_MBPS = 10.0
const val VIDEO_MIN_MBPS = 2.0
const val MESAJ_MIN_MBPS = 0.1
const val CANLI_YUKLEME_MIN = 1.0
const val CANLI_PING_MAX = 150.0

data class HizSonucu(
    val indirmeMbps: Double?,
    val yuklemeMbps: Double?,
    val pingMs: Double?,
    val ag: String?
)

/* ---------- Sinyal ---------- */
internal fun readRadio(context: Context): RadioReading? {
    if (context.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) !=
        android.content.pm.PackageManager.PERMISSION_GRANTED) return null
    return try {
        readRadioWithPermission(context)
    } catch (_: SecurityException) {
        null
    }
}

@androidx.annotation.RequiresPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)
private fun readRadioWithPermission(context: Context): RadioReading? {
    val manager = context.getSystemService(TelephonyManager::class.java) ?: return null
    val cells = manager.allCellInfo ?: return null
    fun valid(value: Int) = value.takeUnless { it == Int.MAX_VALUE }
    if (android.os.Build.VERSION.SDK_INT >= 29) {
        val nr = cells.filterIsInstance<CellInfoNr>().firstOrNull { it.isRegistered }
        if (nr != null) {
            val signal = manager.signalStrength
                ?.getCellSignalStrengths(CellSignalStrengthNr::class.java)?.firstOrNull()
                ?: (nr.cellSignalStrength as CellSignalStrengthNr)
            return RadioReading(
                valid(signal.ssRsrp), valid(signal.ssSinr), valid(signal.ssRsrq), "5G NR",
                level = signal.level
            )
        }
    }
    val lte = cells.filterIsInstance<CellInfoLte>().firstOrNull { it.isRegistered } ?: return null
    val signal = if (android.os.Build.VERSION.SDK_INT >= 29) {
        manager.signalStrength?.getCellSignalStrengths(CellSignalStrengthLte::class.java)?.firstOrNull()
            ?: lte.cellSignalStrength
    } else lte.cellSignalStrength
    return RadioReading(valid(signal.rsrp), valid(signal.rssnr), valid(signal.rsrq), "4G LTE", level = signal.level)
}

/* ---------- Ağ türü: hız testi Wi-Fi'da mı mobilde mi yapıldı ---------- */
internal fun agTuru(context: Context): String? {
    val cm = context.getSystemService(ConnectivityManager::class.java) ?: return null
    val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return null
    return when {
        caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
        caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "mobil"
        else -> null
    }
}

/* ---------- Hız testi: indirme (3 MB), yükleme (512 KB), HTTP gecikmesi (5 başarılı ölçüm ortancası) ----------
   Bir test yaklaşık 3,5 MB veri harcar. */
object HizTesti {
    private const val YUKLEME_BAYT = 512 * 1024

    suspend fun calistir(context: Context, sadeceGecikme: Boolean = false): HizSonucu = withContext(Dispatchers.IO) {
        val endpoint = BuildConfig.SUPABASE_URL.trimEnd('/')
        val key = BuildConfig.SUPABASE_ANON_KEY
        val ag = agTuru(context)
        val ping = runCatching { gecikme(endpoint, key) }.getOrNull()
        if (sadeceGecikme) return@withContext HizSonucu(null, null, ping, ag)
        val indirme = runCatching { indir("$endpoint/storage/v1/object/public/odevler/hiztesti.bin") }.getOrNull()
        val token = AuthRepository.currentAccessToken(context)
        val yukleme = if (token == null) null
            else runCatching { yukle("$endpoint/functions/v1/hiz-testi", key, token) }.getOrNull()
        HizSonucu(indirme, yukleme, ping, ag)
    }

    private fun gecikme(endpoint: String, key: String): Double? {
        val sureler = mutableListOf<Double>()
        repeat(6) { i ->
            val c = URL("$endpoint/auth/v1/health").openConnection() as HttpURLConnection
            try {
                c.connectTimeout = 5_000
                c.readTimeout = 5_000
                c.useCaches = false
                c.setRequestProperty("apikey", key)
                val t0 = System.nanoTime()
                val kod = c.responseCode
                (if (kod in 200..299) c.inputStream else c.errorStream)?.use { it.readBytes() }
                val ms = (System.nanoTime() - t0) / 1e6
                if (i > 0 && kod in 200..299) sureler.add(ms) // ilk istek bağlantı kurulumunu içerir, sayılmaz
            } finally {
                c.disconnect()
            }
        }
        return sureler.sorted().getOrNull(sureler.size / 2)
    }

    private fun indir(url: String): Double? {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 10_000
            c.readTimeout = 30_000
            c.useCaches = false
            val t0 = System.nanoTime()
            if (c.responseCode !in 200..299) return null
            var toplam = 0L
            val tampon = ByteArray(64 * 1024)
            c.inputStream.use { girdi ->
                while (true) {
                    val n = girdi.read(tampon)
                    if (n < 0) break
                    toplam += n
                }
            }
            val sn = (System.nanoTime() - t0) / 1e9
            return if (toplam > 0 && sn > 0) toplam * 8.0 / (sn * 1_000_000) else null
        } finally {
            c.disconnect()
        }
    }

    private fun yukle(url: String, key: String, token: String): Double? {
        // Isınma: sunucu fonksiyonu ilk çağrıda geç uyanır; bu süre ölçüme girmesin
        runCatching {
            val w = URL(url).openConnection() as HttpURLConnection
            try {
                w.connectTimeout = 10_000
                w.readTimeout = 10_000
                w.setRequestProperty("apikey", key)
                w.setRequestProperty("Authorization", "Bearer $token")
                w.responseCode
            } finally {
                w.disconnect()
            }
        }
        val veri = Random.nextBytes(YUKLEME_BAYT)
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.requestMethod = "POST"
            c.doOutput = true
            c.connectTimeout = 10_000
            c.readTimeout = 60_000
            c.setFixedLengthStreamingMode(veri.size)
            c.setRequestProperty("apikey", key)
            c.setRequestProperty("Authorization", "Bearer $token")
            c.setRequestProperty("Content-Type", "application/octet-stream")
            val t0 = System.nanoTime()
            c.outputStream.use { it.write(veri) }
            val kod = c.responseCode
            val sn = (System.nanoTime() - t0) / 1e9
            if (kod !in 200..299 || sn <= 0) return null
            return veri.size * 8.0 / (sn * 1_000_000)
        } finally {
            c.disconnect()
        }
    }
}

/* ---------- Ölçümü Supabase'e gönderecek iş ---------- */
internal fun olcumIsi(studentId: String, okuma: RadioReading, tur: String = "elle"): OneTimeWorkRequest =
    OneTimeWorkRequestBuilder<UploadWorker>()
        .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
        .setInputData(workDataOf(
            UploadWorker.KEY_STUDENT_ID to studentId,
            UploadWorker.KEY_RSRP to (okuma.rsrp ?: Int.MIN_VALUE),
            UploadWorker.KEY_SINR to (okuma.sinr ?: Int.MIN_VALUE),
            UploadWorker.KEY_RSRQ to (okuma.rsrq ?: Int.MIN_VALUE),
            "mbps" to (okuma.mbps ?: Double.NaN),
            UploadWorker.KEY_UP to (okuma.upMbps ?: Double.NaN),
            UploadWorker.KEY_PING to (okuma.pingMs ?: Double.NaN),
            UploadWorker.KEY_AG to okuma.ag,
            UploadWorker.KEY_DEMO to okuma.isDemo,
            UploadWorker.KEY_TUR to tur,
            UploadWorker.KEY_TIME to System.currentTimeMillis()
        ))
        .build()

/* ---------- Sinyal ve bağlantı değerlendirmesi: telefonun çubuğu ve ödev kararı ---------- */
internal fun cubukMetni(level: Int?): String? {
    val l = level ?: return null
    val dolu = l.coerceIn(0, 4)
    return "▮".repeat(dolu) + "▯".repeat(4 - dolu) + " ($dolu/4)"
}

internal fun kararMetni(r: RadioReading): String? {
    val d = r.mbps ?: return null
    val yuklemeKotu = r.upMbps != null && r.upMbps < CANLI_YUKLEME_MIN
    val gecikmeKotu = r.pingMs != null && r.pingMs > CANLI_PING_MAX
    return when {
        d >= CANLI_MIN_MBPS && (r.upMbps == null || r.pingMs == null) -> "Video izlenir; canlı ders için yükleme/gecikme ölçümü eksik"
        d >= CANLI_MIN_MBPS && !yuklemeKotu && !gecikmeKotu -> "Canlı ders için tahmini yeterli"
        d >= CANLI_MIN_MBPS && yuklemeKotu -> "Video izlenir ama canlı ders olmaz: yükleme yetersiz"
        d >= CANLI_MIN_MBPS -> "Video izlenir ama canlı ders olmaz: gecikme yüksek"
        d >= VIDEO_MIN_MBPS -> "Video izlenir, canlı ders zorlanır"
        d >= MESAJ_MIN_MBPS -> "Sadece mesaj: video takılır"
        else -> "Ödev için bağlantı yetersiz"
    }
}
