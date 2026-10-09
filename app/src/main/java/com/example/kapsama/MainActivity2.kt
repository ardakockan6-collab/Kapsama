package com.example.kapsama

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import org.json.JSONObject
import java.time.Instant

class MainActivity2 : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main2)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val btnOlc = findViewById<Button>(R.id.btnOlc)
        val txtDurum = findViewById<TextView>(R.id.txtDurum)

        btnOlc.setOnClickListener {
            val satir = JSONObject().apply {
                put("ogrenci_id", "A")      // ogrenciler.id sayıysa: put("ogrenci_id", 1)
                put("zaman", Instant.now().toString())
                put("enlem", 41.0); put("boylam", 39.77)
                put("rsrp", -95); put("sinr", 10); put("rsrq", -11)
                put("hiz_mbps", JSONObject.NULL)
            }
            txtDurum.text = "Gönderiliyor..."
            Thread {
                val bekleyen = Supabase.olcumGonder(this, satir)
                runOnUiThread {
                    txtDurum.text = if (bekleyen == 0) "Gönderildi ✓" else "Kuyrukta: $bekleyen"
                }
            }.start()
        }

        // Durum yazısına uzun bas: kuyruğu temizler
        txtDurum.setOnLongClickListener {
            Supabase.kuyruguTemizle(this); txtDurum.text = "Kuyruk temizlendi"; true
        }
    }
}