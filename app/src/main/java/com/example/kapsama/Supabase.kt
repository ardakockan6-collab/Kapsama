package com.example.kapsama

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object Supabase {
    private const val SUPA_URL = "https://irjizszyfswkuonawtqi.supabase.co"
    private const val KEY = "sb_publishable_Awd48IEmCDFGSVh2CYlsZA_3QQ6yKei"

    private fun post(tablo: String, govde: String): Boolean = try {
        val c = URL("$SUPA_URL/rest/v1/$tablo").openConnection() as HttpURLConnection
        c.requestMethod = "POST"; c.doOutput = true
        c.connectTimeout = 5000; c.readTimeout = 5000
        c.setRequestProperty("apikey", KEY)
        c.setRequestProperty("Content-Type", "application/json")
        c.setRequestProperty("Prefer", "return=minimal")
        c.outputStream.use { it.write(govde.toByteArray()) }
        val kod = c.responseCode
        if (kod !in 200..299)
            Log.e("SUPA", "$kod ${c.errorStream?.bufferedReader()?.readText()}")
        kod in 200..299
    } catch (e: Exception) { Log.e("SUPA", "ağ hatası", e); false }

    /** Satırı kuyruğa ekler, kuyruğun tamamını gönderir. Dönen sayı: bekleyen satır. */
    @Synchronized
    fun olcumGonder(ctx: Context, satir: JSONObject): Int {
        val p = ctx.getSharedPreferences("kuyruk", Context.MODE_PRIVATE)
        val kuyruk = JSONArray(p.getString("satirlar", "[]"))
        kuyruk.put(satir)
        p.edit().putString("satirlar", kuyruk.toString()).commit()
        if (post("olcumler", kuyruk.toString())) {
            p.edit().putString("satirlar", "[]").commit()
            return 0
        }
        return kuyruk.length()
    }

    fun kuyruguTemizle(ctx: Context) =
        ctx.getSharedPreferences("kuyruk", Context.MODE_PRIVATE)
            .edit().putString("satirlar", "[]").commit()
}

