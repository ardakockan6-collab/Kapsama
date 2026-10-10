# Mobil iyileştirmeler için Supabase kurulumu

Bu dizindeki fonksiyon ve `../sql/iyilestirmeler.sql`, mevcut Auth kurulumuna ek olarak gereklidir. GitHub'a yüklemek bunları Supabase'de çalıştırmaz.

## 1. SQL

Mevcut proje üzerinde Supabase → SQL Editor → New query açıp `database/sql/iyilestirmeler.sql` içeriğini çalıştırın. Dosya anonim tablo erişimini kaldırır, öğrenciye ait ölçüm yazma politikasını kurar; ölçüm, son teslim, dosya boyutu ve kota alanlarını ekler. `benim_ogrenci_id()`, `is_ogretmen()` ve mevcut tablolar önceden bulunmalıdır.

Eski demo SQL'lerini yeniden çalıştırmayın. Yeni dosyanın sonundaki kontrol sorgusunu kullanarak dört yeni ölçüm sütununu, iki ödev sütununu ve kota tablosunu doğrulayın.

## 2. Hız testi

`odevler` adlı public Storage bucket'ında `hiztesti.bin` bulunmalı (yaklaşık 3 MB). Yeni mobil sürüm bu dosyayı indirip gerçek aktarım süresini ölçer.

Supabase → Edge Functions bölümünde editör ile `hiz-testi` adlı fonksiyonu oluşturun. `functions/hiz-testi/index.ts` içeriğini yayımlayın. Fonksiyon kendi Bearer doğrulamasını yaptığı için gateway'deki “Verify JWT” ayarını kapalı kullanın.

Gateway JWT kontrolü kapalı olsa da fonksiyon her istekte Bearer tokenı Supabase Auth `/auth/v1/user` üzerinden doğrular ve `app_metadata.ogrenci_id` ister. Kimliksiz istekler kabul edilmez. `SUPABASE_URL` ve `SUPABASE_ANON_KEY`, Edge ortamının sağladığı standart değişkenlerdir; service-role anahtarı kullanılmaz. Yüklenen rastgele veri sayılıp atılır; dosya veya veritabanı kaydı oluşturulmaz. İstek başına üst sınır 1 MiB'dir.

Yerel HTTP işleyici testleri (Node.js):

```sh
node --test functions/hiz-testi/index.test.cjs
```

Bu testler gerçek Edge dağıtımını ve gerçek kullanıcı girişini doğrulamaz.

## 3. Telefon ve öğretmen paneli kontrolü

1. Güncel `Kapsama20` uygulamasını derleyip öğrenci hesabıyla giriş yapın.
2. DEMO kapalıyken “Ölç + hız testi”ne basın. Yaklaşık 3,5 MB aktarım yapılır; sinyal değerleri cihaz desteğine bağlıdır. Gecikme HTTP isteğinin gidiş-dönüş süresidir, ICMP ping değildir.
3. `olcumler` kaydında öğrenci kimliği, indirme/yükleme/gecikme, `ag`, `olcum_turu` ve `enlem=boylam=0.0` değerlerini kontrol edin.
4. Kotasız ağda video ödevini indirin; uçak modunda “İnternetsiz izle” ile oynatın. Bağlantı geri gelince `odev_durumu.acildi` güncellenir.
5. “Tamamladım, öğretmene gönder” ile `yapildi=true` ve `yapildi_zaman` yazıldığını kontrol edin. Bu öğrenci bildirimi olup öğretmenin ayrı onay kararı değildir.
6. Kota seçin; `ogrenci_kota` kaydını kontrol edin. Web ekibi bu tabloyu ve yeni ölçüm alanlarını panelden okumalıdır; bu depoda öğretmen web arayüzü değiştirilmez.
7. “Ödev saati ölçümünü şimdi dene” ile `olcum_turu=odev_saati_deneme` kaydını kontrol edin. Planlı işler Android pil/ağ koşulları nedeniyle gecikebilir; kesin çalışma saati garanti edilmez.

Uygulama GPS veya koordinat API'si çağırmaz. Hücresel sinyal okuması için Android'in istediği foreground konum izni bulunur; arka plan konum izni eklenmemiştir. Arka planda sinyal alınamazsa yalnızca erişilebilen hız/gecikme değerleri gönderilir. Kota seçilmemiş veya en fazla 2 GB ise arka planda mobil ağ üzerinden büyük hız testi yapılmaz.
