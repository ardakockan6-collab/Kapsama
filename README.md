# Yokla

İnternete erişimi sınırlı öğrenciler için Android ödev ve bağlantı takip uygulaması.

**Güncel uygulama: [`Kapsama20/`](Kapsama20/)** — Android Studio'da bu klasörü açın. Kökteki `app/` önceki XML prototipidir.

## Uygulamada neler var?

- Supabase ile öğrenci girişi ve kullanıcıya ait kayıtlar.
- Öğretmenin yayımladığı ödevleri şubeye göre listeleme ve son listeyi çevrimdışı gösterme.
- Supabase Storage videolarını kotasız ağda indirme ve VideoView ile internetsiz oynatma.
- Ödevin indirilmesini, açılmasını ve öğrencinin tamamlandı bildirimini ayrı kaydetme.
- Cihazın sağladığı RSRP, SINR, RSRQ; indirme/yükleme hızı ve HTTP gecikmesi ölçümü.
- Çevrimdışı gönderim kuyruğu, aylık kota bildirimi ve Android koşullarına bağlı akşam ölçümü.
- Material 3 arayüz, dinamik renkler, açık/koyu tema ve Yokla logosu.

GPS/koordinat API'si kullanılmaz; veritabanındaki `enlem` ve `boylam` alanlarına `0.0` gönderilir. Android hücresel sinyal erişimi için konum izni isteyebilir. DEMO modundaki rastgele değerler `kaynak=simulasyon` ile ayrılır; gerçek ölçüm olarak sunulmaz.

## Çalıştırma

1. Android Studio'da **`Kapsama20`** klasörünü açın. Minimum Android API 28, derleme SDK'sı 37'dir.
2. [Mobil kurulum belgesindeki](Kapsama20/README.md) Supabase URL ve publishable/anon anahtarını yerel `local.properties` dosyanıza ekleyin. `sdk.dir` satırını koruyun.
3. [Sunucu kurulumunu](database/mobile/README.md) tamamlayın: mevcut Auth tablolarına `iyilestirmeler.sql` uygulanmalı ve `hiz-testi` Edge Function yayımlanmalıdır. Eski demo SQL'lerini kurulum sırası olarak çalıştırmayın.
4. Supabase Auth'ta öğrenci hesabı gerekir. `app_metadata.ogrenci_id`, öğrencinin tablo kimliğiyle eşleşmelidir. Şifreleri veya sunucuya özel anahtarları depoya koymayın; demo hesabı bilgilerini ekipten alın.
5. Gradle eşitlemesini tamamlayıp **Run ▶** ile çalıştırın.

Windows PowerShell (`Kapsama20` klasöründe):

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest
```

Derlenen APK, mevcut Gradle ayarı nedeniyle Windows'ta `%TEMP%\kapsama20-gradle-app-build\outputs\apk\debug\app-debug.apk` yolundadır. `local.properties`, derleme önbellekleri ve APK dosyaları kaynak depoya eklenmez. Depoyu indirmek tek başına Supabase kurulumu veya giriş hesabı sağlamaz.

## Dosya rehberi

| Yol | İçerik |
| --- | --- |
| [`Kapsama20/`](Kapsama20/) | Güncel Android uygulaması ve testleri |
| [`database/sql/iyilestirmeler.sql`](database/sql/iyilestirmeler.sql) | Yeni ölçüm, ödev, kota alanları ve RLS güncellemesi |
| [`database/mobile/`](database/mobile/) | Sunucu kurulum belgesi, hız testi fonksiyonu ve testleri |
| [`database/sql/README.md`](database/sql/README.md) | Güncel SQL ile eski demo betiklerinin ayrımı |
| Kök `app/` ve Gradle dosyaları | Önceki XML prototipi; sunumda `Kapsama20` kullanılır |

Öğretmen web panelinin kaynak kodu bu depoda bulunmaz; ekip tarafından ayrı geliştirilmiştir.

## Sunumda doğru aktarılması gerekenler

- Sinyal değerleri telefon/modem desteğine bağlıdır; eksik değerler uydurulmaz.
- Hız testi yaklaşık 3,5 MB aktarır. HTTP gecikmesi ICMP ping değildir.
- Android akşam işlerini pil/ağ koşullarına göre geciktirebilir; kesin saatte çalışma garantisi yoktur.
- “Tamamladım, öğretmene gönder” öğrencinin bildirimidir; öğretmenin ayrı onayı değildir.
- Eski sunucu şemasında temel ödev listesi gösterilir; yeni alanlar ve kota/hız testi sunucu kurulumu gerektirir. Eksik ölçüm sütunları nedeniyle gönderilemeyen ölçümler migration tamamlanana kadar kuyrukta tutulur.
