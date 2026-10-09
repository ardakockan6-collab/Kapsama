# Kapsama 2.0

Android Studio ile `Kapsama20` klasörünü açın. Minimum Android sürümü API 28'dir.

## Supabase ayarı

`Kapsama20/local.properties` dosyasına aşağıdaki satırları ekleyin (Android Studio'nun oluşturduğu `sdk.dir` satırını koruyun):

```properties
supabase.url=https://PROJE.supabase.co
supabase.key=SUPABASE_PUBLISHABLE_KEY
```

Bu dosya Git'e eklenmez. Supabase ayarı olmadan uygulama derlenir ancak ölçümler yüklenemez.

## Ölçüm

Emülatörde DEMO modu temsili RSRP, SINR, RSRQ ve 5,0–80,0 Mbps değerleri üretir. DEMO ölçümleri de `olcumler` tablosuna gönderilir. Gerçek cihazda DEMO modu kapatıldığında Android'in erişebildiği hücresel sinyal değerleri kullanılır; hız testi yapılmaz. Konum okunmaz; `enlem` ve `boylam` değerleri `0.0` gönderilir.
## Web ödevlerinin mobilde görünmesi

Mobil uygulama `public.odevler` tablosundan `id`, `baslik`, `video_url`, `sube` ve `odev_turu` alanlarını okur. Seçilen şubeye ve `Tüm şubeler` hedefli ödevlere yer verir. Şube seçimi bu prototipte cihazda saklanır; henüz öğrenci hesabı doğrulaması değildir.

Son alınan ödev listesi cihazda saklanır ve bağlantı yokken gösterilir. `video_url` bağlantısı cihazın uygun uygulamasında açılır. Google arama bağlantıları doğrudan video değildir; çevrimdışı video oynatma için cihazdaki dosyayı seçme özelliği ayrıdır.

Öğrenci “Tamamladım, öğretmene gönder” düğmesine bastığında `odev_durumu` tablosuna `odev_id`, öğrenci numarası (`ogrenci_id`) ve `acildi=true` gönderilir. İnternet yoksa WorkManager kaydı bağlantı gelene kadar bekletir. Mevcut tablo öğretmenin ayrı onay kararını saklayan bir alan içermediği için web tarafındaki onay durumu için ayrıca bir sütun ve web arayüzü gerekir.
